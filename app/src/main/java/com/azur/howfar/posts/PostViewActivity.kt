package com.azur.howfar.posts

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.net.toFile
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.azur.howfar.R
import com.azur.howfar.activity.MainActivity
import com.azur.howfar.databinding.ActivityPostViewBinding
import com.azur.howfar.howfarchat.chat.FragmentDisplayImage
import com.azur.howfar.livedata.ValueEventLiveData
import com.azur.howfar.models.EventListenerType
import com.azur.howfar.models.Moment
import com.azur.howfar.models.MomentDetails
import com.azur.howfar.models.UserProfile
import com.azur.howfar.retrofit.Const
import com.azur.howfar.user.guestuser.GuestActivity
import com.azur.howfar.utils.HFCoinUtils
import com.azur.howfar.utils.HowFarWaterMark
import com.azur.howfar.utils.Util
import com.azur.howfar.viewmodel.UploadPostSpecialViewModel
import com.bumptech.glide.Glide
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.gson.Gson
import com.tonyodev.fetch2.*
import com.tonyodev.fetch2core.DownloadBlock
import org.jetbrains.annotations.NotNull
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.*

class PostViewActivity : AppCompatActivity(), View.OnClickListener {
    private val binding by lazy { ActivityPostViewBinding.inflate(layoutInflater) }
    private val user = FirebaseAuth.getInstance().currentUser
    private var moment = Moment()
    private val uploadPostSpecialViewModel by viewModels<UploadPostSpecialViewModel>()
    private var fetch: Fetch? = null
    private var cmt = MomentDetails()
    private var recentCommentTime = ""
    private var commentsKey: ArrayList<String> = arrayListOf()
    private var comments: ArrayList<MomentDetails> = arrayListOf()
    private val commentsAdapter = CommentsAdapter()
    private lateinit var progressView: LinearProgressIndicator
    private var loadingProgress: AlertDialog? = null
    private var json = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        when {
            intent.hasExtra("data") -> {
                json = intent.getStringExtra("data")!!
                moment = Gson().fromJson(json, Moment::class.java)
                println("2 ************************* ${moment}")
                setData()
                editPost()
                getComments()
                clickListeners()
            }
            intent.hasExtra("data details") -> {
                json = intent.getStringExtra("data details")!!
                val momentDetails = Gson().fromJson(json, MomentDetails::class.java)
                val retrieveMomentRef = FirebaseDatabase.getInstance().reference
                    .child(PERSONAL_POST_RECORD)
                    .child(user!!.uid)
                    .child(momentDetails.timeMomentPosted)
                retrieveMomentRef.get().addOnSuccessListener {
                    when {
                        it.exists() -> {
                            moment = it.getValue(Moment::class.java)!!
                            setData()
                            editPost()
                            getComments()
                            clickListeners()
                            Snackbar.make(binding.root, "Post exists.", Snackbar.LENGTH_LONG).show()
                        }
                        !it.exists() -> {
                            binding.imagesRootCard.visibility = View.GONE
                            binding.tvCaption.gravity = Gravity.CENTER
                            binding.tvCaption.text = "Unable to fetch this post\nIt may have been deleted by the author."
                            Snackbar.make(binding.root, "Unable to fetch this post\nIt may have been deleted by the author.", Snackbar.LENGTH_INDEFINITE).show()
                        }
                    }
                }
            }
        }
        commentsAdapter.post = moment
        commentsAdapter.comments = comments
        binding.rvComments.adapter = commentsAdapter
        binding.rvComments.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
    }

    private fun clickListeners() {
        binding.imgUser.setOnClickListener(this)
        binding.imagepost.setOnClickListener(this)
        binding.commentsImage.setOnClickListener(this)
        binding.viewPostBack.setOnClickListener(this)
    }

    private fun getComments() {
        val commentsRef = FirebaseDatabase.getInstance().reference
            .child(MOMENT_DETAILS)
            .child(moment.creatorUid)
            .child(moment.timePosted)
        ValueEventLiveData(commentsRef).observe(this) {
            when (it.second) {
                EventListenerType.onDataChange -> {
                    for (x in it.first.children) {
                        val comment = x.getValue(MomentDetails::class.java)!!
                        if (recentCommentTime == comment.localTime && comment.localTime != "") {
                            val pos = comments.indexOf(cmt)
                            comments[pos] = comment
                            commentsAdapter.notifyItemChanged(pos)
                        } else if (comment.comment.profileUid + comment.time !in commentsKey && comment.comment.profileUid != "") {
                            comments.add(comment)
                            commentsKey.add(comment.comment.profileUid + comment.time)
                            commentsAdapter.notifyItemInserted(comments.size)
                        }
                    }
                }
            }
        }
    }

    fun setData() {
        val momentDetails: ArrayList<MomentDetails> = arrayListOf()
        val momentLikesUids: ArrayList<String> = arrayListOf()
        val momentLoveUids: ArrayList<String> = arrayListOf()
        val momentCommentUids: ArrayList<String> = arrayListOf()
        binding.imagesRootCard.visibility = if (moment.images.isNotEmpty()) View.VISIBLE else View.GONE

        when {
            moment.images.isNotEmpty() -> setImagesPartition(moment)
            else -> binding.imagesRootCard.visibility = View.GONE
        }
        Glide.with(this).load(moment.profileImage).error(R.drawable.ic_avatar).centerCrop().into(binding.imgUser)
        binding.imagepost.adjustViewBounds = true
        binding.tvCaption.text = moment.caption
        binding.tvusername.text = moment.profileName
        binding.tvtime.text = Util.formatSmartDateTime(moment.timePosted)

        val userRef = FirebaseDatabase.getInstance().reference.child(USER_DETAILS).child(moment.creatorUid)
        userRef.keepSynced(false)
        userRef.get().addOnSuccessListener {
            if (it.exists()) {
                val userProfile = it.getValue(UserProfile::class.java)!!
                Glide.with(this).load(userProfile.image).error(R.drawable.ic_avatar).centerCrop().into(binding.imgUser)
                binding.tvusername.text = userProfile.name
            }
        }

        val othersRef = FirebaseDatabase.getInstance().reference.child(MOMENT_DETAILS).child(moment.creatorUid).child(moment.timePosted)
        ValueEventLiveData(othersRef).observe(this) {
            when (it.second) {
                EventListenerType.onDataChange -> {
                    for (i in it.first.children) {
                        val momentDetail = i.getValue(MomentDetails::class.java)!!
                        if (momentDetail !in momentDetails) {
                            momentDetails.add(momentDetail)

                            if (momentDetail.likes.profileUid + momentDetail.time !in momentLikesUids && momentDetail.likes.profileUid != "")
                                momentLikesUids.add(momentDetail.likes.profileUid + momentDetail.time + "like")
                            if (momentDetail.loves.profileUid + momentDetail.time !in momentLoveUids && momentDetail.loves.profileUid != "")
                                momentLoveUids.add(momentDetail.loves.profileUid + momentDetail.time + "love")
                            if (momentDetail.comment.profileUid + momentDetail.time !in momentCommentUids && momentDetail.comment.profileUid != "")
                                momentCommentUids.add(momentDetail.comment.profileUid + momentDetail.time + "comment")

                            binding.tvLikes.text = (momentLikesUids.size * Const.LIKE_VALUE).toInt().toString()
                            binding.tvLoves.text = (momentLoveUids.size * Const.LOVE_VALUE).toInt().toString()
                            binding.tvComments.text = momentCommentUids.size.toString()
                            val likesToHFCoin = ((momentLikesUids.size * Const.LIKE_VALUE) + (momentLoveUids.size * Const.LOVE_VALUE)).toString()
                            binding.tvCoins.text = likesToHFCoin
                            if (user!!.uid + momentDetail.time + "like" in momentLoveUids)
                                binding.lovesButton.setImageResource(com.like.view.R.drawable.heart_on)
                            if (user.uid + momentDetail.time + "love" in momentLikesUids)
                                binding.likeButton.setImageResource(R.drawable.like_blue)
                        }
                    }
                }
            }
        }
        binding.btnShare.setOnClickListener { shareMoment(moment) }

        binding.likeButton.setOnClickListener {
            if (user!!.uid != moment.creatorUid) {
                var originalLike = ""
                binding.likeButton.setImageResource(R.drawable.like_blue)
                var like = binding.tvLikes.text.toString().toFloat() + Const.LIKE_VALUE
                originalLike = binding.tvLikes.text.toString()
                binding.tvLikes.text = like.toInt().toString()

                val historyRef = FirebaseDatabase.getInstance().reference.child(NormalFeedAdapter.TRANSFER_HISTORY).child(user.uid)
                historyRef.get().addOnSuccessListener {
                    if (it.exists()) {
                        val available = HFCoinUtils.checkBalance(it)
                        when {
                            available < Const.LIKE_VALUE -> {
                                binding.tvLikes.text = originalLike
                                binding.likeButton.setImageResource(R.drawable.like_white)
                                Toast.makeText(this, "Insufficient HFCoin.", Toast.LENGTH_SHORT).show()
                            }
                            else -> {
                                FirebaseDatabase.getInstance().reference.child(USER_DETAILS).child(user.uid).get().addOnSuccessListener { p ->
                                    if (p.exists()) {
                                        val myProfile = p.getValue(UserProfile::class.java)!!
                                        HFCoinUtils.sendLoveLikeHFCoin(
                                            Const.LIKE_VALUE,
                                            othersRef,
                                            moment.creatorUid,
                                            myProfile = myProfile,
                                            moment.timePosted
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        binding.tvLikes.text = originalLike
                        binding.likeButton.setImageResource(R.drawable.like_white)
                        Toast.makeText(this, "Insufficient HFCoin.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        binding.lovesButton.setOnClickListener {
            if (user!!.uid != moment.creatorUid) {
                binding.lovesButton.setImageResource(com.like.view.R.drawable.heart_on)
                var originalLove = ""
                var love = binding.tvLoves.text.toString().toFloat() + Const.LOVE_VALUE
                originalLove = binding.tvLoves.text.toString()
                binding.tvLoves.text = love.toInt().toString()

                val historyRef = FirebaseDatabase.getInstance().reference.child(NormalFeedAdapter.TRANSFER_HISTORY).child(user.uid)
                historyRef.get().addOnSuccessListener {
                    if (it.exists()) {
                        val available = HFCoinUtils.checkBalance(it)
                        when {
                            available < Const.LOVE_VALUE -> {
                                love -= Const.LOVE_VALUE
                                binding.tvLoves.text = love.toString()
                                binding.lovesButton.setImageResource(com.like.view.R.drawable.heart_off)
                                Toast.makeText(this, "Insufficient HFCoin.", Toast.LENGTH_SHORT).show()
                            }
                            else -> {
                                FirebaseDatabase.getInstance().reference.child(USER_DETAILS).child(user.uid).get().addOnSuccessListener { p ->
                                    if (p.exists()) {
                                        val myProfile = p.getValue(UserProfile::class.java)!!
                                        HFCoinUtils.sendLoveLikeHFCoin(
                                            Const.LOVE_VALUE,
                                            othersRef,
                                            moment.creatorUid,
                                            myProfile = myProfile,
                                            moment.timePosted
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        binding.tvLoves.text = originalLove
                        binding.lovesButton.setImageResource(com.like.view.R.drawable.heart_off)
                        Toast.makeText(this, "Insufficient HFCoin.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun onBackPressed() {
        if (isTaskRoot) startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
        }) else super.onBackPressed()
    }

    private fun editPost() {
        if (moment.creatorUid == user!!.uid) {
            binding.editPost.visibility = View.VISIBLE
            binding.editPost.setOnClickListener {
                val popUp = PopupMenu(this, binding.editPost)
                popUp.inflate(R.menu.eidt_post_menu)
                popUp.show()
                popUp.setOnMenuItemClickListener {
                    when (it?.itemId) {
                        R.id.edit_post -> {
                            startActivity(Intent(this, UploadPostActivity::class.java).apply { putExtra("post data", Gson().toJson(moment)) })
                            overridePendingTransition(R.anim.enter_right_to_left, R.anim.exit_right_to_left)
                        }
                        R.id.delete_post -> {
                            val alertDialog = AlertDialog.Builder(this)
                            alertDialog.setTitle("Delete post")
                            alertDialog.setMessage("Delete this post?")
                            alertDialog.setPositiveButton("Delete") { dialog, _ ->
                                val instance = FirebaseDatabase.getInstance().reference
                                val ref = instance.child(NormalFeedAdapter.MOMENT_DATA).child(moment.timePosted)
                                val postRecord = instance.child(NormalFeedAdapter.PERSONAL_POST_RECORD).child(moment.creatorUid).child(moment.timePosted)
                                postRecord.removeValue()
                                ref.removeValue()
                                dialog.dismiss()
                            }
                            alertDialog.setNegativeButton("Keep") { dialog, _ ->
                                dialog.dismiss()
                            }
                            alertDialog.create().show()
                        }
                    }
                    return@setOnMenuItemClickListener true
                }
            }
        } else binding.editPost.visibility = View.GONE
    }

    private fun shareMoment(moment: Moment) {
        val progressAlert = AlertDialog.Builder(this)
        if (moment.image == "" && moment.images.isEmpty()) {
            val intent = Intent(Intent.ACTION_SEND)
            intent.putExtra(Intent.EXTRA_TEXT, moment.caption)
            intent.putExtra(Intent.EXTRA_SUBJECT, "Moment")
            startActivity(Intent.createChooser(intent, "Share Moment Via"))
            return
        }
        if (moment.images.size == 1) {
            val pView = LayoutInflater.from(this).inflate(R.layout.dialog_progress, binding.root.parent as ViewGroup, false)
            progressView = pView.findViewById(R.id.loading_progress)
            progressAlert.setView(pView)
            loadingProgress = progressAlert.create()
            if (loadingProgress != null) if (loadingProgress!!.isShowing) loadingProgress!!.dismiss() else loadingProgress!!.show()
            val timeNow = Calendar.getInstance().timeInMillis.toString()
            val dir = File("${this.cacheDir.path}/Media/SharedImages/")
            if (!dir.exists()) dir.mkdirs()
            var tempFile = File(dir, "$timeNow.png")
            initiateDownload(moment, Uri.parse(tempFile.path))
        } else if (moment.images.size > 1) {
            uploadPostSpecialViewModel.setStringImagesData(moment.images)
            val frag = UploadPostFragment()
            val bundle = Bundle()
            bundle.putBoolean("shareAble", true)
            frag.arguments = bundle
            supportFragmentManager.beginTransaction()
                .addToBackStack("images").replace(R.id.post_root, frag)
                .commit()
        }
    }

    private fun initiateDownload(moment: Moment, file: Uri) {
        val image = if (moment.images.size == 1) moment.images[0] else moment.image
        val fetchConfiguration: FetchConfiguration = FetchConfiguration.Builder(this).setDownloadConcurrentLimit(3).build()
        fetch = Fetch.getInstance(fetchConfiguration)
        val request = Request(image, file)
        request.priority = Priority.HIGH
        request.networkType = NetworkType.ALL
        request.addHeader("clientKey", image)
        fetch!!.enqueue(request, { updatedRequest ->
            println("enqueue ********************************************* ${updatedRequest.file}")
        }) { error ->
            println("enqueue error ********************************************* $error")
        }
        fetch!!.addListener(object : FetchListener {
            override fun onStarted(download: Download, downloadBlocks: List<DownloadBlock>, totalBlocks: Int) {
                println("download onStarted ********************************************* ${download.error}")
            }

            override fun onCompleted(download: Download) {
                try {
                    if (loadingProgress != null) if (loadingProgress!!.isShowing) loadingProgress!!.dismiss()
                    val bitmap = BitmapFactory.decodeFile(download.fileUri.toFile().path)
                    val b = BitmapFactory.decodeResource(this@PostViewActivity.resources, R.drawable.water_mark)
                    val waterMarked = HowFarWaterMark.addWatermark(bitmap, b, 0.15F)

                    val alertDialog = AlertDialog.Builder(this@PostViewActivity)
                    val viewX = LayoutInflater.from(this@PostViewActivity).inflate(R.layout.watermark_image, binding.root.parent as ViewGroup, false)
                    val shareBtn: TextView = viewX.findViewById(R.id.share_btn)
                    val image: ImageView = viewX.findViewById(R.id.watermarked_image)
                    alertDialog.setView(viewX)
                    image.setImageBitmap(waterMarked)
                    val dialogShare = alertDialog.create()
                    dialogShare.show()

                    shareBtn.setOnClickListener {
                        if (dialogShare.isShowing) dialogShare.dismiss()
                        val file = getImageUri(waterMarked!!)
                        val uriTransfer = FileProvider.getUriForFile(this@PostViewActivity, this@PostViewActivity.packageName + ".fileprovider", file)
                        val intent = Intent(Intent.ACTION_SEND)
                        intent.putExtra(Intent.EXTRA_STREAM, uriTransfer)
                        intent.putExtra(Intent.EXTRA_TEXT, moment.caption)
                        intent.putExtra(Intent.EXTRA_SUBJECT, "Moment")
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                        intent.type = "image/png"
                        startActivity(Intent.createChooser(intent, "Share Moment Via"))
                    }
                } catch (e: Exception) {
                }
            }

            override fun onError(download: Download, error: Error, throwable: Throwable?) {
                if (loadingProgress != null) if (loadingProgress!!.isShowing) loadingProgress!!.dismiss()
                Toast.makeText(this@PostViewActivity, "Download failed", Toast.LENGTH_LONG).show()
                println("download.error ********************************************* ${download.error}")
            }

            override fun onProgress(@NotNull download: Download, etaInMilliSeconds: Long, downloadedBytesPerSecond: Long) {
                if (loadingProgress != null) if (loadingProgress!!.isShowing) loadingProgress!!.dismiss() else {
                    if (download.progress > 0) progressView.progress = download.progress
                }
                println("updatedRequest ********************************************* ${download.progress}")
            }

            override fun onQueued(@NotNull download: Download, waitingOnNetwork: Boolean) = Unit
            override fun onRemoved(download: Download) = Unit
            override fun onResumed(download: Download) = Unit
            override fun onWaitingNetwork(download: Download) = Unit
            override fun onAdded(download: Download) = Unit
            override fun onCancelled(download: Download) = Unit
            override fun onDeleted(download: Download) = Unit
            override fun onDownloadBlockUpdated(download: Download, downloadBlock: DownloadBlock, totalBlocks: Int) = Unit
            override fun onPaused(download: Download) = Unit
        })
    }

    private fun getImageUri(inImage: Bitmap): File {
        val dir = File("${this.cacheDir.path}/Practice/Watermark/")
        if (!dir.exists()) dir.mkdirs()
        val tempFile = File(dir, System.currentTimeMillis().toString() + ".png")
        val bytes = ByteArrayOutputStream()
        inImage.compress(Bitmap.CompressFormat.PNG, 100, bytes)
        val bitmapData = bytes.toByteArray()

        val fileOutPut = FileOutputStream(tempFile)
        try {
            fileOutPut.write(bitmapData)
            fileOutPut.flush()
            fileOutPut.close()
        } catch (e: Exception) {
            fileOutPut.flush()
            fileOutPut.close()
        }
        return tempFile
    }

    private fun setImagesPartition(moment: Moment) {
        when {
            moment.images.size == 1 -> {
                hideAllViews()
                Glide.with(this).load(moment.images[0]).into(binding.imagepost)
            }
            moment.images.size == 2 -> {
                hideAllViews()
                showSecondImageRoot()
                Glide.with(this).load(moment.images[0]).into(binding.imagepost)
                Glide.with(this).load(moment.images[0]).into(binding.doubleImage1)
                Glide.with(this).load(moment.images[1]).into(binding.doubleImage2)
            }
            moment.images.size >= 3 -> {
                hideAllViews()
                showThirdImageRoot()
                try {
                    Glide.with(this).load(moment.images[0]).into(binding.imagepost)
                    Glide.with(this).load(moment.images[0]).into(binding.tripleImage1)
                    Glide.with(this).load(moment.images[1]).into(binding.tripleImage2)
                    Glide.with(this).load(moment.images[2]).into(binding.tripleImage3)
                } catch (e: Exception) {
                }
                if (moment.images.size > 3) binding.tripleImageMore.visibility = View.VISIBLE
            }
        }
    }

    private fun hideAllViews() {
        binding.rootDoubleImage.visibility = View.GONE
        binding.rootTripleImage.visibility = View.GONE
        binding.tripleImageMore.visibility = View.GONE
    }

    private fun showSecondImageRoot() {
        binding.rootDoubleImage.visibility = View.VISIBLE
    }

    private fun showThirdImageRoot() {
        binding.rootTripleImage.visibility = View.VISIBLE
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.view_post_back -> onBackPressed()
            R.id.imgUser -> startActivity(Intent(this, GuestActivity::class.java).putExtra(Const.USER_STR, moment.creatorUid))
            R.id.imagepost -> {
                when(moment.images.size){
                    1->{
                        if (moment.images.size == 1){
                            val fragment = FragmentDisplayImage()
                            val bundle = Bundle()
                            bundle.putString("image", moment.images.first())
                            fragment.arguments = bundle
                            supportFragmentManager.beginTransaction()
                                .addToBackStack("images")
                                .replace(R.id.post_root, fragment)
                                .commit()
                        }
                    }
                    else->{
                        uploadPostSpecialViewModel.setStringImagesData(moment.images)
                        val images = moment.images
                        if (images.isNotEmpty()) {
                            val frag = UploadPostFragment()
                            val bundle = Bundle()
                            bundle.putBoolean("shareAble", true)
                            frag.arguments = bundle
                            supportFragmentManager.beginTransaction()
                                .addToBackStack("images").replace(R.id.post_root, frag)
                                .commit()
                        }
                    }
                }
            }
            R.id.commentsImage -> {
                if (moment.allowedComment) {
                    if (json == "") return
                    val frag = PostViewCommentFragment()
                    val bundle = Bundle()
                    bundle.putString("data", json)
                    frag.arguments = bundle
                    supportFragmentManager.beginTransaction().addToBackStack("comment").replace(R.id.post_root, frag).commit()
                } else binding.commentsImage.setImageResource(R.drawable.comment_off)
            }
        }
    }

    companion object {
        const val MOMENT_DATA = "MOMENT_DATA"
        const val PERSONAL_POST_RECORD = "PERSONAL_POST_RECORD"
        const val MOMENT_IMAGES = "MOMENT IMAGES"
        const val USER_DETAILS = "user_details"
        const val MOMENT_DETAILS = "MOMENT_DETAILS"
    }
}

class CommentsAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    lateinit var post: Moment
    var comments: ArrayList<MomentDetails> = arrayListOf()
    private lateinit var context: Context
    private var user = FirebaseAuth.getInstance().currentUser

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        context = parent.context
        return CommentViewHOlder(LayoutInflater.from(context).inflate(R.layout.item_view_comment, parent, false))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        (holder as CommentViewHOlder).setData(position)
    }

    override fun getItemCount() = comments.size

    inner class CommentViewHOlder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgUser: ShapeableImageView = itemView.findViewById(R.id.imgUser)
        private val tvComment: TextView = itemView.findViewById(R.id.tvComment)
        private val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        val tvUserName: TextView = itemView.findViewById(R.id.tvUserName)
        private val tvCreator: TextView = itemView.findViewById(R.id.tvCreator)

        fun setData(position: Int) {
            val comment = comments[position]
            if (comment.comment.profileUid != "") {
                tvCreator.visibility = if (comment.comment.profileUid == post.creatorUid) View.VISIBLE else View.GONE
                Glide.with(context).load(comment.comment.profilePhoto).circleCrop().into(imgUser)
                tvComment.text = comment.comment.profileComment
                tvDate.text = Util.formatSmartDateTime(comment.time)
                tvUserName.text = comment.comment.profileName
            }
        }
    }
}