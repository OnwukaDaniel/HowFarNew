package com.azur.howfar.broadcasts

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import androidx.core.app.*
import com.azur.howfar.R
import com.azur.howfar.howfarchat.chat.ChatActivity2
import com.azur.howfar.models.ChatData
import com.azur.howfar.models.MessageType
import com.azur.howfar.models.ParticipantTempData
import com.azur.howfar.utils.FirebaseConstants
import com.azur.howfar.utils.Util
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.gson.Gson

class DirectReceiver : BroadcastReceiver() {
    private var KEY_TEXT_REPLY = ""
    private var timeRef = FirebaseDatabase.getInstance().reference
    private val myAuth = FirebaseAuth.getInstance().currentUser!!.uid
    private var receiverChattingRef = FirebaseDatabase.getInstance().reference
    private var myProfileRef = FirebaseDatabase.getInstance().reference

    override fun onReceive(context: Context?, intent: Intent?) {
        val notificationId = intent!!.getIntExtra("index", 0)
        val json = intent.getStringExtra("json")
        val lastMsg = Gson().fromJson(json, ChatData::class.java)
        timeRef = FirebaseDatabase.getInstance().reference.child("time").child(myAuth)
        val chatKey = Util.sortTwoUIDs(lastMsg.participants.first(), lastMsg.participants.last())
        receiverChattingRef = FirebaseDatabase.getInstance().reference.child(CHAT_REFERENCE).child(otherParticipant(lastMsg.participants)).child(myAuth)
        myProfileRef = myProfileRef.child(USER_DETAILS).child(myAuth)
        KEY_TEXT_REPLY = lastMsg.uniqueQuerableTime

        val reply = getMessageText(intent)
        var chatData = ChatData(
            msg = reply.toString(),
            senderuid = myAuth,
            myPhone = lastMsg.myPhone,
            participants = lastMsg.participants,
            sent = true,
            participantsTempData = lastMsg.participantsTempData,
            messagetype = MessageType.TEXT
        )

        if (reply != null) {
            timeRef.setValue(ServerValue.TIMESTAMP).addOnSuccessListener {
                timeRef.get().addOnSuccessListener { snapshot ->
                    if (snapshot.exists()) {
                        val rawTime = snapshot.value.toString()
                        chatData.uniqueQuerableTime = rawTime
                        chatData.timesent = rawTime
                        lastMsg.read = true
                        lastMsg.timeseen = rawTime
                        val chattingRef = FirebaseDatabase.getInstance().reference.child(FirebaseConstants.CHAT_REF).child(chatKey)
                        FirebaseDatabase.getInstance().reference
                            .child(FirebaseConstants.CHAT_DISPLAY)
                            .child(otherParticipant(chatData.participants))
                            .child(myAuth)
                            .child(rawTime)
                            .setValue(chatData)
                        FirebaseDatabase.getInstance().reference
                            .child(FirebaseConstants.CHAT_DISPLAY)
                            .child(myAuth)
                            .child(otherParticipant(chatData.participants))
                            .child(rawTime)
                            .setValue(chatData).addOnSuccessListener {
                            sendNewNotification(chatData, context!!, notificationId)
                            Util.sortTwoUIDs(chatData.participants.first(), chatData.participants.last())
                        }
                    }
                }
            }
        }
    }

    private fun sendNewNotification(chatData: ChatData, context: Context, notificationId: Int) {
        val person = Person.Builder()
            .setName(participantByUid(chatData, chatData.senderuid).tempName)
            .setUri(participantByUid(chatData, chatData.senderuid).tempImage)
            .build()
        val conversation = NotificationCompat.MessagingStyle.Message(chatData.msg, chatData.uniqueQuerableTime.toLong(), person)

        val oldStyle = restoreMessagingStyle(context, notificationId)
        oldStyle?.addMessage(conversation)

        val activityIntent = Intent(context, ChatActivity2::class.java).apply {
            putExtra("data", otherParticipant(chatData.participants))
        }
        val activityPendingIntent =
            PendingIntent.getActivity(context, 0, activityIntent, PendingIntent.FLAG_UPDATE_CURRENT)
        val remoteInput: RemoteInput = RemoteInput.Builder(chatData.uniqueQuerableTime).run {
            setLabel("Your reply")
            build()
        }
        val replyIntent = Intent(context, DirectReceiver::class.java).apply {
            val json = Gson().toJson(chatData)
            putExtra("index", notificationId)
            putExtra("json", json)
        }
        var replyPendingIntent = PendingIntent.getBroadcast(context, notificationId, replyIntent, PendingIntent.FLAG_UPDATE_CURRENT)

        val actionReply = NotificationCompat.Action
            .Builder(R.drawable.app_logo_round, "Reply", replyPendingIntent)
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(true)
            .build()
        val notification = NotificationCompat.Builder(context, "Messages")
            .setSmallIcon(R.drawable.app_icon_sec)
            .setStyle(oldStyle)
            .setColor(Color.BLUE)
            .setContentIntent(activityPendingIntent)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .addAction(actionReply)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        with(NotificationManagerCompat.from(context)) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
            notify(notificationId, notification.build())
        }
    }

    private fun restoreMessagingStyle(context: Context, notificationId: Int): NotificationCompat.MessagingStyle? {
        return (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .activeNotifications
            .find { it.id == notificationId }
            ?.notification
            ?.let { NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(it) }
    }

    private fun findActiveNotification(context: Context, notificationId: Int): Notification? {
        return (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .activeNotifications.find { it.id == notificationId }?.notification
    }

    private fun otherParticipant(participants: java.util.ArrayList<String>): String {
        for (i in participants) return if (i != myAuth) i else participants[1]
        return ""
    }

    private fun otherParticipant(chatUser: ChatData): ParticipantTempData {
        val myAuth = FirebaseAuth.getInstance().currentUser!!.uid
        for (i in chatUser.participantsTempData) if (i.uid != myAuth) return i
        return ParticipantTempData()
    }

    private fun participantByUid(chatUser: ChatData, uid: String): ParticipantTempData {
        val myAuth = FirebaseAuth.getInstance().currentUser!!.uid
        for (i in chatUser.participantsTempData) if (i.uid != uid) return i
        return ParticipantTempData()
    }

    private fun getMessageText(intent: Intent): CharSequence? {
        return RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_TEXT_REPLY)
    }

    private fun closeNotification(NOTIFICATION_ID: Int, context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
    }

    companion object {
        const val CHAT_REFERENCE = "chat_reference"
        const val ONLINE_PRESENCE = "online_presence"
        const val USER_DETAILS = "user_details"
    }
}
