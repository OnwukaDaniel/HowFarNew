package com.azur.howfar.workManger

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.widget.Toast
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.azur.howfar.R
import com.azur.howfar.models.UserProfile
import com.azur.howfar.user.EditProfileActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import com.google.gson.Gson
import java.io.File

class ProfilePicsWorkManager(private val context: Context, params: WorkerParameters) : Worker(context, params) {
    private val uid = FirebaseAuth.getInstance().currentUser
    private lateinit var pref: SharedPreferences
    private var ref = FirebaseDatabase.getInstance().reference
    private var profileData = UserProfile()

    override fun doWork(): Result {
        ref = ref.child(EditProfileActivity.USER_DETAILS).child(uid!!.uid)
        pref = context.getSharedPreferences(context.getString(R.string.ALL_PREFERENCE), Context.MODE_PRIVATE)
        val json = pref.getString("profileData", "")
        profileData = Gson().fromJson(json, UserProfile::class.java)
        when {
            profileData.image == "" -> upload()
            profileData.image.contains("https") -> upload()
            else -> storeImage()
        }
        return Result.success()
    }

    private fun storeImage() {
        if (uid?.uid == null) return
        val uri = Uri.fromFile(File(profileData.image))!!
        val imageRef = FirebaseStorage.getInstance().reference.child(EditProfileActivity.PROFILE_IMAGE).child(uid.uid)
        val imageUploadTask = imageRef.putFile(uri)

        imageUploadTask.continueWith { task ->
            if (!task.isSuccessful) task.exception?.let { it ->
                throw  it
            }
            imageRef.metadata.addOnSuccessListener {
                imageRef.downloadUrl.addOnSuccessListener { uri ->
                    profileData.image = uri.toString()
                    upload()
                }.addOnFailureListener {
                    Toast.makeText(context, "Image upload failed!!! Retry.", Toast.LENGTH_LONG).show()
                    return@addOnFailureListener
                }
            }
        }
    }

    private fun upload() {
        ref.setValue(profileData).addOnSuccessListener {
            Toast.makeText(context, "Profile upload successful.", Toast.LENGTH_LONG).show()
        }.addOnFailureListener {
            Toast.makeText(context, "Profile upload failed!!! Retry.", Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        const val USER_DETAILS = "user_details"
    }
}