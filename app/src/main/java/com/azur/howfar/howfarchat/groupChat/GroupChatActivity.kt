package com.azur.howfar.howfarchat.groupChat

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioAttributes
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.provider.ContactsContract
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.webkit.MimeTypeMap
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.azur.howfar.R
import com.azur.howfar.activity.BaseActivity
import com.azur.howfar.databinding.AttachmentFragmentBinding
import com.azur.howfar.databinding.GroupChatActivityBinding
import com.azur.howfar.howfarchat.chat.*
import com.azur.howfar.howfarchat.status.FragmentViews
import com.azur.howfar.livedata.ValueEventLiveData
import com.azur.howfar.models.*
import com.azur.howfar.models.EventListenerType.onDataChange
import com.azur.howfar.utils.CallUtils
import com.azur.howfar.utils.Keyboard.hideKeyboard
import com.azur.howfar.utils.SwipeTouchCallback
import com.azur.howfar.utils.TimeUtils
import com.azur.howfar.utils.Util
import com.azur.howfar.videos.ModalBottomSheet
import com.azur.howfar.viewmodel.*
import com.azur.howfar.workManger.ChatMediaWorkManager
import com.bumptech.glide.Glide
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageView
import com.canhub.cropper.options
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.gson.Gson
import com.vanniktech.emoji.EmojiPopup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.util.*

class GroupChatActivity : BaseActivity(), View.OnClickListener, QuoteHelper {
    private val binding by lazy { GroupChatActivityBinding.inflate(layoutInflater) }
    private val dataset: ArrayList<ChatData> = arrayListOf()
    private val chatsAdapter = ChatAdapter2()
    private val myAuth = FirebaseAuth.getInstance().currentUser!!.uid
    private val scope = CoroutineScope(Dispatchers.Main)
    private var timeRef = FirebaseDatabase.getInstance().reference.child("time").child(FirebaseAuth.getInstance().currentUser!!.uid)
    private var groupRef = FirebaseDatabase.getInstance().reference
    private var myProfileRef = FirebaseDatabase.getInstance().reference
    private var groupMessagesRef = FirebaseDatabase.getInstance().reference
    private var myGroupMessagesRef = FirebaseDatabase.getInstance().reference
    private val deleteChatsViewModel by viewModels<DeleteChatsViewModel>()
    private val contactViewModel by viewModels<ContactViewModel>()
    private val chatDataViewModel by viewModels<ChatDataViewModel>()
    private val imageDialogViewModel by viewModels<ImageDialogViewModel>()
    private val audioDialogViewModel by viewModels<AudioDialogViewModel>()
    private val videoDialogViewModel by viewModels<VideoDialogViewModel>()
    private val groupProfileViewModel by viewModels<GroupProfileViewModel>()
    private var mediaPlayer = MediaPlayer()
    private lateinit var pref: SharedPreferences
    private var audioUri: Uri = Uri.EMPTY
    private var videoUri: Uri = Uri.EMPTY
    private var emojIcon: EmojiPopup? = null
    private var groupProfile = GroupProfileData()
    private var uuid = ""
    private var quotedChatData = ChatData()
    private var recorder = MediaRecorder()
    private val workManager = WorkManager.getInstance(this)

    @SuppressLint("NotifyDataSetChanged")
    private val chatListener = object : ChildEventListener {
        override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
            if (snapshot.exists()) {
                val data = snapshot.getValue(ChatData::class.java)!!
                scope.launch {
                    for (i in dataset) if (i.uniqueQuerableTime == data.uniqueQuerableTime || i.timesent == data.timesent) {
                        if (i.timesent == data.timesent) {
                            dataset[dataset.indexOf(i)] = data
                            runOnUiThread { chatsAdapter.notifyDataSetChanged() }
                        }
                        return@launch
                    }
                    runOnUiThread {
                        dataset.add(data)
                        chatsAdapter.notifyDataSetChanged()
                        binding.groupRv.scrollToPosition(dataset.size)
                    }
                }
            }
        }

