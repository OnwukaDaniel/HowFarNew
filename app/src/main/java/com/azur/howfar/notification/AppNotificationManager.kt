package com.azur.howfar.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import androidx.core.app.*
import com.azur.howfar.R
import com.azur.howfar.broadcasts.DirectReceiver
import com.azur.howfar.howfarchat.chat.ChatActivity2
import com.azur.howfar.jobservice.PersonCustom
import com.azur.howfar.models.ChatData
import com.azur.howfar.models.ParticipantTempData
import com.azur.howfar.utils.FirebaseConstants
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.gson.Gson

class AppNotificationManager(private val context: Context) {
    private var auth = FirebaseAuth.getInstance().currentUser

    /**
     * Create a notification channel
     * @param channelId The id of the channel. Must be unique per package. The value may be truncated if it is too long.
     * @param channelName The user visible name of the channel.
     * @param notificationImportance NotificationImportance, default is NotificationManager.IMPORTANCE_LOW
     * @param desc the user visible description of this channel.
     *
     * */
    fun createChannel(
        channelId: String,
        channelName: String,
        notificationImportance: Int = NotificationManager.IMPORTANCE_LOW,
        desc: String
    ) {
        val notificationManager: NotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(channelId, channelName, notificationImportance).apply { description = desc }
        channel.enableLights(true)
        channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        notificationManager.createNotificationChannel(channel)
    }

    fun createNormalNotification(
        content: String,
        channelName: String,
        title: String,
        activityPendingIntent: PendingIntent,
        autoCancel: Boolean = true,
        notificationIndex: Int,
        priority: Int = NotificationCompat.PRIORITY_HIGH,
    ) {
        val notification = when {
            content != "" -> NotificationCompat.Builder(context, channelName)
                .setSmallIcon(R.drawable.app_icon_sec)
                .setColor(Color.BLUE)
                .setContentTitle(title)
                .setContentText(content)
                .setContentIntent(activityPendingIntent)
                .setAutoCancel(autoCancel)
                .setPriority(priority)
            else -> NotificationCompat.Builder(context, channelName)
                .setSmallIcon(R.drawable.app_icon_sec)
                .setColor(Color.BLUE)
                .setContentTitle(title)
                .setContentIntent(activityPendingIntent)
                .setAutoCancel(autoCancel)
                .setPriority(priority)
        }
        with(NotificationManagerCompat.from(context)) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
            notify(notificationIndex, notification.build())
        }
    }

    fun listenForMessage(chatData: ChatData) {
        var notificationIndex: Int
        FirebaseAuth.getInstance().currentUser ?: return
        val myAuth = FirebaseAuth.getInstance().currentUser!!.uid
        var priority = NotificationCompat.PRIORITY_HIGH
        val person = Person.Builder()
            .setName(participantByUid(chatData, chatData.senderuid).tempName)
            .setUri(participantByUid(chatData, chatData.senderuid).tempImage)
            .build()
        var msgStyle = NotificationCompat.MessagingStyle(person).setGroupConversation(true)
            .setConversationTitle("Chat with ${otherParticipant(chatData).tempName}")

        val p1 = participantByUid(chatData, myAuth).phone.takeLast(4)
        val p2 = participantByUid(chatData, myAuth).phone.takeLast(4)
        val numberIndexList = arrayListOf(p1, p2)
        var notCounter = ""
        numberIndexList.sortedWith(compareByDescending { it }).forEach { notCounter += it }
        notificationIndex = notCounter.toInt()
        val oldStyle = restoreMessagingStyle(context, notificationIndex)
        val oldStyleCopy = restoreMessagingStyle(context, notificationIndex)

        val personMe = Person.Builder()
            .setName(participantByUid(chatData, chatData.senderuid).tempName)
            .setUri(participantByUid(chatData, chatData.senderuid).tempImage)
            .build()
        val conversation = NotificationCompat.MessagingStyle.Message(chatData.msg, chatData.uniqueQuerableTime.toLong(), personMe)
        val personCustomNew = PersonCustom(
            name = conversation.person!!.name.toString(), time = conversation.timestamp.toString(),
            image = conversation.person!!.uri!!, msg = conversation.text.toString()
        )

        if (oldStyle != null) {
            priority = NotificationCompat.PRIORITY_DEFAULT
            val oldCustomData = arrayListOf<PersonCustom>()
            for (i in oldStyle.messages) {
                val personCustom = PersonCustom(
                    name = i.person!!.name.toString(), time = i.timestamp.toString(),
                    image = i.person!!.uri!!, msg = i.text.toString()
                )
                oldCustomData.add(personCustom)
            }
            if (personCustomNew !in oldCustomData) oldStyle.messages.add(conversation)
            msgStyle = oldStyle
        } else msgStyle.addMessage(conversation)

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
            putExtra("index", notificationIndex)
            putExtra("json", json)
        }
        var replyPendingIntent = PendingIntent.getBroadcast(context, notificationIndex, replyIntent, PendingIntent.FLAG_UPDATE_CURRENT)
        val actionReply = NotificationCompat.Action
            .Builder(R.drawable.app_logo_round, "Reply", replyPendingIntent)
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(true)
            .build()
        val notification = NotificationCompat.Builder(context, "Messages")
            .setSmallIcon(R.drawable.app_icon_sec)
            .setStyle(msgStyle)
            .setColor(Color.BLUE)
            .setContentIntent(activityPendingIntent)
            .setAutoCancel(true)
            .addAction(actionReply)
            .setPriority(priority)

        with(NotificationManagerCompat.from(context)) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
            notify(notificationIndex, notification.build())
        }
    }

    fun onlineMessages() {
        if (auth != null) {
            var ref = FirebaseDatabase.getInstance().reference
                .child(FirebaseConstants.CHAT_DISPLAY)
                .child(auth!!.uid)

        }
    }

    val messageListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {

        }

        override fun onCancelled(error: DatabaseError) = Unit
    }

    private fun restoreMessagingStyle(context: Context, notificationId: Int): NotificationCompat.MessagingStyle? {
        return (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .activeNotifications
            .find { it.id == notificationId }
            ?.notification
            ?.let { NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(it) }
    }

    private fun otherParticipant(participants: ArrayList<String>): String {
        val myAuth = FirebaseAuth.getInstance().currentUser!!.uid
        for (i in participants) return if (i != myAuth) i else participants[1]
        return ""
    }

    private fun otherParticipant(chatUser: ChatData): ParticipantTempData {
        val myAuth = FirebaseAuth.getInstance().currentUser!!.uid
        for (i in chatUser.participantsTempData) if (i.uid != myAuth) return i
        return ParticipantTempData()
    }

    private fun participantByUid(chatUser: ChatData, uid: String): ParticipantTempData {
        for (i in chatUser.participantsTempData) if (i.uid == uid) return i
        return ParticipantTempData()
    }
}