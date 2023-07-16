package com.azur.howfar.services

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Context.JOB_SCHEDULER_SERVICE
import android.content.Intent
import androidx.work.WorkManager
import com.google.firebase.auth.FirebaseAuth
import com.azur.howfar.jobservice.NetworkJobScheduler

class MsgBroadcast : BroadcastReceiver() {
    private var ctx: Context? = null
    private lateinit var workManager: WorkManager

    override fun onReceive(context: Context?, intent: Intent?) {
        ctx = context
        workManager = WorkManager.getInstance(context!!)
        if (FirebaseAuth.getInstance().currentUser == null) return
        println("Work manager schedule started ***************************************** ")
        workManagerForNotification()
    }

    private fun workManagerForNotification() {
        //val service = Intent(ctx!!, NetworkJobScheduler::class.java)
        //ctx!!.startService(service)
        //val workRequest = OneTimeWorkRequestBuilder<NotificationWorkManager>()
        //    .addTag("call and messages").build()
        //workManager.enqueue(workRequest)

        val jobScheduler = ctx!!.getSystemService(JOB_SCHEDULER_SERVICE) as JobScheduler
        val jobService = ComponentName(ctx!!.packageName, NetworkJobScheduler::class.java.name)
        val jobInfo = JobInfo.Builder(111, jobService)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPersisted(true)
            .build()
        val resultCode = jobScheduler.schedule(jobInfo)
        println("Work manager schedule ***************************************** ")
        if (resultCode == JobScheduler.RESULT_FAILURE) {
            jobScheduler.schedule(jobInfo)
        }
    }
}