package com.azur.howfar.services

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.graphics.Color
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.azur.howfar.R
import com.azur.howfar.models.ChatData

class CallNotificationService : Service() {
    val dataset: ArrayList<ChatData> = arrayListOf()

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
    }

    companion object {
        const val CALL_REFERENCE = "call_reference"
        const val USER_DETAILS = "user_details"
        const val CONTACTS = "CONTACTS"
    }
}