package com.azur.howfar.user.guestuser

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.FileProvider
import androidx.core.net.toFile
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.azur.howfar.R
import com.azur.howfar.posts.CommentFragment
import com.azur.howfar.databinding.FragmentGuestUserPostsBinding
import com.azur.howfar.livedata.ValueEventLiveData
import com.azur.howfar.models.EventListenerType.onDataChange
import com.azur.howfar.models.Moment
import com.azur.howfar.models.MomentDetails
import com.azur.howfar.models.UserProfile
import com.azur.howfar.posts.*
import com.azur.howfar.retrofit.Const.LIKE_VALUE
import com.azur.howfar.retrofit.Const.LOVE_VALUE
import com.azur.howfar.utils.HFCoinUtils
import com.azur.howfar.utils.HowFarWaterMark
import com.azur.howfar.utils.Util
import com.azur.howfar.viewmodel.MomentViewModel
import com.azur.howfar.viewmodel.UploadPostSpecialViewModel
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.progressindicator.LinearProgressIndicator
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

class GuestUserPostsFragment : Fragment(), OnPostClickListener {
    private lateinit var binding: FragmentGuestUserPostsBinding
    private val momentViewModel by activityViewModels<MomentViewModel>()
    private var momentList: ArrayList<Moment> = arrayListOf()
    private val guestPostsAdapter = GuestPostsAdapter()
    private var user = FirebaseAuth.getInstance().currentUser
    private var profileRef = FirebaseDatabase.getInstance().reference
    private var myProfile = UserProfile()
    private val uploadPostSpecialViewModel by activityViewModels<UploadPostSpecialViewModel>()

    @SuppressLint("NotifyDataSetChanged")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentGuestUserPostsBinding.inflate(inflater, container, false)

        guestPostsAdapter.activity = requireActivity()
        guestPostsAdapter.onPostClickListener = this
        guestPostsAdapter.uploadPostSpecialViewModel = uploadPostSpecialViewModel
        guestPostsAdapter.viewLifecycleOwner = viewLifecycleOwner
        momentViewModel.momentList.observe(viewLifecycleOwner) { mm ->
            momentList = mm
            momentList.sortWith(compareByDescending { it.timePosted })
            guestPostsAdapter.dataset = momentList
            binding.rvFeed.adapter = guestPostsAdapter
            binding.rvFeed.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
            guestPostsAdapter.notifyDataSetChanged()
        }

        profileRef = FirebaseDatabase.getInstance().reference.child(FeedAdapter.USER_DETAILS).child(user!!.uid)
        profileRef.keepSynced(false)
        ValueEventLiveData(profileRef).observe(viewLifecycleOwner) {
            when (it.second) {
                onDataChange -> {
                    myProfile = it.first.getValue(UserProfile::class.java)!!
                    guestPostsAdapter.myProfile = myProfile
                }
            }
        }
        return binding.root
    }

    override fun onLikeClick(post: Moment) {

    }

    override fun onCommentClick(post: Moment) {
        openFragment(CommentFragment(post))
    }

    private fun openFragment(bottomSheetDialogFragment: BottomSheetDialogFragment) {
        bottomSheetDialogFragment.show(childFragmentManager, bottomSheetDialogFragment.javaClass.simpleName)
    }

    override fun onShareClick(post: Moment) {
    }
}

class GuestPostsAdapter : RecyclerView.Adapter<GuestPostsAdapter.ViewHolder>() {
    lateinit var uploadPostSpecialViewModel: UploadPostSpecialViewModel
    var myProfile = UserProfile()
    var dataset: ArrayList<Moment> = arrayListOf()
    private lateinit var context: Context
    lateinit var activity: Activity
    var onPostClickListener: OnPostClickListener? = null
    private var user = FirebaseAuth.getInstance().currentUser
    lateinit var viewLifecycleOwner: LifecycleOwner

