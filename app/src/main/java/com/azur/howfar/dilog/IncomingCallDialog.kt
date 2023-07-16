package com.azur.howfar.dilog

import android.Manifest
import android.app.AlertDialog
import android.app.PictureInPictureParams
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.*
import android.util.Rational
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.azur.howfar.R
import com.azur.howfar.databinding.IncomingCallDialogBinding
import com.azur.howfar.livedata.ValueEventLiveData
import com.azur.howfar.models.*
import com.azur.howfar.models.CallAnswerType.CANCELLED
import com.azur.howfar.models.CallAnswerType.ENDED
import com.azur.howfar.models.EventListenerType.onDataChange
import com.azur.howfar.utils.AgoraDownloadUtil
import com.azur.howfar.utils.CallUtils
import com.azur.howfar.utils.TimeUtils
import com.azur.howfar.utils.Util
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.gson.Gson
import io.agora.rtc2.*
import io.agora.rtc2.video.VideoCanvas
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.onEach
import okhttp3.*
import java.io.IOException

class IncomingCallDialog : AppCompatActivity(), View.OnClickListener {
    private val binding by lazy { IncomingCallDialogBinding.inflate(layoutInflater) }
    private val myAuth = FirebaseAuth.getInstance().currentUser!!.uid
    private val scope = CoroutineScope(Dispatchers.IO)
    private var callData: CallData = CallData()
    private lateinit var callUtils: CallUtils
    private var timeRef = FirebaseDatabase.getInstance().reference
    private var mp: MediaPlayer? = null
    private val appId = "80579fed1d5849f9afbe5c13ffa89000"
    private var uid = 0
    private var token = ""
    private var otherUid = ""
    private var isJoined = false
    private var agoraEngine: RtcEngine? = null
    private val REQUESTED_PERMISSIONS = arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
    private val permissionNames = arrayListOf("AUDIO", "CAMERA")
    private var tokenRole = Constants.CLIENT_ROLE_BROADCASTER
    private var meetingDuration = 0L
    private val serverUrl = "https://howfar.herokuapp.com"
    private val tokenExpireTime = 60000
    private lateinit var powerManager: PowerManager
    private lateinit var lock: PowerManager.WakeLock
    private lateinit var remoteSurfaceView: SurfaceView
    private lateinit var localSurfaceView: SurfaceView
    private val timeHandler = Handler(Looper.getMainLooper())
    private val waitingHandler = Handler(Looper.getMainLooper())
    private var isMuted = false
    private var isWebCamEnabled = true
    private var isInPictureInPicture = false
    private var userProfile = UserProfile()
    private var pipParams: PictureInPictureParams? = null
    private lateinit var agoraDownloadUtil: AgoraDownloadUtil

