package com.azur.howfar.howfarchat.chat

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Activity.RESULT_OK
import android.app.AlertDialog
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Rect
import android.media.*
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.webkit.MimeTypeMap
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.view.ActionMode
import androidx.appcompat.view.ActionMode.Callback
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.azur.howfar.R
import com.azur.howfar.activity.BaseActivity
import com.azur.howfar.camera.CameraActivity
import com.azur.howfar.databinding.AttachmentFragmentBinding
import com.azur.howfar.databinding.ChatActivityBinding
import com.azur.howfar.databinding.FragmentDialogChatBinding
import com.azur.howfar.dilog.IncomingCallDialog
import com.azur.howfar.howfarchat.groupChat.Contact
import com.azur.howfar.livedata.ValueEventLiveData
import com.azur.howfar.models.*
import com.azur.howfar.models.EventListenerType.onDataChange
import com.azur.howfar.models.MessageType.ADDED_TO_GROUP
import com.azur.howfar.models.MessageType.CREATED_GROUP
import com.azur.howfar.models.MessageType.GROUP_ADMIN
import com.azur.howfar.models.MessageType.LEAVE_GROUP
import com.azur.howfar.models.MessageType.REMOVED_FROM_GROUP
import com.azur.howfar.utils.*
import com.azur.howfar.utils.ImageCompressor.compressImage
import com.azur.howfar.utils.Keyboard.hideKeyboard
import com.azur.howfar.utils.Util.permissionsAvailable
import com.azur.howfar.videos.ModalBottomSheet
import com.azur.howfar.viewmodel.*
import com.azur.howfar.workManger.ChatMediaWorkManager
import com.azur.howfar.workManger.HowFarAnalyticsTypes
import com.azur.howfar.workManger.OpenAppWorkManager
import com.azur.howfar.workManger.SupportWorkManager
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageView
import com.canhub.cropper.options
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
import com.google.gson.Gson
import com.tonyodev.fetch2.*
import com.tonyodev.fetch2core.DownloadBlock
import com.vanniktech.emoji.EmojiPopup
import kotlinx.coroutines.*
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.*

class ChatActivity2 : BaseActivity(), View.OnClickListener, QuoteHelper, MiscHelper {
    private val binding by lazy { ChatActivityBinding.inflate(layoutInflater) }
    private val llm = LinearLayoutManager(this@ChatActivity2, LinearLayoutManager.VERTICAL, false)
    private var receiverProfile = UserProfile()
    private val dataset: ArrayList<ChatData> = arrayListOf()
    private lateinit var pref: SharedPreferences
    private val scope = CoroutineScope(Dispatchers.Main)
    private val chatsAdapter = ChatAdapter2()
    private val myAuth = FirebaseAuth.getInstance().currentUser!!.uid
    private var timeRef = FirebaseDatabase.getInstance().reference
    private var chattingRef = FirebaseDatabase.getInstance().reference
    private var receiverChattingRef = FirebaseDatabase.getInstance().reference
    private var chatNewRef = FirebaseDatabase.getInstance().reference
    private var onlineRef = FirebaseDatabase.getInstance().reference
    private val singleChatInfoViewModel by viewModels<SingleChatInfoViewModel>()
    private val deleteChatsViewModel by viewModels<DeleteChatsViewModel>()
    private val chatDataViewModel by viewModels<ChatDataViewModel>()
    private val contactViewModel by viewModels<ContactViewModel>()
    private val imageDialogViewModel by viewModels<ImageDialogViewModel>()
    private val audioDialogViewModel by viewModels<AudioDialogViewModel>()
    private val videoDialogViewModel by viewModels<VideoDialogViewModel>()
    private val dialogViewModel by viewModels<DialogViewModel>()
    lateinit var sibscription: ActivitySubscription
    private var receiverUid = ""
    private var audioUri: Uri = Uri.EMPTY
    private var videoUri: Uri = Uri.EMPTY
    private var singleChatBubbleColor = "#660099"
    private var myProfileRef = FirebaseDatabase.getInstance().reference
    private var otherUserRef = FirebaseDatabase.getInstance().reference
    private lateinit var callUtils: CallUtils
    private var emojIcon: EmojiPopup? = null
    private var recorder = MediaRecorder()
    private var tempMyProfile = UserProfile()
    private var mediaPlayer: MediaPlayer? = null
    private var handlerPresence = Handler(Looper.getMainLooper())
    private var quotedChatData = ChatData()
    private val workManager = WorkManager.getInstance(this)
    private var localTime = 0L
    private var isSupport = false
    private var onlineStatus = ""

    private fun Activity.addSoftKeyboardVisibilityListener(
        visibleThresholdDp: Int = 100,
        initialState: Boolean = false,
        listener: (Boolean) -> Unit
    ): ActivitySubscription {
        return Keyboard.KeyboardVisibilitySubscription(this, visibleThresholdDp, initialState, listener)
    }

    private val chatListener = object : ChildEventListener {
        @SuppressLint("NotifyDataSetChanged")
        override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
            if (snapshot.exists()) {
                val chatData = snapshot.getValue(ChatData::class.java)!!
                when (chatData.isSupport) {
                    true -> supportMethods()
                    else -> realChatMethods()
                }
                if (chatData.senderuid != myAuth && !chatData.read) {
                    NotificationManagerCompat.from(this@ChatActivity2).cancelAll()
                }
                scope.launch {
                    for (i in dataset) if (i.uniqueQuerableTime == chatData.uniqueQuerableTime || i.timesent == chatData.timesent) {
                        if (i.timesent == chatData.timesent) {
                            dataset[dataset.indexOf(i)] = chatData
                            runOnUiThread { chatsAdapter.notifyDataSetChanged() }
                        }
                        return@launch
                    }

                    runOnUiThread {
                        if (chatData !in dataset) dataset.add(chatData)
                        chatsAdapter.notifyDataSetChanged()
                        binding.rvChat.smoothScrollToPosition(dataset.size)
                    }
                }
            }
        }

