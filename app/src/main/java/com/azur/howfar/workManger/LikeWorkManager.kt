package com.azur.howfar.workManger

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.azur.howfar.livedata.ValueEventLiveData
import com.azur.howfar.livestreamming.LiveListFragment
import com.azur.howfar.livestreamming.WatchLiveActivity
import com.azur.howfar.models.BroadcastCallData
import com.azur.howfar.models.CallAnswerType
import com.azur.howfar.models.EventListenerType.onDataChange
import com.azur.howfar.models.OnlinePresenceData
import com.azur.howfar.notification.AppNotificationManager
import com.azur.howfar.retrofit.Const
import com.azur.howfar.utils.TimeUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class LikeWorkManager(private val context: Context, private val params: WorkerParameters) : Worker(context, params) {
    private val myAuth = FirebaseAuth.getInstance().currentUser!!.uid
    private lateinit var appNotificationManager: AppNotificationManager

    override fun doWork(): Result {
        appNotificationManager = AppNotificationManager(context)
        return Result.success()
    }
}