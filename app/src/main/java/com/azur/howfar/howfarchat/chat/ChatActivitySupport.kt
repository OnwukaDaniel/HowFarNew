package com.azur.howfar.howfarchat.chat

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.azur.howfar.R
import com.azur.howfar.activity.BaseActivity
import com.azur.howfar.databinding.ChatActivitySupportBinding
import com.azur.howfar.models.ChatData
import com.azur.howfar.models.MessageType
import com.azur.howfar.models.ParticipantTempData
import com.azur.howfar.models.UserProfile
import com.azur.howfar.utils.ActivitySubscription
import com.azur.howfar.utils.Keyboard
import com.azur.howfar.utils.TimeUtils
import com.azur.howfar.utils.Util
import com.azur.howfar.workManger.SupportWorkManager
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*

class ChatActivitySupport : BaseActivity(), View.OnClickListener {
    private val binding by lazy { ChatActivitySupportBinding.inflate(layoutInflater) }
    private val llm = LinearLayoutManager(this@ChatActivitySupport, LinearLayoutManager.VERTICAL, false)
    private var receiverProfile = UserProfile()
    private val dataset: ArrayList<ChatData> = arrayListOf()
    private val scope = CoroutineScope(Dispatchers.Main)
    private val workManager = WorkManager.getInstance(this)
    private val chatsAdapter = ChatAdapterSupport()
    private val myAuth = FirebaseAuth.getInstance().currentUser!!.uid
    private var timeRef = FirebaseDatabase.getInstance().reference
    private var chattingRef = FirebaseDatabase.getInstance().reference
    private var myProfileRef = FirebaseDatabase.getInstance().reference
    private var otherUserRef = FirebaseDatabase.getInstance().reference
    lateinit var sibscription: ActivitySubscription

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
                if (chatData.senderuid != myAuth && !chatData.read) {
                    NotificationManagerCompat.from(this@ChatActivitySupport).cancelAll()
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setSupportActionBar(binding.chatToolbar)
        title = ""
        initReferences()
        //listenForIncomingMessage(true, otherUID)

        Glide.with(this).load(R.drawable.app_icon_sec).centerCrop().into(binding.userImage)
        binding.userName.text = "HowFar Admin"
        onClickListeners()
        initAdapter()
        sibscription = addSoftKeyboardVisibilityListener { keyboardShown ->
            if (keyboardShown) {
                llm.findLastVisibleItemPosition().takeIf { it > 3 }?.let {
                    if (dataset.size >= it - 3) binding.rvChat.smoothScrollToPosition(dataset.size)
                }
            } else {
                //hideKeyboard()
            }
        }
    }

    private fun onClickListeners() {
        binding.chatSend.setOnClickListener(this)
        binding.chatVideoCall.setOnClickListener(this)
        binding.chatVoiceCall.setOnClickListener(this)
        binding.chatBack.setOnClickListener(this)
    }

