package com.azur.howfar.howfarchat.chat

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.SeekBar
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import com.azur.howfar.R
import com.azur.howfar.databinding.FragmentDisplayVideoBinding
import com.azur.howfar.models.ChatData
import com.azur.howfar.utils.Util
import com.azur.howfar.videos.CustomExoplayerMethods
import com.azur.howfar.videos.FragmentFetchVideoParams
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import com.tonyodev.fetch2.*
import com.tonyodev.fetch2.Fetch.Impl.getInstance
import com.tonyodev.fetch2core.DownloadBlock
import org.jetbrains.annotations.NotNull
import java.io.File

class FragmentDisplayVideo : Fragment(), View.OnClickListener, SeekBar.OnSeekBarChangeListener, CustomExoplayerMethods {
    private lateinit var binding: FragmentDisplayVideoBinding
    private lateinit var player: ExoPlayer
    private var chatData = ChatData()
    private var videoUrl = ""
    private var fetch: Fetch? = null
    private var isMediaReady = false
    private var downloadFile = Uri.EMPTY
    private var lockFile = File("")
    private var PLAYER_STATE = NOT_READY
    val requestOptions = RequestOptions()

    private val handlerSize = Handler(Looper.getMainLooper())
    private val handlerProgress = Handler(Looper.getMainLooper())
    private val runnableSize = object : Runnable {
        override fun run() {
            handlerSize.postDelayed(this, 500)
            when (PLAYER_STATE) {
                FragmentFetchVideoParams.READY -> {
                    binding.playSeekBar.setOnSeekBarChangeListener(this@FragmentDisplayVideo)
                    handlerSize.removeCallbacks(this)
                }
            }
        }
    }

