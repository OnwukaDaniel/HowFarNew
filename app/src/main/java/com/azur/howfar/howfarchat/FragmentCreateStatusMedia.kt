package com.azur.howfar.howfarchat

import android.Manifest
import android.Manifest.permission.*
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.azur.howfar.databinding.FragmentCreateStatusMediaBinding
import com.azur.howfar.howfarchat.groupChat.Contact
import com.azur.howfar.models.StatusUpdateData
import com.azur.howfar.models.StatusUpdateType
import com.azur.howfar.models.UserProfile
import com.azur.howfar.utils.Util
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import com.azur.howfar.R

class FragmentCreateStatusMedia : Fragment(), View.OnClickListener {
    private lateinit var binding: FragmentCreateStatusMediaBinding
    private val myAuth = FirebaseAuth.getInstance().currentUser!!.uid
    private val contactFormattedList: ArrayList<String> = arrayListOf()
    private val listOfUsers: ArrayList<UserProfile> = arrayListOf()
    private val scope = CoroutineScope(Dispatchers.IO)
    private var timeRef = FirebaseDatabase.getInstance().reference
    private val registeredContactList: ArrayList<UserProfile> = arrayListOf()
    private var allUsersRef = FirebaseDatabase.getInstance().reference.child("user_details")
    private val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
    private var imageCapture: ImageCapture? = null
    private val CAMERA1_RESULT_CODE = 100
    private var pictureImagePath = ""
    private val contactList: ArrayList<Contact> = arrayListOf()

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permission ->
        if (permission[REQUIRED_PERMISSIONS[0]] == false) showRationale()
        if (permission[REQUIRED_PERMISSIONS[1]] == false) showRationale()
        if (permission[REQUIRED_PERMISSIONS[3]] == false) showRationale()
        if (REQUIRED_PERMISSIONS.size > 3 && permission[REQUIRED_PERMISSIONS[3]] == false) showRationale()
        if (permission[CAMERA] == true) {
            if (REQUIRED_PERMISSIONS.size > 2) {
                if (permission[REQUIRED_PERMISSIONS[2]] == true) //startCamera()
                    return@registerForActivityResult
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentCreateStatusMediaBinding.inflate(inflater, container, false)
        binding.capturedImageSend.setOnClickListener(this)
        displayCameraView()
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        Util.hideSystemUI(requireActivity().window, binding.root)
        for ((index, i) in REQUIRED_PERMISSIONS.withIndex()) {
            if (ContextCompat.checkSelfPermission(requireContext(), i) == PackageManager.PERMISSION_DENIED) {
                permissionLauncher.launch(REQUIRED_PERMISSIONS)
                break
            }
            // LAST PERMISSION
            if (index == REQUIRED_PERMISSIONS.size - 1 && ContextCompat.checkSelfPermission(requireContext(), i) == PackageManager.PERMISSION_GRANTED) {
                //startCamera()
            }
            if (index == REQUIRED_PERMISSIONS.size - 1 && ContextCompat.checkSelfPermission(requireContext(), i) == PackageManager.PERMISSION_DENIED) break
        }
        binding.captureCircle.setOnClickListener { camera1Capture() }
    }

    private fun camera1Capture() {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val imageFileName = "$timeStamp.jpg"
        val storageDir: File = Environment.getExternalStoragePublicDirectory("HowFar/Media/Images")
        pictureImagePath = storageDir.absolutePath.toString() + "/" + imageFileName
        val file = File(pictureImagePath)
        val outputFileUri = Uri.fromFile(file)
        val intentCapture = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        intentCapture.putExtra(MediaStore.EXTRA_OUTPUT, outputFileUri)
        if (intentCapture.resolveActivity(requireActivity().packageManager) != null) {
            startActivityForResult(intentCapture, CAMERA1_RESULT_CODE)
        }
    }

    private fun displayOnlyCapturedImage() {
        binding.cameraDisplayImage.visibility = View.VISIBLE
        binding.cameraId.visibility = View.GONE
    }

    private fun displayCameraView() {
        binding.cameraDisplayImage.visibility = View.GONE
        binding.cameraId.visibility = View.VISIBLE
    }

    private fun showRationale() {
        Snackbar.make(binding.root, "HowFar requires permission to get your contacts and read files from your storage", Snackbar.LENGTH_LONG).show()
        permissionLauncher.launch(REQUIRED_PERMISSIONS)
    }

    companion object {
        private val REQUIRED_PERMISSIONS =
            mutableListOf(CAMERA, RECORD_AUDIO, READ_CONTACTS).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }.toTypedArray()
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.captured_image_send -> {
                if (contactList.isNotEmpty()) scope.launch {
                }
            }
        }
    }

    private fun sendStatus(registeredContact: ArrayList<UserProfile>) {
        val timeRef = FirebaseDatabase.getInstance().reference.child("time").child(myAuth)
        val caption = binding.caption.text.toString().trim()
        var dataSentNotifier = false
        val timeListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists() && !dataSentNotifier) {
                    dataSentNotifier = true
                    val rawTime = snapshot.value.toString()
                    val data = StatusUpdateData(serverTime = rawTime, caption = caption, senderUid = myAuth, statusType = StatusUpdateType.TEXT)
                    for (i in registeredContact) {
                        val ref = FirebaseDatabase.getInstance().reference.child("status_update").child(i.uid).child(rawTime)
                        ref.setValue(data).addOnSuccessListener {}
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) = Unit
        }
        timeRef.setValue(ServerValue.TIMESTAMP)
        timeRef.addValueEventListener(timeListener)
    }

    //@SuppressLint("RestrictedApi")
    /*private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder().build()
            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        }, ContextCompat.getMainExecutor(requireContext()))
    }*/

    /*private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        // Create time stamped name and MediaStore entry.
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            this.put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "HowFar/Images")
            }
        }

        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            requireActivity().contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ).build()

        // Set up image capture listener, which is triggered after photo has
        // been taken
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e("TAG", "Photo capture failed: ${exc.message}", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val msg = "Photo capture succeeded: ${output.savedUri}"
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                    Log.d("TAG", msg)
                }
            }
        )
    }*/
}