    init {
        setHasStableIds(true)
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private var fetch: Fetch? = null
        private lateinit var progressView: LinearProgressIndicator
        private var loadingProgress: AlertDialog? = null
        private val userImage: ShapeableImageView = itemView.findViewById(R.id.userImage)
        private val tvUsername: TextView = itemView.findViewById(R.id.tvUsername)
        private val tvTime: TextView = itemView.findViewById(R.id.tvTime)
        private val postText: TextView = itemView.findViewById(R.id.imageText)
        val imagePost: ImageView = itemView.findViewById(R.id.imagepost)
        private val imagesRootCard: CardView = itemView.findViewById(R.id.images_root_card)

        private val tvLikes: TextView = itemView.findViewById(R.id.tvLikes)
        private val tvLoves: TextView = itemView.findViewById(R.id.tvLoves)
        private val lytComments: LinearLayout = itemView.findViewById(R.id.lytComments)
        private val tvCoins: TextView = itemView.findViewById(R.id.tvCoins)
        private val tvComments: TextView = itemView.findViewById(R.id.tvComments)
        private val commentsImage: ImageView = itemView.findViewById(R.id.commentsImage)
        private val btnShare: ImageView = itemView.findViewById(R.id.btnShare)
        private val likeButton: ImageView = itemView.findViewById(R.id.likeButton)
        private val lovesButton: ImageView = itemView.findViewById(R.id.lovesButton)
        private val editPost: ImageView = itemView.findViewById(R.id.edit_post)

        private val rootDoubleImage: LinearLayout = itemView.findViewById(R.id.root_double_image)
        private val rootTripleImage: LinearLayout = itemView.findViewById(R.id.root_triple_image)
        private val tripleImageMore: FrameLayout = itemView.findViewById(R.id.triple_image_more)
        private val imagesRoot: ConstraintLayout = itemView.findViewById(R.id.images_root)
        private val doubleImage1: ImageView = itemView.findViewById(R.id.double_image1)
        private val doubleImage2: ImageView = itemView.findViewById(R.id.double_image2)
        private val tripleImage1: ImageView = itemView.findViewById(R.id.triple_image1)
        private val tripleImage2: ImageView = itemView.findViewById(R.id.triple_image2)
        private val tripleImage3: ImageView = itemView.findViewById(R.id.triple_image3)

        fun setData(datum: Moment, position: Int) {
            val momentDetails: ArrayList<MomentDetails> = arrayListOf()
            val momentLikesUids: ArrayList<String> = arrayListOf()
            val momentLoveUids: ArrayList<String> = arrayListOf()
            val momentCommentUids: ArrayList<String> = arrayListOf()

            imagesRootCard.visibility = if (datum.images.isNotEmpty()) View.VISIBLE else View.GONE
            when {
                datum.images.isNotEmpty() -> setImagesPartition(datum)
            }

            tvUsername.text = datum.profileName
            imagePost.adjustViewBounds = true
            tvTime.text = Util.formatSmartDateTime(datum.timePosted)
            postText.text = datum.caption
            Glide.with(context).load(datum.profileImage).centerCrop().into(userImage)

            FirebaseDatabase.getInstance().reference.child(FeedAdapter.USER_DETAILS).child(datum.creatorUid).get().addOnSuccessListener {
                if (it.exists()) {
                    val userProfile = it.getValue(UserProfile::class.java)!!
                    try {
                        Glide.with(context).load(userProfile.image).centerCrop().into(userImage)
                    } catch (e: Exception) {
                    }
                    tvUsername.text = userProfile.name
                }
            }
            lytComments.setOnClickListener {
                if (datum.allowedComment) {
                    onPostClickListener!!.onCommentClick(datum)
                    commentsImage.setImageResource(R.drawable.comment)
                } else {
                    commentsImage.setImageResource(R.drawable.comment_off)
                }
            }

            val othersRef = FirebaseDatabase.getInstance().reference
                .child(FeedAdapter.MOMENT_DETAILS)
                .child(datum.creatorUid)
                .child(datum.timePosted)
            ValueEventLiveData(othersRef).observe(viewLifecycleOwner) {
                when (it.second) {
                    onDataChange -> {
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

                                tvLikes.text = (momentLikesUids.size * LIKE_VALUE).toInt().toString()
                                tvLoves.text = (momentLoveUids.size * LOVE_VALUE).toInt().toString()
                                tvComments.text = momentCommentUids.size.toString()
                                val likesToHFCoin = ((momentLikesUids.size * LIKE_VALUE) + (momentLoveUids.size * LOVE_VALUE)).toString()
                                tvCoins.text = likesToHFCoin
                                if (user!!.uid + momentDetail.time + "like" in momentLoveUids)
                                    lovesButton.setImageResource(com.like.view.R.drawable.heart_on)
                                if (user!!.uid + momentDetail.time + "love" in momentLikesUids)
                                    likeButton.setImageResource(R.drawable.like_blue)
                            }
                        }
                    }
                }
            }

