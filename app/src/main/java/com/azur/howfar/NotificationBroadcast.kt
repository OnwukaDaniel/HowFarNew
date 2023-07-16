package com.azur.howfar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.IBinder

class NotificationBroadcast: BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        println("********************* ${intent!!.action}")
        when(intent.action){
            "android.net.conn.CONNECTIVITY_CHANGE"->{
                val CONNECTIVITY_CHANGE = intent.getBooleanExtra("state", false)
            }
        }
    }

    override fun peekService(myContext: Context?, service: Intent?): IBinder {
        return super.peekService(myContext, service)
    }
}