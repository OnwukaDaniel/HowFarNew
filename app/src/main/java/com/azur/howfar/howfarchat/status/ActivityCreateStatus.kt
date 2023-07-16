package com.azur.howfar.howfarchat.status

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.azur.howfar.R
import com.azur.howfar.activity.BaseActivity
import com.azur.howfar.databinding.ActivityCreateStatusBinding
import com.azur.howfar.howfarchat.FragmentCreateStatusMedia
import com.azur.howfar.howfarchat.groupChat.Contact
import com.azur.howfar.howfarchat.status.StatusType.IMAGE
import com.azur.howfar.howfarchat.status.StatusType.TEXT
import com.azur.howfar.models.StatusUpdateData
import com.azur.howfar.models.StatusUpdateType
import com.azur.howfar.utils.CanHubImage
import com.azur.howfar.utils.ImageCompressor
import com.azur.howfar.utils.Util
import com.azur.howfar.utils.progressbar.Spinner
import com.bumptech.glide.Glide
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageView
import com.canhub.cropper.options
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import java.util.*

class ActivityCreateStatus : BaseActivity(), View.OnClickListener {
    private val binding by lazy { ActivityCreateStatusBinding.inflate(layoutInflater) }
    private val myAuth = FirebaseAuth.getInstance().currentUser!!.uid
    private lateinit var pref: SharedPreferences
    private var permissionsStorage = arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
    private val scope = CoroutineScope(Dispatchers.IO)
    private val workManager = WorkManager.getInstance(this)
    private var pair: Pair<ByteArrayInputStream, ByteArray>? = null
    private var contacts: ArrayList<Contact> = arrayListOf()
    private lateinit var canhub :CanHubImage
    private val colors = arrayListOf("#25D265", "#660099", "#FFE595", "#FEAD2A", "#E7166B", "#C10451", "#660099", "#95FF00", "#669900", "#33000000", "#757575")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        askPermission()
        canhub = CanHubImage(this)
        binding.imageSend.setOnClickListener(this)
        pref = getSharedPreferences(getString(R.string.ALL_PREFERENCE), Context.MODE_PRIVATE)
        when (intent.getIntExtra("type", TEXT)) {
            TEXT -> showTextRoot()
            IMAGE -> canhub.openCanHub(cropImage, "Story")
        }
        binding.textSend.setOnClickListener {
            val spinner = Spinner()
            val status = binding.textStatus.text.toString().trim()
            if (binding.textStatus.text.isEmpty()) return@setOnClickListener
            supportFragmentManager.beginTransaction().replace(R.id.create_status_root, spinner)
            val data = StatusUpdateData(
                caption = status,
                senderUid = myAuth,
                statusType = StatusUpdateType.TEXT,
                timeSent = Calendar.getInstance().timeInMillis.toString(),
                captionBackgroundColor = colors.first()
            )
            initializeSendStatus(data)
        }
        binding.selectImage.setOnClickListener { canhub.openCanHub(cropImage, "Story")}
        binding.takePicture.setOnClickListener {
            supportFragmentManager.beginTransaction().replace(R.id.create_status_root, FragmentCreateStatusMedia()).commit()
        }
        binding.statusPalette.setOnClickListener {
            colors.shuffle()
            window.statusBarColor = Color.parseColor(colors.first())
            binding.textStatus.setBackgroundColor(Color.parseColor(colors.first()))
        }
        binding.selectVideo.setOnClickListener {
            val toast = Toast.makeText(this, "Coming soon", Toast.LENGTH_SHORT)
            toast.setGravity(Gravity.CENTER, 0, 0)
            toast.show()
        }
    }

    private fun initializeSendStatus(data: StatusUpdateData) {
        val statusDataJson = Gson().toJson(data)
        pref.edit().putString("status_time_sent ${data.timeSent}", statusDataJson).apply()
        val workData = workDataOf("time_sent" to data.timeSent)
        val workRequest = OneTimeWorkRequestBuilder<CreateStatusWorker>().setInputData(workData).build()
        workManager.enqueue(workRequest)
        Snackbar.make(binding.root, "Sending story ...", Snackbar.LENGTH_LONG).show()
        onBackPressed()
    }

    private fun showOptionsDialog() {
        binding.textLayout.visibility = View.GONE
        binding.mediaLayout.visibility = View.VISIBLE
    }

    @SuppressLint("NotifyDataSetChanged")
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { permissions ->
        when (permissions) {
            true -> contacts = Util.getAllSavedContacts(this@ActivityCreateStatus).first
            false -> if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_DENIED) {
                Snackbar.make(binding.root, "HowFar needs permission to find registered contacts from your contact", Snackbar.LENGTH_LONG).show()
                runBlocking {
                    delay(3000)
                    runOnUiThread {
                        onBackPressed()
                    }
                }
            }
        }
    }

    private fun askPermission() {
        if (!Util.permissionsAvailable(arrayOf(Manifest.permission.READ_CONTACTS), this))
            permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
    }

    private fun showTextRoot() {
        binding.textLayout.visibility = View.VISIBLE
        binding.mediaLayout.visibility = View.GONE
        colors.shuffle()
        window.statusBarColor = Color.parseColor(colors.first())
        binding.textStatus.setBackgroundColor(Color.parseColor(colors.first()))
    }

    override fun onDestroy() {
        super.onDestroy()
        window.statusBarColor = resources.getColor(R.color.appPrimaryColor)
    }

    private fun showDisplayImage() {
        binding.textLayout.visibility = View.GONE
        binding.mediaLayout.visibility = View.GONE
        binding.displayImageRoot.visibility = View.VISIBLE
    }

    private val cropImage = registerForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) {
            val uriContent = result.uriContent!!
            val uriFilePath = result.getUriFilePath(this) // optional usage
            try {
                showDisplayImage()
                val pair = ImageCompressor.compressImage(uriContent, this, null)
                Glide.with(this).load(pair.second).into(binding.displayImage)
                binding.imageSend.setOnClickListener {
                    val caption = binding.displayImageCaption.text.toString().trim()
                    val time = Calendar.getInstance().timeInMillis.toString()
                    val statusUpdateData =
                        StatusUpdateData(statusType = IMAGE, caption = caption, timeSent = time, senderUid = myAuth, imageUri = uriContent.toString())
                    initializeSendStatus(statusUpdateData)
                }
            } catch (e: Exception) {
                println("Exception *********************************************************** ${e.printStackTrace()}")
            }
        } else {
            // an error occurred
            val exception = result.error
            exception!!.printStackTrace()
        }
    }

    override fun onClick(v: View?) {
        when (v?.id) {
        }
    }
}

object StatusType {
    const val TEXT = 0
    const val IMAGE = 1
    const val VIDEO = 2
}