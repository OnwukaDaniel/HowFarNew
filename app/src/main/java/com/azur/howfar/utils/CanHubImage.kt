package com.azur.howfar.utils

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageView
import com.canhub.cropper.options

class CanHubImage(val context: Activity, val x : Int= 0, val y: Int = 0) {
    private var callUtils: CallUtils = CallUtils(context as AppCompatActivity, context)

    @RequiresApi(33)
    var permissionsStorageT = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_AUDIO,
        Manifest.permission.READ_MEDIA_VIDEO,
    )
    private var permissionsStorage = arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)

    val permissionLauncher =
        (context as AppCompatActivity).registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val notGranted = arrayListOf<Boolean>()
            for (per in permissions.values) if (!per) notGranted.add(per)
            if (notGranted.isNotEmpty()) {
                callUtils.permissionRationale(message = "HowFar needs permission to choose files and camera.\nGrant app permission")
                return@registerForActivityResult
            }
            Toast.makeText(context, "Select again", Toast.LENGTH_LONG).show()
        }

    fun openCanHub(cropImage: ActivityResultLauncher<CropImageContractOptions>, title: String = "Profile image") {
        when {
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU -> {
                if (!Util.permissionsAvailable(permissionsStorageT, context))
                    permissionLauncher.launch(permissionsStorageT) else launch(cropImage, title)
            }
            android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU -> {
                if (!Util.permissionsAvailable(permissionsStorage, context))
                    permissionLauncher.launch(permissionsStorage) else launch(cropImage, title)
            }
        }
    }

    fun justMediaPermission() {
        when {
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU -> {
                if (!Util.permissionsAvailable(permissionsStorageT, context)) permissionLauncher.launch(permissionsStorageT)
            }
            android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU -> {
                if (!Util.permissionsAvailable(permissionsStorage, context)) permissionLauncher.launch(permissionsStorage)
            }
        }
    }

    private fun launch(cropImage: ActivityResultLauncher<CropImageContractOptions>, title: String = "Profile image") {
        cropImage.launch(
            options {
                this.setActivityTitle(title)
                this.setAllowFlipping(true)
                this.setAllowRotation(true)
                this.setAutoZoomEnabled(true)
                if (x!=0) this.setAspectRatio(x, y)
                this.setBackgroundColor(Color.BLACK)
                this.setImageSource(includeGallery = true, includeCamera = true)
                setGuidelines(CropImageView.Guidelines.ON)
            }
        )
    }
}