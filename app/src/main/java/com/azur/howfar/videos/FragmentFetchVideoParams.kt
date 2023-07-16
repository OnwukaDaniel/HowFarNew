package com.azur.howfar.videos

import VideoHandle.EpEditor
import VideoHandle.EpVideo
import VideoHandle.OnEditorListener
import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.View.OnClickListener
import android.view.View.OnTouchListener
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import com.azur.howfar.R
import com.azur.howfar.workManger.VideoPostWorkManager
import com.azur.howfar.activity.MainActivity
import com.azur.howfar.databinding.FragmentFetchVideoParamsBinding
import com.azur.howfar.dilog.ProgressFragment
import com.azur.howfar.models.VideoPost
import com.azur.howfar.utils.Keyboard.hideKeyboard
import com.azur.howfar.utils.TimeUtils
import com.azur.howfar.utils.UriUtils
import com.azur.howfar.viewmodel.DialogMode
import com.azur.howfar.viewmodel.DialogMode.CHAT_FETCH_PROGRESS
import com.azur.howfar.viewmodel.DialogViewModel

class FragmentFetchVideoParams : Fragment(), OnClickListener, CustomExoplayerMethods, OnSeekBarChangeListener, OnTouchListener {
    private lateinit var binding: FragmentFetchVideoParamsBinding
    private var videoUri = Uri.EMPTY
    private var videoString = ""
    private lateinit var workManager: WorkManager
    private var hashTag: List<String> = listOf()
    private val dialogViewModel by activityViewModels<DialogViewModel>()
    private lateinit var player: ExoPlayer
    private val handler = Handler(Looper.getMainLooper())
    private var PLAYER_STATE = NOT_READY
    private var user = FirebaseAuth.getInstance().currentUser
    private var maxTimeClip = 0F
    private var minTimeClip = 0F
    private var VIDEO_MODE = 0
    private var videoPost = VideoPost()
    val progressFragment = ProgressFragment()

    private val handlerSize = Handler(Looper.getMainLooper())
    private val runnableSize = object : Runnable {
        override fun run() {
            handlerSize.postDelayed(this, 500)
            when (PLAYER_STATE) {
                READY -> {
                    val time = TimeUtils.milliSecondsToTimer(player.duration)
                    binding.allTime.text = time
                    binding.cursor.setOnSeekBarChangeListener(this@FragmentFetchVideoParams)
                    binding.postCursor.setOnSeekBarChangeListener(this@FragmentFetchVideoParams)
                    handlerSize.removeCallbacks(this)
                }
            }
        }
    }

