package com.azur.howfar.reels

import VideoHandle.EpDraw
import VideoHandle.EpEditor
import VideoHandle.EpEditor.OutputOption
import VideoHandle.EpVideo
import VideoHandle.OnEditorListener
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.net.toFile
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.azur.howfar.R
import com.azur.howfar.databinding.ActivityVideoListBinding
import com.azur.howfar.livedata.ChildQueryEventLiveData
import com.azur.howfar.livedata.ValueEventLiveData
import com.azur.howfar.livedata.ValueQueryEventLiveData
import com.azur.howfar.models.EventListenerType
import com.azur.howfar.models.EventListenerType.onChildAdded
import com.azur.howfar.models.EventListenerType.onChildRemoved
import com.azur.howfar.models.EventListenerType.onDataChange
import com.azur.howfar.models.VideoPost
import com.azur.howfar.videos.FragmentViewSingleVideo
import com.azur.howfar.videos.FragmentViewSingleVideo.VideoLongPressClickListener
import com.google.android.exoplayer2.ExoPlayer
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.gson.Gson
import com.tonyodev.fetch2.*
import com.tonyodev.fetch2core.DownloadBlock
import org.jetbrains.annotations.NotNull
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.*

@SuppressLint("NotifyDataSetChanged")
class VideoListActivity : AppCompatActivity(), ReelsHelper, VideoLongPressClickListener, View.OnClickListener {
    private var downloadRequestId: Int = -1
    private val binding by lazy { ActivityVideoListBinding.inflate(layoutInflater) }
    private var animation: Animation? = null
    private var fetch: Fetch? = null
    private val user = FirebaseAuth.getInstance().currentUser
    private var timeRef = FirebaseDatabase.getInstance().reference
    private var fragmentSet: MutableList<Fragment> = arrayListOf()
    private var dataset: MutableList<VideoPost> = arrayListOf()
    private var listOfPlayers: MutableList<ExoPlayer> = arrayListOf()
    private lateinit var llm: LinearLayoutManager
    private lateinit var reelTabAdapter: ReelTabAdapter
    private var savedVideo = VideoPost()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        timeRef = FirebaseDatabase.getInstance().reference.child("time").child(user!!.uid)
        if (intent.hasExtra("personal videos")) {
            val jsonArray = intent.getStringExtra("personal videos")
            val arrayList = Gson().fromJson(jsonArray, ArrayList::class.java)
            for (i in arrayList) {
                val videoPost = Gson().fromJson(Gson().toJson(i), VideoPost::class.java)
                dataset.add(videoPost)
                val json = Gson().toJson(videoPost)
                val fragmentViewSingleVideo = FragmentViewSingleVideo()
                val bundle = Bundle()
                bundle.putString("reel", json)
                fragmentViewSingleVideo.arguments = bundle
                fragmentSet.add(fragmentViewSingleVideo)
            }
        } else observers()
        binding.watchLater.setOnClickListener(this)
        binding.shareVideoCancel.setOnClickListener(this)
        binding.savingCancel.setOnClickListener(this)
        initVIew()
    }

    override fun onDestroy() {
        if (fetch != null) {
            fetch!!.close()
            if (downloadRequestId != -1) fetch!!.cancel(downloadRequestId)
        }
        super.onDestroy()
    }

    private fun observers() {
        val refVideos = FirebaseDatabase.getInstance().reference.child(VIDEO_POST).orderByKey()
        refVideos.keepSynced(false)

        ValueQueryEventLiveData(refVideos).observe(this) {
            when (it.second) {
                onDataChange -> {
                    for (i in it.first.children){
                        val videoPost = i.getValue(VideoPost::class.java)!!
                        if (videoPost !in dataset) {
                            dataset.add(videoPost)
                            dataset.sortByDescending { d-> d.timePosted }
                        }
                    }
                    for(videoPost in dataset){
                        val json = Gson().toJson(videoPost)
                        val fragmentViewSingleVideo = FragmentViewSingleVideo()
                        val bundle = Bundle()
                        bundle.putString("reel", json)
                        fragmentViewSingleVideo.arguments = bundle
                        fragmentSet.add(fragmentViewSingleVideo)
                        reelTabAdapter.notifyDataSetChanged()
                    }
                }
            }
        }
    }

    private fun initVIew() {
        animation = AnimationUtils.loadAnimation(binding.root.context, R.anim.slow_rotate)
        llm = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        reelTabAdapter = ReelTabAdapter(this)
        reelTabAdapter.dataset = fragmentSet as ArrayList<Fragment>
        binding.viewPagerReels.adapter = reelTabAdapter
        binding.viewPagerReels.offscreenPageLimit = 5
        val currentItem = intent.getIntExtra("current", 0)
        if (intent.hasExtra("current")) binding.viewPagerReels.setCurrentItem(currentItem, false)
    }

    inner class ReelTabAdapter(fa: FragmentActivity) : FragmentStateAdapter(fa) {
        lateinit var dataset: ArrayList<Fragment>
        override fun getItemCount(): Int = dataset.size
        override fun createFragment(position: Int): Fragment {
            return dataset[position]
        }
    }

    override fun getPlayer(player: ExoPlayer) {
        if (player !in listOfPlayers) listOfPlayers.add(player)
    }

    override fun onLongPress(videoPost: VideoPost, player: ExoPlayer) {
        savedVideo = videoPost
        showLoading()
        initiateDownload()
    }

    private fun showLoading() {
        binding.shareBottomBar.visibility = View.VISIBLE
        binding.loadingBar.visibility = View.VISIBLE
        binding.shareBar.visibility = View.GONE
    }

    private fun showShare() = runOnUiThread {
        binding.shareBottomBar.visibility = View.VISIBLE
        binding.loadingBar.visibility = View.GONE
        binding.shareBar.visibility = View.VISIBLE
    }

    private fun hideBottomBar() {
        binding.shareBottomBar.visibility = View.GONE
    }

    private fun initiateDownload() {
        val dir = File("${this.cacheDir.path}/Media/SharedVideos/${savedVideo.creatorUid}")
        if (!dir.exists()) dir.mkdirs()
        val tempFile = File(dir, "${savedVideo.timePosted}.mp4")

        val fetchConfiguration: FetchConfiguration = FetchConfiguration.Builder(this).setDownloadConcurrentLimit(1).build()
        fetch = Fetch.getInstance(fetchConfiguration)
        val request = Request(savedVideo.videoUrl, Uri.parse(tempFile.path))
        request.priority = Priority.HIGH
        request.networkType = NetworkType.ALL
        request.addHeader("clientKey", savedVideo.videoUrl)
        fetch!!.enqueue(request, { updatedRequest ->
            downloadRequestId = request.id
            binding.downloadPercent.text = "0 %"
            println("enqueue ********************************************* ${updatedRequest.file}")
        }) { error ->
            println("enqueue error ********************************************* $error")
        }
        fetch!!.addListener(object : FetchListener {
            override fun onStarted(download: Download, downloadBlocks: List<DownloadBlock>, totalBlocks: Int) = runOnUiThread {
                binding.downloadPercent.text = "0 %"
                println("download onStarted ********************************************* ${download.error}")
            }

            override fun onCompleted(download: Download) {
                showShare()
                binding.loadingProgress.progress = 0
                val path = download.fileUri.toFile()
                binding.shareVideo.setOnClickListener {
                    println("path ********************************************* $path")
                    val uriTransfer = FileProvider.getUriForFile(this@VideoListActivity, this@VideoListActivity.packageName + ".fileprovider", path)
                    val intent = Intent(Intent.ACTION_SEND)
                    intent.putExtra(Intent.EXTRA_STREAM, uriTransfer)
                    intent.putExtra(Intent.EXTRA_TEXT, savedVideo.caption)
                    intent.putExtra(Intent.EXTRA_SUBJECT, "Moment")
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    intent.type = "video/mp4"
                    startActivity(Intent.createChooser(intent, "Share Video"))
                    hideBottomBar()
                    //File(path.path).delete()
                }
                binding.saveVideo.setOnClickListener {
                    val resolver = this@VideoListActivity.contentResolver
                    val videoName = "HowFarVideo${System.currentTimeMillis()}.mp4"
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, videoName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                        if (Build.VERSION.SDK_INT >= 29) put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/HowFar/")
                        else put(
                            MediaStore.MediaColumns.DATA,
                            Environment.getExternalStorageDirectory().path + "/" + Environment.DIRECTORY_DOWNLOADS + "/HowFar/"
                        )
                    }
                    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI
                    } else MediaStore.Files.getContentUri("external")
                    val inputStream = FileInputStream(path)
                    hideBottomBar()
                    //File(path.path).delete()
                    val uri = resolver.insert(collection, contentValues)?.also { uri: Uri ->
                        resolver.openOutputStream(uri).use { outputStream ->
                            val buf = ByteArray(1024)
                            var len: Int
                            while (inputStream.read(buf).also { len = it } > 0) {
                                outputStream!!.write(buf, 0, len)
                            }
                            inputStream.close()
                            outputStream!!.close()
                            Toast.makeText(this@VideoListActivity, "Saved successfully to downloads", Toast.LENGTH_LONG).show()
                            hideBottomBar()
                        }
                    }
                }
            }

            override fun onError(download: Download, error: Error, throwable: Throwable?) = runOnUiThread {
                binding.downloadPercent.text = error.name
                hideBottomBar()
            }

            @SuppressLint("SetTextI18n")
            override fun onProgress(@NotNull download: Download, etaInMilliSeconds: Long, downloadedBytesPerSecond: Long) {
                binding.loadingProgress.progress = download.progress
                binding.downloadPercent.text = "${download.progress} %"
                println("updatedRequest ********************************************* ${download.progress}")
            }

            override fun onQueued(@NotNull download: Download, waitingOnNetwork: Boolean) = Unit
            override fun onRemoved(download: Download) = Unit
            override fun onResumed(download: Download) = Unit
            override fun onWaitingNetwork(download: Download) = Unit
            override fun onAdded(download: Download) = Unit
            override fun onCancelled(download: Download) {
            }

            override fun onDeleted(download: Download) = Unit
            override fun onDownloadBlockUpdated(download: Download, downloadBlock: DownloadBlock, totalBlocks: Int) = Unit
            override fun onPaused(download: Download) = Unit
        })
    }

    private fun watermark(videoPath: Uri, player: ExoPlayer) {
        val waterMarkDir = File("${this.getDir("WaterMark", 0).path}/")
        if (!waterMarkDir.exists()) waterMarkDir.mkdir()
        val watermark = File(waterMarkDir, "Watermark.png")
        if (!startedWatermarking(watermark)) return
        val imagePath = watermark.path
        val timeNow = Calendar.getInstance().timeInMillis.toString()
        val dir = File("${this.cacheDir.path}/Media/Videos/Watermark/")
        if (!dir.exists()) dir.mkdirs()
        val path = File(dir, "$timeNow.mp4")
        val epVideo = EpVideo(videoPath.path)
        var width = player.videoFormat!!.width
        var height = player.videoFormat!!.height
        val imgW = 170F
        val imgH = 200F
        val epDraw = EpDraw(imagePath, (width - imgW).toInt(), (height - imgH).toInt(), imgW, imgH, false)
        epVideo.addDraw(epDraw)
        EpEditor.exec(epVideo, OutputOption(path.path), object : OnEditorListener {
            override fun onSuccess() = runOnUiThread {
                println("RETURN_CODE_SUCCESS **********************************************")
                showShare()
                File(videoPath.path!!).delete()
                binding.shareVideo.setOnClickListener {
                    val uriTransfer = FileProvider.getUriForFile(this@VideoListActivity, this@VideoListActivity.packageName + ".fileprovider", path)
                    val intent = Intent(Intent.ACTION_SEND)
                    intent.putExtra(Intent.EXTRA_STREAM, uriTransfer)
                    intent.putExtra(Intent.EXTRA_TEXT, savedVideo.caption)
                    intent.putExtra(Intent.EXTRA_SUBJECT, "Moment")
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    intent.type = "video/mp4"
                    startActivity(Intent.createChooser(intent, "Share Video"))
                }
                binding.saveVideo.setOnClickListener {
                    val resolver = this@VideoListActivity.contentResolver
                    val videoName = "HowFarVideo${System.currentTimeMillis()}.mp4"
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, videoName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                        if (Build.VERSION.SDK_INT >= 29) put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/HowFar/")
                        else put(
                            MediaStore.MediaColumns.DATA,
                            Environment.getExternalStorageDirectory().path + "/" + Environment.DIRECTORY_DOWNLOADS + "/HowFar/"
                        )
                    }
                    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI
                    } else MediaStore.Files.getContentUri("external")
                    val inputStream = FileInputStream(path)
                    val uri = resolver.insert(collection, contentValues)?.also { uri: Uri ->
                        resolver.openOutputStream(uri).use { outputStream ->
                            val buf = ByteArray(1024)
                            var len: Int
                            while (inputStream.read(buf).also { len = it } > 0) {
                                outputStream!!.write(buf, 0, len)
                            }
                            inputStream.close()
                            outputStream!!.close()
                            Toast.makeText(this@VideoListActivity, "Saved successfully to downloads", Toast.LENGTH_LONG).show()
                            hideBottomBar()
                        }
                    }
                }
            }

            override fun onFailure() = runOnUiThread {
                binding.downloadPercent.text = "Failed!! Please retry."
                println("returnCode **********************************************")
            }

            override fun onProgress(progress: Float) {
            }
        })
    }

    private fun startedWatermarking(watermark: File): Boolean {
        if (!watermark.exists()) {
            val bm = BitmapFactory.decodeResource(resources, R.drawable.water_mark_small)
            var fos: FileOutputStream? = null
            try {
                fos = FileOutputStream(watermark)
                bm.compress(Bitmap.CompressFormat.PNG, 100, fos)
                fos.close()
                return true
            } catch (e: IOException) {
                if (fos != null) {
                    try {
                        fos.close()
                    } catch (e1: IOException) {
                        e1.printStackTrace()
                    }
                }
            }
        } else return true
        return false
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.watch_later -> {
                val savedVideosRef = FirebaseDatabase.getInstance().reference.child(WATCH_LATER_VIDEOS).child(user!!.uid)
                timeRef.setValue(ServerValue.TIMESTAMP).addOnSuccessListener { _ ->
                    timeRef.get().addOnSuccessListener {
                        if (it.exists()) {
                            val time = it.value.toString()
                            savedVideosRef.child(time).setValue(savedVideo).addOnSuccessListener {
                                Toast.makeText(this, "Saved successfully", Toast.LENGTH_LONG).show()
                                hideBottomBar()
                            }.addOnFailureListener {
                                Toast.makeText(this, "Failed to save. Please retry.", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
            R.id.saving_cancel -> {
                fetch!!.cancel(downloadRequestId)
                fetch!!.close()
                hideBottomBar()
            }
            R.id.share_video_cancel -> {
                hideBottomBar()
            }
        }
    }

    companion object {
        const val VIDEO_POST = "VIDEO_POST"
        const val USER_DETAILS = "user_details"
        const val WATCH_LATER_VIDEOS = "WATCH_LATER_VIDEOS"
        const val TRANSFER_HISTORY = "user_coins_transfer"
    }
}

interface ReelsHelper {
    fun getPlayer(player: ExoPlayer)
}