    private fun initAdapter() {
        chatsAdapter.viewLifecycleOwner = this
        chatsAdapter.activity = this
        chatsAdapter.dataset = dataset
        chatsAdapter.miscHelper = this
        binding.rvChat.adapter = chatsAdapter
        binding.rvChat.layoutManager = llm
        llm.scrollToPosition(chatsAdapter.itemCount - 1)
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onResume() {
        super.onResume()
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        initAdapter()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun initReferences() {
        myProfileRef = myProfileRef.child(USER_DETAILS).child(myAuth)
        timeRef = FirebaseDatabase.getInstance().reference.child("time").child(myAuth)
        chattingRef = FirebaseDatabase.getInstance().reference
            .child(CHAT_REFERENCE)
            .child(myAuth)
            .child(CONTACT_SUPPORT)
        chattingRef.get().addOnSuccessListener {
            if (it.exists()) for (snapshot in it.children) {
                val chatData = snapshot.getValue(ChatData::class.java)!!
                if (chatData.senderuid != myAuth && !chatData.read) NotificationManagerCompat.from(this@ChatActivitySupport).cancelAll()
                if (chatData !in dataset) dataset.add(chatData)
            }
            chatsAdapter.notifyDataSetChanged()
            binding.rvChat.scrollToPosition(dataset.size - 1)
        }
        chattingRef.addChildEventListener(chatListener)
    }

    private fun sendNewMsg(message: String = "") {
        var myChat = ChatData(
            msg = message,
            senderuid = myAuth, displaytitle = message,
            timesent = Calendar.getInstance().timeInMillis.toString(),
            participants = arrayListOf(myAuth, receiverProfile.uid),
            isSupport = true,
        )
        dataset.add(myChat)
        binding.rvChat.smoothScrollToPosition(dataset.size)
        chatsAdapter.notifyItemInserted(dataset.size)
        myProfileRef.get().addOnSuccessListener {
            if (it.exists()) {
                val myProfile: UserProfile = it.getValue(UserProfile::class.java)!!
                val tempProfile = ParticipantTempData(
                    tempName = myProfile.name, uid = myProfile.uid, tempImage = myProfile.image,
                    phone = Util.formatNumber(myProfile.phone)
                )
                if (myChat.msg.isNotEmpty()) {
                    val howFar1 = "how far"
                    val howFar2 = "How far"
                    val howFar3 = "How Far"
                    val howFar4 = "how Far"
                    myChat.participantsTempData = arrayListOf(tempProfile)
                    myChat.msg = myChat.msg.replace(howFar1, "HowFar")
                    myChat.msg = myChat.msg.replace(howFar2, "HowFar")
                    myChat.msg = myChat.msg.replace(howFar3, "HowFar")
                    myChat.msg = myChat.msg.replace(howFar4, "HowFar")
                }
                val json = Gson().toJson(myChat)
                val workRequest = OneTimeWorkRequestBuilder<SupportWorkManager>().addTag("banner promotion")
                    .setInputData(workDataOf("data" to json))
                    .build()
                workManager.enqueue(workRequest)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        chattingRef.removeEventListener(chatListener)
    }

    override fun onClick(p0: View?) {
        when (p0?.id) {
            R.id.chatSend -> {
                val message = binding.chatInput.text.toString().trim()
                if (message == "") return
                sendNewMsg(message)
                binding.chatInput.text!!.clear()
            }
            R.id.chat_back -> super.onBackPressed()
        }
    }

    companion object {
        const val USER_DETAILS = "user_details"
        const val CONTACT_SUPPORT = "CONTACT_SUPPORT"
        const val CHAT_REFERENCE = "chat_reference"
    }
}

class ChatAdapterSupport : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    lateinit var miscHelper: ChatActivitySupport
    lateinit var dataset: ArrayList<ChatData>
    lateinit var viewLifecycleOwner: LifecycleOwner
    private lateinit var context: Context
    lateinit var activity: Activity
    private val myAuth = FirebaseAuth.getInstance().currentUser!!.uid

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int) = position.toLong()

    class SentTextViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val root: LinearLayout = itemView.findViewById(R.id.chat_row_root)
        val quotedRoot: CardView = itemView.findViewById(R.id.quoted_root)
        val quotedText: TextView = itemView.findViewById(R.id.quoted_text)
        val quotedStatusText: TextView = itemView.findViewById(R.id.quoted_status_text)
        val quotedImage: ImageView = itemView.findViewById(R.id.quoted_image)
        val text: TextView = itemView.findViewById(R.id.chat_msg)
        val background: LinearLayout = itemView.findViewById(R.id.chat_layout)
        val time: TextView = itemView.findViewById(R.id.chat_time)
        val chatNotification: ImageView = itemView.findViewById(R.id.chat_notification)

        // PHOTO-VIEW
        val image: ImageView = itemView.findViewById(R.id.chat_img)
        val imageRoot: CardView = itemView.findViewById(R.id.chat_image_root)

        fun hideTick(data: ChatData){
            chatNotification.visibility = View.GONE
        }
    }

    class ReceivedTextViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val root: LinearLayout = itemView.findViewById(R.id.chat_row_root)
        val quotedRoot: CardView = itemView.findViewById(R.id.quoted_root)
        val quotedStatusText: TextView = itemView.findViewById(R.id.quoted_status_text)
        val quotedText: TextView = itemView.findViewById(R.id.quoted_text)
        val quotedImage: ImageView = itemView.findViewById(R.id.quoted_image)
        val text: TextView = itemView.findViewById(R.id.chat_msg)
        val background: LinearLayout = itemView.findViewById(R.id.chat_layout)
        val time: TextView = itemView.findViewById(R.id.chat_time)

        // PHOTO-VIEW
        val image: ImageView = itemView.findViewById(R.id.chat_img)
        val imageRoot: CardView = itemView.findViewById(R.id.chat_image_root)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        context = parent.context
        return when (viewType) {
            in arrayListOf(SENT_TEXT, SENT_PHOTO) -> {
                val viewMe = LayoutInflater.from(context).inflate(R.layout.row_sent_text_chat, parent, false)
                SentTextViewHolder(viewMe)
            }
            in arrayListOf(RECEIVED_TEXT, RECEIVED_PHOTO) -> {
                val viewReceived = LayoutInflater.from(context).inflate(R.layout.row_received_text_chat, parent, false)
                ReceivedTextViewHolder(viewReceived)
            }
            else -> {
                val viewReceived = LayoutInflater.from(context).inflate(R.layout.row_received_text_chat, parent, false)
                ReceivedTextViewHolder(viewReceived)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility", "NotifyDataSetChanged")
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        var datum = dataset[position]
        viewHolderInit(holder, position, datum)
    }

    private fun viewHolderInit(holder: RecyclerView.ViewHolder, position: Int, datum: ChatData) {
        var choice = (if (datum.uniqueQuerableTime == "") TimeUtils.UTCToLocal(datum.timesent) else TimeUtils.UTCToLocal(datum.timesent))
        val formattedTime = Util.formatDateTime(choice)
        val fireRef = FirebaseDatabase.getInstance().reference
        //val myChattingRef = fireRef.child(CHAT_REFERENCE).child(myAuth).child(otherParticipant(datum.participants)).child(datum.uniqueQuerableTime)

        when {
            getItemViewType(position) in arrayListOf(SENT_TEXT, SENT_PHOTO) -> {
                (holder as SentTextViewHolder)
                holder.time.text = formattedTime
                holder.hideTick(datum)
                when {
                    datum.quotedChatData.uniqueQuerableTime != "" -> {
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

                when (getItemViewType(position)) {
                    SENT_TEXT -> {
                        holder.text.visibility = View.VISIBLE
                        holder.text.text = datum.msg
                    }
                    SENT_PHOTO -> {
                        holder.imageRoot.visibility = View.VISIBLE
                        Glide.with(context).load(datum.imageData.storageLink).centerCrop().into(holder.image)
                    }
                }
            }
            getItemViewType(position) in arrayListOf(RECEIVED_TEXT, RECEIVED_PHOTO) -> {
                (holder as ReceivedTextViewHolder)
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

                when (getItemViewType(position)) {
                    RECEIVED_TEXT -> {
                        holder.text.visibility = View.VISIBLE
                        holder.text.text = datum.msg
                    }
                    RECEIVED_PHOTO -> {
                        holder.imageRoot.visibility = View.VISIBLE
                        Glide.with(context).load(datum.imageData.storageLink).centerCrop().into(holder.image)
                    }
                }
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        val datum = dataset[position]
        var returnMessageType = 0
        when (datum.messagetype) {
            MessageType.TEXT -> returnMessageType = if (datum.senderuid == myAuth) SENT_TEXT else RECEIVED_TEXT
            MessageType.PHOTO -> returnMessageType = if (datum.senderuid == myAuth) SENT_PHOTO else RECEIVED_PHOTO
        }
        return returnMessageType
    }

    override fun getItemCount() = dataset.size

    companion object {
        const val RECEIVED_TEXT = 0
        const val SENT_TEXT = 1

        const val RECEIVED_PHOTO = 2
        const val SENT_PHOTO = 3
    }
}