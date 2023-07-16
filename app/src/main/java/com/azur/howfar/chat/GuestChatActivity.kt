package com.azur.howfar.chat

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.azur.howfar.R
import com.azur.howfar.activity.BaseActivity
import com.azur.howfar.camera.CameraActivity
import com.azur.howfar.databinding.ActivityGuestChatBinding
import com.azur.howfar.dilog.IncomingCallDialog
import com.azur.howfar.howfarchat.chat.FragmentDisplayImage
import com.azur.howfar.models.*
import com.azur.howfar.utils.Util
import com.azur.howfar.workManger.ImageWorkManager
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.gson.Gson

class GuestChatActivity : BaseActivity(), View.OnClickListener {
    private val binding by lazy { ActivityGuestChatBinding.inflate(layoutInflater) }
    private var receiverUid = ""
    private var chatAdapter = ChatAdapter()
    private lateinit var pref: SharedPreferences
    private val workManager = WorkManager.getInstance(this)
    private var timeRef = FirebaseDatabase.getInstance().reference
    private var chatRef = FirebaseDatabase.getInstance().reference.child(GUEST_USERS_CHAT)
    private val user = FirebaseAuth.getInstance().currentUser
    private var dataset = arrayListOf<ChatData>()

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val imageUri = result.data!!.getStringExtra("guest image result")!!
            val chatData = ChatData(
                senderuid = user!!.uid,
                participants = arrayListOf(user.uid, receiverUid),
                imageData = ImageData(storageLink = imageUri, displayMessage = "Image")
            )
            dataset.add(chatData)
            chatAdapter.notifyItemInserted(dataset.size)
            val json = Gson().toJson(chatData)
            workManagerUpload(json)
        }
    }

    private val childEventListener = object : ChildEventListener {
        override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
            if (snapshot.exists()) {
                val chatData = snapshot.getValue(ChatData::class.java)!!
                for (i in dataset) if (i.timeInitial == chatData.timeInitial) {
                    dataset[dataset.indexOf(i)] = chatData
                    chatAdapter.notifyItemChanged(dataset.indexOf(i))
                    return
                }
                if (chatData !in dataset) {
                    dataset.add(chatData)
                    chatAdapter.notifyItemInserted(dataset.size)
                }
            }
        }

        override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
        }

        override fun onChildRemoved(snapshot: DataSnapshot) {
        }

        override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) = Unit
        override fun onCancelled(error: DatabaseError) = Unit
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        pref = getPreferences(Context.MODE_PRIVATE)
        receiverUid = intent.getStringExtra("userId")!!
        initData()
        FirebaseDatabase.getInstance().reference.child(USER_DETAILS).child(receiverUid).get().addOnSuccessListener {
            if (it.exists()) {
                val userProfile = it.getValue(UserProfile::class.java)!!
                binding.tvUserName.text = userProfile.name
                try {
                    Glide.with(this).load(userProfile.image).circleCrop().into(binding.imgUser)
                } catch (e: Exception) {
                }
            }
        }
    }

    private fun workManagerUpload(json: String) {
        val workRequest = OneTimeWorkRequestBuilder<ImageWorkManager>().addTag("call and messages")
            .setInputData(workDataOf("chatData" to json))
            .build()
        workManager.enqueue(workRequest)
    }

    private fun initData() {
        binding.tvSend.setOnClickListener(this)
        binding.guestCamera.setOnClickListener(this)
        binding.guestVideoCall.setOnClickListener(this)
        chatAdapter.chatList = dataset
        chatAdapter.activity = this
        binding.rvChat.adapter = chatAdapter
        binding.rvChat.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        timeRef = timeRef.child("time").child(user!!.uid)
        chatRef = chatRef.child(user.uid).child(receiverUid)
        chatRef.addChildEventListener(childEventListener)
        binding.rvChat.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            binding.rvChat.post {
                chatAdapter.itemCount.takeIf { it > 0 }?.let {
                    //binding.rvChat.scrollToPosition(it - 1)
                }
            }
        }
    }

    override fun onDestroy() {
        chatRef.removeEventListener(childEventListener)
        super.onDestroy()
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.guestCamera -> {
                cameraLauncher.launch(Intent(this, CameraActivity::class.java))
            }
            R.id.guestVideoCall -> {
                val callData = CallData(channelName = user!!.uid, callerUid = user.uid, callType = CallType.VIDEO, uids = arrayListOf(user.uid, receiverUid))
                val intent = Intent(this, IncomingCallDialog::class.java)
                intent.putExtra("CREATE", CallEngagementType.CREATE)
                intent.putExtra("callData", Gson().toJson(callData))
                startActivity(intent)
            }
            R.id.tvSend -> {
                val chatText = binding.etChat.text.trim().toString()
                if (chatText == "") return
                val timeInitial = System.currentTimeMillis().toString()
                var chatData = ChatData(
                    senderuid = user!!.uid, participants = arrayListOf(user.uid, receiverUid),
                    timeInitial = timeInitial, msg = chatText, displaytitle = chatText
                )
                dataset.add(chatData)
                chatAdapter.notifyItemInserted(dataset.size)
                binding.etChat.text.clear()
                timeRef.setValue(ServerValue.TIMESTAMP).addOnSuccessListener {
                    timeRef.get().addOnSuccessListener {
                        if (it.exists()) {
                            val timeSent = it.value.toString()
                            chatData.uniqueQuerableTime = timeSent
                            val myRef = FirebaseDatabase.getInstance().reference.child(USER_DETAILS).child(user.uid)
                            val receiverRef = FirebaseDatabase.getInstance().reference.child(USER_DETAILS).child(receiverUid)
                            myRef.get().addOnSuccessListener { my ->
                                val myProfile = my.getValue(UserProfile::class.java)!!
                                val myTemp = ParticipantTempData(
                                    uid = myProfile.uid, tempName = myProfile.name, tempImage = myProfile.image,
                                    phone = Util.formatNumber(myProfile.phone)
                                )
                                val myGuestRef = FirebaseDatabase.getInstance().reference.child(GUEST_USERS_CHAT).child(user.uid)
                                    .child(receiverUid).child(timeSent)
                                receiverRef.get().addOnSuccessListener { receiver ->
                                    val otherProfile = receiver.getValue(UserProfile::class.java)!!
                                    val receiverTemp = ParticipantTempData(
                                        uid = otherProfile.uid, tempName = otherProfile.name, tempImage = otherProfile.image,
                                        phone = Util.formatNumber(otherProfile.phone)
                                    )
                                    val receiverGuestRef = FirebaseDatabase.getInstance().reference.child(GUEST_USERS_CHAT).child(receiverUid)
                                        .child(user.uid).child(timeSent)
                                    chatData.participantsTempData = arrayListOf(myTemp, receiverTemp)
                                    myGuestRef.setValue(chatData)
                                    receiverGuestRef.setValue(chatData).addOnSuccessListener {
                                        Util.sendNotification(
                                            message = chatText, body = "You have a new message on like",
                                            receiverUid =
                                            receiverUid,
                                            view = "like message",
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val GUEST_USERS_CHAT = "GUEST_USERS_CHAT"
        const val USER_DETAILS = "user_details"
        const val GUEST_IMAGE = "GUEST_IMAGE"
    }
}

class ChatAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    lateinit var context: Context
    lateinit var activity: Activity
    private val user = FirebaseAuth.getInstance().currentUser
    var chatList = arrayListOf<ChatData>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        context = parent.context
        return if (viewType == TEXT_TYPE) ChatTextViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_chat_text, parent, false))
        else ChatImageViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_chat_image, parent, false))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (getItemViewType(position) == TEXT_TYPE) (holder as ChatTextViewHolder).setData(position) else (holder as ChatImageViewHolder).setData(position)
    }

    override fun getItemCount() = chatList.size

    override fun getItemViewType(position: Int) = if (chatList[position].imageData.storageLink == "") TEXT_TYPE else PHOTO_TYPE

    inner class ChatTextViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imgUser1: ImageView = itemView.findViewById(R.id.imgUser1)
        private val imgUser2: ImageView = itemView.findViewById(R.id.imgUser2)
        private val space2: View = itemView.findViewById(R.id.space2)
        private val space1: View = itemView.findViewById(R.id.space1)
        private val tvText: TextView = itemView.findViewById(R.id.tvText)
        fun setData(position: Int) {
            val chat = chatList[position]
            val my = getMe(chat)
            val other = getOtherParticipant(chat)
            try {
                Glide.with(itemView).load(other.tempImage).circleCrop().into(imgUser1)
                Glide.with(itemView).load(my.tempImage).circleCrop().into(imgUser2)
            } catch (e: Exception) {
            }
            if (chat.senderuid == user!!.uid) {
                imgUser1.visibility = View.INVISIBLE
                imgUser2.visibility = View.VISIBLE
                space2.visibility = View.GONE
                space1.visibility = View.VISIBLE
                tvText.background = ContextCompat.getDrawable(context, R.drawable.bg_chat_right)
                tvText.setTextColor(ContextCompat.getColor(context, R.color.white))
                tvText.backgroundTintList = ContextCompat.getColorStateList(context, R.color.pink)
            } else {
                imgUser2.visibility = View.INVISIBLE
                imgUser1.visibility = View.VISIBLE
                space1.visibility = View.GONE
                space2.visibility = View.VISIBLE
                tvText.background = ContextCompat.getDrawable(context, R.drawable.bg_chat_left)
                sendSeen(chat)
            }
            tvText.text = chat.msg
        }
    }

    inner class ChatImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imgUser1: ImageView = itemView.findViewById(R.id.imgUser1)
        private val imgUser2: ImageView = itemView.findViewById(R.id.imgUser2)
        private val space2: View = itemView.findViewById(R.id.space2)
        private val space1: View = itemView.findViewById(R.id.space1)
        private val mainImage: ImageView = itemView.findViewById(R.id.mainImage)
        private val lytMain: RelativeLayout = itemView.findViewById(R.id.lytMain)
        fun setData(position: Int) {
            val chat = chatList[position]
            val my = getMe(chat)
            val other = getOtherParticipant(chat)
            try {
                Glide.with(itemView).load(my.tempImage).circleCrop().into(imgUser2)
                Glide.with(itemView).load(other.tempImage).circleCrop().into(imgUser1)
                Glide.with(itemView).load(chat.imageData.storageLink).into(mainImage)
            } catch (e: Exception) {
            }
            FirebaseDatabase.getInstance().reference.child(ChatUserAdapter.USER_DETAILS).child(user!!.uid).get().addOnSuccessListener {
                if (it.exists()) {
                    val userProfile = it.getValue(UserProfile::class.java)!!
                    try {
                        Glide.with(itemView).load(userProfile.image).circleCrop().into(imgUser2)
                    } catch (e: Exception) {
                    }
                }
            }
            if (chat.senderuid == user.uid) {
                imgUser1.visibility = View.INVISIBLE
                imgUser2.visibility = View.VISIBLE
                space2.visibility = View.GONE
                space1.visibility = View.VISIBLE
                mainImage.background = ContextCompat.getDrawable(context, R.drawable.bg_chat_right)
                lytMain.backgroundTintList = ContextCompat.getColorStateList(context, R.color.pink)
            } else {
                imgUser2.visibility = View.INVISIBLE
                imgUser1.visibility = View.VISIBLE
                space1.visibility = View.GONE
                space2.visibility = View.VISIBLE
                mainImage.background = ContextCompat.getDrawable(context, R.drawable.bg_chat_left)
                sendSeen(chat)
            }
            mainImage.setOnClickListener {
                val fragment = FragmentDisplayImage()
                val bundle = Bundle()
                bundle.putString("image", chat.imageData.storageLink)
                fragment.arguments = bundle
                (activity as AppCompatActivity).supportFragmentManager.beginTransaction().addToBackStack("image")
                    .replace(R.id.guest_chat_root, fragment).commit()
            }
        }
    }

    private fun sendSeen(chat: ChatData) {
        val receiverChattingRef = FirebaseDatabase.getInstance().reference
            .child(GuestChatActivity.GUEST_USERS_CHAT)
            .child(otherParticipant(chat.participants))
            .child(user!!.uid)
            .child(chat.uniqueQuerableTime)

        val myChattingRef = FirebaseDatabase.getInstance().reference
            .child(GuestChatActivity.GUEST_USERS_CHAT)
            .child(user.uid)
            .child(otherParticipant(chat.participants))
            .child(chat.uniqueQuerableTime)
        if (!chat.read) receiverChattingRef.get().addOnSuccessListener { retrieveSnapshot ->
            if (retrieveSnapshot.exists()) {
                val timeRef = FirebaseDatabase.getInstance().reference.child("time").child(user.uid)
                timeRef.setValue(ServerValue.TIMESTAMP).addOnSuccessListener {
                    timeRef.get().addOnSuccessListener { time ->
                        chat.read = true
                        chat.timeseen = time.value.toString()
                        receiverChattingRef.setValue(chat)
                        myChattingRef.get().addOnSuccessListener { myChatSnap ->
                            if (myChatSnap.exists()) myChattingRef.setValue(chat)
                        }
                    }
                }
            }
        }
    }

    private fun otherParticipant(participants: ArrayList<String>): String {
        val myAuth = FirebaseAuth.getInstance().currentUser!!.uid
        for (i in participants) return if (i != myAuth) i else participants[1]
        return ""
    }

    private fun getOtherParticipant(chatUser: ChatData): ParticipantTempData {
        for (i in chatUser.participantsTempData) if (i.uid != user!!.uid) return i
        return ParticipantTempData()
    }

    private fun getMe(chatUser: ChatData): ParticipantTempData {
        for (i in chatUser.participantsTempData) if (i.uid == user!!.uid) return i
        return ParticipantTempData()
    }

    companion object {
        private const val TEXT_TYPE = 3
        private const val PHOTO_TYPE = 1
    }
}