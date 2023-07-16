package com.azur.howfar

import android.app.Application
import android.app.NotificationManager
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.azur.howfar.activity.UnCaughtException
import com.azur.howfar.jobservice.LikeJobService
import com.azur.howfar.jobservice.NetworkJobScheduler
import com.azur.howfar.notification.AppNotificationManager
import com.vanniktech.emoji.EmojiManager
import com.vanniktech.emoji.facebook.FacebookEmojiProvider
import com.vanniktech.emoji.google.GoogleEmojiProvider
import com.vanniktech.emoji.ios.IosEmojiProvider
import com.vanniktech.emoji.twitter.TwitterEmojiProvider

class MainApplication : Application(), UnCaughtException {

    private lateinit var appNotificationManager: AppNotificationManager

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
    }

    override fun onCreate() {
        super.onCreate()
        appNotificationManager = AppNotificationManager(this)
        setUpExceptionHandler()
        appNotificationManager.createChannel(
            channelName = "General", channelId = "General", notificationImportance = NotificationManager.IMPORTANCE_DEFAULT, desc = "General HowFar Notifications"
        )
        appNotificationManager.createChannel(
            channelName = "Messages", channelId = "Messages", notificationImportance = NotificationManager.IMPORTANCE_HIGH, desc = "Messages"
        )
        appNotificationManager.createChannel(
            channelName = "Network", channelId = "Network", notificationImportance = NotificationManager.IMPORTANCE_LOW, desc = "Network"
        )
        appNotificationManager.createChannel(
            channelName = "App files", channelId = "App files", notificationImportance = NotificationManager.IMPORTANCE_LOW, desc = "App files"
        )
        appNotificationManager.createChannel(
            channelName = "Call", channelId = "Call", notificationImportance = 1, desc = "Call"
        )
        appNotificationManager.createChannel(
            channelName = "Live", channelId = "Live", notificationImportance = 1, desc = "Live notification"
        )
        appNotificationManager.createChannel(
            channelName = "Moment", channelId = "Moment", notificationImportance = NotificationManager.IMPORTANCE_HIGH, desc = "Moment notification"
        )
        when (resources.getInteger(R.integer.emoji_variant)) {
            1 -> EmojiManager.install(GoogleEmojiProvider())
            2 -> EmojiManager.install(FacebookEmojiProvider())
            3 -> EmojiManager.install(TwitterEmojiProvider())
            else -> EmojiManager.install(IosEmojiProvider())
        }

        //val intent = Intent(this, ChatAlarm::class.java).let { PendingIntent.getBroadcast(this, 100, it, FLAG_CANCEL_CURRENT) }
        //val alarmMgr = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        //alarmMgr.setRepeating(AlarmManager.RTC_WAKEUP, 5000L, 60000L, intent)

        val jobScheduler = getSystemService(JOB_SCHEDULER_SERVICE) as JobScheduler
        val jobService = ComponentName(packageName, NetworkJobScheduler::class.java.name)
        val jobInfo = JobInfo.Builder(111, jobService)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPersisted(true)
            .build()
        val resultCode = jobScheduler.schedule(jobInfo)
        if (resultCode == JobScheduler.RESULT_FAILURE) {
            jobScheduler.schedule(jobInfo)
        }

        val likeJobScheduler = getSystemService(JOB_SCHEDULER_SERVICE) as JobScheduler
        val likeJobService = ComponentName(packageName, LikeJobService::class.java.name)
        val likeJobInfo = JobInfo.Builder(211, likeJobService)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPersisted(true)
            .build()
        val likeResultCode = likeJobScheduler.schedule(likeJobInfo)
        if (likeResultCode == JobScheduler.RESULT_FAILURE) {
            likeJobScheduler.schedule(jobInfo)
        }
    }

    private fun setUpExceptionHandler() {
        Handler(Looper.getMainLooper()).post{
            while (true){
                try {
                    Looper.loop()
                } catch (e: Exception){
                    uncaughtException(Looper.getMainLooper().thread, e)
                }
            }
        }
        Thread.setDefaultUncaughtExceptionHandler{ t, e ->
            uncaughtException(t, e)
        }
    }

    override fun uncaughtException(thread: Thread, e: Throwable) {
         println("Caught exception *********************************************** ${e.printStackTrace()}")
    }
}