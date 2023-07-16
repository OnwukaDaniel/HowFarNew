package com.azur.howfar.workManger

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.widget.Toast
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.azur.howfar.R
import com.azur.howfar.models.ChatData
import com.azur.howfar.utils.FirebaseConstants
import com.azur.howfar.utils.ImageCompressor
import com.azur.howfar.utils.Util
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.storage.FirebaseStorage
import com.google.gson.Gson
import java.io.ByteArrayInputStream

class SupportWorkManager(private val context: Context, val params: WorkerParameters) : Worker(context, params) {
    private lateinit var pref: SharedPreferences
    private var imageStream: ByteArrayInputStream? = null
    private var user = FirebaseAuth.getInstance().currentUser
    private var timeRef = FirebaseDatabase.getInstance().reference
    private var data = ChatData()

    override fun doWork(): Result {
        pref = context.getSharedPreferences(context.getString(R.string.ALL_PREFERENCE), Context.MODE_PRIVATE)
        val json = params.inputData.getString("data")!!
        data = Gson().fromJson(json, ChatData::class.java)
        when (data.imageData.storageLink) {
            "" -> upload()
            else -> storeImage()
        }
        return Result.success()
    }

    private fun storeImage() {
        timeRef = FirebaseDatabase.getInstance().reference.child("time").child(user!!.uid)
        timeRef.setValue(ServerValue.TIMESTAMP).addOnSuccessListener {
            timeRef.get().addOnSuccessListener { timeSnapshot ->
                if (timeSnapshot.exists()) {
                    val timeSent = timeSnapshot.value.toString()
                    data.uniqueQuerableTime = timeSent
                    val pair: Pair<ByteArrayInputStream, ByteArray> = ImageCompressor.compressImage(Uri.parse(data.imageData.storageLink), context, null)
                    imageStream = pair.first
                    val imageRef = FirebaseStorage.getInstance().reference.child(SUPPORT_IMAGES).child(user!!.uid).child(timeSent)
                    val imageUploadTask = imageRef.putStream(imageStream!!)
                    imageUploadTask.continueWith { task ->
                        if (!task.isSuccessful) task.exception?.let { it ->
                            throw  it
                        }
                        imageRef.metadata.addOnSuccessListener {
                            imageRef.downloadUrl.addOnSuccessListener { uri ->
                                data.imageData.storageLink = uri.toString()
                                upload()
                            }.addOnFailureListener {
                                Toast.makeText(context, "Moment upload failed!!! Retry", Toast.LENGTH_LONG).show()
                                return@addOnFailureListener
                            }
                        }
                    }
                }
            }
        }
    }

    private fun upload() {
        val timeRef = FirebaseDatabase.getInstance().reference.child("time").child(user!!.uid)
        timeRef.setValue(ServerValue.TIMESTAMP).addOnSuccessListener {
            timeRef.get().addOnSuccessListener { timeSnapshot ->
                if (timeSnapshot.exists()) {
                    val timeSent = timeSnapshot.value.toString()
                    data.uniqueQuerableTime = timeSent
                    data.participants = arrayListOf(user!!.uid)
                    data.sent = true
                    sendFinal(data)
                }
            }
        }
    }

    private fun sendFinal(data: ChatData) {
        val con = "${data.uniqueQuerableTime}-${user!!.uid}"
        val ref = FirebaseDatabase.getInstance("https://howfar-b24ef.firebaseio.com").reference.child(CONTACT_SUPPORT).child(con)
        ref.setValue(data).addOnSuccessListener { // SAVES ALL SUPPORT MESSAGE
            Toast.makeText(context, "Support message delivered.", Toast.LENGTH_LONG).show()
        } // SUPPORT SEES IT FRESH
        FirebaseDatabase.getInstance().reference
            .child(FirebaseConstants.CHAT_DISPLAY)
            .child(otherParticipant(data))
            .child(user!!.uid)
            .child(data.uniqueQuerableTime)
            .setValue(data)
        FirebaseDatabase.getInstance().reference
            .child(FirebaseConstants.CHAT_DISPLAY)
            .child(user!!.uid)
            .child(otherParticipant(data))
            .child(data.uniqueQuerableTime)
            .setValue(data)
        Util.sendNotification(data)
    }

    private fun otherParticipant(chatData: ChatData): String {
        for (i in chatData.participants) if (i != user!!.uid) return i
        return ""
    }

    companion object {
        const val CONTACT_SUPPORT = "CONTACT_SUPPORT"
        const val MOMENT_DATA = "MOMENT_DATA"
        const val PERSONAL_POST_RECORD = "PERSONAL_POST_RECORD"
        const val SUPPORT_IMAGES = "SUPPORT_IMAGES"
        const val USER_DETAILS = "user_details"
        const val CHAT_REFERENCE = "chat_reference"
    }
}