package com.azur.howfar.livestreamming

import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.res.Configuration
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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.azur.howfar.R
import com.azur.howfar.databinding.ActivityWatchLiveBinding
import com.azur.howfar.livedata.ChildEventLiveData
import com.azur.howfar.livedata.ValueEventLiveData
import com.azur.howfar.models.*
import com.azur.howfar.models.EventListenerType.onChildAdded
import com.azur.howfar.models.EventListenerType.onDataChange
import com.azur.howfar.posts.FeedAdapter
import com.azur.howfar.retrofit.Const
import com.azur.howfar.retrofit.Const.LIKE_VALUE
import com.azur.howfar.retrofit.Const.LOVE_VALUE
import com.azur.howfar.user.wallet.MyWalletActivity
import com.azur.howfar.utils.CallUtils
import com.azur.howfar.utils.HFCoinUtils
import com.azur.howfar.utils.HFCoinUtils.checkBalance
import com.azur.howfar.utils.TimeUtils
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


class WatchLiveActivity : AppCompatActivity(), View.OnClickListener {
    private val binding by lazy { ActivityWatchLiveBinding.inflate(layoutInflater) }
    var handler = Handler(Looper.getMainLooper())
    private var commentList: ArrayList<BroadcastCommentData> = arrayListOf()
    private var liveStreamCommentAdapter = LiveStreamCommentAdapter()
    private var viewersAdapter = ViewersAdapter()
    private val myAuth = FirebaseAuth.getInstance().currentUser!!.uid
    private var isJoined = false
    private var token = ""
    private var isInPictureInPicture = false
    private val appId = "80579fed1d5849f9afbe5c13ffa89000"
    private lateinit var callUtils: CallUtils
    private var participants: ArrayList<String> = arrayListOf()
    private var uid = 0
    private var pipParams: PictureInPictureParams? = null
    private val permissionNames = arrayListOf("AUDIO", "CAMERA")
    private val serverUrl = "https://howfar.herokuapp.com"
    private var userStr = ""
    private val tokenExpireTime = 60000
    private var timeRef = FirebaseDatabase.getInstance().reference
    private var broadcastCallData = BroadcastCallData()
    private lateinit var localSurfaceView: SurfaceView
    private var agoraEngine: RtcEngine? = null
    private var userProfile = UserProfile()
    private var broadcasterProfile = UserProfile()
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
    private val workManager = WorkManager.getInstance(this)