        @SuppressLint("NotifyDataSetChanged")
        override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
            if (snapshot.exists()) {
                val data = snapshot.getValue(ChatData::class.java)!!
                for ((index, i) in dataset.withIndex()) if (i.uniqueQuerableTime == data.uniqueQuerableTime) {
                    dataset[index] = data
                    chatsAdapter.notifyItemChanged(index)
                    binding.rvChat.smoothScrollToPosition(dataset.size)
                }
            }
        }

        @SuppressLint("NotifyDataSetChanged")
        override fun onChildRemoved(snapshot: DataSnapshot) {
            val data = snapshot.getValue(ChatData::class.java)!!
            dataset.remove(data)
            chatsAdapter.notifyDataSetChanged()
        }

        override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) = Unit
        override fun onCancelled(error: DatabaseError) = Unit
    }

    inner class InputTextWatcher : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            val typingRef = FirebaseDatabase.getInstance().reference.child("typing").child(receiverUid).child(myAuth)
            var timeRef = FirebaseDatabase.getInstance().reference.child("time").child(myAuth)
            timeRef.setValue(ServerValue.TIMESTAMP).addOnSuccessListener {
                timeRef.get().addOnSuccessListener { timeSnap ->
                    val time = timeSnap.value.toString()
                    val typingData = TypingData(uid = myAuth, time = time, otherUser = receiverUid)
                    typingRef.setValue(typingData)
                }
            }
        }

        override fun afterTextChanged(s: Editable?) {
            if (s?.toString()!!.isNotEmpty()) {
                binding.recordButton.visibility = View.GONE
                binding.chatSendRoot.visibility = View.VISIBLE
            } else {
                binding.recordButton.visibility = View.VISIBLE
                binding.chatSendRoot.visibility = View.GONE
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setSupportActionBar(binding.chatToolbar)
        title = ""
        callUtils = CallUtils(this, this)
        pref = getSharedPreferences(getString(R.string.ALL_PREFERENCE), Context.MODE_PRIVATE)
        singleChatBubbleColor = pref.getString(getString(R.string.chatBubbleColor) + receiverUid, "#660099")!!
        val tempProfileJson = pref.getString(getString(R.string.this_user), "")
        if (tempProfileJson == "") return
        emojIcon = EmojiPopup.Builder.fromRootView(binding.chatRoot).setOnEmojiPopupShownListener {}.build(binding.chatInput)
        receiverUid = intent.getStringExtra("data")!!
        isSupport = if (intent.hasExtra("isSupport")) {
            supportMethods()
            true
        } else {
            realChatMethods()
            false
        }
        tempMyProfile = Gson().fromJson(tempProfileJson, UserProfile::class.java)
        if (dataset.isEmpty()) showProgressBar() // CALL THIS NEXT
        showChatView()
        emojIcon = EmojiPopup.Builder.fromRootView(binding.chatRoot).setOnEmojiPopupShownListener {}.build(binding.chatInput)
        receiverUid = intent.getStringExtra("data")!!

        initReferences()
        otherUserDetails()
        onClickListeners()
        initAdapter()
        viewModels()
        recordMethods()
        closeAllNotification()
        sibscription = addSoftKeyboardVisibilityListener { keyboardShown ->
            if (keyboardShown) {
                llm.findLastVisibleItemPosition().takeIf { it > 3 }?.let {
                    if (dataset.size >= it - 3) binding.rvChat.smoothScrollToPosition(dataset.size)
                }
            } else {
                hideKeyboard()
            }
        }
    }

    private fun supportMethods() {
        binding.chatSendRoot.visibility = View.VISIBLE
        binding.recordButton.visibility = View.GONE
        binding.chatCamera.visibility = View.GONE
        binding.chatAttachment.visibility = View.GONE
        binding.more.visibility = View.GONE
        binding.chatCallRoot.visibility = View.GONE
    }

    private fun realChatMethods() {
    }

    private fun otherUserDetails() {
        otherUserRef = otherUserRef.child(USER_DETAILS).child(receiverUid)
        otherUserRef.get().addOnSuccessListener {
            receiverProfile = it.getValue(UserProfile::class.java)!!
            Glide.with(this).load(receiverProfile.image).centerCrop().into(binding.userImage)
            binding.userName.text = receiverProfile.name
        }

        val blockedRef = FirebaseDatabase.getInstance().reference.child(MY_BLOCKED_CONTACTS).child(receiverProfile.uid)
        blockedRef.get().addOnSuccessListener { blocked ->
            val blockedList: ArrayList<String> = arrayListOf()
            if (blocked.exists()) {
                for (i in blocked.children) blockedList.add(i.value.toString())
                if (myAuth !in blockedList) if (!isSupport) sendOnlinePresence()
            } else if (!isSupport) sendOnlinePresence()
        }
    }

    private fun sendOnlinePresence() {
        val onlineRef = onlineRef.child(ONLINE_PRESENCE).child(receiverUid)
        onlineRef.keepSynced(false)
        binding.chatInput.addTextChangedListener(InputTextWatcher())

        handlerPresence.postDelayed(object : Runnable {
            override fun run() {
                val myTime = Calendar.getInstance().timeInMillis
                var timeDiff: Long
                onlineRef.get().addOnSuccessListener {
                    if (it.exists()) {
                        val onlinePresenceData = it.getValue(OnlinePresenceData::class.java)!!
                        localTime = TimeUtils.UTCToLocal(onlinePresenceData.lastTimePosted).toLong()
                        timeDiff = myTime - localTime
                        onlineStatus = if (timeDiff < 60_000) "Online" else Util.formatSmartDateTime(localTime.toString())
                        binding.userLastSeen.text = onlineStatus
                    }
                }.addOnFailureListener {
                    binding.userLastSeen.text = onlineStatus
                }
                handlerPresence.postDelayed(this, 60_000L)
            }
        }, 1_000L)

        val typingRef = FirebaseDatabase.getInstance().reference.child("typing").child(myAuth).child(receiverUid)
        ValueEventLiveData(typingRef).observe(this) {
            when (it.second) {
                onDataChange -> {
                    val myTime = Calendar.getInstance().timeInMillis.toString()
                    var typingData = it.first.getValue(TypingData::class.java)!!
                    val localTime = TimeUtils.UTCToLocal(typingData.time)
                    val diff = TimeUtils.timeDiff(localTime, myTime)
                    if (diff < 10) binding.userLastSeen.text = "typing..."
                    runBlocking {
                        scope.launch {
                            delay(10_000)
                            runOnUiThread {
                                val localTimeUpdate = TimeUtils.UTCToLocal(typingData.time)
                                val myTimeUpdate = Calendar.getInstance().timeInMillis.toString()
                                val diffUpdate = TimeUtils.timeDiff(localTimeUpdate, myTimeUpdate)
                                if (diffUpdate > 10) binding.userLastSeen.text = onlineStatus
                            }
                        }
                    }
                }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun recordMethods() {
        var timer = 0
        val handler = Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                timer++
                handler.postDelayed(this, 1000)
                binding.chatInput.hint = "Release to stop: ${TimeUtils.milliSecondsToTimer((timer * 1000).toLong())}"
            }
        }
        var currentVoiceNotePath = ""
        binding.chatInput.addTextChangedListener(InputTextWatcher())
        binding.recordButton.setOnClickListener { Snackbar.make(binding.recordButton, "Hold down", Snackbar.LENGTH_LONG).show() }
        binding.recordButton.setOnTouchListener { _, event ->
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
                return@setOnTouchListener false
            } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
                return@setOnTouchListener false
            }
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    timer = 0
                    showChatView()
                    currentVoiceNotePath = recordAudio()
                    handler.postDelayed(runnable, 1000)
                    //if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate((VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE)))
                    //else vibrator.vibrate(200)
                }
                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(runnable)
                    binding.chatInput.hint = "Message..."
                    showChatView()
                    if (currentVoiceNotePath == "") return@setOnTouchListener false
                    try {
                        if (timer <= 1) {
                            recorder.reset()
                            recorder.release()
                            return@setOnTouchListener false
                        } else stopRecording()

                        binding.recordTime.text = getTime(currentVoiceNotePath)
                        //playAudio(currentVoiceNotePath)
                        showRecordView()

                        binding.recordSend.setOnClickListener {
                            showChatView()
                            audioDialogViewModel.setSendAudio(true to Uri.fromFile(File(currentVoiceNotePath)))
                        }
                        binding.recordDelete.setOnClickListener {
                            showChatView()
                            Snackbar.make(binding.profile, "Deleted", Snackbar.LENGTH_SHORT).show()
                        }
                    } catch (e: IllegalStateException) {
                        showChatView()
                        Snackbar.make(binding.root, "Error on playback", Snackbar.LENGTH_LONG).show()
                        println("Error on playback ***************************** ${e.printStackTrace()}")
                    }
                }
            }
            return@setOnTouchListener true
        }
    }

    private fun getTime(path: String): String {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(this, Uri.parse(path))
        val time = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!
        return TimeUtils.milliSecondsToTimer(time.toLong())!!
    }

    private fun showRecordView() {
        binding.recordView.visibility = View.VISIBLE
        binding.chatView.visibility = View.GONE
    }

    private fun showChatView() {
        binding.recordView.visibility = View.GONE
        binding.chatView.visibility = View.VISIBLE
    }

    private fun stopRecording() {
        recorder.stop()
        recorder.release()
    }

    private fun playAudio(currentVoiceNotePath: String) {
        mediaPlayer = MediaPlayer()
        val iss = FileInputStream(currentVoiceNotePath)
        mediaPlayer!!.setDataSource(iss.fd)
        mediaPlayer!!.setAudioAttributes(AudioAttributes.Builder().build())
        mediaPlayer!!.prepare()
        mediaPlayer!!.start()
    }

    private fun recordAudio(): String {
        recorder = MediaRecorder()
        val timeNow = Calendar.getInstance().timeInMillis.toString()
        val dir = File("${this.filesDir.path}/Media/Recording/")
        if (!dir.exists()) dir.mkdirs()
        val path = File(dir, "$timeNow.mp3")

        try {
            val values = ContentValues(3)
            values.put(MediaStore.MediaColumns.TITLE, timeNow)
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT)
            recorder.setOutputFile(path.absolutePath)
            recorder.prepare()
            recorder.start()
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
            return ""
        }
        return path.absolutePath
    }

    private fun onClickListeners() {
        binding.chatInput.setOnClickListener(this)
        binding.more.setOnClickListener(this)
        binding.chatSend.setOnClickListener(this)
        binding.chatVideoCall.setOnClickListener(this)
        binding.chatVoiceCall.setOnClickListener(this)
        binding.chatBack.setOnClickListener(this)
        binding.chatEmoji.setOnClickListener(this)
        binding.chatAttachment.setOnClickListener(this)
        binding.quotedCancel.setOnClickListener(this)
        binding.chatCamera.setOnClickListener(this)
        binding.profile.setOnClickListener(this)
    }

    private fun initAdapter() {
        val swipeTouchCallback = SwipeTouchCallback(chatsAdapter, this)
        val itemTouchHelper = ItemTouchHelper(swipeTouchCallback)
        itemTouchHelper.attachToRecyclerView(binding.rvChat)
        chatsAdapter.viewLifecycleOwner = this
        chatsAdapter.activity = this
        chatsAdapter.groupOrChat = 0
        chatsAdapter.dataset = dataset
        chatsAdapter.isSupport = isSupport
        chatsAdapter.deleteChatsViewModel = deleteChatsViewModel
        chatsAdapter.itemTouchHelper = itemTouchHelper
        chatsAdapter.pref = pref
        chatsAdapter.miscHelper = this
        chatsAdapter.quoteHelper = this
        chatsAdapter.singleChatBubbleColor = singleChatBubbleColor
        chatsAdapter.contactViewModel = contactViewModel
        chatsAdapter.receiverUID = receiverUid
        chatsAdapter.rv = binding.rvChat
        binding.rvChat.adapter = chatsAdapter
        binding.rvChat.layoutManager = llm
        llm.scrollToPosition(chatsAdapter.itemCount - 1)
    }

    private fun viewModels() {
        chatDataViewModel.phoneData.observe(this) {
            val phoneData = PhoneData(number = it.second, name = it.first)
            sendNewMsg(message = it.first, phoneData = phoneData)
        }
        imageDialogViewModel.send.observe(this) { data ->
            sendNewMsg(imageData = ImageData(storageLink = data.toString()))
        }
        audioDialogViewModel.sendAudio.observe(this) {
            if (it.first) {
                audioUri = it.second
                sendNewMsg(audioData = AudioData(storageLink = audioUri.toString()))
            }
        }
        videoDialogViewModel.sendVideo.observe(this) {
            if (it.first) {
                videoUri = it.second
                sendNewMsg(videoData = VideoData(storageLink = videoUri.toString()))
            }
        }
    }

    private fun showProgressBar(txt: String = "Getting chat...", backPress: Boolean = false, type: Int = DialogMode.CHAT_FETCH_PROGRESS) {
        dialogViewModel.setDialogMessage(txt)
        dialogViewModel.setDialogMode(type)
        dialogViewModel.setDisableBackPress(backPress)
        dialogViewModel.setCardColor(singleChatBubbleColor)
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onResume() {
        super.onResume()
        recorder = MediaRecorder()
        pref.edit().putString(getString(R.string.in_chat_uid), receiverUid).apply()
        singleChatBubbleColor = pref.getString(getString(R.string.chatBubbleColor) + receiverUid, "#660099")!!
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)

        val unwrappedDrawableRight = AppCompatResources.getDrawable(this, R.drawable.chat_bubble_purple_right)
        val unwrappedDrawableLeft = AppCompatResources.getDrawable(this, R.drawable.chat_bubble_purple_left)
        val unwrappedDrawableRound = AppCompatResources.getDrawable(this, R.drawable.bg_round_purple)
        val unwrappedDrawableSend = AppCompatResources.getDrawable(this, R.drawable.ic_send)
        val wrappedDrawableSend = DrawableCompat.wrap(unwrappedDrawableSend!!)
        val wrappedDrawableRight = DrawableCompat.wrap(unwrappedDrawableRight!!)
        val wrappedDrawableLeft = DrawableCompat.wrap(unwrappedDrawableLeft!!)
        val wrappedDrawableRound = DrawableCompat.wrap(unwrappedDrawableRound!!)
        DrawableCompat.setTint(wrappedDrawableRight, Color.parseColor(singleChatBubbleColor))
        DrawableCompat.setTint(wrappedDrawableLeft, Color.parseColor(singleChatBubbleColor))
        DrawableCompat.setTint(wrappedDrawableSend, Color.parseColor(singleChatBubbleColor))
        DrawableCompat.setTint(wrappedDrawableRound, Color.parseColor(singleChatBubbleColor))

        binding.chatToolbar.setBackgroundColor(Color.parseColor(singleChatBubbleColor))
        window.statusBarColor = Color.parseColor(singleChatBubbleColor)
        binding.chatAttachment.background = wrappedDrawableRound
        binding.chatSend.background = wrappedDrawableSend
        binding.rvChat.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            binding.rvChat.post {
                chatsAdapter.itemCount.takeIf { it > 0 }?.let {
                    //binding.rvChat.scrollToPosition(it - 1)
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        pref.edit().putInt(getString(R.string.in_chat_phone_key), 0).apply()
        pref.edit().putString(getString(R.string.in_chat_uid), "").apply()
    }

    override fun onPause() {
        super.onPause()
        pref.edit().putInt(getString(R.string.in_chat_phone_key), 0).apply()
        pref.edit().putString(getString(R.string.in_chat_uid), "").apply()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun initReferences() {
        chatNewRef = chatNewRef.child(arrayListOf(myAuth, receiverUid).sortedByDescending { it }.last())
        myProfileRef = myProfileRef.child(USER_DETAILS).child(myAuth)
        timeRef = FirebaseDatabase.getInstance().reference.child("time").child(myAuth)
        chattingRef = FirebaseDatabase.getInstance().reference.child(CHAT_REFERENCE).child(myAuth).child(if (!isSupport) receiverUid else CONTACT_SUPPORT)
        receiverChattingRef = FirebaseDatabase.getInstance().reference.child(CHAT_REFERENCE).child(receiverUid).child(myAuth)
        chattingRef.get().addOnSuccessListener {
            if (it.exists()) for (snapshot in it.children) {
                val chatData = snapshot.getValue(ChatData::class.java)!!
                if (chatData.senderuid != myAuth && !chatData.read) NotificationManagerCompat.from(this@ChatActivity2).cancelAll()
                if (chatData !in dataset) dataset.add(chatData)
            }
            chatsAdapter.notifyDataSetChanged()
            binding.rvChat.scrollToPosition(dataset.size - 1)
        }
        chattingRef.addChildEventListener(chatListener)
    }

    private fun sendNewMsg(
        message: String = "", imageData: ImageData = ImageData(), audioData: AudioData = AudioData(),
        videoData: VideoData = VideoData(), phoneData: PhoneData = PhoneData()
    ) {
        if (imageData.storageLink == "" && audioData.storageLink == "" && videoData.storageLink == "" && phoneData.number == "") if (message == "") return
        val timeNow = Calendar.getInstance().timeInMillis.toString()

        var myChat = ChatData(
            senderuid = myAuth, displaytitle = message,
            timesent = timeNow, videoData = videoData, phoneData = phoneData,
            participants = arrayListOf(myAuth, receiverProfile.uid), msg = message,
            imageData = imageData, audioData = audioData, myPhone = tempMyProfile.phone
        )

        when (binding.quotedRoot.visibility) {
            View.GONE -> {}
            View.VISIBLE -> myChat.quotedChatData = chatToQuotedData(quotedChatData)
        }
        binding.quotedRoot.visibility = View.GONE

        when {
            imageData.storageLink != "" -> myChat.messagetype = MessageType.PHOTO
            audioData.storageLink != "" -> myChat.messagetype = MessageType.AUDIO
            videoData.storageLink != "" -> myChat.messagetype = MessageType.VIDEO
            phoneData.number != "" -> myChat.messagetype = MessageType.CONTACT
            myChat.day != "" -> myChat.messagetype = MessageType.CHAT_DAY
        }
        /*var chatDataDay = ChatData(messagetype = MessageType.CHAT_DAY, day = "Today")
        val nowDay = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        val chatInstance = Calendar.getInstance()
        chatInstance.timeInMillis = myChat.timesent.toLong()
        val chatDay = chatInstance.get(Calendar.DAY_OF_YEAR)
        if (nowDay != chatDay) {
            val x = Instant.ofEpochMilli(myChat.timesent.toLong()).atZone(ZoneId.systemDefault())
            val dateFormatter = DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.getDefault())
            chatDataDay.day = dateFormatter.format(x)
        }
        if (chatDataDay !in dataset) dataset.add(chatDataDay)*/

        dataset.add(myChat)
        binding.rvChat.smoothScrollToPosition(dataset.size)
        chatsAdapter.notifyItemInserted(dataset.size)

        if (myChat.messagetype == MessageType.PHOTO || myChat.messagetype == MessageType.AUDIO || myChat.messagetype == MessageType.VIDEO) {
            workManagerUpload(Gson().toJson(myChat))
            return
        }
        otherUserRef.get().addOnSuccessListener { other ->
            if (other.exists()) {
                receiverProfile = other.getValue(UserProfile::class.java)!!
                myProfileRef.get().addOnSuccessListener {
                    if (it.exists()) {
                        val myProfile: UserProfile = it.getValue(UserProfile::class.java)!!
                        val tempProfile = ParticipantTempData(
                            tempName = myProfile.name, uid = myProfile.uid, tempImage = myProfile.image,
                            phone = Util.formatNumber(myProfile.phone)
                        )
                        val receiverTemp = ParticipantTempData(
                            tempName = receiverProfile.name, uid = receiverProfile.uid, tempImage = receiverProfile.image,
                            phone = Util.formatNumber(receiverProfile.phone)
                        )
                        if (myChat.msg.isNotEmpty()) {
                            val howFar1 = "how far"
                            val howFar2 = "How far"
                            val howFar3 = "How Far"
                            val howFar4 = "how Far"
                            myChat.participantsTempData = arrayListOf(tempProfile, receiverTemp)
                            myChat.msg = myChat.msg.replace(howFar1, "HowFar")
                            myChat.msg = myChat.msg.replace(howFar2, "HowFar")
                            myChat.msg = myChat.msg.replace(howFar3, "HowFar")
                            myChat.msg = myChat.msg.replace(howFar4, "HowFar")
                        }
                        timeRef.setValue(ServerValue.TIMESTAMP).addOnSuccessListener {
                            timeRef.get().addOnSuccessListener { snapshot ->
                                if (snapshot.exists()) {
                                    val rawTime = snapshot.value.toString()
                                    myChat.uniqueQuerableTime = rawTime
                                    myChat.sent = true
                                    for ((index, i) in dataset.withIndex()) {
                                        if (i.timesent == myChat.timesent) {
                                            dataset[index].sent = true
                                            chatsAdapter.notifyItemChanged(index)
                                        }
                                    }
                                    val blockedRef = FirebaseDatabase.getInstance().reference.child(MY_BLOCKED_CONTACTS).child(receiverUid)
                                    blockedRef.get().addOnSuccessListener { blocked ->
                                        val blockedList: ArrayList<String> = arrayListOf()
                                        if (blocked.exists()) {
                                            for (i in blocked.children) blockedList.add(i.value.toString())
                                            if (myAuth !in blockedList) receiverChattingRef.child(myChat.uniqueQuerableTime).setValue(myChat).addOnSuccessListener {
                                                println("data 1 ********************************** ")
                                            }
                                            //chatNewRef.child(myChat.uniqueQuerableTime).setValue(myChat).addOnSuccessListener {
                                            //    distributeDisplayMessage(myChat)
                                            //}
                                        } else {
                                            receiverChattingRef.child(myChat.uniqueQuerableTime).setValue(myChat).addOnSuccessListener {
                                                println("data 2 ********************************** ")
                                            }
                                            chattingRef.child(myChat.uniqueQuerableTime).setValue(myChat).addOnSuccessListener {
                                                println("data 3 ********************************** ")
                                                playRing(R.raw.message_sent)
                                                activeUserAnalytics(rawTime)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun distributeDisplayMessage(myChat: ChatData) {
        FirebaseDatabase.getInstance().reference.child(CHAT_REFERENCE_V2).child(myAuth).child(myChat.uniqueQuerableTime).setValue(myChat)
        FirebaseDatabase.getInstance().reference.child(CHAT_REFERENCE_V2).child(myAuth).child(myChat.uniqueQuerableTime).setValue(myChat)
    }

    private fun workManagerUpload(data: ChatData) {
        val json = Gson().toJson(data)
        pref.edit().putString(getString(R.string.support_data), json).apply()
        val workRequest = OneTimeWorkRequestBuilder<SupportWorkManager>().addTag("moment upload")
            .setInputData(workDataOf("otherUid" to receiverUid))
            .build()
        workManager.enqueue(workRequest)
    }

    private fun activeUserAnalytics(rawTime: String) {
        val workRequest = OneTimeWorkRequestBuilder<OpenAppWorkManager>().addTag("analytics")
            .setInputData(workDataOf("time" to rawTime.toLong()))
            .setInputData(workDataOf("action" to HowFarAnalyticsTypes.SEND_MESSAGE))
            .build()
        workManager.enqueue(workRequest)
    }

    private fun workManagerUpload(json: String) {
        val workRequest = OneTimeWorkRequestBuilder<ChatMediaWorkManager>().addTag("chat")
            .setInputData(workDataOf("chatData" to json))
            .build()
        workManager.enqueue(workRequest)
    }

    private fun inflateMenuOptions() {
        val menu = PopupMenu(this, binding.more)
        menu.gravity = Gravity.TOP
        menuInflater.inflate(R.menu.chat_menu, menu.menu)
        menu.show()
        menu.setOnMenuItemClickListener { item ->
            when (item?.itemId) {
                R.id.customize_chat -> {
                    val intent = Intent(this, ActivityCustomizeChat::class.java)
                    intent.putExtra("uid", receiverProfile.uid)
                    startActivity(intent)
                    overridePendingTransition(R.anim.enter_right_to_left, R.anim.exit_right_to_left)
                }
                /*R.id.wallpaper -> {
                    val intent = Intent(this, ActivityCustomizeChatWallpaper::class.java)
                    intent.putExtra("uid", receiverProfile.uid)
                    startActivity(intent)
                    overridePendingTransition(R.anim.enter_right_to_left, R.anim.exit_right_to_left)
                }*/
                //R.id.clear_chat -> {}
            }
            return@setOnMenuItemClickListener true
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        chattingRef.removeEventListener(chatListener)
        pref.edit().putInt(getString(R.string.in_chat_phone_key), 0).apply()
    }

    private fun chatToQuotedData(chatData: ChatData): QuoteChatData {
        return QuoteChatData(
            senderuid = chatData.senderuid,
            participants = chatData.participants,
            timesent = chatData.timesent,
            timeInitial = chatData.timeInitial,
            timeseen = chatData.timeseen,
            myPhone = chatData.myPhone,
            timeddelivered = chatData.timeddelivered,
            sent = chatData.sent,
            read = chatData.read,
            delivered = chatData.delivered,
            msg = chatData.msg,
            day = chatData.day,
            uniqueQuerableTime = chatData.uniqueQuerableTime,
            displaytitle = chatData.displaytitle,
            messagetype = chatData.messagetype,
            phoneData = chatData.phoneData,
            imageData = chatData.imageData,
            audioData = chatData.audioData,
            videoData = chatData.videoData,
        )
    }

    override fun helpQuotedChat(datum: ChatData) {
        binding.quotedRoot.visibility = View.VISIBLE
        quotedChatData = datum
        val time = Util.formatSmartDateTime(TimeUtils.UTCToLocal(datum.uniqueQuerableTime))
        val display = when {
            datum.videoData.storageLink != "" -> "Video $time"
            datum.audioData.storageLink != "" -> "Audio $time"
            datum.audioData.storageLink != "" -> "Image $time"
            datum.audioData.storageLink != "" -> "Contact $time"
            else -> datum.msg
        }
        binding.quotedText.text = display
        binding.chatInput.requestFocus()
        binding.chatInput.postDelayed(Runnable {
            val imm: InputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(binding.chatInput, InputMethodManager.HIDE_IMPLICIT_ONLY)
        }, 1000)
    }

    override fun smoothScrollToPosition(pos: Int) {
        binding.rvChat.smoothScrollToPosition(pos)
    }

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val imageUri = result.data!!.getStringExtra("guest image result")!!
            val chatData = ChatData(
                senderuid = myAuth,
                messagetype = MessageType.PHOTO,
                timesent = System.currentTimeMillis().toString(),
                participants = arrayListOf(myAuth, receiverUid),
                imageData = ImageData(storageLink = Uri.fromFile(File(imageUri)).toString(), displayMessage = "Image"),
                myPhone = tempMyProfile.phone
            )

            dataset.add(chatData)
            chatsAdapter.notifyItemInserted(dataset.size)
            val json = Gson().toJson(chatData)
            workManagerUpload(json)
        }
    }

    override fun onClick(p0: View?) {
        when (p0?.id) {
            R.id.chatInput -> emojIcon!!.toggle()
            R.id.chatCamera -> {
                cameraLauncher.launch(Intent(this, CameraActivity::class.java))
            }
            R.id.quoted_cancel -> binding.quotedRoot.visibility = View.GONE
            R.id.chat_video_call -> {
                val callData = CallData(channelName = myAuth, callerUid = myAuth, callType = CallType.VIDEO, uids = arrayListOf(myAuth, receiverProfile.uid))
                val intent = Intent(this, IncomingCallDialog::class.java)
                intent.putExtra("CREATE", CallEngagementType.CREATE)
                intent.putExtra("callData", Gson().toJson(callData))
                startActivity(intent)
            }
            R.id.chat_voice_call -> {
                val callData = CallData(channelName = myAuth, callerUid = myAuth, callType = CallType.VOICE, uids = arrayListOf(myAuth, receiverProfile.uid))
                val intent = Intent(this, IncomingCallDialog::class.java)
                intent.putExtra("CREATE", CallEngagementType.CREATE)
                intent.putExtra("callData", Gson().toJson(callData))
                startActivity(intent)
            }
            R.id.chatSend -> {
                val message = binding.chatInput.text.toString().trim()
                if (message == "") return
                when (isSupport) {
                    true -> {
                        workManagerUpload(
                            ChatData(
                                senderuid = myAuth,
                                msg = message,
                                displaytitle = "New message",
                                timesent = System.currentTimeMillis().toString(),
                                isSupport = true,
                            )
                        )
                    }
                    else -> sendNewMsg(message)
                }
                binding.chatInput.text!!.clear()
            }
            R.id.chat_back -> super.onBackPressed()
            R.id.chat_attachment -> {
                hideKeyboard()
                val modalBottomSheet = AttachmentFragment()
                modalBottomSheet.show(supportFragmentManager, ModalBottomSheet.TAG)
            }
            R.id.profile -> {
                val intent = Intent(this, UserProfileActivity::class.java)
                intent.putExtra("data", receiverUid)
                startActivity(intent)
                overridePendingTransition(R.anim.enter_right_to_left, R.anim.exit_right_to_left)
            }
            R.id.chatEmoji -> emojIcon!!.toggle()
            R.id.more -> inflateMenuOptions()
        }
    }

    companion object {
        const val CALL_REFERENCE = "call_reference"
        const val CALL_HISTORY = "call_history"
        const val USER_DETAILS = "user_details"
        const val TRANSFER_HISTORY = "user_coins_transfer"
        const val CHAT_REFERENCE = "chat_reference"
        const val CHAT_REFERENCE_V2 = "chat_reference_v2"
        const val AUDIO_REFERENCE = "audio_reference"
        const val CONTACT_SUPPORT = "CONTACT_SUPPORT"
        const val MY_BLOCKED_CONTACTS = "blocked_contacts"
        val REQUESTED_PERMISSIONS = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
        )
    }

    override fun viewChatInfo(datum: ChatData) {
        val modalInfoFragment = ModalInfoFragment()
        singleChatInfoViewModel.setChatData(datum)
        modalInfoFragment.show(supportFragmentManager, "info")
    }
}

class AttachmentFragment : BottomSheetDialogFragment(), View.OnClickListener {
    private lateinit var attachmentBinding: AttachmentFragmentBinding
    private val acceptedAudioTypes: ArrayList<String> = arrayListOf("wav", "mp3", "aac")
    private var permissionsContact = arrayOf(Manifest.permission.READ_CONTACTS)
    private var permissionsStorage = arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
    private val chatDataViewModel: ChatDataViewModel by activityViewModels()
    private val imageDialogViewModel: ImageDialogViewModel by activityViewModels()
    private val audioDialogViewModel: AudioDialogViewModel by activityViewModels()
    private val videoDialogViewModel: VideoDialogViewModel by activityViewModels()

    @RequiresApi(33)
    var permissionsStorageT = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_AUDIO,
        Manifest.permission.READ_MEDIA_VIDEO,
    )

    @SuppressLint("Range")
    private val pickContactLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { dataResult ->
        try {
            if (dataResult.data!!.data == null) return@registerForActivityResult
            if (dataResult.resultCode == RESULT_OK) {
                val contactUri = dataResult.data!!.data!!
                val queryFields = arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER)

                val cursor = requireActivity().contentResolver.query(contactUri, queryFields, null, null, null)
                if (cursor!!.count == 0) {
                    this@AttachmentFragment.dismiss()
                    return@registerForActivityResult
                }
                cursor.moveToFirst()
                val name = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME))!!
                val phoneNo = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER))!!
                chatDataViewModel.setNamePhone(name to phoneNo)
                println("Got here pickContactLauncher ********************************************** $dataResult")
                this@AttachmentFragment.dismiss()
            }
        } catch (e: Exception) {
            this@AttachmentFragment.dismiss()
            println("Exception ********************************************** $e")
        }
    }
    private val pickAudioLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { dataResult ->
        val audioFragment = AudioDialogFragment()
        try {
            if (dataResult.data!!.data == null) return@registerForActivityResult
            if (dataResult.resultCode == RESULT_OK) {
                val timeNow = Calendar.getInstance().timeInMillis.toString()
                val mime = MimeTypeMap.getSingleton()
                val contentResolver = requireActivity().contentResolver
                val ext = mime.getExtensionFromMimeType(contentResolver?.getType(dataResult.data!!.data!!))!!
                val fileLoc = File("${requireContext().filesDir.path}/Media/Audio/Sent/")
                if(!fileLoc.exists()) fileLoc.mkdirs()
                val fileLocation = File(fileLoc, "$timeNow.$ext")

                val file = copy(dataResult.data!!.data!!, fileLocation)
                if(file.path == "") return@registerForActivityResult
                val audioUri = Uri.parse(file.path)
                val lastPathSegment = audioUri.lastPathSegment!!
                val name = lastPathSegment.substring(lastPathSegment.lastIndexOf("/") + 1)

                if (ext in acceptedAudioTypes) {
                    requireActivity().supportFragmentManager.beginTransaction().replace(R.id.chat_root, audioFragment).commit()
                    audioDialogViewModel.setAudioData(name to audioUri)
                    this@AttachmentFragment.dismiss()
                } else Snackbar.make(attachmentBinding.root, "Unsupported image type. Supported types are wav, mp3, aac", Snackbar.LENGTH_LONG).show()
            } else {
                Snackbar.make(attachmentBinding.root, "No Audio picked", Snackbar.LENGTH_LONG).show()
                this@AttachmentFragment.dismiss()
            }

        } catch (e: Exception) {
            this@AttachmentFragment.dismiss()
            Snackbar.make(attachmentBinding.root, "File error", Snackbar.LENGTH_LONG).show()
        }
    }
    private val pickVideoLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { dataResult ->
        val videoFragment = VideoDialogFragment()
        try {
            if (dataResult.data!!.data == null) return@registerForActivityResult
            if (dataResult.resultCode == RESULT_OK) {
                val timeNow = Calendar.getInstance().timeInMillis.toString()
                val mime = MimeTypeMap.getSingleton()
                val contentResolver = requireActivity().contentResolver
                val ext = mime.getExtensionFromMimeType(contentResolver?.getType(dataResult.data!!.data!!))!!
                val fileLoc = File("${requireContext().filesDir.path}/Media/Video/Sent/")
                if(!fileLoc.exists()) fileLoc.mkdirs()
                val fileLocation = File(fileLoc, "$timeNow.$ext")

                val file = copy(dataResult.data!!.data!!, fileLocation)
                if(file.path == "") return@registerForActivityResult

                val videoUri = Uri.parse(file.path)
                val lastPathSegment = videoUri.lastPathSegment!!
                val name = lastPathSegment.substring(lastPathSegment.lastIndexOf("/") + 1)
                requireActivity().supportFragmentManager.beginTransaction().replace(R.id.chat_root, videoFragment).commit()
                videoDialogViewModel.setVideoData(name to videoUri)
                this@AttachmentFragment.dismiss()
            } else {
                Snackbar.make(attachmentBinding.root, "No Video picked", Snackbar.LENGTH_LONG).show()
                this@AttachmentFragment.dismiss()
            }
        } catch (e: Exception) {
            this@AttachmentFragment.dismiss()
        }
    }

    private val pickDocumentLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { dataResult ->
        val documentDialogFragment = DocumentDialogFragment()
        try {
            if (dataResult.data!!.data == null) return@registerForActivityResult
            if (dataResult.resultCode == RESULT_OK) {
                val documentUri = dataResult.data!!.data!!
                val lastPathSegment = documentUri.lastPathSegment!!
                val name = lastPathSegment.substring(lastPathSegment.lastIndexOf("/") + 1)
                requireActivity().supportFragmentManager.beginTransaction().replace(R.id.chat_root, documentDialogFragment).commit()
                videoDialogViewModel.setVideoData(name to documentUri)
                this@AttachmentFragment.dismiss()
            } else {
                Snackbar.make(attachmentBinding.root, "No Document picked", Snackbar.LENGTH_LONG).show()
                this@AttachmentFragment.dismiss()
            }
        } catch (e: Exception) {
            this@AttachmentFragment.dismiss()
        }
    }

    private fun copy(uri: Uri, file: File): File{
        try {
            requireContext().contentResolver.openInputStream(uri).use { inputStream ->
                FileOutputStream(file).use { output ->
                    val buffer = ByteArray(4 * 1024) // or other buffer size
                    var read: Int
                    while (inputStream!!.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                    }
                    output.flush()
                    return file
                }
            }
        } catch (ex: IOException) {
            ex.printStackTrace()
        }
        return File("")
    }

    val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        val notGranted = arrayListOf<Boolean>()
        for (per in permissions.values) if (!per) notGranted.add(per)
        if (notGranted.isNotEmpty()) {
            CallUtils(requireActivity(), requireActivity())
                .permissionRationale(message = "HowFar needs permission to choose files and camera.\nGrant app permission")
            return@registerForActivityResult
        }
        Toast.makeText(context, "Select again", Toast.LENGTH_LONG).show()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        attachmentBinding = AttachmentFragmentBinding.inflate(inflater, container, false)
        attachmentBinding.selectImage.setOnClickListener(this)
        attachmentBinding.selectContact.setOnClickListener(this)
        attachmentBinding.selectAudio.setOnClickListener(this)
        attachmentBinding.selectVideo.setOnClickListener(this)
        attachmentBinding.selectDocument.setOnClickListener(this)
        attachmentBinding.attachmentRoot.setOnClickListener {
            requireActivity().supportFragmentManager.beginTransaction().remove(this).commit()
        }
        return attachmentBinding.root
    }

    private fun openContactPicker() {
        if (permissionsAvailable(permissionsContact, requireContext())) {
            val contactPickerIntent = Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI)
            contactPickerIntent.type = ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE
            pickContactLauncher.launch(contactPickerIntent)
        } else justMediaPermission()
    }

    private fun openAudioPicker() {
        if (permissionsAvailable(permissionsStorage, requireContext())) {
            pickAudioLauncher.launch(Intent().apply {
                type = "audio/*"
                action = Intent.ACTION_GET_CONTENT
            })
        } else justMediaPermission()
    }

    private fun openDocumentPicker() {
        if (permissionsAvailable(permissionsStorage, requireContext())) {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.type = "*/*"
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            pickDocumentLauncher.launch(intent)
        } else justMediaPermission()
    }

    private fun openVideoPicker() {
        if (permissionsAvailable(permissionsStorage, requireContext())) {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            pickVideoLauncher.launch(intent)
        } else justMediaPermission()
    }

    private val cropImage = registerForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) {
            val uriContent = result.uriContent!!
            //result.getUriFilePath(requireContext()) // optional usage
            val lastPathSegment = uriContent.lastPathSegment!!
            val name = lastPathSegment.substring(lastPathSegment.lastIndexOf("/") + 1)
            imageDialogViewModel.setImageName(name)
            imageDialogViewModel.setImageUri(uriContent)
            requireActivity().supportFragmentManager.beginTransaction().replace(R.id.chat_root, ImageDialogFragment()).commit()
            this@AttachmentFragment.dismiss()
        } else {
            val exception = result.error
            exception!!.printStackTrace()
        }
    }

    private fun justMediaPermission() {
        when {
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU -> {
                if (!permissionsAvailable(permissionsStorageT, requireContext())) permissionLauncher.launch(permissionsStorageT)
            }
            android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU -> {
                if (!permissionsAvailable(permissionsStorage, requireContext())) permissionLauncher.launch(permissionsStorage)
            }
        }
    }

    private fun launch() {
        cropImage.launch(
            options {
                this.setActivityTitle("Send image")
                this.setAllowFlipping(true)
                this.setAllowRotation(true)
                this.setAutoZoomEnabled(true)
                this.setBackgroundColor(Color.BLACK)
                this.setImageSource(includeGallery = true, includeCamera = true)
                setGuidelines(CropImageView.Guidelines.ON)
            }
        )
    }

    private fun openCanHub() {
        when {
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU -> {
                if (!permissionsAvailable(permissionsStorageT, requireContext())) permissionLauncher.launch(permissionsStorageT) else launch()
            }
            android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU -> {
                if (!permissionsAvailable(permissionsStorage, requireContext()))
                    permissionLauncher.launch(permissionsStorage) else launch()
            }
        }
    }

    companion object {
        const val TAG = "ModalBottomSheet"
    }

    override fun onClick(p0: View?) {
        when (p0?.id) {
            R.id.select_image -> openCanHub()
            R.id.select_contact -> openContactPicker()
            R.id.select_audio -> openAudioPicker()
            R.id.select_video -> openVideoPicker()
            R.id.select_document -> openDocumentPicker()
        }
    }
}

class ImageDialogFragment : Fragment() {
    private lateinit var binding: FragmentDialogChatBinding
    private val imageDialogViewModel: ImageDialogViewModel by activityViewModels()
    private var uri = Uri.EMPTY
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentDialogChatBinding.inflate(inflater, container, false)
        imageDialogViewModel.imageUri.observe(viewLifecycleOwner) {
            uri = it
            try {
                val dataPair = compressImage(it, requireContext(), null)
                Glide.with(requireContext()).load(dataPair.second).centerCrop().into(binding.dialogImage)
            } catch (e: Exception) {
            }
        }
        binding.dialogCancel.setOnClickListener {
            requireActivity().supportFragmentManager.beginTransaction().remove(this).commit()
        }
        binding.dialogOk.setOnClickListener {
            imageDialogViewModel.setSendImage(uri)
            requireActivity().supportFragmentManager.beginTransaction().remove(this).commit()
        }
        return binding.root
    }
}

