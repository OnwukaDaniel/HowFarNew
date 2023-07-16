package com.azur.howfar.jobservice

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobParameters
import android.app.job.JobService
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.lifecycle.ProcessLifecycleOwner
import com.azur.howfar.R
import com.azur.howfar.broadcasts.DirectReceiver
import com.azur.howfar.howfarchat.chat.ChatActivity2
import com.azur.howfar.livedata.ValueEventLiveData
import com.azur.howfar.livestreamming.WatchLiveActivity
import com.azur.howfar.models.*
import com.azur.howfar.models.EventListenerType.onDataChange
import com.azur.howfar.notification.AppNotificationManager
import com.azur.howfar.posts.CommentFragment
import com.azur.howfar.posts.NormalFeedAdapter
import com.azur.howfar.posts.PostViewActivity
import com.azur.howfar.retrofit.Const
import com.azur.howfar.user.wallet.MyWalletActivity
import com.azur.howfar.user.wallet.WalletNavigation
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.gson.Gson
import kotlinx.coroutines.*
import java.util.ArrayList

@SuppressLint("SpecifyJobSchedulerIdRange")
class LikeJobService : JobService() {
    private lateinit var pref: SharedPreferences
    private lateinit var appNotificationManager: AppNotificationManager

    override fun onStartJob(params: JobParameters?): Boolean {
        pref= getSharedPreferences(getString(R.string.ALL_PREFERENCE), Context.MODE_PRIVATE)
        appNotificationManager = AppNotificationManager(this)
        listenForLiveEvents()
        listenForPosts()
        listenForTransfers()
        return true
    }

