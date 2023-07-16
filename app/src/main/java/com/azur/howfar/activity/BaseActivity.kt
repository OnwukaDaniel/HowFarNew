package com.azur.howfar.activity

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.azur.howfar.R
import com.azur.howfar.chat.SocketApplication
import com.azur.howfar.databinding.BaseActivityBinding
import com.azur.howfar.models.OnlinePresenceData
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable

abstract class BaseActivity : AppCompatActivity() {
    private val binding by lazy { BaseActivityBinding.inflate(layoutInflater) }
    private val scope = CoroutineScope(Dispatchers.IO)
    private var auth = FirebaseAuth.getInstance().currentUser
    private lateinit var pref: SharedPreferences
    val application = SocketApplication()
    var onlineTime = ""
    val handler = Handler(Looper.getMainLooper())

    var runnable = object : Runnable {
        override fun run() {

            handler.postDelayed(this, 60_000)
        }
    }

    fun onClickBack(view: View?) = onBackPressed()

    protected fun permissionsAvailable(permissions: Array<String?>): Boolean {
        var granted = true
        for (permission in permissions) {
            if (ActivityCompat.checkSelfPermission(this, permission!!) != PackageManager.PERMISSION_GRANTED) {
                granted = false
                break
            }
        }
        return granted
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        pref = getSharedPreferences(getString(R.string.ALL_PREFERENCE), Context.MODE_PRIVATE)
        val theme = pref.getInt(getString(R.string.THEME_SHARED_PREFERENCE), R.style.Theme_HowFar)
        setTheme(theme)
        socketFunctions()
    }

    fun showCustomToast(message: String = "", viewRoot: View) {
        val view = layoutInflater.inflate(R.layout.info_pop_up, viewRoot.parent as ViewGroup, false)
        val param = ViewGroup.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        view.layoutParams = param
        (viewRoot.parent as ViewGroup).addView(view)
    }

    private fun socketFunctions() {
        //socket = application.socket
        //socket.connect()
        //socket.emit("online", auth!!.uid)
    }

    private fun checkAccount() {
        if (auth != null) {
            auth!!.reload().addOnSuccessListener {
                if (FirebaseAuth.getInstance().currentUser == null) {
                    Toast.makeText(this, "Account disabled/deleted", Toast.LENGTH_LONG).show()
                    val intent = Intent(this, LoginActivityActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(intent)
                    overridePendingTransition(R.anim.enter_right_to_left, R.anim.exit_right_to_left)
                }
            }
        }
    }

    fun playRing(res: Int = R.raw.short_success) {
        val uri = Uri.Builder()
            .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
            .authority(applicationContext.packageName)
            .path((res).toString())
            .build()
        val ringtone = RingtoneManager.getRingtone(applicationContext, uri)
        ringtone.play()
    }

    private fun onlinePresence() {
        handler.postDelayed(runnable, 1_000L)
    }

    private fun offlinePresence() {
        handler.removeCallbacks(runnable)
    }

    fun showSnackBar(root: View, msg: String) {
        Snackbar.make(root, msg, Snackbar.LENGTH_LONG).show()
    }

    fun closeAllNotification() {
        NotificationManagerCompat.from(this).cancelAll()
    }

    override fun onResume() {
        onlinePresence()
        super.onResume()
        checkAccount()
        val instance = WorkManager.getInstance(this)
        try {
            var isRunning = false
            val listenable = instance.getWorkInfosByTag("call and messages").get()
            for (workInfo in listenable) {
                var workState = workInfo.state
                isRunning = workState == WorkInfo.State.SUCCEEDED
                println("Work state *************************************** $workState")
            }
            //if (!isRunning) workManagerForNotification()
        } catch (e: Exception) {
        }
    }

    override fun onPause() {
        offlinePresence()
        super.onPause()
    }

    companion object {
        const val CHAT_REFERENCE = "chat_reference"
        const val ONLINE_PRESENCE = "online_presence"
    }
}