class AudioDialogFragment : Fragment() {
    private lateinit var binding: FragmentDialogChatBinding
    private val audioDialogViewModel: AudioDialogViewModel by activityViewModels()
    private var audioUri: Uri? = null
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentDialogChatBinding.inflate(inflater, container, false)
        audioDialogViewModel.audio.observe(viewLifecycleOwner) {
            audioUri = it.second
            //val msg = "Send"
            //binding.dialogImageMsg.text = msg
            binding.dialogImage.setImageResource(R.drawable.wave)
        }
        binding.dialogCancel.setOnClickListener {
            requireActivity().supportFragmentManager.beginTransaction().remove(this).commit()
        }
        binding.dialogOk.setOnClickListener {
            if (audioUri != null) {
                audioDialogViewModel.setSendAudio(true to audioUri!!)
                requireActivity().supportFragmentManager.beginTransaction().remove(this).commit()
            }
        }
        return binding.root
    }
}

class DocumentDialogFragment : Fragment() {
    private lateinit var binding: FragmentDialogChatBinding
    private val documentDialogViewModel: DocumentDialogViewModel by activityViewModels()
    private var document: Uri? = null
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentDialogChatBinding.inflate(inflater, container, false)
        documentDialogViewModel.document.observe(viewLifecycleOwner) {
            document = it.second
            //val msg = "Send"
            //binding.dialogImageMsg.text = msg
            binding.dialogImage.setImageResource(R.drawable.ic_document)
        }
        binding.dialogCancel.setOnClickListener {
            requireActivity().supportFragmentManager.beginTransaction().remove(this).commit()
        }
        binding.dialogOk.setOnClickListener {
            if (document != null) {
                documentDialogViewModel.setSendDocument(true to document!!)
                requireActivity().supportFragmentManager.beginTransaction().remove(this).commit()
            }
        }
        return binding.root
    }
}

