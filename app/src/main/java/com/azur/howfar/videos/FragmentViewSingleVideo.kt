package com.azur.howfar.videos

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.azur.howfar.R
import com.azur.howfar.databinding.ActivityViewSingleVideoBinding
import com.azur.howfar.databinding.VideoCommentBottomSheetBinding
import com.azur.howfar.models.MomentComment
import com.azur.howfar.models.MomentDetails
import com.azur.howfar.models.UserProfile
import com.azur.howfar.models.VideoPost
import com.azur.howfar.posts.FeedAdapter
import com.azur.howfar.retrofit.Const
import com.azur.howfar.utils.HFCoinUtils
import com.azur.howfar.utils.HFCoinUtils.sendLoveLikeHFCoin
import com.azur.howfar.viewmodel.MomentDetailsViewModel
import com.azur.howfar.viewmodel.VideoPostsViewModel
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.ext.okhttp.OkHttpDataSourceFactory
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.imageview.ShapeableImageView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.storage.FirebaseStorage
import com.google.gson.Gson
import com.tonyodev.fetch2.*
import kotlinx.coroutines.*
import okhttp3.OkHttpClient

class FragmentViewSingleVideo : Fragment(), CustomExoplayerMethods, View.OnClickListener {
    private lateinit var binding: ActivityViewSingleVideoBinding
    private lateinit var player: ExoPlayer
    private var fetch: Fetch? = null
    private var file = Uri.EMPTY
    private var user = FirebaseAuth.getInstance().currentUser
    private var timeRef = FirebaseDatabase.getInstance().reference
    private var PLAYER_STATE = -1
    private val scope = CoroutineScope(Dispatchers.IO)
    private var playWhenReady = true
    private var videoPost = VideoPost()
    private lateinit var pref: SharedPreferences
    private var commentList = arrayListOf<MomentDetails>()
    private var loveList = arrayListOf<MomentDetails>()
    private var likeList = arrayListOf<MomentDetails>()
    private val momentDetailsViewModel by activityViewModels<MomentDetailsViewModel>()
    private val videoPostViewModel by activityViewModels<VideoPostsViewModel>()
    private var userProfile = UserProfile()
    val requestOptions = RequestOptions()

    interface VideoLongPressClickListener {
        fun onLongPress(videoPost: VideoPost, player: ExoPlayer)
    }

