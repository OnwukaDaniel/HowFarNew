package com.azur.howfar.services

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.azur.howfar.R
import com.azur.howfar.utils.ArchitectureClass
import com.google.gson.Gson
import com.tonyodev.fetch2.*
import com.tonyodev.fetch2core.DownloadBlock
import org.jetbrains.annotations.NotNull
import java.io.File

class AgoraDownloadService : Service() {
    override fun onBind(intent: Intent?): Nothing? = null
    private var stringArray = arrayListOf<ArchitectureClass>()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val array = intent!!.getStringExtra("files")!!
        val rawStringArray = Gson().fromJson(array, ArrayList::class.java)
        for (i in rawStringArray) stringArray.add(Gson().fromJson(Gson().toJson(i), ArchitectureClass::class.java))
        getNextFile()
        createNotification()
        return START_STICKY
    }

    private fun createNotification() {
        val builder: NotificationCompat.Builder = NotificationCompat.Builder(this, "App files")
            .setSmallIcon(R.drawable.app_icon_sec)
            .setContentTitle("Calls")
            .setColor(Color.GREEN)
            .setContentText("Downloading...")
            .setPriority(NotificationCompat.PRIORITY_LOW)
        startForeground(10000, builder.build())
    }

    private fun getNextFile() {
        val locks = File("${this@AgoraDownloadService.filesDir}/AgoraDownload/ArchitectureLock/")
        val abiFile = File("${this.filesDir}/AgoraDownload/Architecture/")
        if (locks.list()!!.isNotEmpty()) {
            val newName = locks.list()!![0]
            for (i in stringArray) {
                if (i.fileName.replace(".so", "") == newName.replace(".lock", "")) {
                    val file = File(abiFile, "${i.fileName}.so")
                    if (file.exists()) file.delete()
                    newRequest(i, stringArray)
                    break
                }
            }
        }
    }

    private fun newRequest(url: ArchitectureClass, stringArray: ArrayList<ArchitectureClass>) {
        val abiFile = File("${this.filesDir}/AgoraDownload/Architecture/")
        val lockFile = File("${this.filesDir}/AgoraDownload/ArchitectureLock/")
        val file = File(abiFile, "${url.fileName}.so")
        val lock = File(lockFile, "${url.fileName}.lock")
        val request = Request(url.downloadLink, Uri.parse(file.path))
        request.priority = Priority.HIGH
        request.networkType = NetworkType.ALL
        request.addHeader("clientKey", url.downloadLink)

        val fetchConfiguration: FetchConfiguration = FetchConfiguration.Builder(this).setDownloadConcurrentLimit(1).build()
        val fetch = Fetch.getInstance(fetchConfiguration)
        fetch.enqueue(request, { updatedRequest ->
            println("enqueue ********************************************* ${updatedRequest.file}")
        }) { error ->
            println("enqueue error ********************************************* $error")
        }
        fetch.addListener(object : FetchListener {
            override fun onStarted(download: Download, downloadBlocks: List<DownloadBlock>, totalBlocks: Int) {
            }

            override fun onCompleted(download: Download) {
                if (lock.exists()) lock.delete()
                getNextFile()
            }

            override fun onError(download: Download, error: Error, throwable: Throwable?) {
                println("download.error ********************************************* ${download.error}")
            }

            override fun onProgress(@NotNull download: Download, etaInMilliSeconds: Long, downloadedBytesPerSecond: Long) {
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
}