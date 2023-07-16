package com.azur.howfar.alarms

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.azur.howfar.R
import com.azur.howfar.activity.BaseActivity
import com.azur.howfar.broadcasts.DirectReceiver
import com.azur.howfar.howfarchat.chat.ChatActivity2
import com.azur.howfar.models.ChatData
import com.azur.howfar.utils.Util
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.gson.Gson
import java.util.ArrayList

class ChatAlarm:  BroadcastReceiver() {
    private val user = FirebaseAuth.getInstance().currentUser
    private lateinit var pref: SharedPreferences

    override fun onReceive(context: Context?, intent: Intent?) {
        if (user == null) return
        println("Alarm sent **************************************************")
        pref = context!!.getSharedPreferences(context.getString(R.string.ALL_PREFERENCE), Context.MODE_PRIVATE)
        val myAuth = user.uid
        val dataset: ArrayList<ChatData> = arrayListOf()
        val messagesRef = FirebaseDatabase.getInstance().reference.child(BaseActivity.CHAT_REFERENCE).child(user.uid)
        messagesRef.get().addOnSuccessListener { snapshot->
            if (snapshot.exists()) {
                println("Alarm message gotten **************************************************")
                dataset.clear()
                for (i in snapshot.children) {
                    val lastMsg = i.children.last().getValue(ChatData::class.java)!!
                    if (!lastMsg.read) {
                        dataset.add(lastMsg)
                        dataset.sortWith(compareByDescending { it.uniqueQuerableTime })
                    }
                }
                for ((index, lastMsg) in dataset.withIndex()) {
                    if (lastMsg.senderuid == myAuth || lastMsg.read) return@addOnSuccessListener
                    val remoteInput: RemoteInput = RemoteInput.Builder(lastMsg.uniqueQuerableTime).run {
                        setLabel("Your reply")
                        build()
                    }
                    val activityIntent = Intent(context, ChatActivity2::class.java).apply {
                        putExtra("data", otherParticipant(lastMsg.participants))
                    }
                    val activityPendingIntent: PendingIntent =
                        PendingIntent.getActivity(context, 0, activityIntent, PendingIntent.FLAG_UPDATE_CURRENT)
                    var replyPendingIntent: PendingIntent

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        val replyIntent = Intent(context, DirectReceiver::class.java).apply {
                            val json = Gson().toJson(lastMsg)
                            putExtra("index", index)
                            putExtra("json", json)
                        }
                        replyPendingIntent = PendingIntent.getBroadcast(context, index, replyIntent, PendingIntent.FLAG_UPDATE_CURRENT)
                    } else {
                        replyPendingIntent = activityPendingIntent
                    }

                    val action: NotificationCompat.Action = NotificationCompat.Action
                        .Builder(R.drawable.app_logo_round, "Reply", replyPendingIntent)
                        .addRemoteInput(remoteInput)
                        .build()

                    sendNotification(lastMsg, action, activityPendingIntent, context)
                }
            }
        }
    }

    private fun sendNotification(lastMsg: ChatData, action: NotificationCompat.Action, pendingIntent: PendingIntent, context: Context) {
        val builder: NotificationCompat.Builder = NotificationCompat.Builder(context, "Messages")
            .setSmallIcon(R.drawable.app_icon_sec)
            .setContentTitle(lastMsg.myPhone)
            .addAction(action)
            .setColor(Color.BLUE)
            .setContentText(lastMsg.msg)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setStyle(NotificationCompat.BigTextStyle().bigText(lastMsg.msg))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        //playRing()
        with(NotificationManagerCompat.from(context)) {
            val no = if (lastMsg.myPhone.length >= 5) Util.formatNumber(lastMsg.myPhone).substring(0, 5).toInt() else 0
            val inPhoneValue = pref.getInt(context.getString(R.string.in_chat_phone_key), 0)
            if (inPhoneValue == 0) {
                notify(no, builder.build())
            }
        }
    }

    private fun otherParticipant(participants: ArrayList<String>): String {
        val myAuth = FirebaseAuth.getInstance().currentUser!!.uid
        for (i in participants) return if (i != myAuth) i else participants[1]
        return ""
    }
}