    private val runnable = object : Runnable {
        override fun run() {
            when (PLAYER_STATE) {
                READY -> if (player.isPlaying) this@FragmentFetchVideoParams.playPosition(player.currentPosition)
                NOT_READY -> {}
            }
            handler.postDelayed(this, 1000)
        }
    }
    private val playingHandler = Handler(Looper.getMainLooper())
    private val playingRunnable = object : Runnable {
        override fun run() {
            if (player.isPlaying) binding.playToggle2.visibility = View.GONE
            playingHandler.postDelayed(this, 3000)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentFetchVideoParamsBinding.inflate(inflater, container, false)
        player = ExoPlayer.Builder(requireContext()).build()
        binding.playToggle2.setOnClickListener(this)
        binding.displayVideo.setOnClickListener(this)
        binding.cancel.setOnClickListener(this)
        binding.save.setOnClickListener(this)
        binding.trimEnd.setOnTouchListener(this)
        binding.trimStart.setOnTouchListener(this)

        binding.tagsBtn.setOnClickListener(this)
        binding.post.setOnClickListener(this)
        binding.cancelPost.setOnClickListener(this)
        videoString = requireArguments().getString("data")!!
        videoUri = Uri.parse(videoString)


        VIDEO_MODE = EDIT_MODE
        loadFile(videoUri)
        playingHandler.postDelayed(playingRunnable, 1000)
        handler.postDelayed(runnable, 1000)
        handlerSize.postDelayed(runnableSize, 500)
        workManager = WorkManager.getInstance(requireContext())
        return binding.root
    }

    override fun onDestroy() {
        handler.removeCallbacks(runnable)
        playingHandler.removeCallbacks(playingRunnable)
        handlerSize.removeCallbacks(runnableSize)
        super.onDestroy()
    }

    private fun loadFile(file: Uri, edited: Boolean = false) {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.CAMERA), 1)
        } else {
            if (!edited) {
                player = ExoPlayer.Builder(requireContext()).build()
                binding.displayVideo.player = player
            }
            val mediaItem = MediaItem.fromUri(file)
            player.setMediaItem(mediaItem)
            player.prepare()
            player.addListener(object : Player.Listener {

                override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_BUFFERING -> {}
                        Player.STATE_ENDED -> {
                            player.seekTo(0)
                            this@FragmentFetchVideoParams.playerPaused()
                        }
                        Player.STATE_IDLE -> {}
                        Player.STATE_READY -> {
                            PLAYER_STATE = READY
                        }
                        else -> {}
                    }
                }
            })
        }
    }

    private fun loadPostVideoLayout() {
        this@FragmentFetchVideoParams.hideControls()
        this@FragmentFetchVideoParams.showPostViews()

        showProgressBar(txt = "Please wait", type = DialogMode.NORMAL_PROGRESS)
        requireActivity().supportFragmentManager.beginTransaction().addToBackStack("progress").replace(R.id.fetch_video_root, progressFragment).commit()
        val epVideo = EpVideo(UriUtils.getPath(requireContext(), videoUri)!!)
        epVideo.clip(minTimeClip, maxTimeClip)

        val outPath: String = "${requireActivity().cacheDir.path}/Cropped" + "${System.currentTimeMillis()}.mp4"
        EpEditor.exec(epVideo, EpEditor.OutputOption(outPath), object : OnEditorListener {
            override fun onSuccess() {
                if (activity != null && isAdded) requireActivity().runOnUiThread {
                    dialogViewModel.setDialogMessage("Successful")
                    dialogViewModel.setDialogMode(DialogMode.NORMAL_PROGRESS)
                    dialogViewModel.setDisableBackPress(true)
                    dialogViewModel.setHideProgress(true)
                    player.stop()
                    handlerSize.postDelayed(runnableSize, 500)
                    loadFile(Uri.parse(outPath), edited = true)
                    VIDEO_MODE = POST_MODE
                    println("CAPTION *************************** ${binding.etCaption.text.trim().toString()}")
                    videoPost = VideoPost(creatorUid = user!!.uid, hashTags = hashTag, videoUrl = outPath)
                }
            }

            override fun onFailure() {
                dialogViewModel.setDialogMessage("Failed")
                dialogViewModel.setDialogMode(DialogMode.NORMAL_PROGRESS)
                dialogViewModel.setDisableBackPress(true)
                dialogViewModel.setHideProgress(true)
            }

            override fun onProgress(v: Float) {
                if (activity != null && isAdded) requireActivity().runOnUiThread { dialogViewModel.setProgress((v * 100).toInt()) }
            }
        })
    }

    private fun showProgressBar(txt: String = "Getting ...", backPress: Boolean = false, hideProgress: Boolean = false, type: Int = CHAT_FETCH_PROGRESS) {
        if (activity != null && isAdded) requireActivity().runOnUiThread {
            dialogViewModel.setDialogMessage(txt)
            dialogViewModel.setDialogMode(type)
            dialogViewModel.setDisableBackPress(backPress)
            dialogViewModel.setHideProgress(hideProgress)
        }
    }

    private fun workManagerUpload() {
        videoPost.caption = binding.etCaption.text.trim().toString()
        val json = Gson().toJson(videoPost)
        val workRequest = OneTimeWorkRequestBuilder<VideoPostWorkManager>().addTag("call and messages")
            .setInputData(workDataOf("json" to json))
            .build()
        workManager.enqueue(workRequest)
    }

    override fun onStop() {
        if (player.isPlaying) {
            this@FragmentFetchVideoParams.playerPaused()
        }
        super.onStop()
    }

    //override fun onPause() {
    //    if (player.isPlaying) this@FragmentFetchVideoParams.playerPaused()
    //    super.onPause()
    //}

    override fun playPosition(pos: Long) {
        val position = (player.currentPosition.toDouble() / player.duration.toDouble()) * 100
        val time = TimeUtils.milliSecondsToTimer(player.currentPosition)
        binding.currentTime.text = time
        when (VIDEO_MODE) {
            EDIT_MODE -> binding.cursor.progress = position.toInt()
            POST_MODE -> binding.postCursor.progress = position.toInt()
        }
    }

    override fun playerPaused() {
        player.pause()
        binding.playToggle2.setImageResource(R.drawable.ic_play_circle_outline)
    }

    override fun playerPlayed() {
        player.play()
        binding.playToggle2.setImageResource(R.drawable.ic_pause)
    }

    override fun hideControls() {
        binding.controlLayoutBottom.visibility = View.GONE
        binding.editToolbar.visibility = View.GONE
    }

    override fun showPostViews() {
        binding.postLayoutBottom.visibility = View.VISIBLE
        binding.postToolbar.visibility = View.VISIBLE
        binding.postTrackLayout.visibility = View.VISIBLE
    }

    private fun backLogic() {
        try {
            val alert = AlertDialog.Builder(requireContext())
            alert.setTitle("Exit")
            alert.setMessage("Do you want to discard this video?")
            alert.setNegativeButton("Continue") { dialog, _ ->
                dialog.dismiss()
            }
            alert.setPositiveButton("Discard") { dialog, _ ->
                dialog.dismiss()
                requireActivity().onBackPressed()
            }
            alert.create().show()
        } catch (e: Exception) {
            requireActivity().onBackPressed()
        }
    }

    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
        if (PLAYER_STATE == READY && fromUser) {
            player.pause()
            player.seekTo(((progress.toDouble() / 100) * player.duration.toDouble()).toLong())
            player.play()
        }
    }

    override fun onStartTrackingTouch(seekBar: SeekBar?) {
    }

    override fun onStopTrackingTouch(seekBar: SeekBar?) {
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View?, event: MotionEvent?): Boolean {
        val rectStartMarker = Rect()
        val rectEndMarker = Rect()
        val rectCursor = Rect()
        binding.trimStartMarker.getGlobalVisibleRect(rectStartMarker)
        binding.trimEndMarker.getGlobalVisibleRect(rectEndMarker)
        binding.cursor.getGlobalVisibleRect(rectCursor)

        val dXStart = 0F
        val dXEnd = 0F
        when (event!!.action) {
            MotionEvent.ACTION_DOWN -> {
                //dXStart = binding.trimStart.x - event.rawX
                //dXEnd = binding.trimEnd.x - event.rawX
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                when (v?.id) {
                    R.id.trim_start -> {
                        val pos = event.rawX + dXStart
                        val endCursor = Rect()
                        binding.trimEnd.getGlobalVisibleRect(endCursor)
                        if (pos < rectEndMarker.left && pos > binding.trimStartMarker.x && pos < endCursor.left) binding.trimStart.animate().x(pos)
                            .setDuration(0).start()
                    }
                    R.id.trim_end -> {
                        val startCursor = Rect()
                        binding.trimStart.getGlobalVisibleRect(startCursor)
                        val pos = event.rawX + dXEnd
                        if (pos < rectEndMarker.left && pos > binding.trimStartMarker.x && pos > startCursor.right) binding.trimEnd.animate().x(pos)
                            .setDuration(0).start()
                    }
                }
                return true
            }
            else -> {
                return false
            }
        }
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.display_video -> binding.playToggle2.visibility = View.VISIBLE
            R.id.play_toggle2 -> {
                if (player.isPlaying) this@FragmentFetchVideoParams.playerPaused()
                else this@FragmentFetchVideoParams.playerPlayed()
            }
            R.id.cancel -> backLogic()
            R.id.cancel_post -> backLogic()
            R.id.save -> {
                val endCursor = Rect()
                binding.trimEnd.getGlobalVisibleRect(endCursor)
                val startCursor = Rect()
                binding.trimStart.getGlobalVisibleRect(startCursor)

                val seekCursor = Rect()
                binding.track.getGlobalVisibleRect(seekCursor)
                minTimeClip = ((((startCursor.left - seekCursor.left).toDouble() / (seekCursor.right - seekCursor.left).toDouble())
                        * player.duration.toDouble()).toInt() / 1000).toFloat()
                maxTimeClip = (((endCursor.right.toDouble() / seekCursor.right.toDouble()) * player.duration.toDouble()).toInt() / 1000).toFloat()
                maxTimeClip = if (maxTimeClip > player.duration) player.duration.toFloat() else maxTimeClip
                loadPostVideoLayout()
            }
            R.id.tags_btn -> try {
                val alert = AlertDialog.Builder(requireContext())
                val view = LayoutInflater.from(requireContext()).inflate(R.layout.hash_tag_dialog, null)
                alert.setView(view)
                alert.setCancelable(true)
                val hashDialog = alert.create()

                val close: Button = view.findViewById(R.id.close)
                val caption: EditText = view.findViewById(R.id.caption)
                val addCaption: Button = view.findViewById(R.id.add_caption)
                close.setOnClickListener { hashDialog.dismiss() }
                caption.setText(binding.tags.text.toString().replace(" ", ""))
                addCaption.setOnClickListener {
                    hashDialog.dismiss()
                    if (caption.text.isEmpty()) return@setOnClickListener
                    val hashes = caption.text.toString().trim().replace(" ", "").replace("#", "").lowercase().split(",")
                    hashTag = hashes
                    var hashText = ""
                    for (i in hashes) hashText += "#${i.replace(" ", "")} "
                    binding.tags.text = hashText
                }
                alert.setOnDismissListener { hideKeyboard() }
                hashDialog.show()
            } catch (e: Exception) {
            }
            R.id.post -> try {
                val alert = AlertDialog.Builder(requireContext())
                alert.setTitle("VIDEO")
                alert.setMessage("Post this video?")
                alert.setPositiveButton("Post") { dialog, _ ->
                    dialog.dismiss()
                    workManagerUpload()
                    Toast.makeText(requireContext(), "Posting...", Toast.LENGTH_LONG).show()
                    val intent = Intent(requireContext(), MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    startActivity(intent)
                }
                alert.setNegativeButton("Cancel") { dialog, _ ->
                    dialog.dismiss()
                }
                alert.create().show()
            } catch (e: Exception) {
            }
        }
    }

    companion object {
        var READY = 3
        var NOT_READY = -1
        var EDIT_MODE = 4
        var POST_MODE = 5
    }
}

interface CustomExoplayerMethods {
    fun playPosition(pos: Long)
    fun playerPaused()
    fun playerPlayed()
    fun hideControls()
    fun showPostViews()
}