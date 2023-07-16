package com.azur.howfar.livestreamming

import android.Manifest
import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Rational
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.azur.howfar.R
import com.azur.howfar.databinding.ActivityHostLiveBinding
import com.azur.howfar.livedata.ChildEventLiveData
import com.azur.howfar.livedata.ValueEventLiveData
import com.azur.howfar.models.*
import com.azur.howfar.models.EventListenerType.onChildAdded
import com.azur.howfar.models.EventListenerType.onDataChange
import com.azur.howfar.retrofit.Const
import com.azur.howfar.utils.CallUtils
import com.azur.howfar.workManger.HowFarAnalyticsTypes
import com.azur.howfar.workManger.OpenAppWorkManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.gif.GifDrawable
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.gson.Gson
import io.agora.rtc2.*
import io.agora.rtc2.video.VideoCanvas
import okhttp3.*
import java.io.IOException

class HostLiveActivity : AppCompatActivity(), View.OnClickListener {
    private val binding by lazy { ActivityHostLiveBinding.inflate(layoutInflater) }
    private val myAuth = FirebaseAuth.getInstance().currentUser!!.uid
    private var lovesList = arrayListOf<MomentDetails>()
    private var commentList: ArrayList<BroadcastCommentData> = arrayListOf()
    private var participants: ArrayList<String> = arrayListOf()
    private var liveStreamCommentAdapter = LiveStreamCommentAdapter()
    private var viewersAdapter = ViewersAdapter()
    var handler = Handler(Looper.getMainLooper())
    private var isJoined = false
    private var token = ""
    private var isInPictureInPicture = false
    private val appId = "80579fed1d5849f9afbe5c13ffa89000"
    private lateinit var callUtils: CallUtils
    private var uid = 1
    private var pipParams: PictureInPictureParams? = null
    private val permissionNames = arrayListOf("AUDIO", "CAMERA")
    private val serverUrl = "https://howfar.herokuapp.com"
    private val tokenExpireTime = 60000
    private var timeRef = FirebaseDatabase.getInstance().reference
    private var broadcastCallData = BroadcastCallData()
    private lateinit var localSurfaceView: SurfaceView
    private var agoraEngine: RtcEngine? = null
    private var userProfile = UserProfile()
    private val timeHandler = Handler(Looper.getMainLooper())
    private var broadcastTime = 0
    val onlineHandler = Handler(Looper.getMainLooper())
    var onlineRunnable = object : Runnable {
        override fun run() {
            val ref = FirebaseDatabase.getInstance().reference
                .child(LIVE_PRESENCE)
                .child(broadcastCallData.callerUid /*Channel name*/)
                .child(broadcastCallData.timeCalled)
                .child(myAuth)
            timeRef.setValue(ServerValue.TIMESTAMP).addOnSuccessListener {
                timeRef.get().addOnSuccessListener { if (it.exists()) ref.setValue(OnlinePresenceData(uid = myAuth, lastTimePosted = it.value.toString())) }
            }
            onlineHandler.postDelayed(this, 10_000)
        }
    }
    private var workManager = WorkManager.getInstance(this)

    val runnable = object : Runnable {
        override fun run() {
            timeHandler.postDelayed(this, 1000)
            broadcastTime += 1000
        }
    }

