package com.azur.howfar.broadcasts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import com.azur.howfar.models.ChatData
import com.azur.howfar.utils.Util
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.gson.Gson

class MessageMarkAsRead : BroadcastReceiver() {
    private val myAuth = FirebaseAuth.getInstance().currentUser!!.uid

    override fun onReceive(context: Context?, intent: Intent?) {
        val json = intent?.getStringExtra("json")
        var chatData = Gson().fromJson(json, ChatData::class.java)!!
        val no = if (chatData.myPhone.length >= 5) Util.formatNumber(chatData.myPhone).substring(0, 5).toInt() else 0

        val myChattingRef = FirebaseDatabase.getInstance().reference
            .child(CHAT_REFERENCE)
            .child(myAuth)
            .child(otherParticipant(chatData.participants))
            .child(chatData.uniqueQuerableTime)

        val receiverChattingRef = FirebaseDatabase.getInstance().reference
            .child(CHAT_REFERENCE)
            .child(otherParticipant(chatData.participants))
            .child(myAuth)
            .child(chatData.uniqueQuerableTime)
        if (!chatData.read) receiverChattingRef.get().addOnSuccessListener { retrieveSnapshot ->
            if (retrieveSnapshot.exists()) {
                val timeRef = FirebaseDatabase.getInstance().reference.child("time").child(myAuth)
                timeRef.setValue(ServerValue.TIMESTAMP).addOnSuccessListener {
                    timeRef.get().addOnSuccessListener { time ->
                        chatData.read = true
                        chatData.timeseen = time.value.toString()
                        receiverChattingRef.setValue(chatData)
                        myChattingRef.get().addOnSuccessListener { myChatSnap -> if (myChatSnap.exists()) myChattingRef.setValue(chatData) }
                        NotificationManagerCompat.from(context!!).cancel(no)
                    }
                }
            }
        }
    }

    override fun peekService(myContext: Context?, service: Intent?): IBinder {
        return super.peekService(myContext, service)
    }

    private fun otherParticipant(participants: ArrayList<String>): String {
        val myAuth = FirebaseAuth.getInstance().currentUser!!.uid
        for (i in participants) return if (i != myAuth) i else participants[1]
        return ""
    }

    companion object {
        const val CHAT_REFERENCE = "chat_reference"
    }
}