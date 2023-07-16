package com.azur.howfar.room

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.LiveData
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.azur.howfar.R
import com.azur.howfar.models.*
import com.azur.howfar.models.MessageType.ADDED_TO_GROUP
import com.azur.howfar.models.MessageType.AUDIO
import com.azur.howfar.models.MessageType.CHAT_DAY
import com.azur.howfar.models.MessageType.CONTACT
import com.azur.howfar.models.MessageType.CREATED_GROUP
import com.azur.howfar.models.MessageType.GROUP_ADMIN
import com.azur.howfar.models.MessageType.LEAVE_GROUP
import com.azur.howfar.models.MessageType.PHOTO
import com.azur.howfar.models.MessageType.REMOVED_FROM_GROUP
import com.azur.howfar.models.MessageType.TEXT
import com.azur.howfar.models.MessageType.VIDEO
import com.azur.howfar.room.RoomWorkAccessObject.DELETE_ALL
import com.azur.howfar.room.RoomWorkAccessObject.DELETE_LIST
import com.azur.howfar.room.RoomWorkAccessObject.DELETE_ONE
import com.azur.howfar.room.RoomWorkAccessObject.READ_ALL
import com.azur.howfar.room.RoomWorkAccessObject.WRITE
import com.azur.howfar.utils.FirebaseConstants
import com.azur.howfar.utils.ImageCompressor
import com.azur.howfar.utils.Util
import com.azur.howfar.workManger.ChatMediaWorkManager
import com.azur.howfar.workManger.ImageWorkManager
import com.azur.howfar.workManger.VideoPostWorkManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.UploadTask
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.io.File

class ChatDataOperation(private val context: Context, val params: WorkerParameters) : Worker(context, params) {
    private var groupRef = FirebaseDatabase.getInstance().reference
    private var groupMessagesRef = FirebaseDatabase.getInstance().reference
    private lateinit var preferences: SharedPreferences
    private var user = FirebaseAuth.getInstance().currentUser
    private var timeRef = FirebaseDatabase.getInstance().reference
    private var chatData = ChatData()

    override fun doWork(): Result {
        preferences = context.getSharedPreferences(context.getString(R.string.CHAT_PREFERENCES), Context.MODE_PRIVATE)
        val inputData = preferences.getString("chatData","")
        if (inputData == "") return Result.failure()
        chatData = Gson().fromJson(inputData, ChatData::class.java)!!
        when (chatData.groupUid) {
            "" -> {}
            else -> {
                groupMessagesRef = groupMessagesRef.child(ChatMediaWorkManager.GROUPS_MESSAGES).child(chatData.groupUid)
                groupRef = groupRef.child(ChatMediaWorkManager.GROUPS).child(chatData.groupUid)
            }
        }
        when {
            chatData.imageData.storageLink != "" -> chatData.messagetype = PHOTO
            chatData.audioData.storageLink != "" -> chatData.messagetype = AUDIO
            chatData.videoData.storageLink != "" -> chatData.messagetype = VIDEO
            chatData.phoneData.number != "" -> chatData.messagetype = CONTACT
        }

        when (chatData.messagetype) {
            TEXT -> uploadText()
            PHOTO -> storeData()
            VIDEO -> storeData()
            AUDIO -> storeData()
            CONTACT -> uploadText()
        }
        return Result.success()
    }

