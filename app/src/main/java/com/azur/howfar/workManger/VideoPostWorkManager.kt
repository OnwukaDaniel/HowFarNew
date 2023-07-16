package com.azur.howfar.workManger

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.storage.FirebaseStorage
import com.google.gson.Gson
import com.azur.howfar.models.UserProfile
import com.azur.howfar.models.VideoPost
import java.io.File

class VideoPostWorkManager(private val context: Context, private val params: WorkerParameters) : Worker(context, params) {
    private var user = FirebaseAuth.getInstance().currentUser
    private var timeRef = FirebaseDatabase.getInstance().reference
    private var videoPost = VideoPost()

    override fun doWork(): Result {
        val json = params.inputData.getString("json")
        videoPost = Gson().fromJson(json, VideoPost::class.java)
        storeImage()
        return Result.success()
    }

    private fun storeImage() {
        timeRef = FirebaseDatabase.getInstance().reference.child("time").child(user!!.uid)
        timeRef.setValue(ServerValue.TIMESTAMP).addOnSuccessListener {
            timeRef.get().addOnSuccessListener { timeSnapshot ->
                if (timeSnapshot.exists()) {
                    val timeSent = timeSnapshot.value.toString()
                    videoPost.timePosted = timeSent
                    val imageRef = FirebaseStorage.getInstance().reference.child(VIDEO_POST).child(user!!.uid).child(timeSent)
                    val imageUploadTask = imageRef.putFile(Uri.fromFile(File(videoPost.videoUrl))!!)

                    imageUploadTask.continueWith { task ->
                        if (!task.isSuccessful) task.exception?.let { it ->
                            throw  it
                        }
                        imageRef.metadata.addOnSuccessListener {
                            imageRef.downloadUrl.addOnSuccessListener { uri ->
                                videoPost.videoUrl = uri.toString()
                                uploadVideo()
                            }.addOnFailureListener {
                                Toast.makeText(context, "Video upload failed!!! Retry", Toast.LENGTH_LONG).show()
                                return@addOnFailureListener
                            }
                        }
                    }
                }
            }
        }
    }

    private fun uploadVideo() {
        val ref = FirebaseDatabase.getInstance().reference.child(USER_DETAILS).child(user!!.uid)
        ref.get().addOnSuccessListener { userProfile ->
            if (userProfile.exists()) {
                val myProfile = userProfile.getValue(UserProfile::class.java)!!
                videoPost.profileImage = myProfile.image
                videoPost.profileName = myProfile.name
                if (videoPost.timePosted == "") {
                    timeRef = FirebaseDatabase.getInstance().reference.child("time").child(user!!.uid)
                    timeRef.setValue(ServerValue.TIMESTAMP).addOnSuccessListener {
                        timeRef.get().addOnSuccessListener { snapshot ->
                            videoPost.timePosted = snapshot.value.toString()
                            if (snapshot.exists()) upload()
                        }
                    }
                } else upload()
            }
        }
    }

    private fun upload() {
        val refM = FirebaseDatabase.getInstance().reference.child(VIDEO_POST).child("-${videoPost.timePosted}")
        val postRecord = FirebaseDatabase.getInstance().reference.child(VIDEO_POST_RECORD).child(user!!.uid).child(videoPost.timePosted)
        refM.setValue(videoPost).addOnSuccessListener {
            postRecord.setValue(videoPost)
            Toast.makeText(context, "Video post successfully uploaded", Toast.LENGTH_LONG).show()
        }.addOnFailureListener {
            Toast.makeText(context, "Video post Failed. Please retry", Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        const val MOMENT_DATA = "MOMENT_DATA"
        const val PERSONAL_POST_RECORD = "PERSONAL_POST_RECORD"
        const val VIDEO_POST_RECORD = "VIDEO_POST_RECORD"
        const val VIDEO_POST = "VIDEO_POST"
        const val USER_DETAILS = "user_details"
    }
}