class VideoDialogFragment : Fragment() {
    private lateinit var binding: FragmentDialogChatBinding
    private val videoDialogViewModel: VideoDialogViewModel by activityViewModels()
    private val requestOptions = RequestOptions()
    private var videoUri: Uri? = null
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentDialogChatBinding.inflate(inflater, container, false)
        videoDialogViewModel.video.observe(viewLifecycleOwner) {
            videoUri = it.second
            //val msg = "Send"
            //binding.dialogImageMsg.text = msg
            binding.dialogImage.setImageResource(R.drawable.videocamara)
            Glide.with(requireContext()).load(it.second).into(binding.dialogImage)
        }
        binding.dialogCancel.setOnClickListener {
            requireActivity().supportFragmentManager.beginTransaction().remove(this).commit()
        }
        binding.dialogOk.setOnClickListener {
            if (videoUri != null) {
                videoDialogViewModel.setSendVideo(true to videoUri!!)
                requireActivity().supportFragmentManager.beginTransaction().remove(this).commit()
            }
        }
        return binding.root
    }
}

class ChatAdapter2 : RecyclerView.Adapter<RecyclerView.ViewHolder>(), ItemTouchHelperAdapter {
    lateinit var miscHelper: ChatActivity2
    lateinit var quoteHelper: QuoteHelper
    lateinit var rv: RecyclerView
    lateinit var contactViewModel: ContactViewModel
    lateinit var itemTouchHelper: ItemTouchHelper
    lateinit var viewLifecycleOwner: LifecycleOwner
    lateinit var pref: SharedPreferences
    var groupOrChat = 0
    var groupUuid = ""
    lateinit var deleteChatsViewModel: DeleteChatsViewModel
    var receiverUID: String = ""
    private lateinit var context: Context
    lateinit var activity: Activity
    var isSupport = false
    private val myAuth = FirebaseAuth.getInstance().currentUser!!.uid
    var dataset: ArrayList<ChatData> = arrayListOf()
    private var selectedChats: ArrayList<ChatData> = arrayListOf()
    private val scope = CoroutineScope(Dispatchers.Main)
    val mediaTypes = arrayListOf(MessageType.PHOTO, MessageType.AUDIO, MessageType.VIDEO)
    private var fetch: Fetch? = null
    private var actionMode: ActionMode? = null
    var singleChatBubbleColor: String = "#660099"
    private val touchable = arrayListOf(
        SENT_TEXT, SENT_VIDEO, SENT_AUDIO, SENT_CONTACT, SENT_PHOTO,
        RECEIVED_TEXT, RECEIVED_VIDEO, RECEIVED_AUDIO, RECEIVED_CONTACT, RECEIVED_PHOTO
    )