    init {
        timeRef = FirebaseDatabase.getInstance().reference.child("time").child(FirebaseAuth.getInstance().currentUser!!.uid)
        pipParams = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(10, 16)) /*Portrait Aspect Ratio*/
            .build()
    }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        var notGranted = arrayListOf<String>()
        for ((index, per) in permissions.values.withIndex()) if (!per) notGranted.add(permissionNames[index])
        if (notGranted.isNotEmpty()) {
            var permissionsText = ""
            for (i in notGranted) permissionsText += "$i, "
            callUtils.permissionRationale(message = "HowFar needs $permissionsText permission to deliver good notification experience\nGrant app permission")
            finish()
        } else {
            setupVoiceSDKEngine()
            setupLocalVideo()
            observeComments()
            fetchToken(broadcastCallData.channelName)
            FirebaseDatabase.getInstance().reference.child(USER_DETAILS).child(myAuth).get().addOnSuccessListener {
                if (it.exists()) userProfile = it.getValue(UserProfile::class.java)!!
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        binding.imgfilterclose.setOnClickListener(this)
        binding.btnsend.setOnClickListener(this)
        binding.btnClose.setOnClickListener(this)
        val json = intent.getStringExtra("data")
        broadcastCallData = Gson().fromJson(json, BroadcastCallData::class.java)
        if (broadcastCallData.isPrivate) {
            binding.imgLock.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.lock))
            binding.tvPrivacy.text = "Private"
        } else {
            binding.imgLock.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.unlock))
            binding.tvPrivacy.text = "Public"
        }
        callUtils = CallUtils(this, this)
        permissionLauncher.launch(REQUESTED_PERMISSIONS)

        liveStreamCommentAdapter.comments = commentList
        binding.rvComments.adapter = liveStreamCommentAdapter
        binding.rvComments.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)

        viewersAdapter.dataset = participants
        viewersAdapter.activity = this
        binding.rvViewUsers.adapter = viewersAdapter
    }

    override fun onResume() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        pip()
        super.onResume()
    }

    override fun onPause() {
        pip()
        super.onPause()
    }

    private fun pip() {
        if (isInPictureInPictureMode) {
            binding.rvViewUsers.visibility = View.GONE
            binding.coinsSegment.visibility = View.GONE
            binding.lytPrivacy.visibility = View.GONE
            binding.lytFilterFunctions.visibility = View.GONE
            binding.userActions.visibility = View.GONE
            binding.lytFilters.visibility = View.GONE
            binding.lytviewcount.visibility = View.GONE
            binding.btnClose.visibility = View.GONE
            binding.lytbuttons.visibility = View.GONE
            binding.rvComments.visibility = View.GONE
        } else {
            binding.rvViewUsers.visibility = View.VISIBLE
            binding.coinsSegment.visibility = View.VISIBLE
            binding.lytPrivacy.visibility = View.VISIBLE
            binding.lytFilterFunctions.visibility = View.VISIBLE
            binding.userActions.visibility = View.VISIBLE
            //binding.lytFilters.visibility = View.VISIBLE
            binding.lytviewcount.visibility = View.VISIBLE
            binding.btnClose.visibility = View.VISIBLE
            binding.lytbuttons.visibility = View.VISIBLE
            binding.rvComments.visibility = View.VISIBLE
        }
    }

    private fun activeUserAnalytics() {
        val workRequest = OneTimeWorkRequestBuilder<OpenAppWorkManager>().addTag("analytics")
            .setInputData(workDataOf("action" to HowFarAnalyticsTypes.GO_LIVE))
            .build()
        workManager.enqueue(workRequest)
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun observeComments() {
        val commentHistory = FirebaseDatabase.getInstance().reference.child(BROADCAST_COMMENT_HISTORY).child(myAuth).child(broadcastCallData.timeCalled)
        ChildEventLiveData(commentHistory).observe(this) {
            when (it.second) {
                onChildAdded -> {
                    val comments = it.first.getValue(BroadcastCommentData::class.java)!!
                    if (comments !in commentList) {
                        commentList.add(comments)
                        liveStreamCommentAdapter.notifyItemInserted(commentList.size)
                        binding.rvComments.smoothScrollToPosition(commentList.size)
                    }
                    for (i in commentList) {
                        if (i.isJoined && i.comment == "" && i.uid !in participants) {
                            participants.add(i.uid)
                            viewersAdapter.notifyItemInserted(participants.size)
                        }
                        if (!i.isJoined && i.comment == "") {
                            participants.remove(i.uid)
                            viewersAdapter.notifyDataSetChanged()
                        }
                    }
                    binding.viewersCount.text = participants.size.toString()
                }
            }
        }
    }

    private fun sendInvites() {
        for ((index, i) in broadcastCallData.uids.withIndex()) {
            val eligibleUser = FirebaseDatabase.getInstance().reference.child(LIVE_BROADCAST).child(i).child(myAuth)
            broadcastCallData.index = index
            eligibleUser.setValue(broadcastCallData)
        }
    }

    private fun setupVoiceSDKEngine() {
        try {
            val config = RtcEngineConfig()
            config.mContext = baseContext
            config.mAppId = appId
            config.mEventHandler = mRtcEventHandler
            agoraEngine = RtcEngine.create(config)
            agoraEngine!!.enableVideo()
            agoraEngine!!.enableAudio()
        } catch (e: java.lang.Exception) {
            throw RuntimeException("Check the error.")
        }
    }

    private fun setupLocalVideo() = runOnUiThread {
        localSurfaceView = SurfaceView(baseContext)
        localSurfaceView.visibility = View.VISIBLE
        localSurfaceView.setZOrderMediaOverlay(true)
        binding.myVideoViewContainer.addView(localSurfaceView)
        agoraEngine!!.setupLocalVideo(VideoCanvas(localSurfaceView, VideoCanvas.RENDER_MODE_HIDDEN, 1))
    }

    private val mRtcEventHandler: IRtcEngineEventHandler = object : IRtcEngineEventHandler() {
        override fun onTokenPrivilegeWillExpire(token: String?) {
            fetchToken(broadcastCallData.channelName)
            super.onTokenPrivilegeWillExpire(token)
        }

        override fun onUserJoined(uid: Int, elapsed: Int) = runOnUiThread {
            showMessage("A user joined")
        }

        @SuppressLint("SetTextI18n")
        override fun onJoinChannelSuccess(channel: String, uid: Int, elapsed: Int) = runOnUiThread {
            activeUserAnalytics()
            isJoined = true
            binding.progressBanner.visibility = View.GONE

            onlineHandler.postDelayed(onlineRunnable, 500)
            timeHandler.postDelayed(runnable, 1000)
            val ref = FirebaseDatabase.getInstance().reference.child(LIVE_BROADCAST_CREATOR).child(myAuth)
            ref.setValue(broadcastCallData).addOnSuccessListener {
                if (broadcastCallData.uids.isNotEmpty()) sendInvites()
                // TODO: Subscribe to the live broadcast topic here
            }.addOnFailureListener { Snackbar.make(binding.root, "Unable to go live", Snackbar.LENGTH_INDEFINITE).show() }

            val loves = FirebaseDatabase.getInstance()
                .reference.child(BROADCAST_MOMENT_DATA)
                .child(broadcastCallData.callerUid)
                .child(broadcastCallData.timeCalled)
            ValueEventLiveData(loves).observe(this@HostLiveActivity) {
                var likeCount = 0
                var loveCount = 0
                when (it.second) {
                    onDataChange -> for (i in it.first.children) {
                        val moment = i.getValue(MomentDetails::class.java)!!
                        if (moment !in lovesList) {
                            lovesList.add(moment)
                        }
                        if (moment.likes.profileUid != "") {
                            likeCount++
                            Glide.with(this@HostLiveActivity).asGif().load(R.drawable.like_gif).listener(object : RequestListener<GifDrawable> {
                                override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<GifDrawable>?, isFirstResource: Boolean): Boolean {
                                    return false
                                }

                                override fun onResourceReady(
                                    resource: GifDrawable?,
                                    model: Any?,
                                    target: Target<GifDrawable>?,
                                    dataSource: DataSource?,
                                    isFirstResource: Boolean
                                ): Boolean {
                                    resource?.setLoopCount(1)
                                    return false
                                }
                            }).into(binding.loveGif)
                        } else if (moment.loves.profileUid != "") {
                            loveCount++
                            Glide.with(this@HostLiveActivity).asGif().load(R.drawable.love_gif).listener(object : RequestListener<GifDrawable> {
                                override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<GifDrawable>?, isFirstResource: Boolean): Boolean {
                                    return false
                                }

                                override fun onResourceReady(
                                    resource: GifDrawable?,
                                    model: Any?,
                                    target: Target<GifDrawable>?,
                                    dataSource: DataSource?,
                                    isFirstResource: Boolean
                                ): Boolean {
                                    resource?.setLoopCount(1)
                                    return false
                                }
                            }).into(binding.loveGif)
                        }
                    }
                }
                binding.tvCoins.text = (likeCount * Const.LIKE_VALUE + loveCount * Const.LOVE_VALUE).toString()
            }
        }

        override fun onUserOffline(uid: Int, reason: Int) = runOnUiThread {
            if (isJoined) showMessage("Other user left.")
        }

        override fun onLeaveChannel(stats: RtcStats) {
            leaveInCall()
            runOnUiThread { showMessage("You left.") }
        }
    }

    private fun setToken(newValue: String) {
        token = newValue
        if (!isJoined) {
            val options = ChannelMediaOptions()
            options.clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
            options.channelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION
            agoraEngine!!.enableLocalVideo(true)
            agoraEngine!!.startPreview()
            val history = FirebaseDatabase.getInstance().reference.child(BROADCAST_HISTORY).child(myAuth).child(broadcastCallData.timeCalled)
            history.setValue(broadcastCallData).addOnSuccessListener {}
            agoraEngine!!.joinChannel(token, broadcastCallData.channelName, uid, options)

        } else {
            agoraEngine!!.renewToken(token)
            showMessage("Token renewed")
        }
    }

    private var retries = 0
    private fun fetchToken(channelName: String) {
        val tokenRole = Constants.CLIENT_ROLE_BROADCASTER
        val uRLString: String = ("$serverUrl/rtc/$channelName/$tokenRole/uid/$uid/?expiry=$tokenExpireTime")
        val client = OkHttpClient()
        val request: Request = Request.Builder()
            .url(uRLString)
            .header("Content-Type", "application/json; charset=UTF-8")
            .get()
            .build()
        val call: Call = client.newCall(request)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                println("onFailure ********************************* $e")
                if (retries < 3) {
                    fetchToken(channelName)
                    retries++
                } else {
                    val snack = Snackbar.make(binding.root, "Can't connect. Retry", Snackbar.LENGTH_INDEFINITE)
                    snack.setAction("Retry") { recreate() }
                    snack.show()
                    println("IOExecption ********************************* $e")
                }
            }

            @Throws(IOException::class)
            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val gson = Gson()
                    val result: String = response.body!!.string()
                    val map = gson.fromJson<Map<*, *>>(result, MutableMap::class.java)
                    val _token = map["rtcToken"].toString()
                    setToken(_token)
                }
            }
        })
    }

    fun showMessage(message: String) = runOnUiThread { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }

    fun onSwitchCameraClicked(view: View?) {
        agoraEngine!!.switchCamera()
    }

    fun onClickEmojiIcon(view: View?) {
        Log.d(TAG, "onClickEmojiIcon:1 ")
    }

    fun onLocalAudioMuteClicked(view: View?) {
        //if(!agoraEngine!!.isSpeakerphoneEnabled) agoraEngine!!.enableAudio() else agoraEngine!!.disableAudio()
        Log.d(TAG, "onClickEmojiIcon: ")
    }

    fun onclickGiftIcon(view: View?) {
    }

    override fun onDestroy() {
        leaveInCall()
        super.onDestroy()
    }

    fun onclickShare(view: View?) {
        Log.d(TAG, "onClickEmojiIcon: ")
    }

    private fun endBroadcast(msg: String = "Are you sure you want to end broadcast session?") {
        val alertDialog = AlertDialog.Builder(this)
        alertDialog.setTitle("End session?")
        alertDialog.setMessage(msg)
        alertDialog.setPositiveButton("Ok") { dialog, _ -> leaveInCall() }
        alertDialog.setNegativeButton("Continue broadcast") { dialog, _ -> dialog.dismiss() }
        alertDialog.create().show()
    }

    private fun leaveCallExt() = runOnUiThread {
        var likeCount = 0
        var loveCount = 0
        for (i in lovesList) {
            if (i.likes.profileUid != "") likeCount++
            else if (i.loves.profileUid != "") loveCount++
        }
        val coinCount = likeCount * Const.LIKE_VALUE + loveCount * Const.LOVE_VALUE
        agoraEngine!!.enableLocalVideo(false)
        broadcastCallData.answerType = CallAnswerType.ENDED
        FirebaseDatabase.getInstance().reference.child(LIVE_BROADCAST_CREATOR).child(myAuth).setValue(broadcastCallData)
        // TODO: Unsubscribe from the live broadcast topic here
        for (i in broadcastCallData.uids) FirebaseDatabase.getInstance().reference.child(LIVE_BROADCAST).child(i).child(myAuth).setValue(broadcastCallData)
        val intent = Intent(this, LiveSummaryActivity::class.java)
        val json = Gson().toJson(broadcastCallData)
        val jsonUserProfile = Gson().toJson(userProfile)
        intent.putExtra("comments", commentList.size)
        intent.putExtra("profile", jsonUserProfile)
        intent.putExtra("coinCount", coinCount)
        intent.putExtra("broadcastTime", broadcastTime)
        intent.putExtra("data", json)
        finish()
        startActivity(intent)
        handler.removeCallbacks(runnable)
        onlineHandler.removeCallbacks(onlineRunnable)
    }

    private fun leaveInCall() = runOnUiThread {
        if (agoraEngine != null) agoraEngine!!.stopPreview()
        if (isJoined) {
            isJoined = false
            agoraEngine!!.leaveChannel()
            Thread {
                RtcEngine.destroy()
                agoraEngine = null
            }.start()
            leaveCallExt()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (!isJoined) super.onBackPressed() else endBroadcast()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isJoined) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try {
                    enterPictureInPictureMode(pipParams!!)
                } catch (e: Exception) {
                }
            } else try {
                enterPictureInPictureMode(pipParams!!)
            } catch (e: Exception) {
            }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPictureInPicture = isInPictureInPictureMode
        pip()
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.btnsend -> {
                val commentHistory = FirebaseDatabase.getInstance().reference.child(BROADCAST_COMMENT_HISTORY).child(myAuth).child(broadcastCallData.timeCalled)
                if (binding.etComment.text.isEmpty()) return
                timeRef.setValue(ServerValue.TIMESTAMP).addOnSuccessListener {
                    timeRef.get().addOnSuccessListener { t ->
                        val timeSent = t.value.toString()
                        val cmt = binding.etComment.text.trim().toString()
                        val comment = BroadcastCommentData(uid = myAuth, comment = cmt, timeSent = timeSent, user = userProfile)
                        commentHistory.child(timeSent).setValue(comment)
                        binding.etComment.setText("")
                    }
                }
            }
            R.id.btnClose -> if (isJoined) endBroadcast() else onBackPressed()
            R.id.imgfilterclose -> binding.lytFilters.visibility = View.GONE
        }
    }

    companion object {
        const val TAG = "hostliveactivity"
        const val CALL_REFERENCE = "call_reference"
        const val BROADCAST_HISTORY = "broadcast_history"
        const val BROADCAST_COMMENT_HISTORY = "broadcast_comment_history"
        const val USER_DETAILS = "user_details"
        const val TRANSFER_HISTORY = "user_coins_transfer"
        const val LIVE_BROADCAST = "live_broadcast"
        const val LIVE_BROADCAST_CREATOR = "live_broadcast_creator"
        const val LIVE_PRESENCE = "LIVE_PRESENCE"
        const val BROADCAST_MOMENT_DATA = "BROADCAST_MOMENT_DATA"
        val REQUESTED_PERMISSIONS = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
        )
    }
}