            likeButton.setOnClickListener {
                if (user!!.uid != datum.creatorUid) {
                    var originalLike = ""
                    likeButton.setImageResource(R.drawable.like_blue)
                    var like = tvLikes.text.toString().toFloat() + LIKE_VALUE
                    originalLike = tvLikes.text.toString()
                    tvLikes.text = like.toInt().toString()

                    val historyRef = FirebaseDatabase.getInstance().reference.child(FeedAdapter.TRANSFER_HISTORY).child(user!!.uid)
                    historyRef.get().addOnSuccessListener {
                        if (it.exists()) {
                            val available = HFCoinUtils.checkBalance(it)
                            when {
                                available < LIKE_VALUE -> {
                                    tvLikes.text =  originalLike
                                    likeButton.setImageResource(R.drawable.like_white)
                                    Toast.makeText(context, "Insufficient HFCoin.", Toast.LENGTH_SHORT).show()
                                }
                                else -> HFCoinUtils.sendLoveLikeHFCoin(LOVE_VALUE, othersRef, datum.creatorUid, myProfile = myProfile, datum.timePosted)
                            }
                        } else {
                            tvLikes.text =  originalLike
                            likeButton.setImageResource(R.drawable.like_white)
                            Toast.makeText(context, "Insufficient HFCoin.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }

            btnShare.setOnClickListener { shareMoment(datum) }

            lovesButton.setOnClickListener {
                if (user!!.uid != datum.creatorUid) {
                    var originalLove = ""
                    lovesButton.setImageResource(com.like.view.R.drawable.heart_on)
                    var love = tvLoves.text.toString().toFloat() + LOVE_VALUE
                    originalLove = tvLoves.text.toString()
                    tvLoves.text = love.toInt().toString()

                    val historyRef = FirebaseDatabase.getInstance().reference.child(FeedAdapter.TRANSFER_HISTORY).child(user!!.uid)
                    historyRef.get().addOnSuccessListener {
                        if (it.exists()) {
                            val available = HFCoinUtils.checkBalance(it)
                            when {
                                available < LOVE_VALUE -> {
                                    tvLoves.text = originalLove
                                    lovesButton.setImageResource(com.like.view.R.drawable.heart_off)
                                    Toast.makeText(context, "Insufficient HFCoin.", Toast.LENGTH_SHORT).show()
                                }
                                else -> HFCoinUtils.sendLoveLikeHFCoin(LOVE_VALUE, othersRef, datum.creatorUid, myProfile = myProfile, datum.timePosted)
                            }
                        } else {
                            tvLoves.text = originalLove
                            lovesButton.setImageResource(com.like.view.R.drawable.heart_off)
                            Toast.makeText(context, "Insufficient HFCoin.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

        fun editPost(holder: ViewHolder, position: Int) {
            val datum = dataset[position]
            if (datum.creatorUid == user!!.uid) {
                holder.editPost.visibility = View.VISIBLE
                holder.editPost.setOnClickListener {
                    val popUp = PopupMenu(context, holder.editPost)
                    popUp.inflate(R.menu.eidt_post_menu)
                    popUp.show()
                    popUp.setOnMenuItemClickListener {
                        when (it?.itemId) {
                            R.id.edit_post -> {
                                context.startActivity(Intent(activity, UploadPostActivity::class.java).apply { putExtra("post data", Gson().toJson(datum)) })
                                activity.overridePendingTransition(R.anim.enter_right_to_left, R.anim.exit_right_to_left)
                            }
                            R.id.delete_post -> {
                                val alertDialog = AlertDialog.Builder(context)
                                alertDialog.setTitle("Delete post")
                                alertDialog.setMessage("Delete this post?")
                                alertDialog.setPositiveButton("Delete") { dialog, _ ->
                                    val instance = FirebaseDatabase.getInstance().reference
                                    val ref = instance.child(MOMENT_DATA).child(datum.timePosted)
                                    val postRecord = instance.child(PERSONAL_POST_RECORD).child(datum.creatorUid).child(datum.timePosted)
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
            } else holder.editPost.visibility = View.GONE
        }

        private fun shareMoment(moment: Moment) {
            if (moment.image == "" && moment.images.isEmpty()) {
                val intent = Intent(Intent.ACTION_SEND)
                intent.putExtra(Intent.EXTRA_TEXT, moment.caption)
                intent.putExtra(Intent.EXTRA_SUBJECT, "Moment")
                activity.startActivity(Intent.createChooser(intent, "Share Moment Via"))
                return
            }
            val progressAlert = AlertDialog.Builder(context)
            if (moment.images.size == 1) {
                val pView = LayoutInflater.from(context).inflate(R.layout.dialog_progress, itemView.parent as ViewGroup, false)
                progressView = pView.findViewById(R.id.loading_progress)
                progressAlert.setView(pView)
                loadingProgress = progressAlert.create()
                if (loadingProgress != null) if (loadingProgress!!.isShowing) loadingProgress!!.dismiss() else loadingProgress!!.show()
                val timeNow = Calendar.getInstance().timeInMillis.toString()
                val dir = File("${activity.cacheDir.path}/Media/SharedImages/")
                if (!dir.exists()) dir.mkdirs()
                var tempFile = File(dir, "$timeNow.png")
                initiateDownload(moment, Uri.parse(tempFile.path))
            } else if (moment.images.size > 1) {
                uploadPostSpecialViewModel.setStringImagesData(moment.images)
                val frag = UploadPostFragment()
                val bundle = Bundle()
                bundle.putBoolean("shareAble", true)
                frag.arguments = bundle
                (activity as AppCompatActivity).supportFragmentManager.beginTransaction()
                    .addToBackStack("images").replace(R.id.main_root, frag)
                    .commit()
            }
        }

        private fun initiateDownload(moment: Moment, file: Uri) {
            val image = if (moment.images.size == 1) moment.images[0] else moment.image
            val fetchConfiguration: FetchConfiguration = FetchConfiguration.Builder(context).setDownloadConcurrentLimit(3).build()
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
                        val b = BitmapFactory.decodeResource(activity.resources, R.drawable.water_mark)
                        val waterMarked = HowFarWaterMark.addWatermark(bitmap, b, 0.15F)

                        val alertDialog = AlertDialog.Builder(context)
                        val viewX = LayoutInflater.from(context).inflate(R.layout.watermark_image, itemView.parent as ViewGroup, false)
                        val shareBtn: TextView = viewX.findViewById(R.id.share_btn)
                        val image: ImageView = viewX.findViewById(R.id.watermarked_image)
                        alertDialog.setView(viewX)
                        image.setImageBitmap(waterMarked)
                        val dialogShare = alertDialog.create()
                        dialogShare.show()

                        shareBtn.setOnClickListener {
                            if (dialogShare.isShowing) dialogShare.dismiss()
                            val file = getImageUri(waterMarked!!)
                            val uriTransfer = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
                            val intent = Intent(Intent.ACTION_SEND)
                            intent.putExtra(Intent.EXTRA_STREAM, uriTransfer)
                            intent.putExtra(Intent.EXTRA_TEXT, moment.caption)
                            intent.putExtra(Intent.EXTRA_SUBJECT, "Moment")
                            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                            intent.type = "image/png"
                            activity.startActivity(Intent.createChooser(intent, "Share Moment Via"))
                        }
                    } catch (e: Exception) {
                    }
                }

                override fun onError(download: Download, error: Error, throwable: Throwable?) {
                    if (loadingProgress != null) if (loadingProgress!!.isShowing) loadingProgress!!.dismiss()
                    Toast.makeText(context, "Download failed", Toast.LENGTH_LONG).show()
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
            val dir = File("${activity.cacheDir.path}/Practice/Watermark/")
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
                    Glide.with(context).load(moment.images[0]).into(imagePost)
                }
                moment.images.size == 2 -> {
                    hideAllViews()
                    showSecondImageRoot()
                    Glide.with(context).load(moment.images[0]).into(imagePost)
                    Glide.with(context).load(moment.images[0]).into(doubleImage1)
                    Glide.with(context).load(moment.images[1]).into(doubleImage2)
                }
                moment.images.size >= 3 -> {
                    hideAllViews()
                    showThirdImageRoot()
                    try {
                        Glide.with(context).load(moment.images[0]).into(imagePost)
                        Glide.with(context).load(moment.images[0]).into(tripleImage1)
                        Glide.with(context).load(moment.images[1]).into(tripleImage2)
                        Glide.with(context).load(moment.images[2]).into(tripleImage3)
                    } catch (e: Exception) {
                    }
                    if (moment.images.size > 3) tripleImageMore.visibility = View.VISIBLE
                }
            }
        }

        private fun hideAllViews() {
            rootDoubleImage.visibility = View.GONE
            rootTripleImage.visibility = View.GONE
            tripleImageMore.visibility = View.GONE
        }

        private fun showSecondImageRoot() {
            rootDoubleImage.visibility = View.VISIBLE
        }

        private fun showThirdImageRoot() {
            rootTripleImage.visibility = View.VISIBLE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        context = parent.context
        val view = LayoutInflater.from(context).inflate(R.layout.item_feed_grid_list, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val moment = dataset[position]
        holder.editPost(holder, position)
        holder.setData(moment, position)
        holder.itemView.setOnClickListener {
            context.startActivity(Intent(context, PostViewActivity::class.java).apply {
                val json = Gson().toJson(dataset[position])
                putExtra("data", json)
            })
        }
    }

    override fun getItemCount() = dataset.size

    override fun getItemViewType(position: Int): Int {
        return position
    }

    override fun getItemId(position: Int) = position.toLong()

    companion object {
        const val MOMENT_DATA = "MOMENT_DATA"
        const val PERSONAL_POST_RECORD = "PERSONAL_POST_RECORD"
        const val MOMENT_IMAGES = "MOMENT IMAGES"
        const val USER_DETAILS = "user_details"
    }
}