    init {
        timeRef = FirebaseDatabase.getInstance().reference.child("time").child(FirebaseAuth.getInstance().currentUser!!.uid)
        pipParams = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(10, 16)) // Portrait Aspect Ratio
            .build()
    }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        val notGranted = arrayListOf<String>()
        for ((index, per) in permissions.values.withIndex()) if (!per) notGranted.add(permissionNames[index])
        if (notGranted.isNotEmpty()) {
            var permissionsText = ""
            for (i in notGranted) permissionsText += "$i, "
            callUtils.permissionRationale(message = "HowFar needs $permissionsText permission to deliver good notification experience\nGrant app permission")
            finish()
        } else {
            userStr = intent.getStringExtra(Const.USER_STR)!!
            val ref = FirebaseDatabase.getInstance().reference.child(LIVE_BROADCAST_CREATOR).child(userStr)
            ref.get().addOnSuccessListener {
                if (it.exists()) {
                    broadcastCallData = it.getValue(BroadcastCallData::class.java)!!
                    val onlineRef = FirebaseDatabase.getInstance().reference
                        .child(LiveListFragment.LIVE_PRESENCE)
                        .child(broadcastCallData.callerUid)
                        .child(broadcastCallData.timeCalled)
                    onlineRef.get().addOnSuccessListener { onlineSnapshot ->
                        val onlineParticipantsTime = arrayListOf<String>()
                        if (onlineSnapshot.exists()) {
                            for (i in onlineSnapshot.children) {
                                val presenceData = i.getValue(OnlinePresenceData::class.java)!!
                                if (presenceData.lastTimePosted !in onlineParticipantsTime) onlineParticipantsTime.add(presenceData.lastTimePosted)
                            }
                            onlineParticipantsTime.sortWith(compareByDescending { t -> t })
                            val timeRef = FirebaseDatabase.getInstance().reference.child("time").child(myAuth)
                            timeRef.get().addOnSuccessListener { time ->
                                if (time.exists()) {
                                    val diff = TimeUtils.timeDiff(onlineParticipantsTime.first(), time.value.toString())
                                    if (diff <= 120) {
                                        setupVoiceSDKEngine()
                                        fetchToken(broadcastCallData.channelName)
                                    } else {
                                        broadcastCallData.answerType = CallAnswerType.ENDED
                                        ref.setValue(broadcastCallData).addOnSuccessListener {
                                            Toast.makeText(this, "Broadcast ended", Toast.LENGTH_LONG).show()
                                            finish()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            FirebaseDatabase.getInstance().reference.child(USER_DETAILS).child(myAuth).get().addOnSuccessListener {
                if (it.exists()) userProfile = it.getValue(UserProfile::class.java)!!
            }
            FirebaseDatabase.getInstance().reference.child(USER_DETAILS).child(userStr).get().addOnSuccessListener {
                if (it.exists()) {
                    try {
                        broadcasterProfile = it.getValue(UserProfile::class.java)!!
                        binding.tvName.text = broadcasterProfile.name
                        Glide.with(this).load(broadcasterProfile.image).into(binding.imgProfile)
                    } catch (e: Exception) {
                    }
                }
            }

            /*var lovesList = arrayListOf<MomentDetails>()
            val loves = FirebaseDatabase.getInstance()
                .reference.child(BROADCAST_MOMENT_DATA)
                .child(broadcastCallData.callerUid)
                .child(broadcastCallData.timeCalled)
            ValueEventLiveData(loves).observe(this) {
                when (it.second) {
                    onDataChange -> for (i in it.first.children) {
                        val moment = i.getValue(MomentDetails::class.java)!!
                        if (moment !in lovesList) lovesList.add(moment)
                    }
                }
                binding.tvLoves.text = lovesList.size.toString()
                binding.tvCoins.text = (lovesList.size * LOVE_VALUE).toString()
            }*/
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        binding.btnsend.setOnClickListener(this)
        binding.lovesButton.setOnClickListener(this)
        binding.likeButton.setOnClickListener(this)
        callUtils = CallUtils(this, this)
        val userStr = intent.getStringExtra(Const.USER_STR)
        permissionLauncher.launch(HostLiveActivity.REQUESTED_PERMISSIONS)
        if (userStr != null && userStr.isNotEmpty()) {
            liveStreamCommentAdapter.comments = commentList
            binding.rvComments.adapter = liveStreamCommentAdapter
            binding.rvComments.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
            viewersAdapter.dataset = participants
            viewersAdapter.activity = this
            binding.rvViewUsers.adapter = viewersAdapter
        }
    }

    override fun onResume() {
        super.onResume()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        pip()
    }

    override fun onPause() {
        super.onPause()
        pip()
    }

    private fun pip() {
        if (isInPictureInPictureMode) {
            binding.rvViewUsers.visibility = View.GONE
            binding.profileRoot.visibility = View.GONE
            binding.tvUserId.visibility = View.GONE
            //binding.tvCoinLayout.visibility = View.GONE
            binding.rvComments.visibility = View.GONE
            binding.lytBottomBar.visibility = View.GONE
        } else {
            binding.rvViewUsers.visibility = View.VISIBLE
            binding.profileRoot.visibility = View.VISIBLE
            binding.tvUserId.visibility = View.VISIBLE
            //binding.tvCoinLayout.visibility = View.VISIBLE
            binding.rvComments.visibility = View.VISIBLE
            binding.lytBottomBar.visibility = View.VISIBLE
        }
    }

    private fun endBroadcast(msg: String = "Are you sure you want to end broadcast session?") {
        val alertDialog = AlertDialog.Builder(this)
        alertDialog.setTitle("End session?")
        alertDialog.setMessage(msg)
        alertDialog.setPositiveButton("Ok") { dialog, _ -> leaveInCall() }
        alertDialog.setNegativeButton("Continue broadcast") { dialog, _ -> dialog.dismiss() }
        alertDialog.create().show()
    }

    private fun activeUserAnalytics() {
        val workRequest = OneTimeWorkRequestBuilder<OpenAppWorkManager>().addTag("analytics")
            .setInputData(workDataOf("action" to HowFarAnalyticsTypes.WATCH_LIVE))
            .build()
        workManager.enqueue(workRequest)
    }

    private fun leaveInCall() = runOnUiThread {
        agoraEngine!!.stopPreview()
        onlineHandler.removeCallbacks(onlineRunnable)
        if (isJoined) {
            isJoined = false
            agoraEngine!!.leaveChannel()
            Thread {
                RtcEngine.destroy()
                agoraEngine = null
            }.start()
        }
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (!isJoined) super.onBackPressed()
        else {
            val alertBuilder = android.app.AlertDialog.Builder(this)
            alertBuilder.setTitle("Exit broadcast??")
            alertBuilder.setIcon(R.drawable.app_icon_sec)
            alertBuilder.setPositiveButton("Yes") { dialog, which -> leaveInCall() }
            alertBuilder.setNegativeButton("No") { dialog, which -> dialog.dismiss() }
            alertBuilder.create().show()
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isJoined) {
            enterPictureInPictureMode(pipParams!!)
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPictureInPicture = isInPictureInPictureMode
        pip()
    }

    override fun onDestroy() {
        leaveInCall()
        super.onDestroy()
    }

    private val mRtcEventHandler: IRtcEngineEventHandler = object : IRtcEngineEventHandler() {
        override fun onTokenPrivilegeWillExpire(token: String?) {
            fetchToken(broadcastCallData.channelName)
            super.onTokenPrivilegeWillExpire(token)
        }

        override fun onUserJoined(uid: Int, elapsed: Int) = runOnUiThread {
            //showMessage("Remote host joined.")
            setupAudienceVideo(uid)
        }

        @SuppressLint("NotifyDataSetChanged")
        override fun onJoinChannelSuccess(channel: String, uid: Int, elapsed: Int) = runOnUiThread {
            isJoined = true
            activeUserAnalytics()
            onlineHandler.postDelayed(onlineRunnable, 500)
            binding.progressBanner.visibility = View.GONE
            val ref = FirebaseDatabase.getInstance().reference.child(LIVE_BROADCAST_CREATOR).child(userStr)
            ValueEventLiveData(ref).observe(this@WatchLiveActivity) {
                if (it.second == onDataChange) {
                    val broadcast = it.first.getValue(BroadcastCallData::class.java)!!
                    if (broadcast.answerType == CallAnswerType.ENDED) leaveInCall()
                }
            }
            val commentHistory = FirebaseDatabase.getInstance()
                .reference.child(HostLiveActivity.BROADCAST_COMMENT_HISTORY)
                .child(broadcastCallData.callerUid)
                .child(broadcastCallData.timeCalled)
            timeRef.setValue(ServerValue.TIMESTAMP).addOnSuccessListener {
                timeRef.get().addOnSuccessListener { t ->
                    val timeSent = t.value.toString()
                    val comment = BroadcastCommentData(uid = myAuth, isJoined = true, timeSent = timeSent, user = userProfile)
                    commentHistory.child(timeSent).setValue(comment)
                }
            }

            val time = broadcastCallData.timeCalled
            val myHistory = FirebaseDatabase.getInstance().reference.child(BROADCAST_HISTORY).child(myAuth).child(time)
            myHistory.setValue(broadcastCallData)

            val callerHistory = FirebaseDatabase.getInstance().reference.child(BROADCAST_HISTORY).child(userStr).child(time)
            callerHistory.get().addOnSuccessListener { hist ->
                if (hist.exists()) {
                    val broadcastHistoryData = hist.getValue(BroadcastCallData::class.java)!!
                    broadcastHistoryData.uids.add(myAuth)
                    callerHistory.setValue(broadcastHistoryData)
                }
            }
            ChildEventLiveData(commentHistory).observe(this@WatchLiveActivity) {
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
                    }
                }
            }
        }

        override fun onUserOffline(uid: Int, reason: Int) = runOnUiThread {
            leaveInCall()
            if (isJoined) {
                showMessage("Other user left.")
            }
        }

        override fun onLeaveChannel(stats: RtcStats) = runOnUiThread {
            leaveInCall()
            showMessage("You left.")
            val commentHistory = FirebaseDatabase.getInstance()
                .reference.child(HostLiveActivity.BROADCAST_COMMENT_HISTORY)
                .child(broadcastCallData.callerUid)
                .child(broadcastCallData.timeCalled)
            timeRef.setValue(ServerValue.TIMESTAMP).addOnSuccessListener {
                timeRef.get().addOnSuccessListener { t ->
                    val timeSent = t.value.toString()
                    val comment = BroadcastCommentData(uid = myAuth, isJoined = false, timeSent = timeSent, user = userProfile)
                    commentHistory.child(timeSent).setValue(comment)
                }
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
            agoraEngine!!.enableVideo()
            agoraEngine!!.enableAudio()
        } catch (e: java.lang.Exception) {
            throw RuntimeException("Check the error.")
        }
    }

    private fun setupAudienceVideo(uid: Int) = runOnUiThread {
        localSurfaceView = SurfaceView(baseContext)
        localSurfaceView.visibility = View.VISIBLE
        //localSurfaceView.setZOrderMediaOverlay(true)
        binding.myVideoViewContainer.addView(localSurfaceView)
        agoraEngine!!.setupRemoteVideo(VideoCanvas(localSurfaceView, VideoCanvas.RENDER_MODE_HIDDEN, uid))
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun setToken(newValue: String) = runOnUiThread {
        token = newValue
        if (!isJoined) {
            val options = ChannelMediaOptions()
            options.clientRoleType = Constants.CLIENT_ROLE_AUDIENCE
            agoraEngine!!.enableLocalVideo(true)
            agoraEngine!!.startPreview()
            agoraEngine!!.joinChannel(token, broadcastCallData.channelName, 0, options)
        } else {
            agoraEngine!!.renewToken(token)
            showMessage("Token renewed")
        }
    }

    private fun showMessage(msg: String) {
        Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG).show()
    }

    private fun fetchToken(channelName: String) {
        val tokenRole = Constants.CLIENT_ROLE_AUDIENCE
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
                showMessage("Can't connect.")
                println("IOExecption ********************************* $e")
            }

            @Throws(IOException::class)
            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val gson = Gson()
                    val result: String = response.body!!.string()
                    val map = gson.fromJson<Map<*, *>>(result, MutableMap::class.java)
                    val _token = map["rtcToken"].toString()
                    println("Got here isSuccessful ******************************** $_token")
                    setToken(_token)
                }
            }
        })
    }

    fun onClickBack(view: View?) {
        onBackPressed()
    }

    fun onclickShare(view: View?) {
        Log.d(TAG, "onclickShare: ")
    }

    companion object {
        const val BROADCAST_HISTORY = "broadcast_history"
        private const val TAG = "watchliveact"
        const val BROADCAST_COMMENT_HISTORY = "broadcast_comment_history"
        const val BROADCAST_MOMENT_DATA = "BROADCAST_MOMENT_DATA"
        const val LIVE_BROADCAST_CREATOR = "live_broadcast_creator"
        const val LIVE_PRESENCE = "LIVE_PRESENCE"
        const val USER_DETAILS = "user_details"
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.lovesButton -> {
                if (myAuth != broadcastCallData.callerUid) {
                    val lovesRef = FirebaseDatabase.getInstance()
                        .reference.child(BROADCAST_MOMENT_DATA)
                        .child(broadcastCallData.callerUid)
                        .child(broadcastCallData.timeCalled)

                    val historyRef = FirebaseDatabase.getInstance().reference.child(FeedAdapter.TRANSFER_HISTORY).child(myAuth)
                    historyRef.get().addOnSuccessListener {
                        if (it.exists()) {
                            val available = checkBalance(it)
                            when {
                                available < LOVE_VALUE -> {
                                    binding.lovesButton.setImageResource(com.like.view.R.drawable.heart_off)
                                    Toast.makeText(this, "Insufficient HFCoin.", Toast.LENGTH_SHORT).show()
                                    insufficientCoinDialog()
                                }
                                else -> {
                                    HFCoinUtils.sendLoveLikeHFCoin(
                                        LOVE_VALUE,
                                        othersRef = lovesRef,
                                        broadcastCallData.callerUid,
                                        myProfile = userProfile,
                                        broadcastCallData.timeCalled
                                    )
                                    Glide.with(this).asGif().load(R.drawable.love_gif).listener(object :RequestListener<GifDrawable>{
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

                                    binding.lovesButton.setImageResource(com.like.view.R.drawable.heart_on)
                                }
                            }
                        }
                    }
                }
            }
            R.id.likeButton -> {
                if (myAuth != broadcastCallData.callerUid) {
                    val lovesRef = FirebaseDatabase.getInstance()
                        .reference.child(BROADCAST_MOMENT_DATA)
                        .child(broadcastCallData.callerUid)
                        .child(broadcastCallData.timeCalled)

                    val historyRef = FirebaseDatabase.getInstance().reference.child(FeedAdapter.TRANSFER_HISTORY).child(myAuth)
                    historyRef.get().addOnSuccessListener {
                        if (it.exists()) {
                            val available = checkBalance(it)
                            when {
                                available < LIKE_VALUE -> {
                                    binding.likeButton.setImageResource(R.drawable.like_white)
                                    Toast.makeText(this, "Insufficient HFCoin.", Toast.LENGTH_SHORT).show()
                                    insufficientCoinDialog()
                                }
                                else -> {
                                    HFCoinUtils.sendLoveLikeHFCoin(
                                        LIKE_VALUE,
                                        othersRef = lovesRef,
                                        broadcastCallData.callerUid,
                                        myProfile = userProfile,
                                        broadcastCallData.timeCalled
                                    )

                                    Glide.with(this).asGif().load(R.drawable.like_gif).listener(object :RequestListener<GifDrawable>{
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

                                    binding.likeButton.setImageResource(R.drawable.like_blue)
                                }
                            }
                        }
                    }
                }
            }
            R.id.btnsend -> {
                if (binding.etComment.text.isEmpty()) return
                val commentHistory = FirebaseDatabase.getInstance()
                    .reference.child(BROADCAST_COMMENT_HISTORY)
                    .child(broadcastCallData.callerUid)
                    .child(broadcastCallData.timeCalled)
                timeRef.setValue(ServerValue.TIMESTAMP).addOnSuccessListener {
                    timeRef.get().addOnSuccessListener { t ->
                        val timeSent = t.value.toString()
                        val comment =
                            BroadcastCommentData(uid = myAuth, comment = binding.etComment.text.trim().toString(), timeSent = timeSent, user = userProfile)
                        commentHistory.child(timeSent).setValue(comment)
                        binding.etComment.setText("")
                    }
                }
            }
        }
    }

    private fun insufficientCoinDialog() {
        val dialog = AlertDialog.Builder(this)
        dialog.setTitle("Message")
        dialog.setMessage("You have insufficient HowFar coin.\n")
        dialog.setPositiveButton("Get HowFar Coin"){ dialog, _ ->
            startActivity(Intent(this, MyWalletActivity::class.java))
            overridePendingTransition(R.anim.enter_right_to_left, R.anim.exit_right_to_left)
            dialog.dismiss()
        }
        dialog.setNegativeButton("Cancel"){ dialog, _ ->
            dialog.dismiss()
        }
        dialog.create().show()
    }
}