    lateinit var videoLongPressClickListener: VideoLongPressClickListener

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = ActivityViewSingleVideoBinding.inflate(inflater, container, false)
        pref = requireActivity().getSharedPreferences(getString(R.string.ALL_PREFERENCE), Context.MODE_PRIVATE)
        val json = requireArguments().getString("reel")!!
        videoPost = Gson().fromJson(json, VideoPost::class.java)!!
        player = ExoPlayer.Builder(requireActivity()).build()
        binding.playerView.setOnClickListener(this)
        binding.loveBtn.setOnClickListener(this)
        binding.deleteVideo.setOnClickListener(this)
        binding.likeBtn.setOnClickListener(this)
        binding.imgComment.setOnClickListener(this)
        videoLongPressClickListener = requireActivity() as VideoLongPressClickListener
        timeRef = FirebaseDatabase.getInstance().reference.child("time").child(user!!.uid)
        oneTImeDownloadInstruction()
        return binding.root
    }

    private fun oneTImeDownloadInstruction() {
        val firstTime = pref.getBoolean("video download instruction", true)
        if (firstTime) {
            val view = layoutInflater.inflate(R.layout.first_time_video_ins, binding.root, false)
            val param = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            view.layoutParams = param
            binding.root.addView(view)
            runBlocking {
                scope.launch {
                    delay(10000)
                    if (isAdded) requireActivity().runOnUiThread { binding.root.removeView(view) }
                    pref.edit().putBoolean("video download instruction", false).apply()
                }
            }
        }
    }

    private fun setData() {
        var tags = ""
        var caption = videoPost.caption
        var captionTagCompound = ""
        if (videoPost.hashTags.isNotEmpty()) {
            for (i in videoPost.hashTags) tags += "#$i,"
            tags.dropLast(tags.length - 1)
        }
        if (caption != "" && tags == "") captionTagCompound = caption
        if (caption == "" && tags != "") captionTagCompound = tags
        if (caption != "" && tags != "") captionTagCompound = caption + "\n" + tags

        binding.tvUserName.text = videoPost.profileName
        binding.tvDescription.text = captionTagCompound
        Glide.with(this).load(videoPost.profileImage).into(binding.imgUser)
        binding.videoOwnerRoot.visibility = if (videoPost.creatorUid == user!!.uid) View.VISIBLE else View.GONE
    }

    override fun onResume() {
        if (activity != null && isAdded) {
            videoPostViewModel.setVideoPost(videoPost)
        }
        setData()
        val ref = FirebaseDatabase.getInstance().reference.child(USER_DETAILS).child(user!!.uid)
        ref.get().addOnSuccessListener { if (it.exists()) userProfile = it.getValue(UserProfile::class.java)!! }
        loadFile(Uri.parse(videoPost.videoUrl))
        Glide.with(requireActivity()).setDefaultRequestOptions(requestOptions).load(Uri.parse(videoPost.videoUrl)).into(binding.playerViewPreview)

        val videoReelRef = FirebaseDatabase.getInstance().reference
            .child(VIDEO_REEL_DETAILS)
            .child(videoPost.creatorUid)
            .child(videoPost.timePosted)
        videoReelRef.get().addOnSuccessListener {
            if (it.exists()) {
                for (i in it.children) {
                    val md = i.getValue(MomentDetails::class.java)!!
                    if (md.comment.profileUid != "" && md !in commentList) commentList.add(md)
                    if (md.loves.profileUid != "" && md !in loveList) loveList.add(md)
                    if (md.likes.profileUid != "" && md !in likeList) likeList.add(md)
                    if (md.loves.profileUid == user!!.uid) binding.loveBtn.setImageResource(com.like.view.R.drawable.heart_on)
                    if (md.likes.profileUid == user!!.uid) binding.likeBtn.setImageResource(R.drawable.like_blue)
                }
                binding.tvCommentCount.text = commentList.size.toString()
                binding.tvLoveCount.text = (loveList.size * Const.LOVE_VALUE).toInt().toString()
                binding.tvLikeCount.text = (likeList.size * Const.LIKE_VALUE).toInt().toString()
                if (activity != null && isAdded) {
                    momentDetailsViewModel.setMomentList(commentList)
                }
            }
        }
        super.onResume()
    }

    override fun onDestroy() {
        player.release()
        super.onDestroy()
    }

    override fun onPause() {
        player.pause()
        super.onPause()
    }

    private fun playerLongPress(videoPost: VideoPost, player: ExoPlayer) {
        binding.playerView.setOnLongClickListener {
            videoLongPressClickListener.onLongPress(videoPost, player)
            return@setOnLongClickListener false
        }
    }

    private fun loadFile(file: Uri) {
        if (ContextCompat.checkSelfPermission(requireActivity(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.CAMERA), 1)
        } else {
            try {
                loadingVideo()
                player = ExoPlayer.Builder(requireActivity()).build()
                binding.playerView.player = player
                val mediaItem = MediaItem.Builder().setLiveConfiguration(
                    MediaItem.LiveConfiguration.Builder()
                        .setMaxPlaybackSpeed(1.02f)
                        .setTargetOffsetMs(5000)
                        .setMinOffsetMs(5000)
                        .build()
                ).setUri(file).build()
                player.setMediaItem(mediaItem)
                player.playWhenReady = playWhenReady
                player.prepare()
                player.addListener(object : Player.Listener {
                    override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_BUFFERING -> loadingVideo()
                            Player.STATE_ENDED -> {
                                player.seekTo(0)
                                this@FragmentViewSingleVideo.playerPlayed()
                            }
                            Player.STATE_IDLE -> {}
                            Player.STATE_READY -> {
                                PLAYER_STATE = READY
                                videoReady()
                            }
                            else -> {}
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        super.onPlayerError(error)
                    }
                })
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Playback error", Toast.LENGTH_LONG).show()
                e.printStackTrace()
            }
        }
    }

    private fun loadingVideo() {
        binding.progressIndicator.visibility = View.VISIBLE
    }

    private fun videoReady() {
        playerLongPress(videoPost, player)
        binding.progressIndicator.visibility = View.GONE
        binding.playerViewPreview.visibility = View.GONE
    }

    private fun likePost(hfCoin: Float) {
        val otherRef = FirebaseDatabase.getInstance().reference.child(VIDEO_REEL_DETAILS).child(videoPost.creatorUid).child(videoPost.timePosted)
        when (userProfile.uid) {
            "" -> {
                val ref = FirebaseDatabase.getInstance().reference.child(USER_DETAILS).child(user!!.uid)
                ref.get().addOnSuccessListener {
                    if (it.exists()) {
                        userProfile = it.getValue(UserProfile::class.java)!!
                        sendLoveLikeHFCoin(hfCoin, othersRef = otherRef, creatorUid = videoPost.creatorUid, myProfile = userProfile, videoPost.timePosted)
                    }
                }
            }
            else -> sendLoveLikeHFCoin(hfCoin, othersRef = otherRef, creatorUid = videoPost.creatorUid, myProfile = userProfile, videoPost.timePosted)
        }
    }

    override fun playerPaused() {
        player.pause()
    }

    override fun playerPlayed() {
        player.play()
    }

    override fun playPosition(pos: Long) = Unit
    override fun hideControls() = Unit
    override fun showPostViews() = Unit

    override fun onClick(v: View?)  {
        when (v?.id) {
            R.id.delete_video -> {
                val alertDialog = AlertDialog.Builder(requireContext())
                alertDialog.setTitle("Delete video")
                alertDialog.setMessage("Confirm delete video")
                alertDialog.setPositiveButton("Delete") { dialog, _ ->
                    dialog.dismiss()
                    Toast.makeText(requireContext(), "Deleting...", Toast.LENGTH_LONG).show()
                    val imageRef = FirebaseStorage.getInstance().reference.child(VIDEO_POST).child(user!!.uid).child(videoPost.timePosted)
                    val refM = FirebaseDatabase.getInstance().reference.child(VIDEO_POST).child("-${videoPost.timePosted}")
                    val postRecord = FirebaseDatabase.getInstance().reference.child(VIDEO_POST_RECORD).child(user!!.uid).child(videoPost.timePosted)
                    refM.removeValue().addOnSuccessListener {
                        postRecord.removeValue()
                        imageRef.delete()
                        requireActivity().finish()
                    }
                }
                alertDialog.create().show()
            }
            R.id.img_comment -> {
                val modalBottomSheet = ModalBottomSheet()
                modalBottomSheet.show(requireActivity().supportFragmentManager, ModalBottomSheet.TAG)
            }
            R.id.player_view -> {
                if (player.isPlaying) this.playerPaused() else this.playerPlayed()
            }
            R.id.likeBtn -> {
                if (user!!.uid != videoPost.creatorUid) {
                    binding.likeBtn.setImageResource(com.like.view.R.drawable.thumb_on)
                    var like = binding.tvLikeCount.text.toString().toFloat() + Const.LIKE_VALUE
                    var originalLike = binding.tvLikeCount.text.toString().toInt().toString()
                    binding.tvLikeCount.text = like.toInt().toString()

                    val historyRef = FirebaseDatabase.getInstance().reference.child(FeedAdapter.TRANSFER_HISTORY).child(user!!.uid)
                    historyRef.get().addOnSuccessListener {
                        if (it.exists()) {
                            val available = HFCoinUtils.checkBalance(it)
                            when {
                                available < Const.LIKE_VALUE -> {
                                    binding.tvLikeCount.text = originalLike
                                    binding.likeBtn.setImageResource(R.drawable.like_white)
                                    Toast.makeText(requireContext(), "Insufficient HFCoin.", Toast.LENGTH_SHORT).show()
                                }
                                else -> likePost(Const.LIKE_VALUE)
                            }
                        } else {
                            binding.tvLikeCount.text = originalLike
                            binding.likeBtn.setImageResource(R.drawable.like_white)
                            Toast.makeText(requireContext(), "Insufficient HFCoin.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            R.id.loveBtn -> {
                if (user!!.uid != videoPost.creatorUid) {
                    binding.loveBtn.setImageResource(com.like.view.R.drawable.heart_on)
                    var love = binding.tvLoveCount.text.toString().toFloat() + Const.LOVE_VALUE
                    var originalove = binding.tvLoveCount.text.toString().toInt().toString()
                    binding.tvLoveCount.text = love.toInt().toString()

                    val historyRef = FirebaseDatabase.getInstance().reference.child(FeedAdapter.TRANSFER_HISTORY).child(user!!.uid)
                    historyRef.get().addOnSuccessListener {
                        if (it.exists()) {
                            val available = HFCoinUtils.checkBalance(it)
                            when {
                                available < Const.LOVE_VALUE -> {
                                    binding.tvLoveCount.text = originalove
                                    binding.likeBtn.setImageResource(com.like.view.R.drawable.heart_off)
                                    Toast.makeText(requireContext(), "Insufficient HFCoin.", Toast.LENGTH_SHORT).show()
                                }
                                else -> likePost(Const.LOVE_VALUE)
                            }
                        } else{
                            binding.tvLoveCount.text = originalove
                            binding.likeBtn.setImageResource(com.like.view.R.drawable.heart_off)
                            Toast.makeText(requireContext(), "Insufficient HFCoin.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val TRANSFER_HISTORY = "user_coins_transfer"
        const val USER_DETAILS = "user_details"
        const val VIDEO_REEL_DETAILS = "VIDEO_REEL_DETAILS"
        const val VIDEO_POST_RECORD = "VIDEO_POST_RECORD"
        const val VIDEO_POST = "VIDEO_POST"
        var READY = 3
    }
}

class ModalBottomSheet : BottomSheetDialogFragment(), View.OnClickListener {
    private lateinit var binding: VideoCommentBottomSheetBinding
    private var user = FirebaseAuth.getInstance().currentUser
    private var timeRef = FirebaseDatabase.getInstance().reference
    private var commentList = arrayListOf<MomentDetails>()
    private var userProfile = UserProfile()
    private val momentDetailsViewModel by activityViewModels<MomentDetailsViewModel>()
    private val videoPostViewModel by activityViewModels<VideoPostsViewModel>()
    private var commentAdapter = CommentAdapter()
    private var videoPost = VideoPost()

    @SuppressLint("NotifyDataSetChanged")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = VideoCommentBottomSheetBinding.inflate(inflater, container, false)
        binding.closeComments.setOnClickListener(this)
        binding.sendComments.setOnClickListener(this)
        timeRef = FirebaseDatabase.getInstance().reference.child("time").child(user!!.uid)
        commentAdapter.commentList = commentList
        binding.rvComments.adapter = commentAdapter
        binding.rvComments.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
        momentDetailsViewModel.momentDetailsList.observe(viewLifecycleOwner) {
            if (it.isNotEmpty()) binding.emptyComments.visibility = View.GONE
            for (i in it) if (i !in commentList) commentList.add(i)
            commentAdapter.notifyDataSetChanged()
        }
        videoPostViewModel.videoPost.observe(viewLifecycleOwner) {
            videoPost = it
        }
        return binding.root
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.close_comments -> this.dismiss()
            R.id.send_comments -> {
                val comment = binding.etComments.text.trim().toString()
                if (comment == "") return
                binding.etComments.text.clear()
                binding.progressComments.visibility = View.VISIBLE
                binding.sendComments.visibility = View.GONE
                val ref = FirebaseDatabase.getInstance().reference.child(USER_DETAILS).child(user!!.uid)
                ref.get().addOnSuccessListener {
                    if (it.exists()) {
                        userProfile = it.getValue(UserProfile::class.java)!!
                        timeRef.setValue(ServerValue.TIMESTAMP).addOnSuccessListener {
                            timeRef.get().addOnSuccessListener { timeSnapshot ->
                                if (timeSnapshot.exists()) {
                                    val timeSent = timeSnapshot.value.toString()
                                    val md = MomentDetails(
                                        time = timeSent,
                                        comment = MomentComment(
                                            profileComment = comment,
                                            profileName = userProfile.name,
                                            profilePhoto = userProfile.image,
                                            profileUid = user!!.uid
                                        )
                                    )
                                    val refX = FirebaseDatabase.getInstance().reference
                                        .child(VIDEO_REEL_DETAILS)
                                        .child(videoPost.creatorUid)
                                        .child(videoPost.timePosted)
                                        .child(timeSent)
                                    refX.setValue(md).addOnSuccessListener {
                                        binding.progressComments.visibility = View.GONE
                                        binding.sendComments.visibility = View.VISIBLE
                                        commentList.add(md)
                                        commentAdapter.notifyItemInserted(commentList.size)
                                        Toast.makeText(requireContext(), "Sent", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val TRANSFER_HISTORY = "user_coins_transfer"
        const val USER_DETAILS = "user_details"
        const val VIDEO_REEL_DETAILS = "VIDEO_REEL_DETAILS"
        const val TAG = "ModalBottomSheet"
    }
}

class CommentAdapter : RecyclerView.Adapter<CommentAdapter.ViewHolder>() {
    var commentList = arrayListOf<MomentDetails>()
    lateinit var context: Context

    init {
        setHasStableIds(true)
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val commentImage: ShapeableImageView = itemView.findViewById(R.id.comment_image)
        val commentName: TextView = itemView.findViewById(R.id.comment_name)
        val commentComment: TextView = itemView.findViewById(R.id.comment_comment)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        context = parent.context
        val view = LayoutInflater.from(context).inflate(R.layout.comment_row, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val datum = commentList[position]
        try {
            holder.commentComment.text = datum.comment.profileComment
            holder.commentName.text = datum.comment.profileName
            Glide.with(context).load(datum.comment.profilePhoto).into(holder.commentImage)
        } catch (e: Exception) {
        }
    }

    override fun getItemCount() = commentList.size
    override fun getItemId(position: Int) = position.toLong()
}