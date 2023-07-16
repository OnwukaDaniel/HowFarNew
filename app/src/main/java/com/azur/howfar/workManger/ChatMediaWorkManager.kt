package com.azur.howfar.workManger

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.azur.howfar.howfarchat.chat.ChatActivity2
import com.azur.howfar.models.*
import com.azur.howfar.utils.ImageCompressor
import com.azur.howfar.utils.Util
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.UploadTask
import com.google.gson.Gson

class ChatMediaWorkManager(private val context: Context, private val params: WorkerParameters) : Worker(context, params) {
    private var user = FirebaseAuth.getInstance().currentUser
    private var timeRef = FirebaseDatabase.getInstance().reference
    private var groupRef = FirebaseDatabase.getInstance().reference
    private var groupMessagesRef = FirebaseDatabase.getInstance().reference
    private var receiverChattingRef = FirebaseDatabase.getInstance().reference
    private var chattingRef = FirebaseDatabase.getInstance().reference
    private var chatData = ChatData()

    override fun doWork(): Result {
        val json = params.inputData.getString("chatData")!!
        chatData = Gson().fromJson(json, ChatData::class.java)!!
        when (chatData.groupUid) {
            "" -> {
                chattingRef = FirebaseDatabase.getInstance().reference.child(CHAT_REFERENCE).child(user!!.uid).child(getOtherParticipant())
                receiverChattingRef = FirebaseDatabase.getInstance().reference.child(CHAT_REFERENCE).child(getOtherParticipant()).child(user!!.uid)
            }
            else -> {
                groupMessagesRef = groupMessagesRef.child(GROUPS_MESSAGES).child(chatData.groupUid)
                groupRef = groupRef.child(GROUPS).child(chatData.groupUid)
            }
        }
        storeData()
        return Result.success()
    }

    private fun storeData() {
        var reference = ""
        val storageUid: String
        when (chatData.groupUid) {
            "" -> {
                storageUid = chatData.senderuid
                when (chatData.messagetype) {
                    MessageType.PHOTO -> reference = IMAGE_REFERENCE
                    MessageType.AUDIO -> reference = AUDIO_REFERENCE
                    MessageType.VIDEO -> reference = VIDEO_REFERENCE
                }
            }
            else -> {
                storageUid = chatData.groupUid
                when (chatData.messagetype) {
                    MessageType.PHOTO -> reference = GROUP_IMAGES
                    MessageType.AUDIO -> reference = GROUP_AUDIOS
                    MessageType.VIDEO -> reference = GROUP_VIDEOS
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
                    var mediaUploadTask: UploadTask = when (chatData.messagetype) {
                        MessageType.AUDIO -> mediaRef.putFile(Uri.parse(chatData.audioData.storageLink))
                        MessageType.VIDEO -> mediaRef.putFile(Uri.parse(chatData.videoData.storageLink))
                        MessageType.PHOTO -> {
                            val dataPair = ImageCompressor.compressImage(Uri.parse(chatData.imageData.storageLink), context, null)
                            mediaRef.putStream(dataPair.first)
                        }
                        else ->{
                            Toast.makeText(context, "Unknown media type", Toast.LENGTH_LONG).show()
                            return@addOnSuccessListener
                        }
                    }

                    mediaUploadTask.continueWith { task ->
                        if (!task.isSuccessful) task.exception?.let { it ->
                            throw  it
                        }
                        mediaRef.metadata.addOnSuccessListener { metadata ->
                            mediaRef.downloadUrl.addOnSuccessListener { uri ->
                                when (chatData.messagetype) {
                                    MessageType.PHOTO -> {
                                        chatData.msg = "Image"
                                        chatData.imageData.displayMessage = "Image"
                                        chatData.imageData.storageLink = uri.toString()
                                        chatData.imageData.fileSize = metadata.sizeBytes.toString()
                                    }
                                    MessageType.AUDIO -> {
                                        chatData.msg = "Audio"
                                        chatData.audioData.displayMessage = "Audio"
                                        chatData.audioData.storageLink = uri.toString()
                                        chatData.audioData.fileSize = metadata.sizeBytes.toString()
                                    }
                                    MessageType.VIDEO -> {
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
                                Toast.makeText(context, "Upload failed!!! Retry", Toast.LENGTH_LONG).show()
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

    private fun upload() {
        val ref = FirebaseDatabase.getInstance().reference.child(USER_DETAILS).child(user!!.uid)
        ref.get().addOnSuccessListener { userProfile ->
            if (userProfile.exists()) {
                val myProfile = userProfile.getValue(UserProfile::class.java)!!
                val myTemp = ParticipantTempData(
                    tempImage = myProfile.image,
                    tempName = myProfile.name,
                    uid = myProfile.uid,
                    phone = Util.formatNumber(myProfile.phone)
                )
                val otherRef = FirebaseDatabase.getInstance().reference.child(USER_DETAILS).child(getOtherParticipant())
                otherRef.get().addOnSuccessListener { otherSnapshot ->
                    if (otherSnapshot.exists()) {
                        val otherProfile = otherSnapshot.getValue(UserProfile::class.java)!!
                        val otherTemp = ParticipantTempData(tempImage = otherProfile.image, tempName = otherProfile.name, uid = otherProfile.uid,
                            phone = Util.formatNumber(otherProfile.phone))
                        chatData.participantsTempData = arrayListOf(myTemp, otherTemp)
                        // BLOCKED CONTACTS
                        val blockedRef = FirebaseDatabase.getInstance().reference.child(ChatActivity2.MY_BLOCKED_CONTACTS).child(getOtherParticipant())
                        chatData.sent = true
                        chattingRef.child(chatData.uniqueQuerableTime).setValue(chatData).addOnSuccessListener {
                            blockedRef.get().addOnSuccessListener { blocked ->
                                val blockedList: ArrayList<String> = arrayListOf()
                                if (blocked.exists()) {
                                    for (i in blocked.children) blockedList.add(i.value.toString())
                                    if (user!!.uid !in blockedList) {
                                        receiverChattingRef.child(chatData.uniqueQuerableTime).setValue(chatData)
                                    }
                                } else receiverChattingRef.child(chatData.uniqueQuerableTime).setValue(chatData)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun getOtherParticipant(): String {
        for (i in chatData.participants) if (i != user!!.uid) return i
        return ""
    }

    private fun distributeDisplayMessage(groupProfile: GroupProfileData, myChat: ChatData) {
        for (auth in groupProfile.members) {
            FirebaseDatabase.getInstance().reference
                .child(MY_GROUPS_MESSAGES)
                .child(auth)
                .child(groupProfile.uuid)
                .child(myChat.uniqueQuerableTime).setValue(myChat)
        }
    }

    companion object {
        const val MY_GROUPS_MESSAGES = "my_groups_messages"
        const val CHAT_REFERENCE = "chat_reference"
        const val GROUPS = "groups"
        const val GROUPS_MESSAGES = "groups_messages"
        const val IMAGE_REFERENCE = "image_reference"
        const val AUDIO_REFERENCE = "audio_reference"
        const val VIDEO_REFERENCE = "video_reference"
        const val USER_DETAILS = "user_details"
        const val GROUP_IMAGES = "GroupImages"
        const val GROUP_VIDEOS = "GroupImages"
        const val GROUP_AUDIOS = "GroupImages"
    }
}