    init {
        timeRef = FirebaseDatabase.getInstance().reference.child("time").child(FirebaseAuth.getInstance().currentUser!!.uid)
        pipParams = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(10, 16)) // Portrait Aspect Ratio
            .build()
    }

    private fun callNotPicked(message: String = "Call not answered") = runOnUiThread {
        if (!isJoined) {
            callData.answerType = CallAnswerType.NO_RESPONSE
            FirebaseDatabase.getInstance().reference.child(CALL_REFERENCE).child(otherUid).setValue(callData).addOnSuccessListener {}
            FirebaseDatabase.getInstance().reference.child(CALL_REFERENCE).child(myAuth).setValue(callData).addOnSuccessListener {}
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            onBackPressed()
            stopRinging()
            Util.sendNotification(message = "Missed call", body = "You have missed call", receiverUid = otherUid, view = "missed call")
        }
    }

    private fun waitForCall() = runOnUiThread {
        //val callRef = FirebaseDatabase.getInstance().reference.child(CALL_REFERENCE).child(myAuth)
        val range = (0..70).toList().reversed()
        val flow = range.asSequence().asFlow().onEach { delay(1_000) }
        runBlocking {
            CoroutineScope(Dispatchers.IO).launch {
                flow.collect { f ->
                    if (f <= 1 && !this@IncomingCallDialog.isDestroyed && !isJoined) callNotPicked()
                }
            }
        }
    }

    private val runnable: Runnable = object : Runnable {
        override fun run() {
            if (meetingDuration >= 5_000 && !isJoined) {
                callData
                leaveInCall()
            }
            val time = TimeUtils.milliSecondsToTimer(meetingDuration)
            binding.videoTimerVoice.text = time
            binding.videoTimer.text = time
            meetingDuration += 1000L
            timeHandler.postDelayed(this, 1000)
        }
    }

    private fun initCall() {
        val callJson = intent.getStringExtra("callData")
        callData = Gson().fromJson(callJson, CallData::class.java)
        println("Call raw *********************************** $callData")
        otherUid = otherParticipant(callData.uids)
        requestAudioFocus()
        //val arch = Build.SUPPORTED_ABIS[0]
        //if (!agoraDownloadUtil.checkAgoraFiles(arch)) {
        //    agoraDownloadUtil.downloadFiles()
        //    onBackPressed()
        //    return
        //}
        //setAgoraLibPath(File("${this.filesDir}/AgoraDownload/Architecture/").path)

        when (callData.callType) {
            CallType.VOICE -> binding.callTypeImage.setImageResource(R.drawable.ic_phone)
            CallType.VIDEO -> binding.callTypeImage.setImageResource(R.drawable.videocamara)
        }
        when (callData.callType) {
            CallType.VOICE -> {
                setupVoiceSDKEngine()
                if (!lock.isHeld) lock.acquire(10 * 60 * 1000L)
            }
            CallType.VIDEO -> setupVideoSDKEngine()
        }
        waitForCall()
        when (callData.engagementType) {
            CallEngagementType.JOIN -> {
                startRinging()
                uid = 2
            }
            CallEngagementType.CREATE -> uid = 1
        }
        directMessageMethod()

        if (intent.hasExtra("callFrom")) {
            when (intent.getIntExtra("callFrom", 0)) {
                CallFromConstant.DIRECT -> directMessageMethod()
                CallFromConstant.SERVICE -> joinFromService(uid)
            }
        }
        binding.answerCall.setOnClickListener(this)
        binding.cancelCall.setOnClickListener(this)
        binding.outGoingCancelCall.setOnClickListener(this)
        binding.btnVoiceLeave.setOnClickListener(this)
        binding.btnLeave.setOnClickListener(this)
        binding.btnWebcam.setOnClickListener(this)
        binding.btnMic.setOnClickListener(this)
        setImage()
    }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        var notGranted = arrayListOf<String>()
        for ((index, per) in permissions.values.withIndex()) if (!per) notGranted.add(permissionNames[index])
        if (notGranted.isNotEmpty()) {
            var permissionsText = ""
            for (i in notGranted) permissionsText += "$i, "
            callUtils.permissionRationale(message = "HowFar needs $permissionsText permission to deliver good notification experience\nGrant app permission")
        } else initCall()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        agoraDownloadUtil = AgoraDownloadUtil(this)
        callUtils = CallUtils(this, this)
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        lock = powerManager.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "simplewakelock:wakelocktag")
        //window.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        permissionLauncher.launch(REQUESTED_PERMISSIONS)
    }

    private fun requestAudioFocus() {
        val audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setOnAudioFocusChangeListener {}.build()
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val result = am.requestAudioFocus(audioFocusRequest);

        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
        } else {
        }
    }

    override fun onBackPressed() {
        if (!isJoined) {
            leaveInCall()
        } else {
            val alertBuilder = AlertDialog.Builder(this)
            alertBuilder.setTitle("Exit call?")
            alertBuilder.setIcon(R.drawable.app_icon_sec)
            alertBuilder.setPositiveButton("Yes") { _, _ -> leaveInCall() }
            alertBuilder.setNegativeButton("No") { dialog, _ -> dialog.dismiss() }
            alertBuilder.create().show()
        }
    }

    override fun onUserLeaveHint() {
        if (isJoined) enterPictureInPictureMode(pipParams!!)
        super.onUserLeaveHint()
    }

    private fun pipMode() {
        if (isInPictureInPictureMode) {
            binding.localVideoViewContainer.visibility = View.GONE
            binding.videoBottomCard.visibility = View.GONE
            binding.btnVoiceLeave.visibility = View.GONE
        } else {
            binding.localVideoViewContainer.visibility = View.VISIBLE
            binding.videoBottomCard.visibility = View.VISIBLE
            binding.btnVoiceLeave.visibility = View.VISIBLE
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPictureInPicture = isInPictureInPictureMode
        pipMode()
    }

    override fun onResume() {
        super.onResume()
        pipMode()
    }

    override fun onPause() {
        super.onPause()
        if (isInPictureInPicture) {
            binding.localVideoViewContainer.visibility = View.GONE
            binding.videoBottomCard.visibility = View.GONE
            binding.btnVoiceLeave.visibility = View.GONE
            binding.callImage.setImageResource(R.drawable.call)
        } else {
            binding.localVideoViewContainer.visibility = View.VISIBLE
            binding.videoBottomCard.visibility = View.VISIBLE
            binding.btnVoiceLeave.visibility = View.VISIBLE
            if (!this.isDestroyed) Glide.with(this).load(userProfile.image).centerCrop().error(R.drawable.ic_avatar).into(binding.callImage)
        }
    }

    private fun joinFromService(uid: Int) {
        val options = ChannelMediaOptions()
        options.clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
        options.channelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION
        when (callData.callType) {
            CallType.VOICE -> options.autoSubscribeAudio = true
            CallType.VIDEO -> {
                showVideoRoot()
                agoraEngine!!.startPreview()
            }
        }
        agoraEngine!!.joinChannel(token, callData.channelName, uid, options)
    }

    private fun directMessageMethod() {
        when (callData.engagementType) {
            CallEngagementType.JOIN -> showCallRoot()
            CallEngagementType.CREATE -> {
                showGoingRoot()
                fetchToken(callData.channelName)
            }
        }
    }

    private fun setImage() {
        val otherUid = otherParticipant(callData.uids)
        FirebaseDatabase.getInstance().reference.child(USER_DETAILS).child(otherUid).get().addOnSuccessListener { profile ->
            if (profile.exists()) {
                userProfile = profile.getValue(UserProfile::class.java)!!
                try {
                    if (!this.isDestroyed) {
                        Glide.with(this).load(userProfile.image).centerCrop().into(binding.callerImage)
                        Glide.with(this).load(userProfile.image).centerCrop().into(binding.callImage)
                    }
                } catch (e: Exception) {
                }
                binding.callerName.text = userProfile.name
            }
        }
    }

    private fun setupVoiceSDKEngine() {
        try {
            val config = RtcEngineConfig()
            config.mContext = baseContext
            config.mAppId = appId
            config.mEventHandler = mRtcEventHandler
            agoraEngine = RtcEngine.create(config)
        } catch (e: java.lang.Exception) {
            throw RuntimeException("Check the error. ******************************************88 ${e.printStackTrace()}")
        }
    }

    private fun setupVideoSDKEngine() {
        try {
            val config = RtcEngineConfig()
            config.mContext = baseContext
            config.mAppId = appId
            config.mEventHandler = mRtcEventHandler
            agoraEngine = RtcEngine.create(config)
            // By default, the video module is disabled, call enableVideo to enable it.
            agoraEngine!!.enableVideo()
        } catch (e: java.lang.Exception) {
            showMessage(e.toString())
        }
    }

    private fun setupRemoteVideo(uid: Int) = runOnUiThread {
        remoteSurfaceView = SurfaceView(baseContext)
        remoteSurfaceView.visibility = View.VISIBLE
        binding.remoteVideoViewContainer.addView(remoteSurfaceView)
        agoraEngine!!.setupRemoteVideo(VideoCanvas(remoteSurfaceView, VideoCanvas.RENDER_MODE_HIDDEN, uid))
    }

    private fun setupLocalVideo() = runOnUiThread {
        localSurfaceView = SurfaceView(baseContext)
        localSurfaceView.visibility = View.VISIBLE
        localSurfaceView.setZOrderMediaOverlay(true)
        binding.localVideoViewContainer.addView(localSurfaceView)
        agoraEngine!!.setupLocalVideo(VideoCanvas(localSurfaceView, VideoCanvas.RENDER_MODE_HIDDEN, uid))
    }

    private val mRtcEventHandler: IRtcEngineEventHandler = object : IRtcEngineEventHandler() {
        override fun onTokenPrivilegeWillExpire(token: String?) {
            fetchToken(callData.channelName)
            super.onTokenPrivilegeWillExpire(token)
        }

        override fun onUserJoined(uid: Int, elapsed: Int) = runOnUiThread {
            isJoined = true
            setupRemoteVideo(uid)
            setupLocalVideo()
            stopRinging()
        }

        override fun onJoinChannelSuccess(channel: String, uid: Int, elapsed: Int) {
            timeHandler.postDelayed(runnable, 1000)
            stopRinging()
            when (callData.callType) {
                CallType.VOICE -> showVoiceRoot()
                CallType.VIDEO -> showVideoRoot()
            }
        }

        override fun onUserOffline(uid: Int, reason: Int) {
            if (isJoined) runOnUiThread {
                leaveInCall()
            }
        }

        override fun onLeaveChannel(stats: RtcStats) = runOnUiThread {
            leaveInCall()
            isJoined = false
        }
    }

    private fun leaveInCall() = runOnUiThread {
        val other = otherParticipant(callData.uids)
        FirebaseDatabase.getInstance().reference.child(CALL_REFERENCE).child(myAuth).setValue(callData).addOnSuccessListener {}
        FirebaseDatabase.getInstance().reference.child(CALL_REFERENCE).child(other).setValue(callData).addOnSuccessListener {}
        timeHandler.removeCallbacks(runnable)
        super.onBackPressed()
    }

    private fun clearAllViews() {
        binding.callRoot.visibility = View.GONE
        binding.outGoingRoot.visibility = View.GONE
        binding.voiceRoot.visibility = View.GONE
        binding.videoRoot.visibility = View.GONE
    }

    private fun showCallRoot() = runOnUiThread {
        clearAllViews()
        binding.callRoot.visibility = View.VISIBLE
    }

    private fun showGoingRoot() = runOnUiThread {
        clearAllViews()
        binding.outGoingRoot.visibility = View.VISIBLE
    }

    private fun showVoiceRoot() = runOnUiThread {
        clearAllViews()
        binding.voiceRoot.visibility = View.VISIBLE
    }

    private fun showVideoRoot() = runOnUiThread {
        clearAllViews()
        binding.videoRoot.visibility = View.VISIBLE
    }

    private fun showMessage(message: String) = runOnUiThread { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }

    private fun startRinging() {
        if (mp == null) mp = MediaPlayer.create(this, R.raw.ringing)
        if (mp!!.isPlaying) {
            mp!!.stop()
            mp!!.prepare()
            mp!!.seekTo(0)
        } else {
            mp!!.isLooping = true
            mp!!.start()
        }
    }

    private fun stopRinging() {
        mp = if (mp != null && mp!!.isPlaying) {
            mp!!.stop()
            mp!!.release()
            null
        } else null
    }

    private fun otherParticipant(participants: ArrayList<String>): String {
        for (i in participants) return if (i != myAuth) i else participants[1]
        return ""
    }

    private fun cantConnect() {
        runOnUiThread {
            try {
                onBackPressed()
                Toast.makeText(this, "Cannot connect", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
            }
        }
    }

    private fun setToken(newValue: String) = try {
        token = newValue
        if (!isJoined) {
            val options = ChannelMediaOptions()
            options.clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
            options.channelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION
            when (callData.callType) {
                CallType.VOICE -> options.autoSubscribeAudio = true
                CallType.VIDEO -> {
                    agoraEngine!!.enableLocalVideo(true)
                    agoraEngine!!.startPreview()
                }
            }
            if (uid == 1) {
                timeRef.setValue(ServerValue.TIMESTAMP).addOnSuccessListener {
                    timeRef.get().addOnSuccessListener { time ->
                        if (time.exists()) {
                            val callServerTime = time.value.toString()
                            var call = callData
                            call.timeCalled = callServerTime
                            val historyRef = FirebaseDatabase.getInstance().reference.child(CALL_HISTORY)

                            val myCallerHistory = historyRef.child(myAuth)
                            val otherCallerHistory = historyRef.child(otherParticipant(callData.uids))
                            val rawReceivedTime = time.value.toString()
                            val callHistoryData = CallHistoryData(callTime = rawReceivedTime, callData.uids, callType = callData.callType)
                            myCallerHistory.child(rawReceivedTime).setValue(callHistoryData).addOnSuccessListener {}
                            val otherCallHistoryData = CallHistoryData(
                                callTime = rawReceivedTime, callData.uids, callType = callData.callType,
                                engagementType = CallEngagementType.JOIN,
                                answerType = CallAnswerType.RECEIVED
                            )
                            otherCallerHistory.child(rawReceivedTime).setValue(otherCallHistoryData).addOnSuccessListener {}

                            var callAccepted = false
                            val myCallRef = FirebaseDatabase.getInstance().reference.child(CALL_REFERENCE).child(myAuth)
                            val receiverCall = FirebaseDatabase.getInstance().reference.child(CALL_REFERENCE).child(otherParticipant(call.uids))
                            myCallRef.setValue(call).addOnSuccessListener {
                                call.engagementType = CallEngagementType.JOIN
                                receiverCall.setValue(call).addOnSuccessListener {
                                    Util.sendNotification(
                                        message = "Incoming call", body = "Call", data = Gson().toJson(callData),
                                        receiverUid = otherParticipant(call.uids),
                                        view = "call incoming",
                                    )
                                    val callDataLiveData = ValueEventLiveData(myCallRef)
                                    if (!callAccepted) callDataLiveData.observe(this) {
                                        when (it.second) {
                                            onDataChange -> {
                                                val listenerCallData = it.first.getValue(CallData::class.java)!!
                                                when (listenerCallData.answerType) {
                                                    CallAnswerType.AWAITING -> {
                                                        startRinging()
                                                    }
                                                    CANCELLED -> {
                                                        onBackPressed()
                                                        myCallRef.removeValue().addOnSuccessListener {}
                                                    }
                                                    CallAnswerType.ANSWERED -> {
                                                        callAccepted = true
                                                        agoraEngine!!.joinChannel(token, call.channelName, uid, options)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }.addOnFailureListener { cantConnect() }
                            }.addOnFailureListener { cantConnect() }
                        }
                    }.addOnFailureListener { cantConnect() }
                }.addOnFailureListener { cantConnect() }
            } else {
                agoraEngine!!.joinChannel(token, callData.channelName, 2, options)
                val myCallerDetails = FirebaseDatabase.getInstance().reference.child(CALL_REFERENCE).child(myAuth)
                val callerCallDetails = FirebaseDatabase.getInstance().reference.child(CALL_REFERENCE).child(otherParticipant(callData.uids))
                callData.answerType = CallAnswerType.ANSWERED
                myCallerDetails.setValue(callData).addOnSuccessListener {
                    callerCallDetails.setValue(callData).addOnSuccessListener {
                    }.addOnFailureListener {
                        //Snackbar.make(binding.root, "Cannot connect.", Snackbar.LENGTH_LONG).show()
                        onBackPressed()
                    }
                }.addOnFailureListener {
                    //Snackbar.make(binding.root, "Cannot connect.", Snackbar.LENGTH_LONG).show()
                    onBackPressed()
                }
            }
        } else {
            agoraEngine!!.renewToken(token)
            showMessage("Token renewed")
        }
    } catch (e: Exception) {
        println("Error setToken ******************************** ${e.printStackTrace()}")
    }

    var trialCount = 0
    private fun fetchToken(channelName: String) {
        if (uid == 1) runOnUiThread {
            binding.callerBackground.visibility = View.GONE
            binding.incoming.setTextColor(Color.WHITE)
            binding.incoming.text = "Connecting..."
            binding.callerName.setTextColor(Color.WHITE)
        }
        tokenRole = Constants.CLIENT_ROLE_BROADCASTER
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
                if (trialCount < 3) {
                    fetchToken(channelName)
                    trialCount++
                } else {
                    showMessage("Can't connect. Try again")
                    println("IOExecption ********************************* ${e.message}")
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
                    runOnUiThread { binding.textView4.text = "Ringing..." }
                }
            }
        })
    }

    override fun onDestroy() {
        if (lock.isHeld) lock.release()
        stopRinging()
        //window.clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        // Destroy the engine in a sub-thread to avoid congestion
        Thread {
            RtcEngine.destroy()
            agoraEngine = null
        }.start()
        timeHandler.removeCallbacks(runnable)
        super.onDestroy()
    }

    companion object {
        const val CALL_REFERENCE = "call_reference"
        const val CALL_HISTORY = "call_history"
        const val USER_DETAILS = "user_details"
        const val TRANSFER_HISTORY = "user_coins_transfer"
        const val CHAT_REFERENCE = "chat_reference"
        const val IMAGE_REFERENCE = "image_reference"
        const val PERMISSION_REQ_ID = 22
        val REQUESTED_PERMISSIONS = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
        )
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.answer_call -> {
                binding.callerBackground.visibility = View.GONE
                binding.incoming.setTextColor(Color.WHITE)
                binding.incoming.text = "Connecting..."
                binding.callerName.setTextColor(Color.WHITE)
                fetchToken(callData.channelName)
                stopRinging()
            }
            R.id.cancel_call -> {
                callData.answerType = CANCELLED
                leaveInCall()
            }
            R.id.out_going_cancel_call -> {
                callData.answerType = CANCELLED
                leaveInCall()
            }
            R.id.btnVoiceLeave -> {
                callData.answerType = ENDED
                leaveInCall()
            }
            R.id.btnLeave -> {
                callData.answerType = ENDED
                leaveInCall()
            }
            R.id.btnWebcam -> {
                if (isWebCamEnabled) {
                    binding.btnWebcam.setImageResource(R.drawable.ic_video_off)
                    agoraEngine!!.muteLocalVideoStream(!isWebCamEnabled)
                } else {
                    binding.btnWebcam.setImageResource(R.drawable.ic_video_on)
                    agoraEngine!!.muteLocalVideoStream(!isWebCamEnabled)
                }
                isWebCamEnabled = !isWebCamEnabled
            }
            R.id.btnMic -> {
                if (!isMuted) {
                    agoraEngine!!.adjustAudioMixingVolume(0)
                    agoraEngine!!.adjustPlaybackSignalVolume(0)
                    binding.btnMic.setImageResource(R.drawable.ic_mic_off)
                } else {
                    agoraEngine!!.adjustAudioMixingVolume(100)
                    agoraEngine!!.adjustPlaybackSignalVolume(100)
                    binding.btnMic.setImageResource(R.drawable.ic_mic_on)
                }
                isMuted = !isMuted
            }
        }
    }
}

object CallFromConstant {
    const val DIRECT = 0
    const val SERVICE = 1
}