    init {
        setHasStableIds(true)
    }

    private val actionContextModeCallback: Callback = object : Callback {

        override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            when (groupOrChat) {
                0 -> {
                    if (selectedChats.size > 1) mode!!.menuInflater.inflate(R.menu.chat_action_mode_menu, menu)
                    else if (selectedChats.size == 1) mode!!.menuInflater.inflate(R.menu.chat_action_mode_menu_single_selected, menu)
                }
                1 -> mode!!.menuInflater.inflate(R.menu.copy_mode_menu, menu)
            }
            mode!!.title = "Select"
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            return true
        }

        override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
            when (item!!.itemId) {
                R.id.delete -> {
                    deleteMessages()
                }
                R.id.info -> {
                    if (groupOrChat == 0) miscHelper.viewChatInfo(selectedChats[0])
                    mode!!.finish()
                }
                R.id.copy -> {
                    var text = ""
                    for ((index, i) in selectedChats.withIndex()) text += if (index == selectedChats.size - 1) i.msg else "${i.msg}\n"
                    val clipboard: ClipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Copied", text)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "Copied", Toast.LENGTH_LONG).show()
                    mode!!.finish()
                }
            }
            return true
        }

        @SuppressLint("NotifyDataSetChanged")
        override fun onDestroyActionMode(mode: ActionMode?) {
            actionMode = null
            selectedChats.clear()
            notifyDataSetChanged()
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun actionModeAddDatum(datum: ChatData): Boolean {
        activity.hideKeyboard()
        if (actionMode != null) {
            when (datum) {
                !in selectedChats -> {
                    selectedChats.add(datum)
                    if (selectedChats.size > 1) {
                        actionMode!!.finish()
                        actionMode = null
                        //actionMode = (activity as AppCompatActivity).startSupportActionMode(actionContextModeCallback)
                    }
                }
                in selectedChats -> {
                    selectedChats.remove(datum)
                    if (selectedChats.isEmpty()) actionMode!!.finish()
                }
            }
            notifyDataSetChanged()
            return true
        }
        return false
    }

    override fun getItemId(position: Int) = position.toLong()

    class SentTextViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val root: LinearLayout = itemView.findViewById(R.id.chat_row_root)
        val quotedRoot: CardView = itemView.findViewById(R.id.quoted_root)
        val quotedText: TextView = itemView.findViewById(R.id.quoted_text)
        val quotedStatusText: TextView = itemView.findViewById(R.id.quoted_status_text)
        val quotedImage: ImageView = itemView.findViewById(R.id.quoted_image)
        val text: com.vanniktech.emoji.EmojiTextView = itemView.findViewById(R.id.chat_msg)
        val background: LinearLayout = itemView.findViewById(R.id.chat_layout)
        val time: TextView = itemView.findViewById(R.id.chat_time)
        val notification: ImageView = itemView.findViewById(R.id.chat_notification)

        // PHOTO-VIEW
        val image: ImageView = itemView.findViewById(R.id.chat_img)
        val imageRoot: CardView = itemView.findViewById(R.id.chat_image_root)

        val pointer: ImageView = itemView.findViewById(R.id.pointer)

        // AUDIO-VIEW
        val chatAudioRoot: LinearLayout = itemView.findViewById(R.id.chat_audio_root)
        val playImage: ImageView = itemView.findViewById(R.id.chat_play_audio)
        val pause: ImageView = itemView.findViewById(R.id.chat_pause_audio)
        val chatPlayDownload: ImageView = itemView.findViewById(R.id.chat_play_download)
        val chatPlayProgress: ProgressBar = itemView.findViewById(R.id.chat_play_progress)
        val chatPlayCancel: ImageView = itemView.findViewById(R.id.chat_play_cancel)
        val seekBar: SeekBar = itemView.findViewById(R.id.chat_seek_var)

        // VIDEO-VIEW
        val videoRoot: ConstraintLayout = itemView.findViewById(R.id.chat_video_root)
        val videoImage: ImageView = itemView.findViewById(R.id.chat_video)

        // CONTACT
        val chatContactRoot: LinearLayout = itemView.findViewById(R.id.chat_contact_root)
        val contactImage: ImageView = itemView.findViewById(R.id.chat_image)
        val contactName: com.vanniktech.emoji.EmojiTextView = itemView.findViewById(R.id.chat_name)


        fun clearSentViews() {
            text.visibility = View.GONE
            imageRoot.visibility = View.GONE
            chatAudioRoot.visibility = View.GONE
            videoRoot.visibility = View.GONE
            chatContactRoot.visibility = View.GONE
        }
    }

    class ReceivedTextViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val root: LinearLayout = itemView.findViewById(R.id.chat_row_root)
        val quotedRoot: CardView = itemView.findViewById(R.id.quoted_root)
        val quotedStatusText: TextView = itemView.findViewById(R.id.quoted_status_text)
        val quotedText: TextView = itemView.findViewById(R.id.quoted_text)
        val quotedImage: ImageView = itemView.findViewById(R.id.quoted_image)
        val text: com.vanniktech.emoji.EmojiTextView = itemView.findViewById(R.id.chat_msg)
        val username: com.vanniktech.emoji.EmojiTextView = itemView.findViewById(R.id.chat_user)
        val chatReceiverImage: ShapeableImageView = itemView.findViewById(R.id.chat_receiver_image)

        val background: LinearLayout = itemView.findViewById(R.id.chat_layout)
        val time: TextView = itemView.findViewById(R.id.chat_time)

        // PHOTO-VIEW
        val image: ImageView = itemView.findViewById(R.id.chat_img)
        val imageRoot: CardView = itemView.findViewById(R.id.chat_image_root)

        val pointer: ImageView = itemView.findViewById(R.id.pointer)

        // AUDIO-VIEW
        val chatAudioRoot: LinearLayout = itemView.findViewById(R.id.chat_audio_root)
        val playImage: ImageView = itemView.findViewById(R.id.chat_play_audio)
        val pause: ImageView = itemView.findViewById(R.id.chat_pause_audio)
        val chatPlayDownload: ImageView = itemView.findViewById(R.id.chat_play_download)
        val chatPlayProgress: ProgressBar = itemView.findViewById(R.id.chat_play_progress)
        val chatPlayCancel: ImageView = itemView.findViewById(R.id.chat_play_cancel)
        val seekBar: SeekBar = itemView.findViewById(R.id.chat_seek_var)

        // VIDEO-VIEW
        val videoRoot: ConstraintLayout = itemView.findViewById(R.id.chat_video_root)
        val videoImage: ImageView = itemView.findViewById(R.id.chat_video)

        // CONTACT
        val chatContactRoot: LinearLayout = itemView.findViewById(R.id.chat_contact_root)
        val contactImage: ImageView = itemView.findViewById(R.id.chat_image)
        val contactName: com.vanniktech.emoji.EmojiTextView = itemView.findViewById(R.id.chat_name)

        fun clearReceivedViews() {
            text.visibility = View.GONE
            imageRoot.visibility = View.GONE
            chatAudioRoot.visibility = View.GONE
            videoRoot.visibility = View.GONE
            chatContactRoot.visibility = View.GONE
        }
    }

    class ChatDayViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val chatDay: TextView = itemView.findViewById(R.id.chat_day)
    }

    class AddedRemovedViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val message: TextView = itemView.findViewById(R.id.message)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        context = parent.context
        return when (viewType) {
            in arrayListOf(SENT_TEXT, SENT_VIDEO, SENT_AUDIO, SENT_CONTACT, SENT_PHOTO) -> {
                val viewMe = LayoutInflater.from(context).inflate(R.layout.row_sent_text_chat, parent, false)
                SentTextViewHolder(viewMe)
            }
            in arrayListOf(RECEIVED_TEXT, RECEIVED_VIDEO, RECEIVED_AUDIO, RECEIVED_CONTACT, RECEIVED_PHOTO) -> {
                val viewReceived = LayoutInflater.from(context).inflate(R.layout.row_received_text_chat, parent, false)
                ReceivedTextViewHolder(viewReceived)
            }
            CHAT_DAY -> {
                val view = LayoutInflater.from(context).inflate(R.layout.row_chat_day, parent, false)
                ChatDayViewHolder(view)
            }
            ADDED_TO_GROUP, REMOVED_FROM_GROUP -> {
                val view = LayoutInflater.from(context).inflate(R.layout.row_chat_day, parent, false)
                ChatDayViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(context).inflate(R.layout.group_added_removed, parent, false)
                AddedRemovedViewHolder(view)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility", "NotifyDataSetChanged")
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        clearSentViews(holder, position)
        singleChatBubbleColor = pref.getString(context.getString(R.string.chatBubbleColor) + receiverUID, "#660099")!!
        var datum = dataset[position]
        viewHolderInit(holder, position, datum)
        onTouchListener(holder, position)
        itemClickListener(position, datum, holder)
        onClickListeners(position, datum, holder)
        holder.itemView.setOnLongClickListener {
            if (actionMode != null) return@setOnLongClickListener false
            if (actionMode == null) {
                if (getItemViewType(position) in touchable) {
                    selectedChats.add(datum)
                    actionMode = (activity as AppCompatActivity).startSupportActionMode(actionContextModeCallback)
                    notifyDataSetChanged()
                    return@setOnLongClickListener true
                }
            }
            return@setOnLongClickListener true
        }
        chatBubble(holder)
    }

    private fun clearSentViews(holder: RecyclerView.ViewHolder, position: Int) {
        when {
            getItemViewType(position) in arrayListOf(SENT_TEXT, SENT_VIDEO, SENT_AUDIO, SENT_CONTACT, SENT_PHOTO) -> {
                (holder as SentTextViewHolder).clearSentViews()
            }
            getItemViewType(position) in arrayListOf(RECEIVED_TEXT, RECEIVED_VIDEO, RECEIVED_AUDIO, RECEIVED_CONTACT, RECEIVED_PHOTO) -> {
                (holder as ReceivedTextViewHolder).clearReceivedViews()
            }
        }
    }

    private fun chatBubble(holder: RecyclerView.ViewHolder) {
        val unwrappedDrawableDay = AppCompatResources.getDrawable(context, R.drawable._20dp_corner_left)
        val unwrappedDrawableRight = AppCompatResources.getDrawable(context, R.drawable.chat_bubble_purple_right)
        val unwrappedDrawableLeft = AppCompatResources.getDrawable(context, R.drawable.chat_bubble_purple_left)
        val unwrappedDrawableRightPointer = AppCompatResources.getDrawable(context, R.drawable.right_quote)
        val unwrappedDrawableLeftPointer = AppCompatResources.getDrawable(context, R.drawable.left_quote)
        val wrappedDrawableDay = DrawableCompat.wrap(unwrappedDrawableDay!!)
        val wrappedDrawableRight = DrawableCompat.wrap(unwrappedDrawableRight!!)
        val wrappedDrawableLeft = DrawableCompat.wrap(unwrappedDrawableLeft!!)
        val wrappedDrawableRightPointer = DrawableCompat.wrap(unwrappedDrawableRightPointer!!)
        val wrappedDrawableLeftPointer = DrawableCompat.wrap(unwrappedDrawableLeftPointer!!)
        val sentColor = singleChatBubbleColor.replace("#", "#99")
        DrawableCompat.setTint(wrappedDrawableRight, Color.parseColor(sentColor))
        DrawableCompat.setTint(wrappedDrawableLeft, Color.parseColor(singleChatBubbleColor))
        DrawableCompat.setTint(wrappedDrawableDay, Color.parseColor(singleChatBubbleColor))
        DrawableCompat.setTint(wrappedDrawableRightPointer, Color.parseColor(singleChatBubbleColor))
        DrawableCompat.setTint(wrappedDrawableLeftPointer, Color.parseColor(singleChatBubbleColor))

        when (getItemViewType(holder.bindingAdapterPosition)) {
            SENT_TEXT -> {
                (holder as SentTextViewHolder).background.background = wrappedDrawableRight
                holder.pointer.background = wrappedDrawableRightPointer
            }
            RECEIVED_TEXT -> {
                (holder as ReceivedTextViewHolder).background.background = wrappedDrawableLeft
                holder.pointer.background = wrappedDrawableLeftPointer
            }
            CHAT_DAY -> (holder as ChatDayViewHolder).chatDay.background = wrappedDrawableDay
        }
    }

    private fun viewHolderInit(holder: RecyclerView.ViewHolder, position: Int, datum: ChatData) {
        val requestOptions = RequestOptions()
        var choice = (if (datum.uniqueQuerableTime == "") TimeUtils.UTCToLocal(datum.timesent) else TimeUtils.UTCToLocal(datum.timesent))
        val formattedTime = Util.formatDateTime(choice)
        val audioHandler = Handler(Looper.getMainLooper())
        val fireRef = FirebaseDatabase.getInstance().reference
        val myChattingRef = fireRef.child(CHAT_REFERENCE).child(myAuth).child(otherParticipant(datum.participants)).child(datum.uniqueQuerableTime)

        when {
            getItemViewType(position) in arrayListOf(SENT_TEXT, SENT_VIDEO, SENT_AUDIO, SENT_CONTACT, SENT_PHOTO) -> {
                (holder as SentTextViewHolder)
                holder.time.text = formattedTime
                when {
                    datum.quotedChatData.uniqueQuerableTime != "" || datum.replyFromStatus -> {
                        holder.quotedRoot.visibility = View.VISIBLE
                        if (datum.replyFromStatus) holder.quotedStatusText.visibility = View.VISIBLE
                        when {
                            datum.quotedChatData.imageData.storageLink != "" -> {
                                holder.quotedText.visibility = View.GONE
                                holder.quotedImage.visibility = View.VISIBLE
                                Glide.with(context).load(datum.quotedChatData.imageData.storageLink).into(holder.quotedImage)
                            }
                            datum.quotedChatData.videoData.storageLink != "" -> {
                                holder.quotedText.visibility = View.GONE
                                holder.quotedImage.visibility = View.VISIBLE
                                Glide.with(context).load(datum.quotedChatData.videoData.storageLink).into(holder.quotedImage)
                            }
                            else -> {
                                holder.quotedText.visibility = View.VISIBLE
                                holder.quotedText.text = datum.quotedChatData.msg
                            }
                        }
                    }
                    else -> holder.quotedRoot.visibility = View.GONE
                }

                if (datum.read) {
                    holder.notification.setImageResource(R.drawable.ic_read)
                } else if (datum.delivered) {
                    holder.notification.setImageResource(R.drawable.ic_delivered)
                } else if (datum.sent) {
                    holder.notification.setImageResource(R.drawable.ic_sent_msg)
                } else {
                    holder.notification.setImageResource(R.drawable.ic_error)
                }

                when (getItemViewType(position)) {
                    SENT_TEXT -> {
                        holder.text.visibility = View.VISIBLE
                        holder.text.text = datum.msg
                    }
                    SENT_PHOTO -> {
                        holder.imageRoot.visibility = View.VISIBLE
                        Glide.with(context).load(datum.imageData.storageLink).centerCrop().into(holder.image)
                    }
                    SENT_VIDEO -> {
                        holder.videoRoot.visibility = View.VISIBLE
                        requestOptions.isMemoryCacheable
                        Glide.with(context).setDefaultRequestOptions(requestOptions).load(datum.videoData.storageLink).into(holder.videoImage)
                    }
                    SENT_CONTACT -> {
                        holder.chatContactRoot.visibility = View.VISIBLE
                        holder.contactName.text = datum.phoneData.name
                    }
                    SENT_AUDIO -> {
                        val mediaPlayer = MediaPlayer()
                        holder.chatAudioRoot.visibility = View.VISIBLE
                        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC)

                        var getPosition = 0
                        val audioUrl = datum.audioData.storageLink
                        val time = datum.uniqueQuerableTime
                        val uid = datum.senderuid.substring(0, datum.senderuid.length / 2)

                        val file = File("${context.filesDir.path}/Media/Audio/$uid/$time/$time.mp3")
                        if (file.exists()) {
                            holder.playImage.visibility = View.VISIBLE
                            var runnable = Runnable {}
                            scope.launch {
                                try {
                                    holder.playImage.setOnClickListener {
                                        audioHandler.removeCallbacks(runnable)
                                        runnable = object : Runnable {
                                            override fun run() {
                                                val progress = mediaPlayer.currentPosition / 1000
                                                val duration = mediaPlayer.duration / 1000
                                                getPosition = mediaPlayer.currentPosition
                                                val pos = (((progress.toFloat()) / (duration.toFloat())) * 100).toInt()
                                                holder.seekBar.progress = pos
                                                if (pos == 100) {
                                                    activity.runOnUiThread {
                                                        holder.pause.visibility = View.GONE
                                                        holder.playImage.visibility = View.VISIBLE
                                                    }
                                                    holder.seekBar.progress = 0
                                                    audioHandler.removeCallbacks(this)
                                                    getPosition = 0
                                                }
                                                audioHandler.postDelayed(this, 1_000L)
                                            }
                                        }
                                        mediaPlayer.reset()
                                        mediaPlayer.setDataSource(file.absolutePath)
                                        mediaPlayer.prepare()
                                        activity.runOnUiThread {
                                            holder.pause.visibility = View.VISIBLE
                                            holder.playImage.visibility = View.GONE
                                            audioHandler.postDelayed(runnable, 1000L)
                                            mediaPlayer.seekTo(getPosition)
                                            mediaPlayer.start()
                                        }
                                    }
                                    holder.pause.setOnClickListener {
                                        activity.runOnUiThread {
                                            holder.pause.visibility = View.GONE
                                            holder.playImage.visibility = View.VISIBLE
                                        }
                                        getPosition = mediaPlayer.currentPosition
                                        mediaPlayer.pause()
                                        audioHandler.removeCallbacks(runnable)
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        } else downloadSentAudio(uid = uid, time = time, fileUrl = audioUrl, holder = holder, mediaPlayer = mediaPlayer)
                    }
                }
            }
            getItemViewType(position) in arrayListOf(RECEIVED_TEXT, RECEIVED_VIDEO, RECEIVED_AUDIO, RECEIVED_CONTACT, RECEIVED_PHOTO) -> {
                (holder as ReceivedTextViewHolder)
                when {
                    datum.quotedChatData.uniqueQuerableTime != "" || datum.replyFromStatus -> {
                        holder.quotedRoot.visibility = View.VISIBLE
                        if (datum.replyFromStatus) holder.quotedStatusText.visibility = View.VISIBLE
                        when {
                            datum.quotedChatData.imageData.storageLink != "" -> {
                                holder.quotedText.visibility = View.GONE
                                holder.quotedImage.visibility = View.VISIBLE
                                Glide.with(context).load(datum.quotedChatData.imageData.storageLink).into(holder.quotedImage)
                            }
                            datum.quotedChatData.videoData.storageLink != "" -> {
                                holder.quotedText.visibility = View.GONE
                                holder.quotedImage.visibility = View.VISIBLE
                                Glide.with(context).load(datum.quotedChatData.videoData.storageLink).into(holder.quotedImage)
                            }
                            else -> {
                                holder.quotedText.visibility = View.VISIBLE
                                holder.quotedText.text = datum.quotedChatData.msg
                            }
                        }
                    }
                    else -> holder.quotedRoot.visibility = View.GONE
                }
                when (groupOrChat) {
                    1 -> {
                        val other = otherParticipant(datum)
                        holder.username.visibility = View.VISIBLE
                        holder.chatReceiverImage.visibility = View.VISIBLE
                        holder.username.text = other.tempName
                        Glide.with(context).load(other.tempImage).error(R.drawable.ic_avatar).into(holder.chatReceiverImage)

                        val senderProfileRef = fireRef.child(USER_DETAILS).child(datum.senderuid)
                        val profileLiveData = ValueEventLiveData(senderProfileRef)
                        profileLiveData.observe(viewLifecycleOwner) {
                            when (it.second) {
                                onDataChange -> {
                                    val profile = it.first.getValue(UserProfile::class.java)!!
                                    holder.username.text = profile.name
                                    Glide.with(context).load(profile.image).error(R.drawable.ic_avatar).into(holder.chatReceiverImage)
                                }
                            }
                        }
                    }
                    else -> {
                        holder.time.text = formattedTime
                        if (groupOrChat == 0) {
                            if (!datum.read) {
                                when (datum.isSupport) {
                                    true -> {
                                        val supportRef = FirebaseDatabase.getInstance().reference
                                            .child(CHAT_REFERENCE)
                                            .child(myAuth)
                                            .child(CONTACT_SUPPORT)
                                            .child(datum.uniqueQuerableTime)
                                        supportRef.get().addOnSuccessListener {
                                            if (it.exists()) {
                                                val timeRef = FirebaseDatabase.getInstance().reference.child("time").child(myAuth)
                                                timeRef.setValue(ServerValue.TIMESTAMP).addOnSuccessListener {
                                                    timeRef.get().addOnSuccessListener { time ->
                                                        datum.read = true
                                                        datum.timeseen = time.value.toString()
                                                        supportRef.setValue(datum)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    false -> {
                                        val receiverChattingRef = FirebaseDatabase.getInstance().reference
                                            .child(CHAT_REFERENCE)
                                            .child(otherParticipant(datum.participants))
                                            .child(myAuth)
                                            .child(datum.uniqueQuerableTime)
                                        receiverChattingRef.get().addOnSuccessListener { retrieveSnapshot ->
                                            if (retrieveSnapshot.exists()) {
                                                val timeRef = FirebaseDatabase.getInstance().reference.child("time").child(myAuth)
                                                timeRef.setValue(ServerValue.TIMESTAMP).addOnSuccessListener {
                                                    timeRef.get().addOnSuccessListener { time ->
                                                        datum.read = true
                                                        datum.timeseen = time.value.toString()
                                                        receiverChattingRef.setValue(datum)
                                                        myChattingRef.get()
                                                            .addOnSuccessListener { myChatSnap -> if (myChatSnap.exists()) myChattingRef.setValue(datum) }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                when (getItemViewType(position)) {
                    RECEIVED_TEXT -> {
                        holder.text.visibility = View.VISIBLE
                        holder.text.text = datum.msg
                    }
                    RECEIVED_PHOTO -> {
                        holder.imageRoot.visibility = View.VISIBLE
                        Glide.with(context).load(datum.imageData.storageLink).centerCrop().into(holder.image)
                    }
                    RECEIVED_AUDIO -> {
                        holder.chatAudioRoot.visibility = View.VISIBLE
                        val mediaPlayer = MediaPlayer()
                        var getPosition = 0
                        val audioUrl = datum.audioData.storageLink
                        val time = datum.uniqueQuerableTime
                        val uid = datum.senderuid.substring(0, datum.senderuid.length / 2)

                        val file = File("${context.filesDir.path}/Media/Audio/$uid/$time/$time.mp3")
                        if (file.exists()) {
                            holder.playImage.visibility = View.VISIBLE
                            var runnable = Runnable {}
                            scope.launch {
                                try {
                                    holder.playImage.setOnClickListener {
                                        audioHandler.removeCallbacks(runnable)
                                        runnable = object : Runnable {
                                            override fun run() {
                                                val progress = mediaPlayer.currentPosition / 1000
                                                val duration = mediaPlayer.duration / 1000
                                                getPosition = mediaPlayer.currentPosition
                                                val pos = (((progress.toFloat()) / (duration.toFloat())) * 100).toInt()
                                                holder.seekBar.progress = pos
                                                if (pos == 100) {
                                                    activity.runOnUiThread {
                                                        holder.pause.visibility = View.GONE
                                                        holder.playImage.visibility = View.VISIBLE
                                                    }
                                                    holder.seekBar.progress = 0
                                                    audioHandler.removeCallbacks(this)
                                                    getPosition = 0
                                                }
                                                audioHandler.postDelayed(this, 1_000L)
                                            }
                                        }
                                        mediaPlayer.reset()
                                        mediaPlayer.setDataSource(file.absolutePath)
                                        mediaPlayer.prepare()
                                        activity.runOnUiThread {
                                            holder.pause.visibility = View.VISIBLE
                                            holder.playImage.visibility = View.GONE
                                            audioHandler.postDelayed(runnable, 1000L)
                                            mediaPlayer.seekTo(getPosition)
                                            mediaPlayer.start()
                                        }
                                    }
                                    holder.pause.setOnClickListener {
                                        activity.runOnUiThread {
                                            holder.pause.visibility = View.GONE
                                            holder.playImage.visibility = View.VISIBLE
                                        }
                                        getPosition = mediaPlayer.currentPosition
                                        mediaPlayer.pause()
                                        audioHandler.removeCallbacks(runnable)
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        } else downloadReceivedAudio(uid = uid, time = time, fileUrl = audioUrl, holder = holder, mediaPlayer = mediaPlayer)
                    }
                    RECEIVED_VIDEO -> {
                        holder.videoRoot.visibility = View.VISIBLE
                        requestOptions.isMemoryCacheable
                        Glide.with(context).setDefaultRequestOptions(requestOptions).load(datum.videoData.storageLink).into(holder.videoImage)
                    }
                    RECEIVED_CONTACT -> {
                        holder.chatContactRoot.visibility = View.VISIBLE
                        holder.contactName.text = datum.phoneData.name
                    }
                }
                when {
                    datum.quotedChatData.uniqueQuerableTime != "" || datum.replyFromStatus -> {
                        holder.quotedRoot.visibility = View.VISIBLE
                        holder.quotedText.text = datum.quotedChatData.msg
                    }
                }
            }
        }
        when (getItemViewType(position)) {
            CHAT_DAY -> (holder as ChatDayViewHolder).chatDay.text = datum.day
            ADDED_REMOVED_ADMIN_CREATE -> (holder as AddedRemovedViewHolder).message.text = datum.msg
        }
    }

    private fun itemClickListener(position: Int, datum: ChatData, holder: RecyclerView.ViewHolder) {
        var rootView = when (datum.groupUid) {
            "" -> R.id.chat_root
            else -> R.id.group_chat_root
        }
        when {
            getItemViewType(position) in arrayListOf(SENT_TEXT, SENT_VIDEO, SENT_AUDIO, SENT_CONTACT, SENT_PHOTO) -> {
                (holder as SentTextViewHolder)
                when (getItemViewType(position)) {
                    SENT_PHOTO -> holder.image.setOnClickListener {
                        if (actionModeAddDatum(datum)) return@setOnClickListener
                        val fragment = FragmentDisplayImage()
                        val bundle = Bundle()
                        bundle.putString("image", datum.imageData.storageLink)
                        fragment.arguments = bundle
                        (activity as AppCompatActivity).supportFragmentManager.beginTransaction()
                            .addToBackStack("view").replace(rootView, fragment).commit()
                    }
                    SENT_VIDEO -> holder.videoImage.setOnClickListener {
                        if (actionModeAddDatum(datum)) return@setOnClickListener
                        val fragment = FragmentDisplayVideo()
                        val bundle = Bundle()
                        bundle.putString("json", Gson().toJson(datum))
                        bundle.putString("video", datum.videoData.storageLink)
                        fragment.arguments = bundle
                        (activity as AppCompatActivity).supportFragmentManager.beginTransaction()
                            .addToBackStack("view").replace(rootView, fragment).commit()
                    }
                    SENT_CONTACT -> {
                        val num = Util.formatNumber(datum.phoneData.number)
                        val usersRef = FirebaseDatabase.getInstance().reference.child(USER_DETAILS)
                        var userProfile: UserProfile
                        usersRef.get().addOnSuccessListener {
                            for (i in it.children) {
                                userProfile = i.getValue(UserProfile::class.java)!!
                                if (Util.formatNumber(userProfile.phone) == num) {
                                    userProfile = i.getValue(UserProfile::class.java)!!
                                    Glide.with(context).load(userProfile.image).error(R.drawable.ic_avatar).centerCrop().into(holder.image)
                                    return@addOnSuccessListener
                                }
                            }
                        }
                        holder.chatContactRoot.setOnClickListener {
                            if (actionModeAddDatum(datum)) return@setOnClickListener
                            contactViewModel.setUserContact(Contact(name = datum.phoneData.name, mobileNumber = datum.phoneData.number))
                            (activity as AppCompatActivity).supportFragmentManager.beginTransaction().addToBackStack("contact")
                                .replace(rootView, FragmentContactFromChat()).commit()
                        }
                    }
                }
            }
            getItemViewType(position) in arrayListOf(RECEIVED_TEXT, RECEIVED_VIDEO, RECEIVED_AUDIO, RECEIVED_CONTACT, RECEIVED_PHOTO) -> {
                (holder as ReceivedTextViewHolder)
                when (getItemViewType(position)) {
                    RECEIVED_PHOTO -> holder.image.setOnClickListener {
                        if (actionModeAddDatum(datum)) return@setOnClickListener
                        val fragment = FragmentDisplayImage()
                        val bundle = Bundle()
                        bundle.putString("image", datum.imageData.storageLink)
                        fragment.arguments = bundle
                        (activity as AppCompatActivity).supportFragmentManager.beginTransaction()
                            .addToBackStack("view").replace(rootView, fragment).commit()
                    }
                    RECEIVED_VIDEO -> holder.videoImage.setOnClickListener {
                        if (actionModeAddDatum(datum)) return@setOnClickListener
                        val fragment = FragmentDisplayVideo()
                        val bundle = Bundle()
                        bundle.putString("json", Gson().toJson(datum))
                        bundle.putString("video", datum.videoData.storageLink)
                        fragment.arguments = bundle
                        (activity as AppCompatActivity).supportFragmentManager.beginTransaction()
                            .addToBackStack("view").replace(rootView, fragment).commit()
                    }
                    RECEIVED_CONTACT -> {
                        val num = Util.formatNumber(datum.phoneData.number)
                        val usersRef = FirebaseDatabase.getInstance().reference.child(USER_DETAILS)
                        var userProfile: UserProfile
                        usersRef.get().addOnSuccessListener {
                            for (i in it.children) {
                                userProfile = i.getValue(UserProfile::class.java)!!
                                if (Util.formatNumber(userProfile.phone) == num) {
                                    userProfile = i.getValue(UserProfile::class.java)!!
                                    Glide.with(context).load(userProfile.image).error(R.drawable.ic_avatar).centerCrop().into(holder.image)
                                    return@addOnSuccessListener
                                }
                            }
                        }
                        holder.chatContactRoot.setOnClickListener {
                            if (actionModeAddDatum(datum)) return@setOnClickListener
                            contactViewModel.setUserContact(Contact(name = datum.phoneData.name, mobileNumber = datum.phoneData.number))
                            (activity as AppCompatActivity).supportFragmentManager.beginTransaction().addToBackStack("contact")
                                .replace(rootView, FragmentContactFromChat()).commit()
                        }
                    }
                }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun onTouchListener(holder: RecyclerView.ViewHolder, position: Int) {
        holder.itemView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.AXIS_HSCROLL -> {
                    if (getItemViewType(position) in touchable) if (actionMode == null) {
                        itemTouchHelper.startSwipe(holder)
                    }
                    return@setOnTouchListener true
                }
            }
            return@setOnTouchListener false
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun onClickListeners(position: Int, datum: ChatData, holder: RecyclerView.ViewHolder) {
        val transparent = context.resources.getColor(R.color.transparent)
        if (datum in selectedChats) holder.itemView.setBackgroundColor(Color.parseColor(singleChatBubbleColor))
        else holder.itemView.setBackgroundColor(transparent)

        holder.itemView.setOnClickListener {
            if (getItemViewType(position) in touchable) if (actionMode != null) {
                when (datum) {
                    !in selectedChats -> selectedChats.add(datum)
                    in selectedChats -> {
                        selectedChats.remove(datum)
                        if (selectedChats.isEmpty()) actionMode!!.finish()
                    }
                }
                notifyDataSetChanged()
            }
        }
        if (actionMode == null) when {
            getItemViewType(position) in arrayListOf(SENT_TEXT, SENT_VIDEO, SENT_AUDIO, SENT_CONTACT, SENT_PHOTO) -> {
                (holder as SentTextViewHolder).quotedRoot.setOnClickListener { scrollOnClickQuotedText(datum) }
            }
            getItemViewType(position) in arrayListOf(RECEIVED_TEXT, RECEIVED_VIDEO, RECEIVED_AUDIO, RECEIVED_CONTACT, RECEIVED_PHOTO) -> {
                (holder as ReceivedTextViewHolder).quotedRoot.setOnClickListener { scrollOnClickQuotedText(datum) }
            }
        }
    }

    private fun scrollOnClickQuotedText(datum: ChatData) {
        var scrollPosition: Int
        if (datum.replyFromStatus) {
            return
        }
        for ((index, i) in dataset.withIndex())
            if (i.uniqueQuerableTime == datum.quotedChatData.uniqueQuerableTime && i.senderuid == datum.quotedChatData.senderuid) {
                scrollPosition = index
                quoteHelper.smoothScrollToPosition(scrollPosition)
                return
            }
    }

    private fun deleteMessages() {
        activity.hideKeyboard()
        val alertDialog = AlertDialog.Builder(context)
        alertDialog.setTitle("Delete message")
        var allMine = true
        for (msg in selectedChats) if (msg.senderuid != myAuth) {
            allMine = false
            break
        }
        when (allMine) {
            false -> alertDialog.setItems(arrayOf("Delete for me", "Cancel")) { dialog, which ->
                println("Which ********************************* $which")
                when (which) {
                    0 -> {
                        when (groupOrChat) {
                            0 -> chatDeleteForMe()
                            1 -> groupDeleteForMe()
                        }
                    }
                    2 -> dialog.dismiss()
                }
                if (actionMode != null) actionMode!!.finish()
            }
            true -> alertDialog.setItems(arrayOf("Delete for me", "Delete for everyone", "Cancel")) { dialog, which ->
                when (which) {
                    0 -> {
                        when (groupOrChat) {
                            0 -> chatDeleteForMe()
                            1 -> groupDeleteForMe()
                        }
                        if (actionMode != null) actionMode!!.finish()
                    }
                    1 -> {
                        when (groupOrChat) {
                            0 -> chatDeleteForEveryOne()
                            1 -> groupDeleteForEveryOne()
                        }
                    }
                    2 -> dialog.dismiss()
                }
            }
        }
        alertDialog.create().show()
    }

    private fun chatDeleteForMe() {
        var myChattingRef = FirebaseDatabase.getInstance().reference
            .child(CHAT_REFERENCE)
            .child(myAuth)
            .child(otherParticipant(selectedChats.first().participants))
        for (i in selectedChats) myChattingRef.child(i.uniqueQuerableTime).removeValue()
    }

    private fun chatDeleteForEveryOne() {
        var receiverChattingRef = FirebaseDatabase.getInstance().reference
            .child(CHAT_REFERENCE)
            .child(otherParticipant(selectedChats.first().participants))
            .child(myAuth)
        var myChattingRef = FirebaseDatabase.getInstance().reference
            .child(CHAT_REFERENCE)
            .child(myAuth)
            .child(otherParticipant(selectedChats.first().participants))
        for (i in selectedChats) {
            var reference = ""
            myChattingRef.child(i.uniqueQuerableTime).removeValue().addOnSuccessListener {
                receiverChattingRef.child(i.uniqueQuerableTime).removeValue()
                when (i.messagetype) {
                    MessageType.PHOTO -> reference = IMAGE_REFERENCE
                    MessageType.AUDIO -> reference = AUDIO_REFERENCE
                    MessageType.VIDEO -> reference = VIDEO_REFERENCE
                }
                if (i.messagetype in mediaTypes)
                    FirebaseStorage.getInstance().reference.child(reference).child(i.senderuid).child(i.uniqueQuerableTime).delete()
            }
        }
        if (actionMode != null) actionMode!!.finish()
    }

    private fun groupDeleteForMe() {
        for (i in selectedChats) {
            FirebaseDatabase.getInstance().reference.child(MY_GROUPS_MESSAGES).child(myAuth).child(groupUuid).child(i.uniqueQuerableTime).removeValue()
        }
    }

    private fun groupDeleteForEveryOne() {
        val groupRef = FirebaseDatabase.getInstance().reference.child(GROUPS).child(groupUuid)
        groupRef.get().addOnSuccessListener {
            if (it.exists()) {
                val groupProfile = it.getValue(GroupProfileData::class.java)!!
                for (i in selectedChats) for (auth in groupProfile.members) {
                    var reference = ""
                    FirebaseDatabase.getInstance().reference.child(MY_GROUPS_MESSAGES).child(auth).child(groupUuid).child(i.uniqueQuerableTime).removeValue()
                    when (i.messagetype) {
                        MessageType.PHOTO -> reference = GROUP_IMAGES
                        MessageType.AUDIO -> reference = GROUP_AUDIOS
                        MessageType.VIDEO -> reference = GROUP_VIDEOS
                    }
                    if (i.messagetype in mediaTypes)
                        FirebaseStorage.getInstance().reference.child(reference).child(i.groupUid).child(i.uniqueQuerableTime).delete()
                }
            }
            if (actionMode != null) actionMode!!.finish()
        }
    }

    private fun otherParticipant(participants: ArrayList<String>): String {
        val myAuth = FirebaseAuth.getInstance().currentUser!!.uid
        for (i in participants) return if (i != myAuth) i else participants[1]
        return ""
    }

    private fun otherParticipant(chatUser: ChatData): ParticipantTempData {
        val myAuth = FirebaseAuth.getInstance().currentUser!!.uid
        for (i in chatUser.participantsTempData) if (i.uid != myAuth) return i
        return ParticipantTempData()
    }

    private fun downloadSentAudio(uid: String, time: String, fileUrl: String, holder: SentTextViewHolder, mediaPlayer: MediaPlayer) {
        val dir = File("${context.filesDir.path}/Media/Audio/$uid/$time/")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "$time.mp3")
        val uriFromFile = Uri.fromFile(file)

        clearAllSentAudioViews(holder)
        holder.chatPlayDownload.visibility = View.VISIBLE
        val fetchListener: FetchListener = object : FetchListener {
            override fun onQueued(@NotNull download: Download, waitingOnNetwork: Boolean) = Unit
            override fun onRemoved(download: Download) = Unit
            override fun onResumed(download: Download) = Unit
            override fun onWaitingNetwork(download: Download) = Unit
            override fun onAdded(download: Download) = Unit

            override fun onStarted(download: Download, downloadBlocks: List<DownloadBlock>, totalBlocks: Int) {
                clearAllSentAudioViews(holder)
                holder.chatPlayProgress.visibility = View.VISIBLE
                holder.chatPlayCancel.visibility = View.VISIBLE
            }

            override fun onCancelled(download: Download) {
                clearAllSentAudioViews(holder)
                holder.chatPlayDownload.visibility = View.VISIBLE
            }

            override fun onCompleted(download: Download) {
                try {
                    clearAllSentAudioViews(holder)
                    holder.playImage.visibility = View.VISIBLE

                    val audioHandler = Handler(Looper.getMainLooper())
                    mediaPlayer.setDataSource(file.path)
                    mediaPlayer.prepare()
                    var getPosition = 0
                    val runnable = object : Runnable {
                        override fun run() {
                            val progress = mediaPlayer.currentPosition / 1000
                            val duration = mediaPlayer.duration / 1000
                            getPosition = mediaPlayer.currentPosition
                            val pos = (((progress.toFloat()) / (duration.toFloat())) * 100).toInt()
                            holder.seekBar.progress = pos
                            if (pos == 100) {
                                activity.runOnUiThread {
                                    holder.pause.visibility = View.GONE
                                    holder.playImage.visibility = View.VISIBLE
                                }
                                holder.seekBar.progress = 0
                                audioHandler.removeCallbacks(this)
                                getPosition = 0
                            }
                            audioHandler.postDelayed(this, 1_000L)
                        }
                    }
                    scope.launch {
                        holder.playImage.setOnClickListener {
                            playMedia(audioHandler, runnable, getPosition)
                        }
                        holder.pause.setOnClickListener {
                            playMedia(audioHandler, runnable, getPosition)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            fun playMedia(audioHandler: Handler, runnable: Runnable, getPosition: Int) {
                if (mediaPlayer.isPlaying) {
                    mediaPlayer.pause()
                    audioHandler.removeCallbacks(runnable)
                    activity.runOnUiThread {
                        clearAllSentAudioViews(holder)
                        holder.playImage.visibility = View.VISIBLE
                    }
                } else {
                    activity.runOnUiThread {
                        audioHandler.postDelayed(runnable, 1000L)
                        clearAllSentAudioViews(holder)
                        holder.pause.visibility = View.VISIBLE
                        mediaPlayer.seekTo(getPosition)
                        mediaPlayer.start()
                    }
                }
            }

            override fun onDeleted(download: Download) = Unit

            override fun onDownloadBlockUpdated(download: Download, downloadBlock: DownloadBlock, totalBlocks: Int) = Unit

            override fun onError(download: Download, error: Error, throwable: Throwable?) = Unit

            override fun onPaused(download: Download) = Unit

            override fun onProgress(@NotNull download: Download, etaInMilliSeconds: Long, downloadedBytesPerSecond: Long) {
                clearAllSentAudioViews(holder)
                holder.chatPlayProgress.visibility = View.VISIBLE
                holder.chatPlayCancel.visibility = View.VISIBLE
                val progress = download.progress
                if (progress >= 1) holder.chatPlayProgress.isIndeterminate = false
                holder.chatPlayProgress.progress = progress
            }
        }

        holder.chatPlayDownload.setOnClickListener {
            val fetchConfiguration: FetchConfiguration = FetchConfiguration.Builder(context)
                .setDownloadConcurrentLimit(20)
                .build()
            fetch = Fetch.getInstance(fetchConfiguration)

            val request = Request(fileUrl, uriFromFile)
            request.priority = Priority.HIGH
            request.networkType = NetworkType.ALL
            request.addHeader("clientKey", fileUrl)

            fetch!!.enqueue(request, { updatedRequest ->
                println("updatedRequest ********************************************* ${updatedRequest.file}")
            }) { error ->
                println("Error ********************************************* $error")
            }
            fetch!!.addListener(fetchListener)
        }
    }

    private fun downloadReceivedAudio(uid: String, time: String, fileUrl: String, holder: ReceivedTextViewHolder, mediaPlayer: MediaPlayer) {
        val dir = File("${context.filesDir.path}/Media/Audio/$uid/$time/")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "$time.mp3")
        val uriFromFile = Uri.fromFile(file)

        clearAllReceivedAudioViews(holder)
        holder.chatPlayDownload.visibility = View.VISIBLE

        val fetchListener: FetchListener = object : FetchListener {
            override fun onQueued(@NotNull download: Download, waitingOnNetwork: Boolean) = Unit
            override fun onRemoved(download: Download) = Unit
            override fun onResumed(download: Download) = Unit
            override fun onWaitingNetwork(download: Download) = Unit
            override fun onAdded(download: Download) = Unit
            override fun onDeleted(download: Download) = Unit
            override fun onDownloadBlockUpdated(download: Download, downloadBlock: DownloadBlock, totalBlocks: Int) = Unit
            override fun onError(download: Download, error: Error, throwable: Throwable?) = Unit
            override fun onPaused(download: Download) = Unit

            override fun onStarted(download: Download, downloadBlocks: List<DownloadBlock>, totalBlocks: Int) {
                clearAllReceivedAudioViews(holder)
                holder.chatPlayProgress.visibility = View.VISIBLE
                holder.chatPlayCancel.visibility = View.VISIBLE
            }

            override fun onCancelled(download: Download) {
                clearAllReceivedAudioViews(holder)
                holder.chatPlayDownload.visibility = View.VISIBLE
            }

            override fun onCompleted(download: Download) {
                try {
                    clearAllReceivedAudioViews(holder)
                    holder.playImage.visibility = View.VISIBLE

                    val audioHandler = Handler(Looper.getMainLooper())
                    mediaPlayer.setDataSource(file.path)
                    mediaPlayer.prepare()
                    var getPosition = 0
                    val runnable = object : Runnable {
                        override fun run() {
                            val progress = mediaPlayer.currentPosition / 1000
                            val duration = mediaPlayer.duration / 1000
                            getPosition = mediaPlayer.currentPosition
                            val pos = (((progress.toFloat()) / (duration.toFloat())) * 100).toInt()
                            holder.seekBar.progress = pos
                            if (pos == 100) {
                                activity.runOnUiThread {
                                    holder.pause.visibility = View.GONE
                                    holder.playImage.visibility = View.VISIBLE
                                }
                                holder.seekBar.progress = 0
                                audioHandler.removeCallbacks(this)
                                getPosition = 0
                            }
                            audioHandler.postDelayed(this, 1_000L)
                        }
                    }
                    scope.launch {
                        holder.playImage.setOnClickListener {
                            playMedia(audioHandler, runnable, getPosition)
                        }
                        holder.pause.setOnClickListener {
                            playMedia(audioHandler, runnable, getPosition)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            fun playMedia(audioHandler: Handler, runnable: Runnable, getPosition: Int) {
                if (mediaPlayer.isPlaying) {
                    mediaPlayer.pause()
                    audioHandler.removeCallbacks(runnable)
                    activity.runOnUiThread {
                        clearAllReceivedAudioViews(holder)
                        holder.playImage.visibility = View.VISIBLE
                    }
                } else {
                    activity.runOnUiThread {
                        audioHandler.postDelayed(runnable, 1000L)
                        clearAllReceivedAudioViews(holder)
                        holder.pause.visibility = View.VISIBLE
                        mediaPlayer.seekTo(getPosition)
                        mediaPlayer.start()
                    }
                }
            }

            override fun onProgress(@NotNull download: Download, etaInMilliSeconds: Long, downloadedBytesPerSecond: Long) {
                clearAllReceivedAudioViews(holder)
                holder.chatPlayProgress.visibility = View.VISIBLE
                holder.chatPlayCancel.visibility = View.VISIBLE
                val progress = download.progress
                if (progress >= 1) holder.chatPlayProgress.isIndeterminate = false
                holder.chatPlayProgress.progress = progress
            }
        }

        holder.chatPlayDownload.setOnClickListener {
            val fetchConfiguration: FetchConfiguration = FetchConfiguration.Builder(context)
                .setDownloadConcurrentLimit(20)
                .build()
            fetch = Fetch.getInstance(fetchConfiguration)

            val request = Request(fileUrl, uriFromFile)
            request.priority = Priority.HIGH
            request.networkType = NetworkType.ALL
            request.addHeader("clientKey", fileUrl)

            fetch!!.enqueue(request, {}) { error ->
                println("Error ********************************************* $error")
            }
            fetch!!.addListener(fetchListener)
        }
    }

    private fun clearAllReceivedAudioViews(holder: ReceivedTextViewHolder) {
        holder.chatPlayDownload.visibility = View.GONE
        holder.chatPlayProgress.visibility = View.GONE
        holder.chatPlayCancel.visibility = View.GONE
        holder.pause.visibility = View.GONE
        holder.playImage.visibility = View.GONE
    }

    private fun clearAllSentAudioViews(holder: SentTextViewHolder) {
        holder.chatPlayDownload.visibility = View.GONE
        holder.chatPlayProgress.visibility = View.GONE
        holder.chatPlayCancel.visibility = View.GONE
        holder.pause.visibility = View.GONE
        holder.playImage.visibility = View.GONE
    }

    private fun isVisible(rootView: View = rv, @Nullable view: View?, minPercentageViewed: Int): Boolean {
        val mClipRect = Rect()
        if (view == null || view.visibility != View.VISIBLE || rootView.parent == null) {
            return false
        }
        if (!view.getGlobalVisibleRect(mClipRect)) {
            return false
        }

        val visibleViewArea: Long = mClipRect.height().toLong() * mClipRect.width()
        val totalViewArea = view.height.toLong() * view.width
        return if (totalViewArea <= 0) {
            false
        } else 100 * visibleViewArea >= minPercentageViewed * totalViewArea
    }

    override fun getItemViewType(position: Int): Int {
        val datum = dataset[position]
        var returnMessageType = 0
        when (datum.messagetype) {
            MessageType.TEXT -> returnMessageType = if (datum.senderuid == myAuth) SENT_TEXT else RECEIVED_TEXT
            MessageType.AUDIO -> returnMessageType = if (datum.senderuid == myAuth) SENT_AUDIO else RECEIVED_AUDIO
            MessageType.VIDEO -> returnMessageType = if (datum.senderuid == myAuth) SENT_VIDEO else RECEIVED_VIDEO
            MessageType.PHOTO -> returnMessageType = if (datum.senderuid == myAuth) SENT_PHOTO else RECEIVED_PHOTO
            MessageType.CONTACT -> returnMessageType = if (datum.senderuid == myAuth) SENT_CONTACT else RECEIVED_CONTACT
            MessageType.CHAT_DAY -> returnMessageType = CHAT_DAY
            ADDED_TO_GROUP -> returnMessageType = ADDED_REMOVED_ADMIN_CREATE
            REMOVED_FROM_GROUP -> returnMessageType = ADDED_REMOVED_ADMIN_CREATE
            LEAVE_GROUP -> returnMessageType = ADDED_REMOVED_ADMIN_CREATE
            GROUP_ADMIN -> returnMessageType = ADDED_REMOVED_ADMIN_CREATE
            CREATED_GROUP -> returnMessageType = ADDED_REMOVED_ADMIN_CREATE
        }
        if (datum.day != "") returnMessageType = CHAT_DAY
        return returnMessageType
    }

    override fun getItemCount() = dataset.size

    override fun finishSwipeClear(viewHolder: RecyclerView.ViewHolder) {
        isVisible(view = viewHolder.itemView, minPercentageViewed = 50)
    }

    override fun onItemMoved(fromPosition: Int, toPosition: Int) {
    }

    override fun onItemSwiped(position: Int) {
        println("Group touched onItemSwiped ***************************************** $position")
    }

    override fun showReplyUI(pos: Int) {
        val datum = dataset[pos]
        if (!datum.sent) return
        quoteHelper.helpQuotedChat(datum)
    }

    companion object {
        const val RECEIVED_TEXT = 0
        const val SENT_TEXT = 1

        const val RECEIVED_PHOTO = 2
        const val SENT_PHOTO = 3

        const val RECEIVED_AUDIO = 4
        const val SENT_AUDIO = 5

        const val RECEIVED_VIDEO = 6
        const val SENT_VIDEO = 7

        const val RECEIVED_CONTACT = 8
        const val SENT_CONTACT = 9

        const val CHAT_DAY = 10
        const val ADDED_REMOVED_ADMIN_CREATE = 11

        const val GROUPS = "groups"
        const val TRANSFER_HISTORY = "user_coins_transfer"
        const val USER_DETAILS = "user_details"
        const val CHAT_REFERENCE = "chat_reference"
        const val CONTACT_SUPPORT = "CONTACT_SUPPORT"
        const val MY_GROUPS_MESSAGES = "my_groups_messages"
        const val IMAGE_REFERENCE = "image_reference"
        const val AUDIO_REFERENCE = "audio_reference"
        const val VIDEO_REFERENCE = "video_reference"

        const val GROUP_IMAGES = "GroupImages"
        const val GROUP_VIDEOS = "GroupImages"
        const val GROUP_AUDIOS = "GroupImages"
    }
}

interface MiscHelper {
    fun viewChatInfo(datum: ChatData)
}

interface QuoteHelper {
    fun helpQuotedChat(datum: ChatData)
    fun smoothScrollToPosition(pos: Int)
}