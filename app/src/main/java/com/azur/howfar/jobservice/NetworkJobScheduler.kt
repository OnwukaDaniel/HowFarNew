package com.azur.howfar.jobservice

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobParameters
import android.app.job.JobService
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.media.RingtoneManager
import android.net.Uri
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.Action
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import com.azur.howfar.R
import com.azur.howfar.activity.BaseActivity
import com.azur.howfar.broadcasts.DirectReceiver
import com.azur.howfar.dilog.IncomingCallDialog
import com.azur.howfar.howfarchat.chat.ChatActivity2
import com.azur.howfar.models.*
import com.azur.howfar.utils.TimeUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*

@SuppressLint("SpecifyJobSchedulerIdRange")
class NetworkJobScheduler : JobService() {
    private var callRef = FirebaseDatabase.getInstance().reference
    private var messagesRef = FirebaseDatabase.getInstance().reference
    private val scope = CoroutineScope(Dispatchers.Main)
    private val messageList: ArrayList<ArrayList<ChatData>> = arrayListOf()
    private lateinit var pref: SharedPreferences
    private val contactList: ArrayList<Pair<String, String>> = arrayListOf()
    private val phoneList: ArrayList<String> = arrayListOf()
    var timesObserved = 0L

    override fun onStartJob(params: JobParameters?): Boolean {
        pref = this@NetworkJobScheduler.getSharedPreferences(this@NetworkJobScheduler.getString(R.string.ALL_PREFERENCE), Context.MODE_PRIVATE)

        if (FirebaseAuth.getInstance().currentUser == null) return false
        val myAuth = FirebaseAuth.getInstance().currentUser!!.uid
        callRef = FirebaseDatabase.getInstance().reference.child(ChatActivity2.CALL_REFERENCE).child(myAuth)
        messagesRef = FirebaseDatabase.getInstance().reference.child(BaseActivity.CHAT_REFERENCE).child(myAuth)

        messagesRef.keepSynced(false)
        callRef.keepSynced(false)
        callRef.addValueEventListener(callListener)
        //messagesRef.addValueEventListener(messageListener)
        return true
    }

