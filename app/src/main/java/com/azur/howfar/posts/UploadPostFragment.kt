package com.azur.howfar.posts

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
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.net.toFile
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.azur.howfar.R
import com.azur.howfar.databinding.FragmentUploadPostBinding
import com.azur.howfar.howfarchat.chat.FragmentDisplayImage
import com.azur.howfar.utils.HowFarWaterMark
import com.azur.howfar.viewmodel.UploadPostSpecialViewModel
import com.bumptech.glide.Glide
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.tonyodev.fetch2.*
import com.tonyodev.fetch2core.DownloadBlock
import org.jetbrains.annotations.NotNull
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.*

class UploadPostFragment : Fragment() {
    private lateinit var binding: FragmentUploadPostBinding
    private var dataset: ArrayList<String> = arrayListOf()
    private val uploadImagesAdapter = UploadImagesAdapter()
    private var shareAble = false
    private val uploadPostSpecialViewModel by activityViewModels<UploadPostSpecialViewModel>()

    @SuppressLint("NotifyDataSetChanged")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentUploadPostBinding.inflate(inflater, container, false)
        shareAble = requireArguments().getBoolean("shareAble", false)
        uploadPostSpecialViewModel.imagesStringList.observe(viewLifecycleOwner) {
            dataset = it
            uploadImagesAdapter.dataset = dataset
            uploadImagesAdapter.shareAble = shareAble
            uploadImagesAdapter.activity = requireActivity()
            binding.rvUploads.adapter = uploadImagesAdapter
            binding.rvUploads.layoutManager = LinearLayoutManager(requireActivity(), LinearLayoutManager.VERTICAL, false)
            uploadImagesAdapter.notifyDataSetChanged()
        }
        return binding.root
    }
}

class UploadImagesAdapter : RecyclerView.Adapter<UploadImagesAdapter.ViewHolder>() {
    lateinit var dataset: ArrayList<String>
    lateinit var context: Context
    lateinit var activity: Activity
    var shareAble = false
    private lateinit var loadingProgress: AlertDialog
    private lateinit var progressView: LinearProgressIndicator

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.row_image)
        private val shareText: TextView = itemView.findViewById(R.id.share_text)
        fun setData(arr: String) {
            shareText.visibility = if (shareAble) View.VISIBLE else View.GONE
            Glide.with(context).load(arr).centerCrop().error(R.drawable.app_logo_water_maek).into(imageView)
            imageView.setOnClickListener {
                val fragment = FragmentDisplayImage()
                val bundle = Bundle()
                bundle.putString("image", arr)
                fragment.arguments = bundle
                (activity as AppCompatActivity).supportFragmentManager.beginTransaction()
                    .addToBackStack("post")
                    .replace(R.id.upload_display_root, fragment)
                    .commit()
            }
            shareText.setOnClickListener {
                val progressAlert = AlertDialog.Builder(context)
                val pView = LayoutInflater.from(context).inflate(R.layout.dialog_progress, itemView.parent as ViewGroup, false)
                progressView = pView.findViewById(R.id.loading_progress)
                progressAlert.setView(pView)
                loadingProgress = progressAlert.create()
                if (loadingProgress.isShowing) loadingProgress.dismiss() else loadingProgress.show()
                val timeNow = Calendar.getInstance().timeInMillis.toString()
                val dir = File("${activity.cacheDir.path}/Media/SharedImages/")
                if (!dir.exists()) dir.mkdirs()
                val tempFile = File(dir, "$timeNow.png")
                initiateDownload(arr, Uri.parse(tempFile.path))
            }
        }

        fun getImageUri(inImage: Bitmap): File {
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

        private fun initiateDownload(image: String, file: Uri) {
            val fetchConfiguration: FetchConfiguration = FetchConfiguration.Builder(context).setDownloadConcurrentLimit(3).build()
            val fetch = Fetch.getInstance(fetchConfiguration)
            val request = Request(image, file)
            request.priority = Priority.HIGH
            request.networkType = NetworkType.ALL
            request.addHeader("clientKey", image)
            fetch.enqueue(request, { updatedRequest ->
                println("enqueue ********************************************* ${updatedRequest.file}")
            }) { error ->
                println("enqueue error ********************************************* $error")
            }
            fetch.addListener(object : FetchListener {
                override fun onStarted(download: Download, downloadBlocks: List<DownloadBlock>, totalBlocks: Int) {
                    println("download onStarted ********************************************* ${download.error}")
                }

                override fun onCompleted(download: Download) {
                    if (loadingProgress.isShowing) loadingProgress.dismiss()
                    val bitmap = BitmapFactory.decodeFile(download.fileUri.toFile().path)
                    val b = BitmapFactory.decodeResource(activity.resources, R.drawable.water_mark)
                    val waterMarked = HowFarWaterMark.addWatermark(bitmap, b, 0.15F)

                    val alertDialog = AlertDialog.Builder(context)
                    val viewX = LayoutInflater.from(context).inflate(R.layout.watermark_image, itemView.parent as ViewGroup, false)
                    val shareBtn: TextView = viewX.findViewById(R.id.share_btn)
                    val imageW: ImageView = viewX.findViewById(R.id.watermarked_image)
                    alertDialog.setView(viewX)
                    imageW.setImageBitmap(waterMarked)
                    val dialogShare = alertDialog.create()
                    dialogShare.show()

                    shareBtn.setOnClickListener {
                        if (dialogShare.isShowing) dialogShare.dismiss()
                        val fileW = getImageUri(waterMarked!!)
                        val uriTransfer = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", fileW)
                        val intent = Intent(Intent.ACTION_SEND)
                        intent.putExtra(Intent.EXTRA_STREAM, uriTransfer)
                        intent.putExtra(Intent.EXTRA_SUBJECT, "Moment")
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                        intent.type = "image/png"
                        activity.startActivity(Intent.createChooser(intent, "Share Moment Via"))
                    }
                }

                override fun onError(download: Download, error: Error, throwable: Throwable?) {
                    if (loadingProgress.isShowing) loadingProgress.dismiss()
                    Toast.makeText(context, "Download failed", Toast.LENGTH_LONG).show()
                    println("download.error ********************************************* ${download.error}")
                }

                override fun onProgress(@NotNull download: Download, etaInMilliSeconds: Long, downloadedBytesPerSecond: Long) {
                    if (loadingProgress.isShowing) loadingProgress.dismiss() else {
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
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        context = parent.context
        val view = LayoutInflater.from(context).inflate(R.layout.single_image, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.setData(dataset[position])
    }

    override fun getItemCount() = dataset.size

    override fun getItemViewType(position: Int) = position
}