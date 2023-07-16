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
import com.azur.howfar.chat.GuestChatActivity
import com.azur.howfar.models.ChatData
import com.azur.howfar.models.ParticipantTempData
import com.azur.howfar.models.UserProfile
import com.azur.howfar.utils.Util
import java.io.File

class ImageWorkManager(private val context: Context, private val params: WorkerParameters) : Worker(context, params) {
    private var user = FirebaseAuth.getInstance().currentUser
    private var timeRef = FirebaseDatabase.getInstance().reference
    private var chatData = ChatData()

    override fun doWork(): Result {
        val json = params.inputData.getString("chatData")!!
        chatData = Gson().fromJson(json, ChatData::class.java)!!
        storeImage()
        return Result.success()
    }

    private fun storeImage() {
        timeRef = FirebaseDatabase.getInstance().reference.child("time").child(user!!.uid)
        timeRef.setValue(ServerValue.TIMESTAMP).addOnSuccessListener {
            timeRef.get().addOnSuccessListener { timeSnapshot ->
                if (timeSnapshot.exists()) {
                    val timeSent = timeSnapshot.value.toString()
                    chatData.uniqueQuerableTime = timeSent
                    val imageRef = FirebaseStorage.getInstance().reference.child(GUEST_IMAGES).child(user!!.uid).child(timeSent)
                    val imageUploadTask = imageRef.putFile(Uri.fromFile(File(chatData.imageData.storageLink)))

                    imageUploadTask.continueWith { task ->
                        if (!task.isSuccessful) task.exception?.let { it ->
                            throw  it
                        }
                        imageRef.metadata.addOnSuccessListener {
                            imageRef.downloadUrl.addOnSuccessListener { uri ->
                                chatData.imageData.storageLink = uri.toString()
                                upload()
                            }.addOnFailureListener {
                                Toast.makeText(context, "Upload failed!!! Retry", Toast.LENGTH_LONG).show()
                                return@addOnFailureListener
                            }
                        }
                    }
                }
            }
        }
    }

    private fun uploadFinal() {
        val myRef = FirebaseDatabase.getInstance().reference.child(GuestChatActivity.GUEST_USERS_CHAT).child(user!!.uid)
            .child(getOtherParticipant()).child(chatData.uniqueQuerableTime)
        val receiverRef = FirebaseDatabase.getInstance().reference.child(GuestChatActivity.GUEST_USERS_CHAT).child(getOtherParticipant())
            .child(user!!.uid).child(chatData.uniqueQuerableTime)
        myRef.setValue(chatData).addOnSuccessListener {
            receiverRef.setValue(chatData)
        }.addOnFailureListener {
        }
    }

    private fun upload() {
        val ref = FirebaseDatabase.getInstance().reference.child(USER_DETAILS).child(user!!.uid)
        ref.get().addOnSuccessListener { userProfile ->
            if (userProfile.exists()) {
                val myProfile = userProfile.getValue(UserProfile::class.java)!!
                val myTemp = ParticipantTempData(tempImage = myProfile.image, tempName = myProfile.name, uid = myProfile.uid,
                phone = Util.formatNumber(myProfile.phone))
                val otherRef = FirebaseDatabase.getInstance().reference.child(USER_DETAILS).child(getOtherParticipant())
                otherRef.get().addOnSuccessListener { otherSnapshot ->
                    if (otherSnapshot.exists()) {
                        val otherProfile = otherSnapshot.getValue(UserProfile::class.java)!!
                        val otherTemp = ParticipantTempData(tempImage = otherProfile.image, tempName = otherProfile.name, uid = otherProfile.uid,
                        phone = Util.formatNumber(otherProfile.phone))
                        chatData.participantsTempData = arrayListOf(myTemp, otherTemp)
                        uploadFinal()
                    }
                }
            }
        }
    }

    private fun getOtherParticipant(): String {
        for (i in chatData.participants) if (i != user!!.uid) return i
        return ""
    }

    companion object {
        const val GUEST_IMAGES = "GUEST_IMAGES"
        const val USER_DETAILS = "user_details"
    }
}