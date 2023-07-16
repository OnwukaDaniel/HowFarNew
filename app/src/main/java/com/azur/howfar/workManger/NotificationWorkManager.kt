package com.azur.howfar.workManger

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.azur.howfar.R
import com.azur.howfar.activity.BaseActivity
import com.azur.howfar.broadcasts.DirectReceiver
import com.azur.howfar.broadcasts.MessageMarkAsRead
import com.azur.howfar.dilog.IncomingCallDialog
import com.azur.howfar.howfarchat.chat.ChatActivity2
import com.azur.howfar.models.*
import com.azur.howfar.notification.AppNotificationManager
import com.azur.howfar.utils.TimeUtils
import com.azur.howfar.utils.Util
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

class NotificationWorkManager(val context: Context, val params: WorkerParameters) : Worker(context, params) {
    private var auth = FirebaseAuth.getInstance().currentUser

    override fun doWork(): Result {
        return Result.success()
    }
}