package com.azur.howfar.utils

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class CallUtils(val lifecycleOwner: LifecycleOwner, val activity: Activity) {
    private val scope = CoroutineScope(Dispatchers.IO)

    fun checkSelfPermission(permission: String, requestCode: Int, REQUESTED_PERMISSIONS: Array<String>): Boolean {
        if (ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, REQUESTED_PERMISSIONS, requestCode)
            return false
        }
        return true
    }

    fun openAppSettings() {
        val packageUri = Uri.fromParts("package", activity.packageName, null)
        val intent = Intent()
        intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        intent.data = packageUri
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        activity.startActivity(intent)
    }

    fun permissionRationale(title: String = "Permission", message: String){
        val alertBuilder = AlertDialog.Builder(activity)
        alertBuilder.setTitle(title)
        alertBuilder.setMessage(message)
        alertBuilder.setPositiveButton("Grant permission"){ dialog,_->
            openAppSettings()
            dialog.cancel()
            activity.finish()
        }
        alertBuilder.setNegativeButton("Cancel"){ dialog,_->
            dialog.cancel()
            activity.finish()
        }
        val dialog = alertBuilder.create()
        if (!dialog.isShowing) dialog.show()
    }

    fun messageRationale(title: String = "", message: String){
        val alertBuilder = AlertDialog.Builder(activity)
        alertBuilder.setTitle(title)
        alertBuilder.setMessage(message)
        alertBuilder.setPositiveButton("Ok"){ dialog,_->
            dialog.cancel()
        }
        val dialog = alertBuilder.create()
        dialog.show()
    }

    fun askPermissions(context: Context, activity: Activity) {
        if (checkSelfPermission(CallUtils.REQUESTED_PERMISSIONS[0], CallUtils.PERMISSION_REQ_ID, context, activity)) {
            if (checkSelfPermission(CallUtils.REQUESTED_PERMISSIONS[1], CallUtils.PERMISSION_REQ_ID, context, activity)) {
                scope.launch { }
            } else askPermissions(context, activity)
        } else askPermissions(context, activity)
    }

    fun checkSelfPermission(permission: String, requestCode: Int, context: Context, activity: Activity): Boolean {
        if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, CallUtils.REQUESTED_PERMISSIONS, requestCode)
            return false
        }
        return true
    }

    object CallUtils {
        const val CALL_REFERENCE = "call_reference"
        const val PERMISSION_REQ_ID = 22
        val REQUESTED_PERMISSIONS = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
        )
    }
}