    private val runnable = object : Runnable {
        override fun run() {
            when (PLAYER_STATE) {
                READY -> if (player.isPlaying) this@FragmentDisplayVideo.playPosition(player.currentPosition)
            }
            handlerProgress.postDelayed(this, 1000)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentDisplayVideoBinding.inflate(inflater, container, false)
        videoUrl = requireArguments().getString("video")!!
        val jsonChat = requireArguments().getString("json")
        chatData = Gson().fromJson(jsonChat, ChatData::class.java)
        binding.playDownload.visibility = View.GONE
        player = ExoPlayer.Builder(requireContext()).build()

        tryLoadingLocalFirst()

        binding.videoDisplayRoot.setOnClickListener(this)
        binding.playDownload.setOnClickListener(this)
        handlerSize.postDelayed(runnableSize, 500)
        handlerProgress.postDelayed(runnable, 500)
        return binding.root
    }

    private fun tryLoadingLocalFirst() {
        loadFile(Uri.parse(videoUrl))
    }

    private fun loadFile(file: Uri) {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.CAMERA), 1)
        } else {
            Glide.with(requireActivity()).setDefaultRequestOptions(requestOptions).load(file).into(binding.playerViewPreview)
            if (lockFile.exists()) lockFile.delete()
            binding.displayVideo.player = player
            val mediaItem = MediaItem.fromUri(file)
            player.setMediaItem(mediaItem)
            player.prepare()
            player.addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    //Toast.makeText(context, "${error.message}", Toast.LENGTH_LONG).show()
                    val tempDir = File("${requireContext().filesDir.path}/temp/Media/Videos/Chat/")
                    lockFile = File(tempDir.path, "${chatData.senderuid} + ${chatData.uniqueQuerableTime}.lock")
                    val dir = File("${requireContext().filesDir.path}/Media/Videos/Chat/")
                    if (!dir.exists()) dir.mkdirs()
                    val realFile = File(dir, "${chatData.senderuid}${chatData.uniqueQuerableTime}.mp4")
                    if (!tempDir.exists()) tempDir.mkdirs()
                    if (!realFile.exists()) {
                        lockFile.createNewFile()
                        if (lockFile.exists()) {
                            downloadFile = Uri.fromFile(realFile)
                            binding.playDownload.visibility = View.VISIBLE
                        }
                    } else {
                        loadFile(Uri.fromFile(realFile))
                    }

                    super.onPlayerError(error)
                }

                override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_BUFFERING -> {}
                        Player.STATE_ENDED -> {}
                        Player.STATE_IDLE -> {}
                        Player.STATE_READY -> {
                            fileDownloaded()
                            PLAYER_STATE = READY
                        }
                        else -> {
                        }
                    }
                }
            })
        }
        binding.playSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(p0: SeekBar?, p1: Int, p2: Boolean) {
                player.seekTo(p1.toLong())
            }

            override fun onStartTrackingTouch(p0: SeekBar?) {
            }

            override fun onStopTrackingTouch(p0: SeekBar?) {
            }
        })

        binding.playToggle2.setOnClickListener {
            if (!player.isPlaying) myOnPlay() else myOnPause()
        }
    }

    private fun myOnPause() {
        binding.playToggle2.setImageResource(R.drawable.ic_play_circle_outline)
        player.pause()
    }

    private fun myOnPlay() {
        binding.playToggle2.setImageResource(R.drawable.ic_pause)
        player.play()
    }

    private fun openControls() {
        binding.playControls.visibility = View.VISIBLE
    }

    override fun playPosition(pos: Long) {
        val position = (player.currentPosition.toDouble() / player.duration.toDouble()) * 100
        binding.playSeekBar.progress = position.toInt()
    }

    override fun playerPaused() = Unit
    override fun playerPlayed() = Unit
    override fun hideControls() = Unit
    override fun showPostViews() = Unit
    private fun classHideControls() {
        binding.playControls.visibility = View.GONE
    }

    private fun fileDownloaded() {
        isMediaReady = true
        binding.playerViewPreview.visibility = View.GONE
        binding.playDownload.visibility = View.GONE
    }

    override fun onPause() {
        super.onPause()
        player.pause()
    }

    override fun onClick(p0: View?) {
        when (p0?.id) {
            R.id.video_display_root -> {
                when (binding.playControls.visibility) {
                    View.VISIBLE -> classHideControls()
                    View.GONE -> openControls()
                    View.INVISIBLE -> {}
                }
            }
            R.id.play_download -> downloadVideo(downloadFile)
        }
    }

    private fun downloadVideo(file: Uri) {
        val fetchConfiguration: FetchConfiguration = FetchConfiguration.Builder(requireContext())
            .setDownloadConcurrentLimit(3)
            .build()
        fetch = getInstance(fetchConfiguration)

        val request = Request(chatData.videoData.storageLink, file)
        request.priority = Priority.HIGH
        request.networkType = NetworkType.ALL
        request.addHeader("clientKey", chatData.videoData.storageLink)

        fetch!!.enqueue(request, { updatedRequest ->
            println("updatedRequest ********************************************* ${updatedRequest.file}")
        }) { error ->
            println("Error ********************************************* $error")
            binding.playDownload.visibility = View.GONE
        }
        fetch!!.addListener(object : FetchListener {
            override fun onQueued(@NotNull download: Download, waitingOnNetwork: Boolean) = Unit
            override fun onRemoved(download: Download) = Unit
            override fun onResumed(download: Download) = Unit
            override fun onWaitingNetwork(download: Download) = Unit
            override fun onAdded(download: Download) = Unit
            override fun onPaused(download: Download) = Unit
            override fun onCancelled(download: Download) {
                binding.playDownload.visibility = View.GONE
            }

            override fun onDeleted(download: Download) = Unit

            override fun onStarted(download: Download, downloadBlocks: List<DownloadBlock>, totalBlocks: Int) {
                startDownloadSetting()
            }

            override fun onCompleted(download: Download) {
                binding.playProgress.visibility = View.GONE
                binding.playDownload.visibility = View.GONE
                loadFile(file)
            }


            override fun onDownloadBlockUpdated(download: Download, downloadBlock: DownloadBlock, totalBlocks: Int) = Unit

            override fun onError(download: Download, error: Error, throwable: Throwable?) {
                println("download.error ********************************************* ${download.error}")
                Snackbar.make(binding.root, error.name, Snackbar.LENGTH_LONG).show()
                binding.playDownload.visibility = View.GONE
            }

            override fun onProgress(@NotNull download: Download, etaInMilliSeconds: Long, downloadedBytesPerSecond: Long) {
                println("updatedRequest ********************************************* ${download.progress}")
                val progress = download.progress
                if (progress == 1) binding.playProgress.setProgressCompat(progress, true)
                if (binding.playProgress.visibility != View.VISIBLE) binding.playProgress.visibility = View.VISIBLE
                binding.playProgress.progress = download.progress
            }
        })
    }

    private fun startDownloadSetting() {
        binding.playProgress.visibility = View.VISIBLE
        binding.playDownload.visibility = View.GONE
    }

    override fun onDestroy() {
        player.stop()
        player.release()
        requireActivity().window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        handlerSize.removeCallbacks(runnableSize)
        handlerProgress.removeCallbacks(runnable)
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        requireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
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

    companion object {
        var READY = 3
        var NOT_READY = -1
    }
}