    private fun listenForLiveEvents() {
        FirebaseAuth.getInstance().currentUser ?: return
        val myAuth = FirebaseAuth.getInstance().currentUser!!.uid
        if(pref.getBoolean(getString(R.string.live_switch), true)) return
        val eligibleUser = FirebaseDatabase.getInstance().reference.child(LIVE_BROADCAST).child(myAuth)
        ValueEventLiveData(eligibleUser).observe(ProcessLifecycleOwner.get()) {
            when (it.second) {
                onDataChange -> {
                    for (i in it.first.children) {
                        val broadcastCallData = i.getValue(BroadcastCallData::class.java)!!
                        when {
                            broadcastCallData.callerUid == myAuth -> return@observe
                            broadcastCallData.notificationDelivered -> return@observe
                            broadcastCallData.answerType == CallAnswerType.ENDED -> return@observe
                        }
                        val activityIntent = Intent(this, WatchLiveActivity::class.java).apply {
                            putExtra(Const.USER_STR, broadcastCallData.callerUid)
                        }
                        val activityPendingIntent =
                            PendingIntent.getActivity(this, 0, activityIntent, PendingIntent.FLAG_UPDATE_CURRENT)

                        appNotificationManager.createNormalNotification(
                            content = "A channel you follow is live",
                            channelName = "Live",
                            title = "${broadcastCallData.senderTempData.tempName} is live.",
                            activityPendingIntent = activityPendingIntent,
                            autoCancel = true,
                            notificationIndex = broadcastCallData.senderTempData.phone.takeLast(6).toInt(),
                            priority = NotificationManager.IMPORTANCE_HIGH,
                        )
                        val bc = FirebaseDatabase.getInstance().reference
                            .child(LIVE_BROADCAST)
                            .child(myAuth)
                            .child(broadcastCallData.callerUid)
                        bc.get().addOnSuccessListener { bcSnap ->
                            if (bcSnap.exists()) {
                                broadcastCallData.notificationDelivered = true
                                bc.setValue(broadcastCallData)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun listenForPosts() {
        FirebaseAuth.getInstance().currentUser ?: return
        val myAuth = FirebaseAuth.getInstance().currentUser!!.uid
        val postRef = FirebaseDatabase.getInstance().reference.child(MOMENT_DATA_INVITE).child(myAuth)
        ValueEventLiveData(postRef).observe(ProcessLifecycleOwner.get()) {
            when (it.second) {
                onDataChange -> {
                    for (i in it.first.children) {
                        var moment = i.getValue(Moment::class.java)!!
                        when {
                            moment.seen -> return@observe
                        }
                        val activityIntent = Intent(this, PostViewActivity::class.java).apply {
                            val json = Gson().toJson(moment)
                            putExtra("data", json)
                        }
                        val activityPendingIntent = PendingIntent.getActivity(this, 0, activityIntent, PendingIntent.FLAG_UPDATE_CURRENT)

                        appNotificationManager.createNormalNotification(
                            content = "Check this out",
                            channelName = "Moment",
                            title = "${moment.profileName} created a new post.",
                            activityPendingIntent = activityPendingIntent,
                            autoCancel = true,
                            notificationIndex = moment.profilePhone.takeLast(6).toInt(),
                            priority = NotificationManager.IMPORTANCE_HIGH,
                        )
                        runBlocking {
                            CoroutineScope(Dispatchers.IO).launch {
                                delay(1000)
                                val postRef2 = FirebaseDatabase.getInstance().reference.child(MOMENT_DATA_INVITE).child(myAuth).child(moment.creatorUid)
                                postRef2.get().addOnSuccessListener { momentSnap ->
                                    if (momentSnap.exists()) {
                                        moment.seen = true
                                        postRef2.removeValue()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun listenForMyPostsComments() {
        FirebaseAuth.getInstance().currentUser ?: return
        val myAuth = FirebaseAuth.getInstance().currentUser!!.uid
        val postActivityRef = FirebaseDatabase.getInstance().reference
            .child(CommentFragment.MOMENT_DETAILS)
            .child(myAuth)
        ValueEventLiveData(postActivityRef).observe(ProcessLifecycleOwner.get()) {
            when (it.second) {
                onDataChange -> {
                    for (i in it.first.children) {
                        val seenDetails = arrayListOf<MomentDetails>()
                        for (x in i.children) {
                            val momentDetails = x.getValue(MomentDetails::class.java)!!
                            if (!momentDetails.creatorSean) seenDetails.add(momentDetails)
                            if (seenDetails.isEmpty()) return@observe
                            val activityIntent = Intent(this, PostViewActivity::class.java).apply {
                                val json = Gson().toJson(seenDetails.first())
                                putExtra("data details", json)
                            }
                            val activityPendingIntent = PendingIntent.getActivity(this, 0, activityIntent, PendingIntent.FLAG_UPDATE_CURRENT)

                            var likes = 0
                            var loves = 0
                            var comments = 0
                            seenDetails.forEach { md ->
                                if (md.likes.profileUid != "") likes++
                                if (md.loves.profileUid != "") loves++
                                if (md.comment.profileUid != "") comments++
                            }
                            when {
                                likes + loves > 0 -> {
                                    val titlePlural = if (likes + loves > 1) "reactions" else "reaction"
                                    appNotificationManager.createNormalNotification(
                                        content = "Check it out",
                                        channelName = "Moment",
                                        title = "You have new $titlePlural to your post",
                                        activityPendingIntent = activityPendingIntent,
                                        autoCancel = true,
                                        notificationIndex = seenDetails.first().time.takeLast(6).toInt(), // TODO OPTIMIZE
                                        priority = NotificationCompat.PRIORITY_HIGH,
                                    )
                                }
                            }
                            when {
                                comments > 0 -> {
                                    val index =
                                        if (seenDetails.first().timeMomentPosted == "") 111111 else seenDetails.first().timeMomentPosted.takeLast(6).toInt()
                                    appNotificationManager.createNormalNotification(
                                        content = "Check this out",
                                        channelName = "Moment",
                                        title = "You have $comments new comments",
                                        activityPendingIntent = activityPendingIntent,
                                        autoCancel = true,
                                        notificationIndex = index + 1, // TODO OPTIMIZE
                                        priority = NotificationCompat.PRIORITY_HIGH,
                                    )
                                }
                            }
                        }
                    }

                }
            }
        }
    }

    private fun listenForTransfers() {
        FirebaseAuth.getInstance().currentUser ?: return
        val myAuth = FirebaseAuth.getInstance().currentUser!!.uid
        val historyRef = FirebaseDatabase.getInstance().reference.child(NormalFeedAdapter.TRANSFER_HISTORY).child(myAuth)
        ValueEventLiveData(historyRef).observe(ProcessLifecycleOwner.get()) {
            when (it.second) {
                onDataChange -> {
                    for (i in it.first.children) {
                        var dataCurrency = i.getValue(Currency::class.java)!!
                        if (!dataCurrency.transactionSeen) {
                            val activityIntent = Intent(this, MyWalletActivity::class.java).apply {
                                putExtra(WalletNavigation.TAG, WalletNavigation.RecordFragment)
                            }
                            var message = ""
                            when (dataCurrency.transactionType) {
                                TransactionType.EARNED -> message = "You earned ${dataCurrency.hfcoin} HFCoin"
                                TransactionType.BOUGHT -> message = "Your purchase of ${dataCurrency.hfcoin} HFCoin was succesful"
                                TransactionType.RECEIVED -> message = "Someone sent you ${dataCurrency.hfcoin} HFCoin"
                                TransactionType.APP_GIFT -> message = "You were gifted ${dataCurrency.hfcoin} by HowFar App."
                            }
                            val notifiable = arrayListOf(TransactionType.EARNED, TransactionType.BOUGHT, TransactionType.RECEIVED, TransactionType.APP_GIFT)
                            if (dataCurrency.transactionType in notifiable){
                                val activityPendingIntent = PendingIntent.getActivity(this, 0, activityIntent, PendingIntent.FLAG_UPDATE_CURRENT)
                                appNotificationManager.createNormalNotification(
                                    content = "",
                                    channelName = "Moment",
                                    title = message,
                                    activityPendingIntent = activityPendingIntent,
                                    autoCancel = true,
                                    notificationIndex = dataCurrency.timeOfTransaction.takeLast(6).toInt(), // TODO OPTIMIZE
                                    priority = NotificationCompat.PRIORITY_HIGH,
                                )
                                dataCurrency.transactionSeen = true
                                historyRef.child(dataCurrency.timeOfTransaction).setValue(dataCurrency)
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val TAG = "hostliveactivity"
        const val CALL_REFERENCE = "call_reference"
        const val BROADCAST_HISTORY = "broadcast_history"
        const val BROADCAST_COMMENT_HISTORY = "broadcast_comment_history"
        const val USER_DETAILS = "user_details"
        const val TRANSFER_HISTORY = "user_coins_transfer"
        const val MOMENT_DATA_INVITE = "MOMENT_DATA_INVITE"
        const val LIVE_BROADCAST = "live_broadcast"
        const val LIVE_BROADCAST_CREATOR = "live_broadcast_creator"
        const val LIVE_PRESENCE = "LIVE_PRESENCE"
        const val BROADCAST_MOMENT_DATA = "BROADCAST_MOMENT_DATA"
        val REQUESTED_PERMISSIONS = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
        )
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        return true
    }
}