        override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
            if (snapshot.exists()) {
                val data = snapshot.getValue(ChatData::class.java)!!
                dataset.remove(data)
                chatsAdapter.notifyItemRemoved(dataset.indexOf(data))
            }
        }

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
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
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
        pref = getSharedPreferences(getString(R.string.ALL_PREFERENCE), Context.MODE_PRIVATE)
        emojIcon = EmojiPopup.Builder.fromRootView(binding.groupChatRoot).setOnEmojiPopupShownListener {}.build(binding.chatInput)
        uuid = intent.getStringExtra("data")!!
        val noLongerParticipant = Snackbar.make(binding.root, "You are no longer a participant", Snackbar.LENGTH_INDEFINITE)

        myProfileRef = myProfileRef.child(USER_DETAILS).child(myAuth)
        groupRef = groupRef.child(GROUPS).child(uuid)
        myGroupMessagesRef = groupMessagesRef.child(MY_GROUPS_MESSAGES).child(myAuth).child(uuid)
        groupMessagesRef = groupMessagesRef.child(GROUPS_MESSAGES).child(uuid)

        val swipeTouchCallback = SwipeTouchCallback(chatsAdapter, this)
        val itemTouchHelper = ItemTouchHelper(swipeTouchCallback)
        itemTouchHelper.attachToRecyclerView(binding.groupRv)
        chatsAdapter.dataset = dataset
        chatsAdapter.groupOrChat = 1
        chatsAdapter.groupUuid = uuid
        chatsAdapter.activity = this
        chatsAdapter.viewLifecycleOwner = this
        chatsAdapter.deleteChatsViewModel = deleteChatsViewModel
        chatsAdapter.pref = pref
        chatsAdapter.contactViewModel = contactViewModel
        chatsAdapter.quoteHelper = this
        chatsAdapter.rv = binding.groupRv
        binding.groupRv.adapter = chatsAdapter
        binding.groupRv.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        myGroupMessagesRef.addChildEventListener(chatListener)
        recordMethods()
        viewModels()
        clickListeners()
        val groupLiveData = ValueEventLiveData(groupRef)
        groupLiveData.observe(this@GroupChatActivity) {
            when (it?.second) {
                onDataChange -> {
                    groupProfile = it.first.getValue(GroupProfileData::class.java)!!
                    val numMembers = "${groupProfile.members.size} members"
                    binding.numberMembers.text = numMembers
                    binding.groupName.text = groupProfile.groupName
                    if (myAuth !in groupProfile.members) {
                        binding.groupInputRoot.visibility = View.GONE
                        binding.groupMore.visibility = View.GONE
                        noLongerParticipant.show()
                    } else noLongerParticipant.dismiss()

                    Glide.with(this).load(groupProfile.groupImage).centerCrop().into(binding.groupImage)
                    binding.groupProfile.setOnClickListener {
                        groupProfileViewModel.setGroupProfile(groupProfile)
                        supportFragmentManager.beginTransaction().addToBackStack("group_profile")
                            .setCustomAnimations(R.anim.enter_right_to_left, R.anim.exit_right_to_left)
                            .replace(R.id.group_chat_root, FragmentGroupProfile()).commit()
                    }
                }
            }
        }
    }

    override fun onResume() {
        window.statusBarColor = Color.parseColor("#282B39")
        super.onResume()
    }

    private fun clickListeners() {
        binding.groupMore.setOnClickListener(this)
        binding.chatBack.setOnClickListener(this)
        binding.chatSendRoot.setOnClickListener(this)
        binding.chatAttachment.setOnClickListener(this)
        binding.chatEmoji.setOnClickListener(this)
        binding.quotedCancel.setOnClickListener(this)
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
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        binding.chatInput.addTextChangedListener(InputTextWatcher())
        binding.recordButton.setOnClickListener { Snackbar.make(binding.recordButton, "Hold down", Snackbar.LENGTH_LONG).show() }
        binding.recordButton.setOnTouchListener { v, event ->
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
                        showRecordView()

                        binding.recordPlay.setOnClickListener {
                            playAudio(currentVoiceNotePath)
                        }

                        binding.recordSend.setOnClickListener {
                            showChatView()
                            audioDialogViewModel.setSendAudio(true to Uri.fromFile(File(currentVoiceNotePath)))
                        }
                        binding.recordDelete.setOnClickListener {
                            showChatView()
                            Snackbar.make(binding.root, "Deleted", Snackbar.LENGTH_SHORT).show()
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

    private fun getTime(path: String): String {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(this, Uri.parse(path))
        val time = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!
        return TimeUtils.milliSecondsToTimer(time.toLong())!!
    }

    private fun playAudio(currentVoiceNotePath: String) {
        mediaPlayer = MediaPlayer()
        val iss = FileInputStream(currentVoiceNotePath)
        mediaPlayer.setDataSource(iss.fd)
        mediaPlayer.setAudioAttributes(AudioAttributes.Builder().build())
        mediaPlayer.prepare()
        if (mediaPlayer.isPlaying) mediaPlayer.stop() else mediaPlayer.start()
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

    private fun sendNewMsg(
        message: String = "", imageData: ImageData = ImageData(), audioData: AudioData = AudioData(),
        videoData: VideoData = VideoData(), phoneData: PhoneData = PhoneData()
    ) {
        if (imageData.storageLink == "" && audioData.storageLink == "" && videoData.storageLink == "" && phoneData.number == "") if (message == "") return
        val timeNow = Calendar.getInstance().timeInMillis.toString()
        var myChat = ChatData(
            senderuid = myAuth, timesent = timeNow, msg = message, displaytitle = message, messagetype = MessageType.TEXT,
            phoneData = phoneData, groupUid = uuid, imageData = imageData, audioData = audioData, videoData = videoData,
        )

        when {
            imageData.storageLink != "" -> myChat.messagetype = MessageType.PHOTO
            audioData.storageLink != "" -> myChat.messagetype = MessageType.AUDIO
            videoData.storageLink != "" -> myChat.messagetype = MessageType.VIDEO
            phoneData.number != "" -> myChat.messagetype = MessageType.CONTACT
            myChat.day != "" -> myChat.messagetype = MessageType.CHAT_DAY
        }
        when (binding.quotedRoot.visibility) {
            View.GONE -> {}
            View.VISIBLE -> myChat.quotedChatData = chatToQuotedData(quotedChatData)
        }
        binding.quotedRoot.visibility = View.GONE

        dataset.add(myChat)
        binding.groupRv.smoothScrollToPosition(dataset.size)
        chatsAdapter.notifyItemInserted(dataset.size)

        if (myChat.messagetype == MessageType.PHOTO || myChat.messagetype == MessageType.AUDIO || myChat.messagetype == MessageType.VIDEO) {
            workManagerUpload(Gson().toJson(myChat))
            return
        }
        timeRef.setValue(ServerValue.TIMESTAMP).addOnSuccessListener { void ->
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
                    val myProfileRef = FirebaseDatabase.getInstance().reference.child(FragmentViews.USER_DETAILS).child(myAuth)
                    myProfileRef.get().addOnSuccessListener { proSnap ->
                        if (proSnap.exists()) {
                            val myProfile: UserProfile = proSnap.getValue(UserProfile::class.java)!!
                            val tempProfile = ParticipantTempData(
                                tempName = myProfile.name, uid = myProfile.uid, tempImage = myProfile.image,
                                phone = Util.formatNumber(myProfile.phone)
                            )
                            myChat.myPhone = myProfile.phone
                            myChat.participantsTempData = arrayListOf(tempProfile)

                            groupRef.keepSynced(false)
                            groupRef.get().addOnSuccessListener {
                                val groupProfile = it.getValue(GroupProfileData::class.java)!!
                                groupMessagesRef.child(myChat.uniqueQuerableTime).setValue(myChat).addOnSuccessListener {
                                    distributeDisplayMessage(groupProfile, myChat)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun workManagerUpload(json: String) {
        val workRequest = OneTimeWorkRequestBuilder<ChatMediaWorkManager>().addTag("chat")
            .setInputData(workDataOf("chatData" to json))
            .build()
        workManager.enqueue(workRequest)
    }

    private fun chatToQuotedData(chatData: ChatData): QuoteChatData {
        return QuoteChatData(
            senderuid = chatData.senderuid,
            timesent = chatData.timesent,
            timeInitial = chatData.timeInitial,
            timeseen = chatData.timeseen,
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

    private fun distributeDisplayMessage(groupProfile: GroupProfileData, myChat: ChatData) {
        for (auth in groupProfile.members) {
            FirebaseDatabase.getInstance().reference.child(MY_GROUPS_MESSAGES).child(auth).child(uuid).child(myChat.uniqueQuerableTime).setValue(myChat)
        }
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
        binding.chatInput.postDelayed(kotlinx.coroutines.Runnable {
            val imm: InputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(binding.chatInput, InputMethodManager.HIDE_IMPLICIT_ONLY)
        }, 1000)
    }

    override fun smoothScrollToPosition(pos: Int) {
        binding.groupRv.smoothScrollToPosition(pos)
    }

    override fun onClick(p0: View?) {
        when (p0?.id) {
            R.id.quoted_cancel -> binding.quotedRoot.visibility = View.GONE
            R.id.chatSendRoot -> {
                val message = binding.chatInput.text.toString().trim()
                sendNewMsg(message)
                binding.chatInput.text!!.clear()
            }
            R.id.chat_back -> super.onBackPressed()
            R.id.chat_attachment -> {
                hideKeyboard()
                val modalBottomSheet = GroupAttachmentFragment()
                modalBottomSheet.show(supportFragmentManager, ModalBottomSheet.TAG)
            }
            R.id.chatEmoji -> emojIcon!!.toggle()
            R.id.group_more -> {
                if (groupProfile.uuid == "") return
                val popUp = PopupMenu(applicationContext, binding.groupMore)
                popUp.inflate(R.menu.group_menu)
                popUp.show()
                popUp.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.leave_group -> {
                            val alert = AlertDialog.Builder(this)
                            alert.setTitle("Leave ${groupProfile.groupName}")
                            alert.setMessage("Are you sure you want to exit?")
                            alert.setCancelable(false)
                            alert.setPositiveButton("Yes") { dialog, _ ->
                                val groupRef = FirebaseDatabase.getInstance().reference.child(GROUPS).child(groupProfile.uuid)
                                groupRef.get().addOnSuccessListener { groupSnapshot ->
                                    if (groupSnapshot.exists()) {
                                        var groupProfile2 = groupSnapshot.getValue(GroupProfileData::class.java)!!
                                        groupProfile2.members.remove(myAuth)
                                        if (myAuth in groupProfile2.admins && groupProfile2.admins.size == 1 && groupProfile2.members.isNotEmpty()) {
                                            groupProfile2.members.shuffle()
                                            groupProfile2.admins.add(groupProfile2.members.first())
                                        }
                                        groupRef.setValue(groupProfile2).addOnSuccessListener {
                                            val timeRef = FirebaseDatabase.getInstance().reference.child("time").child(myAuth)
                                            timeRef.setValue(ServerValue.TIMESTAMP).addOnSuccessListener {
                                                timeRef.get().addOnSuccessListener { snapshot ->
                                                    if (snapshot.exists()) {
                                                        val rawTime = snapshot.value.toString()
                                                        sendLeaveForEveryone(rawTime, groupProfile2)
                                                    }
                                                }
                                            }
                                        }.addOnFailureListener {
                                            showSnackBar(binding.root, "Failed to leave group")
                                            dialog.dismiss()
                                        }
                                    } else {
                                        showSnackBar(binding.root, "Group doesn't exist")
                                        dialog.dismiss()
                                    }
                                }.addOnFailureListener {
                                    showSnackBar(binding.root, "Failed to leave group")
                                    dialog.dismiss()
                                }
                            }
                            alert.setNegativeButton("No") { dialog, _ -> dialog.dismiss() }
                            alert.setNeutralButton("Cancel") { dialog, _ -> dialog.dismiss() }
                            val alertDialog = alert.create()
                            alertDialog.show()
                            return@setOnMenuItemClickListener true
                        }
                    }
                    return@setOnMenuItemClickListener true
                }
            }
        }
    }

    private fun sendLeaveForEveryone(rawTime: String, groupProfile2: GroupProfileData) {
        var chatData = ChatData(
            senderuid = myAuth,
            uniqueQuerableTime = rawTime,
            timesent = rawTime,
            groupUid = groupProfile2.uuid,
            messagetype = MessageType.LEAVE_GROUP,
        )
        var uniqueQueryableTime = rawTime.toLong()
        uniqueQueryableTime += 1

        val myRef = FirebaseDatabase.getInstance().reference.child(USER_DETAILS).child(myAuth)
        myRef.get().addOnSuccessListener { userSnap ->
            if (userSnap.exists()) {
                val myProfile = userSnap.getValue(UserProfile::class.java)!!
                chatData.timesent = uniqueQueryableTime.toString()
                chatData.uniqueQuerableTime = uniqueQueryableTime.toString()
                chatData.msg = "You left"

                val myUser = FirebaseDatabase.getInstance().reference
                    .child(MY_GROUPS_MESSAGES)
                    .child(myAuth)
                    .child(groupProfile2.uuid)
                    .child(uniqueQueryableTime.toString())
                myUser.setValue(chatData)

                for (i in groupProfile2.members) {
                    uniqueQueryableTime += 1
                    chatData.timesent = uniqueQueryableTime.toString()
                    chatData.uniqueQuerableTime = uniqueQueryableTime.toString()
                    chatData.msg = "${myProfile.name} left"

                    val otherUser = FirebaseDatabase.getInstance().reference
                        .child(MY_GROUPS_MESSAGES)
                        .child(i)
                        .child(groupProfile2.uuid)
                        .child(uniqueQueryableTime.toString())
                    otherUser.setValue(chatData)
                }
            }
        }
    }

    companion object {
        const val GROUPS = "groups"
        const val GROUPS_MESSAGES = "groups_messages"
        const val MY_GROUPS_MESSAGES = "my_groups_messages"
        const val TRANSFER_HISTORY = "user_coins_transfer"
        const val USER_DETAILS = "user_details"
    }
}

class GroupAttachmentFragment : BottomSheetDialogFragment(), View.OnClickListener {
    private lateinit var attachmentBinding: AttachmentFragmentBinding
    private val chatDataViewModel: ChatDataViewModel by activityViewModels()
    private val imageDialogViewModel: ImageDialogViewModel by activityViewModels()
    private val audioDialogViewModel: AudioDialogViewModel by activityViewModels()
    private val videoDialogViewModel: VideoDialogViewModel by activityViewModels()
    private val acceptedAudioTypes: ArrayList<String> = arrayListOf("wav", "mp3", "aac")
    private var permissionsStorage = arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)

    @RequiresApi(33)
    var permissionsStorageT = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_AUDIO,
        Manifest.permission.READ_MEDIA_VIDEO,
    )

    val permissionLauncher =  registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        val notGranted = arrayListOf<Boolean>()
        for (per in permissions.values) if (!per) notGranted.add(per)
        if (notGranted.isNotEmpty()) {
            CallUtils(requireActivity(), requireActivity())
                .permissionRationale(message = "HowFar needs permission to choose files and camera.\nGrant app permission")
            return@registerForActivityResult
        }
        Toast.makeText(context, "Select again", Toast.LENGTH_LONG).show()
    }

    private val pickAudioLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { dataResult ->
        val audioFragment = AudioDialogFragment()
        try {
            if (dataResult.data!!.data == null) return@registerForActivityResult
            if (dataResult.resultCode == Activity.RESULT_OK) {
                val audioUri = dataResult.data!!.data!!
                val contentResolver = requireActivity().contentResolver
                val mime = MimeTypeMap.getSingleton()
                val lastPathSegment = audioUri.lastPathSegment!!
                val name = lastPathSegment.substring(lastPathSegment.lastIndexOf("/") + 1)

                if (mime.getExtensionFromMimeType(contentResolver?.getType(audioUri))!! in acceptedAudioTypes) {
                    requireActivity().supportFragmentManager.beginTransaction().replace(R.id.group_chat_root, audioFragment).commit()
                    audioDialogViewModel.setAudioData(name to audioUri)
                    this@GroupAttachmentFragment.dismiss()
                } else Snackbar.make(attachmentBinding.root, "Unsupported image type. Supported types are wav, mp3, aac", Snackbar.LENGTH_LONG).show()
            } else {
                Snackbar.make(attachmentBinding.root, "No Audio picked", Snackbar.LENGTH_LONG).show()
                this@GroupAttachmentFragment.dismiss()
            }

        } catch (e: Exception) {
            this@GroupAttachmentFragment.dismiss()
            Snackbar.make(attachmentBinding.root, "File error", Snackbar.LENGTH_LONG).show()
        }
    }
    private val pickVideoLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { dataResult ->
        val videoFragment = VideoDialogFragment()
        try {
            if (dataResult.data!!.data == null) return@registerForActivityResult
            if (dataResult.resultCode == Activity.RESULT_OK) {
                val videoUri = dataResult.data!!.data!!
                val lastPathSegment = videoUri.lastPathSegment!!
                val name = lastPathSegment.substring(lastPathSegment.lastIndexOf("/") + 1)
                requireActivity().supportFragmentManager.beginTransaction().replace(R.id.group_chat_root, videoFragment).commit()
                videoDialogViewModel.setVideoData(name to videoUri)
                this@GroupAttachmentFragment.dismiss()
            } else {
                Snackbar.make(attachmentBinding.root, "No Video picked", Snackbar.LENGTH_LONG).show()
                this@GroupAttachmentFragment.dismiss()
            }
        } catch (e: Exception) {
            this@GroupAttachmentFragment.dismiss()
        }
    }

    @SuppressLint("Range")
    private val pickContactLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { dataResult ->
        try {
            if (dataResult.data!!.data == null) return@registerForActivityResult
            if (dataResult.resultCode == Activity.RESULT_OK) {
                val contactUri = dataResult.data!!.data!!
                val queryFields = arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER)

                val cursor = requireActivity().contentResolver.query(contactUri, queryFields, null, null, null)
                if (cursor!!.count == 0) {
                    this@GroupAttachmentFragment.dismiss()
                    return@registerForActivityResult
                }
                cursor.moveToFirst()
                val name = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME))!!
                val phoneNo = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER))!!
                chatDataViewModel.setNamePhone(name to phoneNo)
                println("Got here pickContactLauncher ********************************************** $dataResult")
                this@GroupAttachmentFragment.dismiss()
            }
        } catch (e: Exception) {
            this@GroupAttachmentFragment.dismiss()
            println("Exception ********************************************** $e")
        }
    }
    private val pickDocumentLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { dataResult ->
        val documentDialogFragment = DocumentDialogFragment()
        try {
            if (dataResult.data!!.data == null) return@registerForActivityResult
            if (dataResult.resultCode == Activity.RESULT_OK) {
                val documentUri = dataResult.data!!.data!!
                val lastPathSegment = documentUri.lastPathSegment!!
                val name = lastPathSegment.substring(lastPathSegment.lastIndexOf("/") + 1)
                requireActivity().supportFragmentManager.beginTransaction().replace(R.id.group_chat_root, documentDialogFragment).commit()
                videoDialogViewModel.setVideoData(name to documentUri)
                this@GroupAttachmentFragment.dismiss()
            } else {
                Snackbar.make(attachmentBinding.root, "No Document picked", Snackbar.LENGTH_LONG).show()
                this@GroupAttachmentFragment.dismiss()
            }
        } catch (e: Exception) {
            this@GroupAttachmentFragment.dismiss()
        }
    }
    private var permissionsContact = arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_EXTERNAL_STORAGE)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        attachmentBinding = AttachmentFragmentBinding.inflate(inflater, container, false)
        attachmentBinding.selectImage.setOnClickListener(this)
        attachmentBinding.selectContact.setOnClickListener(this)
        attachmentBinding.selectAudio.setOnClickListener(this)
        attachmentBinding.selectVideo.setOnClickListener(this)
        attachmentBinding.selectDocument.setOnClickListener(this)
        attachmentBinding.attachmentRoot.setOnClickListener { requireActivity().supportFragmentManager.beginTransaction().remove(this).commit() }
        return attachmentBinding.root
    }

    private fun openContactPicker() {
        if (Util.permissionsAvailable(permissionsContact, requireContext())) {
            val contactPickerIntent = Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI)
            contactPickerIntent.type = ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE
            pickContactLauncher.launch(contactPickerIntent)
        } else justMediaPermission()
    }

    private fun openAudioPicker() {
        if (Util.permissionsAvailable(permissionsStorage, requireContext())) {
            pickAudioLauncher.launch(Intent().apply {
                type = "audio/*"
                action = Intent.ACTION_GET_CONTENT
            })
        } else justMediaPermission()
    }

    private fun openDocumentPicker() {
        if (Util.permissionsAvailable(permissionsStorage, requireContext())) {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.type = "*/*"
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            pickDocumentLauncher.launch(intent)
        } else justMediaPermission()
    }

    private fun openVideoPicker() {
        if (Util.permissionsAvailable(permissionsStorage, requireContext())) {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            pickVideoLauncher.launch(intent)
        } else justMediaPermission()
    }

    private val cropImage = registerForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) {
            val imageDialogFragment = ImageDialogFragment()
            val uriContent = result.uriContent!!
            //result.getUriFilePath(requireContext()) // optional usage
            val lastPathSegment = uriContent.lastPathSegment!!
            val name = lastPathSegment.substring(lastPathSegment.lastIndexOf("/") + 1)
            imageDialogViewModel.setImageName(name)
            imageDialogViewModel.setImageUri(uriContent)
            requireActivity().supportFragmentManager.beginTransaction().replace(R.id.group_chat_root, ImageDialogFragment()).commit()
            this.dismiss()
        } else {
            val exception = result.error
            exception!!.printStackTrace()
        }
    }

    private fun justMediaPermission() {
        when {
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU -> {
                if (!Util.permissionsAvailable(permissionsStorageT, requireContext())) permissionLauncher.launch(permissionsStorageT)
            }
            android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU -> {
                if (!Util.permissionsAvailable(permissionsStorage, requireContext())) permissionLauncher.launch(permissionsStorage)
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
                if (!Util.permissionsAvailable(permissionsStorageT, requireContext())) permissionLauncher.launch(permissionsStorageT) else launch()
            }
            android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU -> {
                if (!Util.permissionsAvailable(permissionsStorage, requireContext()))
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