    private val callListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            if (snapshot.exists()) {
                var callData = snapshot.getValue(CallData::class.java)!!
                FirebaseAuth.getInstance().currentUser ?: return
                if (callData.timeCalled == "") return
                val myAuth = FirebaseAuth.getInstance().currentUser!!.uid
                val callTime = TimeUtils.UTCToLocal(callData.timeCalled).toLong() / 1000 // CALL EXPIRED IMPLEMENTATION
                val timeNow = Calendar.getInstance().timeInMillis / 1000 // CALL EXPIRED IMPLEMENTATION
                val ansType = callData.answerType
                when {
                    ansType == CallAnswerType.CANCELLED -> return
                    callData.callerUid == myAuth -> return
                    callData.timeCalled == "" -> return
                    timesObserved == callTime -> return
                    timeNow > callTime + 30 -> return
                }
                timesObserved = callTime
                Toast.makeText(this@NetworkJobScheduler, "Call", Toast.LENGTH_LONG).show()
                callData.answerType = CallAnswerType.RECEIVED
                callRef.setValue(callData).addOnSuccessListener {
                    val intent = Intent(this@NetworkJobScheduler, IncomingCallDialog::class.java)
                    callData.engagementType = CallEngagementType.JOIN
                    intent.putExtra("callData", Gson().toJson(callData))
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(intent)
                    scope.launch {
                        delay(60_000)
                        return@launch
                    }
                }
            }
        }

        override fun onCancelled(error: DatabaseError) = Unit
    }

    private val messageListener = object : ValueEventListener {
        @SuppressLint("UnspecifiedImmutableFlag")
        override fun onDataChange(snapshot: DataSnapshot) {
            var notificationIndex = 1000
            FirebaseAuth.getInstance().currentUser ?: return
            val myAuth = FirebaseAuth.getInstance().currentUser!!.uid
            if (snapshot.exists()) {
                messageList.clear()
                for (i in snapshot.children) {
                    val datasetEachUser: ArrayList<ChatData> = arrayListOf()
                    for (x in i.children) {
                        val chatData = x.getValue(ChatData::class.java)!!
                        if(chatData.participantsTempData.isEmpty()) continue
                        if (chatData.participantsTempData.first().phone == "" || chatData.participantsTempData.last().phone == "") return
                        if (!chatData.read && chatData.senderuid != myAuth) datasetEachUser.add(chatData)
                    }
                    datasetEachUser.sortWith(compareByDescending { it.uniqueQuerableTime })
                    datasetEachUser.reverse()
                    if (datasetEachUser.isNotEmpty()) messageList.add(datasetEachUser)
                }
                messageList.sortWith(compareByDescending { it.first().uniqueQuerableTime })

                for (eachUserMsg in messageList) {
                    var priority = NotificationCompat.PRIORITY_HIGH
                    val person = Person.Builder()
                        .setName(participantByUid(eachUserMsg.first(), eachUserMsg.first().senderuid).tempName)
                        .setUri(participantByUid(eachUserMsg.first(), eachUserMsg.first().senderuid).tempImage)
                        .build()
                    var msgStyle = NotificationCompat.MessagingStyle(person).setGroupConversation(true)
                        .setConversationTitle("Chat with ${otherParticipant(eachUserMsg.first()).tempName}")

                    for (chatData in eachUserMsg) {
                        val p1 = participantByUid(chatData, myAuth).phone.takeLast(4)
                        val p2 = participantByUid(chatData, myAuth).phone.takeLast(4)
                        val numberIndexList = arrayListOf(p1, p2)
                        var notCounter = ""
                        numberIndexList.sortedWith(compareByDescending { it }).forEach { notCounter += it }
                        notificationIndex = notCounter.toInt()
                        val oldStyle = restoreMessagingStyle(this@NetworkJobScheduler, notificationIndex)
                        val oldStyleCopy = restoreMessagingStyle(this@NetworkJobScheduler, notificationIndex)

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
                    }

                    val activityIntent = Intent(this@NetworkJobScheduler, ChatActivity2::class.java).apply {
                        putExtra("data", otherParticipant(eachUserMsg.first().participants))
                    }
                    val activityPendingIntent =
                        PendingIntent.getActivity(this@NetworkJobScheduler, 0, activityIntent, PendingIntent.FLAG_UPDATE_CURRENT)
                    val remoteInput: RemoteInput = RemoteInput.Builder(eachUserMsg.first().uniqueQuerableTime).run {
                        setLabel("Your reply")
                        build()
                    }
                    val replyIntent = Intent(this@NetworkJobScheduler, DirectReceiver::class.java).apply {
                        val json = Gson().toJson(eachUserMsg.first())
                        putExtra("index", notificationIndex)
                        putExtra("json", json)
                    }
                    var replyPendingIntent = PendingIntent.getBroadcast(applicationContext, notificationIndex, replyIntent, PendingIntent.FLAG_UPDATE_CURRENT)
                    val actionReply = Action
                        .Builder(R.drawable.app_logo_round, "Reply", replyPendingIntent)
                        .addRemoteInput(remoteInput)
                        .setAllowGeneratedReplies(true)
                        .build()
                    val notification = NotificationCompat.Builder(this@NetworkJobScheduler, "Messages")
                        .setSmallIcon(R.drawable.app_icon_sec)
                        .setStyle(msgStyle)
                        .setColor(Color.BLUE)
                        .setContentIntent(activityPendingIntent)
                        .setAutoCancel(true)
                        .addAction(actionReply)
                        .setPriority(priority)

                    with(NotificationManagerCompat.from(this@NetworkJobScheduler)) {
                        notify(notificationIndex, notification.build())
                    }
                }
            }
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

    private fun playRing(res: Int = R.raw.delivered) {
        val uri = Uri.Builder()
            .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
            .authority(applicationContext.packageName)
            .path((res).toString())
            .build()
        RingtoneManager.getRingtone(applicationContext, uri).play()
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        callRef.removeEventListener(callListener)
        //messagesRef.removeEventListener(messageListener)
        return true
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
        val myAuth = FirebaseAuth.getInstance().currentUser!!.uid
        for (i in chatUser.participantsTempData) if (i.uid == uid) return i
        return ParticipantTempData()
    }
}

data class PersonCustom(
    var name: String = "",
    var time: String = "",
    var image: String = "",
    var msg: String = "",
)