package com.azur.howfar.services

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.os.Binder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.auth.FirebaseAuth
import com.azur.howfar.R
import com.azur.howfar.models.CallData
import io.agora.rtc2.RtcEngine

class CallService : Service() {
    var iBinder = MyBinder()
    var callData = CallData()
    private var agoraEngine: RtcEngine? = null
    private val myAuth = FirebaseAuth.getInstance().currentUser!!.uid

    override fun onBind(intent: Intent?) = iBinder

    override fun onCreate() {
        super.onCreate()
        createNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startActivity(intent)
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Thread {
            RtcEngine.destroy()
            agoraEngine = null
        }.start()
    }

    inner class MyBinder : Binder() {
        fun getService() = this@CallService
    }

    private fun createNotification() {
        val builder: NotificationCompat.Builder = NotificationCompat.Builder(this, "Call")
            .setSmallIcon(R.drawable.app_icon_sec)
            .setContentTitle("Call")
            .setColor(Color.GREEN)
            .setContentText("On-Going call")
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        with(NotificationManagerCompat.from(this)) {
            notify(1, builder.build())
        }
    }
}