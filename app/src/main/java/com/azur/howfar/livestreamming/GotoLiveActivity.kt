package com.azur.howfar.livestreamming

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.azur.howfar.R
import com.azur.howfar.camera.CameraActivity
import com.azur.howfar.databinding.ActivityGotoLiveBinding
import com.azur.howfar.dilog.ProgressFragment
import com.azur.howfar.models.BroadcastCallData
import com.azur.howfar.models.ParticipantTempData
import com.azur.howfar.models.UserProfile
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.gson.Gson
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class GotoLiveActivity : AppCompatActivity(), View.OnClickListener {
    private val binding by lazy { ActivityGotoLiveBinding.inflate(layoutInflater) }
    var front = 1
    var back = 2
    var camara = CameraSelector.DEFAULT_BACK_CAMERA
    var isPrivate = false
    private lateinit var cameraProvider: ProcessCameraProvider
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    private val myAuth = FirebaseAuth.getInstance().currentUser!!.uid
    private var timeRef = FirebaseDatabase.getInstance().reference
    val progressFragment = ProgressFragment()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        timeRef = FirebaseDatabase.getInstance().reference.child("time").child(FirebaseAuth.getInstance().currentUser!!.uid)
        setContentView(binding.root)
        if (allPermissionsGranted()) {
            startCamera()
            binding.btnLive.setOnClickListener(this)
        } else {
            ActivityCompat.requestPermissions(this, CameraActivity.REQUIRED_PERMISSIONS, CameraActivity.REQUEST_CODE_PERMISSIONS)
        }
        binding.btnSwitchCamara.setOnClickListener(this)
        binding.lytPrivacy.setOnClickListener(this)
        binding.btnClose.setOnClickListener(this)
        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    override fun onResume() {
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        super.onResume()
    }

    private fun allPermissionsGranted() = CameraActivity.REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(binding.viewFinder.surfaceProvider) }
            imageCapture = ImageCapture.Builder().build()
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            } catch (exc: Exception) {
                Log.e(CameraActivity.TAG, "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CameraActivity.REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun goLive(listOfFollowers: Set<String> = setOf()) {
        val myRef = FirebaseDatabase.getInstance().reference.child(USER_DETAILS).child(FirebaseAuth.getInstance().currentUser!!.uid)
        timeRef.setValue(ServerValue.TIMESTAMP).addOnSuccessListener {
            timeRef.get().addOnSuccessListener { time ->
                if (time.exists()) {
                    var broadcastCallData = BroadcastCallData(
                        callerUid = myAuth,
                        timeCalled = time.value.toString(),
                        channelName = myAuth,
                        uids = listOfFollowers.toMutableList() as ArrayList<String>,
                        isPrivate = isPrivate,
                    )
                    myRef.get().addOnSuccessListener {
                        if (it.exists()) {
                            val myProfile = it.getValue(UserProfile::class.java)!!
                            broadcastCallData.senderTempData =
                                ParticipantTempData(tempImage = myProfile.image, tempName = myProfile.name, uid = myProfile.uid, phone = myProfile.phone)
                            val json = Gson().toJson(broadcastCallData)
                            val intent = Intent(this, HostLiveActivity::class.java).apply { putExtra("data", json) }
                            startActivity(intent)
                            finish()
                        }
                    }
                }
            }
        }
    }

    private fun getFollowers() {
        supportFragmentManager.beginTransaction().replace(R.id.live_root, progressFragment).commit()
        when (isPrivate) {
            true -> {
                val followers = FirebaseDatabase.getInstance().reference.child(FOLLOWERS).child(myAuth)
                val following = FirebaseDatabase.getInstance().reference.child(FOLLOWING).child(myAuth)
                followers.keepSynced(false)
                following.keepSynced(false)
                followers.get().addOnSuccessListener {
                    var listOfFollowers: Set<String> = setOf()
                    supportFragmentManager.beginTransaction().remove(progressFragment).commit()
                    if (it.exists()) for (i in it.children) listOfFollowers = listOfFollowers.plus(i.value.toString())

                    following.get().addOnSuccessListener { following ->
                        if (following.exists()) for (i in following.children) listOfFollowers = listOfFollowers.plus(i.value.toString())
                        if (listOfFollowers.isEmpty()) {
                            val alertDialog = AlertDialog.Builder(this)
                            alertDialog.setTitle("Can't go live")
                            alertDialog.setMessage(
                                "Reason: you don't have any followers or following any like user\n" +
                                        "Suggestion: Follow at least on user to create broadcast or go public."
                            )
                            alertDialog.setPositiveButton("Ok") { dialog, _ -> dialog.dismiss() }
                            alertDialog.create().show()
                        } else goLive(listOfFollowers)
                    }.addOnFailureListener {
                        failed()
                    }
                }.addOnFailureListener {
                    Toast.makeText(this, "You don't have followers", Toast.LENGTH_LONG).show()
                    failed()
                }
            }
            false -> goLive()
        }
    }

    private fun failed() {
        supportFragmentManager.beginTransaction().remove(progressFragment).commit()
        Toast.makeText(this, "Failed to go live. Try again", Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        super.onDestroy()
    }

    companion object {
        const val TAG = "hostliveactivity"
        const val CALL_REFERENCE = "call_reference"
        const val USER_DETAILS = "user_details"
        const val FOLLOWERS = "followers"
        const val FOLLOWING = "following"
        val REQUESTED_PERMISSIONS = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
        )
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.btnLive -> getFollowers()
            R.id.btnSwitchCamara -> {
                camara = if (camara == CameraSelector.DEFAULT_BACK_CAMERA) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(binding.viewFinder.surfaceProvider) }
                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(this, camara, preview, imageCapture)
                } catch (exc: Exception) {
                    Log.e(CameraActivity.TAG, "Use case binding failed", exc)
                }
            }
            R.id.btnClose -> onBackPressed()
            R.id.lytPrivacy -> {
                isPrivate = !isPrivate
                if (isPrivate) {
                    binding.imgLock.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.lock))
                    binding.tvPrivacy.text = "Private"
                } else {
                    binding.imgLock.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.unlock))
                    binding.tvPrivacy.text = "Public"
                }
            }
        }
    }
}