    private fun uploadText() {
        val ref = FirebaseDatabase.getInstance().reference.child(ImageWorkManager.USER_DETAILS).child(user!!.uid)
        ref.get().addOnSuccessListener { userProfile ->
            if (userProfile.exists()) {
                val myProfile = userProfile.getValue(UserProfile::class.java)!!
                val myTemp = ParticipantTempData(
                    tempImage = myProfile.image, tempName = myProfile.name, uid = myProfile.uid,
                    phone = Util.formatNumber(myProfile.phone)
                )
                val otherRef = FirebaseDatabase.getInstance().reference.child(ImageWorkManager.USER_DETAILS).child(getOtherParticipant())
                otherRef.get().addOnSuccessListener { otherSnapshot ->
                    if (otherSnapshot.exists()) {
                        val otherProfile = otherSnapshot.getValue(UserProfile::class.java)!!
                        val otherTemp = ParticipantTempData(
                            tempImage = otherProfile.image, tempName = otherProfile.name, uid = otherProfile.uid,
                            phone = Util.formatNumber(otherProfile.phone)
                        )
                        chatData.participantsTempData = arrayListOf(myTemp, otherTemp)
                        timeRef = FirebaseDatabase.getInstance().reference.child("time").child(user!!.uid)
                        timeRef.setValue(ServerValue.TIMESTAMP).addOnSuccessListener {
                            timeRef.get().addOnSuccessListener { timeSnapshot ->
                                if (timeSnapshot.exists()) {
                                    val timeSent = timeSnapshot.value.toString()
                                    chatData.uniqueQuerableTime = timeSent
                                    chatData.sent = true
                                    val chatKey = Util.sortTwoUIDs(chatData.participants.first(), chatData.participants.last())
                                    FirebaseDatabase.getInstance().reference.child(FirebaseConstants.CHAT_ROOM)
                                        .child(chatData.participants.first())
                                        .child(chatData.participants.last())
                                        .setValue(ChatKey(chatKey = chatKey, time = timeSent, author = user!!.uid))
                                    FirebaseDatabase.getInstance().reference.child(FirebaseConstants.CHAT_ROOM)
                                        .child(chatData.participants.last())
                                        .child(chatData.participants.first())
                                        .setValue(ChatKey(chatKey = chatKey, time = timeSent, author = user!!.uid))
                                    FirebaseDatabase.getInstance().reference.child(FirebaseConstants.CHAT_REF)
                                        .child(chatKey)
                                        .child(timeSent).setValue(chatData)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun upload() {
        val ref = FirebaseDatabase.getInstance().reference.child(ImageWorkManager.USER_DETAILS).child(user!!.uid)
        ref.get().addOnSuccessListener { userProfile ->
            if (userProfile.exists()) {
                val myProfile = userProfile.getValue(UserProfile::class.java)!!
                val myTemp = ParticipantTempData(
                    tempImage = myProfile.image, tempName = myProfile.name, uid = myProfile.uid,
                    phone = Util.formatNumber(myProfile.phone)
                )
                val otherRef = FirebaseDatabase.getInstance().reference.child(ImageWorkManager.USER_DETAILS).child(getOtherParticipant())
                otherRef.get().addOnSuccessListener { otherSnapshot ->
                    if (otherSnapshot.exists()) {
                        val otherProfile = otherSnapshot.getValue(UserProfile::class.java)!!
                        val otherTemp = ParticipantTempData(
                            tempImage = otherProfile.image, tempName = otherProfile.name, uid = otherProfile.uid,
                            phone = Util.formatNumber(otherProfile.phone)
                        )
                        chatData.sent = true
                        chatData.participantsTempData = arrayListOf(myTemp, otherTemp)
                        val chatKey = Util.sortTwoUIDs(chatData.participants.first(), chatData.participants.last())
                        FirebaseDatabase.getInstance().reference.child(FirebaseConstants.CHAT_ROOM)
                            .child(chatData.participants.first())
                            .child(chatData.participants.last())
                            .setValue(ChatKey(chatKey = chatKey, time = chatData.uniqueQuerableTime, author = user!!.uid))
                        FirebaseDatabase.getInstance().reference.child(FirebaseConstants.CHAT_ROOM)
                            .child(chatData.participants.last())
                            .child(chatData.participants.first())
                            .setValue(ChatKey(chatKey = chatKey, time = chatData.uniqueQuerableTime, author = user!!.uid))
                        FirebaseDatabase.getInstance().reference.child(FirebaseConstants.CHAT_REF)
                            .child(Util.sortTwoUIDs(chatData.participants.first(), chatData.participants.last()))
                            .child(chatData.uniqueQuerableTime).setValue(chatData)
                    }
                }
            }
        }
    }

    private fun storeData() {
        var reference = ""
        val storageUid: String
        when (chatData.groupUid) {
            "" -> {
                storageUid = chatData.senderuid
                when (chatData.messagetype) {
                    PHOTO -> reference = ChatMediaWorkManager.IMAGE_REFERENCE
                    AUDIO -> reference = ChatMediaWorkManager.AUDIO_REFERENCE
                    VIDEO -> reference = ChatMediaWorkManager.VIDEO_REFERENCE
                }
            }
            else -> {
                storageUid = chatData.groupUid
                when (chatData.messagetype) {
                    PHOTO -> reference = ChatMediaWorkManager.GROUP_IMAGES
                    AUDIO -> reference = ChatMediaWorkManager.GROUP_AUDIOS
                    VIDEO -> reference = ChatMediaWorkManager.GROUP_VIDEOS
                }
            }
        }

        timeRef = FirebaseDatabase.getInstance().reference.child("time").child(user!!.uid)
        timeRef.setValue(ServerValue.TIMESTAMP).addOnSuccessListener {
            timeRef.get().addOnSuccessListener { timeSnapshot ->
                if (timeSnapshot.exists()) {
                    val timeSent = timeSnapshot.value.toString()
                    chatData.uniqueQuerableTime = timeSent
                    val mediaRef = FirebaseStorage.getInstance().reference.child(reference).child(storageUid).child(timeSent)
                    var mediaUploadTask: UploadTask
                    when (chatData.messagetype) {
                        AUDIO -> mediaUploadTask = mediaRef.putFile(Uri.parse(chatData.audioData.storageLink))
                        VIDEO -> mediaUploadTask = mediaRef.putFile(Uri.parse(chatData.videoData.storageLink))
                        PHOTO -> {
                            val dataPair = ImageCompressor.compressImage(Uri.parse(chatData.imageData.storageLink), context, null)
                            mediaUploadTask = mediaRef.putStream(dataPair.first)
                        }
                        else ->{
                            Toast.makeText(context, "Unknown media type", Toast.LENGTH_LONG).show()
                            return@addOnSuccessListener
                        }
                    }

                    mediaUploadTask.continueWith { task ->
                        if (!task.isSuccessful) task.exception?.let { it ->
                            throw it
                        }
                        mediaRef.metadata.addOnSuccessListener { metadata ->
                            mediaRef.downloadUrl.addOnSuccessListener { uri ->
                                when (chatData.messagetype) {
                                    PHOTO -> {
                                        chatData.msg = "Image"
                                        chatData.imageData.displayMessage = "Image"
                                        chatData.imageData.storageLink = uri.toString()
                                        chatData.imageData.fileSize = metadata.sizeBytes.toString()
                                    }
                                    AUDIO -> {
                                        chatData.msg = "Audio"
                                        chatData.audioData.displayMessage = "Audio"
                                        chatData.audioData.storageLink = uri.toString()
                                        chatData.audioData.fileSize = metadata.sizeBytes.toString()
                                    }
                                    VIDEO -> {
                                        chatData.msg = "Video"
                                        chatData.videoData.displayMessage = "Video"
                                        chatData.videoData.storageLink = uri.toString()
                                        chatData.videoData.fileSize = metadata.sizeBytes.toString()
                                    }
                                }

                                when (chatData.groupUid) {
                                    "" -> upload()
                                    else -> uploadGroup()
                                }
                            }.addOnFailureListener {
                                Toast.makeText(context, "Upload media failed!!! Retry", Toast.LENGTH_LONG).show()
                                return@addOnFailureListener
                            }
                        }
                    }
                }
            }
        }
    }

    private fun uploadGroup() {
        groupRef.get().addOnSuccessListener {
            val groupProfile = it.getValue(GroupProfileData::class.java)!!
            chatData.sent = true
            groupMessagesRef.child(chatData.uniqueQuerableTime).setValue(chatData).addOnSuccessListener {
                distributeDisplayMessage(groupProfile, chatData)
            }
        }
    }

    private fun distributeDisplayMessage(groupProfile: GroupProfileData, myChat: ChatData) {
        for (auth in groupProfile.members) {
            FirebaseDatabase.getInstance().reference
                .child(ChatMediaWorkManager.MY_GROUPS_MESSAGES)
                .child(auth)
                .child(groupProfile.uuid)
                .child(myChat.uniqueQuerableTime).setValue(myChat)
        }
    }

    private fun getOtherParticipant(): String {
        for (i in chatData.participants) if (i != user!!.uid) return i
        return ""
    }
}

object RoomWorkAccessObject {
    const val WRITE = 0
    const val READ_ALL = 1
    const val DELETE_ALL = 2
    const val DELETE_ONE = 3
    const val DELETE_LIST = 4
}