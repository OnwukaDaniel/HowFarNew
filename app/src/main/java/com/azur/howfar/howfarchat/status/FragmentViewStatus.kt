package com.azur.howfar.howfarchat.status

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.doOnAttach
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.azur.howfar.R
import com.azur.howfar.databinding.FragmentViewStatusBinding
import com.azur.howfar.databinding.FragmentViewsBinding
import com.azur.howfar.databinding.ViewsBottomSheetBinding
import com.azur.howfar.howfarchat.chat.ChatActivity2
import com.azur.howfar.livedata.ValueEventLiveData
import com.azur.howfar.models.*
import com.azur.howfar.models.Currency
import com.azur.howfar.retrofit.Const
import com.azur.howfar.utils.*
import com.azur.howfar.utils.Keyboard.hideKeyboard
import com.azur.howfar.workManger.ImageWorkManager
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.gson.Gson
import com.vanniktech.emoji.EmojiPopup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pt.tornelas.segmentedprogressbar.SegmentedProgressBarListener
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

class FragmentViewStatus : Fragment(), View.OnLongClickListener {
    private lateinit var binding: FragmentViewStatusBinding
    private val statusViewModel by activityViewModels<StatusViewModel>()
    private lateinit var viewStatusTabAdapter: ViewStatusTabAdapter
    private val myAuth = FirebaseAuth.getInstance().currentUser!!.uid
    private var dataset: ArrayList<StatusUpdateData> = arrayListOf()
    private var scrollAssist = false
    private var isScrolling = false
    private var isPlaying = false

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentViewStatusBinding.inflate(inflater, container, false)
        viewStatusTabAdapter = ViewStatusTabAdapter(requireActivity())
        //binding.rightClick.setOnLongClickListener(this)

        val fragmentList: ArrayList<Fragment> = arrayListOf()
        val json = requireArguments().getString("datum")
        scrollAssist = requireArguments().getBoolean("assist")
        val list = Gson().fromJson(json, ArrayList::class.java)
        for (i in list) {
            val statusUpdateData = Gson().fromJson(Gson().toJson(i), StatusUpdateData::class.java)
            val jsonDatum = Gson().toJson(statusUpdateData)
            val fragment = FragmentViews()
            val bundle = Bundle()
            bundle.putString("datum", jsonDatum)
            fragment.arguments = bundle
            fragmentList.add(fragment)
            dataset.add(statusUpdateData)
        }
        viewStatusTabAdapter.dataset = fragmentList
        binding.viewViewPager.offscreenPageLimit = 1
        binding.viewViewPager.adapter = viewStatusTabAdapter
        binding.viewViewPager.doOnAttach {
            binding.segmentedProgressBar.segmentCount = dataset.size
            if (!isScrolling) playStatus()
        }

        binding.segmentedProgressBar.listener = object : SegmentedProgressBarListener {
            override fun onPage(oldPageIndex: Int, newPageIndex: Int) {
                val currentDatumPosition = binding.viewViewPager.currentItem
                val z = Instant.ofEpochMilli(dataset[currentDatumPosition].serverTime.toLong()).atZone(ZoneId.systemDefault())
                val date = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
                val formattedTime = date.format(z)
                binding.viewUserTime.text = formattedTime
                if (currentDatumPosition + 1 != dataset.size) {
                    binding.viewViewPager.setCurrentItem(currentDatumPosition + 1, true)
                }
            }

            override fun onFinished() {
                try {
                    if (scrollAssist) {
                        showScrollAssist()
                    } else {
                        requireActivity().supportFragmentManager.beginTransaction().remove(this@FragmentViewStatus).commit()
                    }
                } catch (e: Exception) {
                }
            }
        }
        when (dataset.first().isAdmin) {
            true -> {
                binding.viewUserName.text = "HowFar"
                Glide.with(requireContext()).load(R.drawable.app_icon_sec).centerCrop().into(binding.viewUserImage)
            }
            false -> {
                val ref = FirebaseDatabase.getInstance().reference.child("user_details").child(dataset.first().senderUid)
                val profileLiveData = ValueEventLiveData(ref)
                profileLiveData.observe(viewLifecycleOwner) {
                    when (it.second) {
                        EventListenerType.onDataChange -> {
                            val profile = it.first.getValue(UserProfile::class.java)!!
                            if (profile.uid != myAuth) binding.viewUserName.text = profile.name
                            Glide.with(requireContext()).load(profile.image).centerCrop().into(binding.viewUserImage)
                        }
                    }
                }
            }
        }
        binding.viewBack.setOnClickListener { requireActivity().onBackPressed() }
        return binding.root
    }

    private fun showScrollAssist() {
        //val scroll = pref.getBoolean("status scroll up seen", false)
        binding.statusArrowUp.visibility = View.VISIBLE
        Glide.with(requireContext()).asGif().load(R.drawable.scroll).into(binding.statusArrowUp)
        CoroutineScope(Dispatchers.IO).launch {
            delay(7_000)
            if (isAdded && activity != null) {
                requireActivity().runOnUiThread { binding.statusArrowUp.visibility = View.GONE }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.leftClick.setOnClickListener {
            val currentDatumPosition = binding.viewViewPager.currentItem
            if (currentDatumPosition != 0) {
                val desiredPosition = currentDatumPosition - 1
                binding.segmentedProgressBar.previous()
                binding.viewViewPager.setCurrentItem(desiredPosition, true)
            }
        }
        binding.rightClick.setOnClickListener {
            val currentDatumPosition = binding.viewViewPager.currentItem
            if (currentDatumPosition + 1 != dataset.size) {
                val desiredPosition = currentDatumPosition + 1
                binding.segmentedProgressBar.setPosition(desiredPosition)
                playStatus()
                binding.viewViewPager.setCurrentItem(currentDatumPosition + 1, true)
            } else if (currentDatumPosition + 1 == dataset.size) {
                try {
                    requireActivity().onBackPressed()
                } catch (e: Exception) {
                }
            }
        }
    }

    var pos = 0
    override fun onResume() {
        super.onResume()
        binding.viewViewPager.currentItem = 0
        statusViewModel.playCurrentStatus.observe(viewLifecycleOwner) {
            if (it == true) playStatus() else pauseStatus()
        }
        statusViewModel.segmentController.observe(viewLifecycleOwner) {
            pos = it.second
            val pair = it
            if (pair.first) {
                isScrolling = false
                pauseStatus()
            } else {
                isScrolling = true
                playStatus()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        pauseStatus()
    }

    private fun playStatus() {
        binding.segmentedProgressBar.start()
        isPlaying = true
    }

    private fun pauseStatus() {
        binding.segmentedProgressBar.pause()
        isPlaying = false
    }

    inner class ViewStatusTabAdapter(fa: FragmentActivity) : FragmentStateAdapter(fa) {
        lateinit var dataset: ArrayList<Fragment>
        override fun getItemCount(): Int = dataset.size
        override fun createFragment(position: Int): Fragment {
            return dataset[position]
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onLongClick(v: View?): Boolean {
        when (v?.id) {
            R.id.right_click -> {
                if (isPlaying) {
                    pauseStatus()
                    binding.rightClick.setOnTouchListener { _, event ->
                        when (event!!.action) {
                            MotionEvent.ACTION_UP -> {
                                if (!isPlaying) playStatus()
                                return@setOnTouchListener true
                            }
                            else -> return@setOnTouchListener false
                        }
                    }
                }
                return true
            }
        }
        return false
    }
}

class FragmentViews : Fragment(), View.OnClickListener, View.OnLongClickListener, View.OnTouchListener {
    private lateinit var binding: FragmentViewsBinding
    private lateinit var statusUpdateData: StatusUpdateData
    private var user = FirebaseAuth.getInstance().currentUser
    private var timeRef = FirebaseDatabase.getInstance().reference
    private val statusViewModel by activityViewModels<StatusViewModel>()
    private var commentList = arrayListOf<MomentDetails>()
    private var loveList = arrayListOf<MomentDetails>()
    private var likeList = arrayListOf<MomentDetails>()
    private var viewList = arrayListOf<StatusView>()
    private val viewersIds = arrayListOf<String>()
    private var emojIcon: EmojiPopup? = null
    lateinit var sibscription: ActivitySubscription
    private var userProfile = UserProfile()

    private fun Activity.addSoftKeyboardVisibilityListener(
        visibleThresholdDp: Int = 100,
        initialState: Boolean = false,
        listener: (Boolean) -> Unit
    ): ActivitySubscription {
        return Keyboard.KeyboardVisibilitySubscription(this, visibleThresholdDp, initialState, listener)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentViewsBinding.inflate(inflater, container, false)
        binding.likeBtn.setOnClickListener(this)
        binding.myViewStatusRoot.setOnLongClickListener(this)
        binding.myViewStatusRoot.setOnTouchListener(this)
        binding.chatEmoji.setOnClickListener(this)
        binding.loveBtn.setOnClickListener(this)
        binding.statusReplySend.setOnClickListener(this)
        binding.userViews.setOnClickListener(this)
        binding.replyRoot.setOnClickListener(this)

        timeRef = FirebaseDatabase.getInstance().reference.child("time").child(user!!.uid)
        emojIcon = EmojiPopup.Builder.fromRootView(binding.myViewStatusRoot).setOnEmojiPopupShownListener {}.build(binding.chatInput)
        statusUpdateData = Gson().fromJson(requireArguments().getString("datum"), StatusUpdateData::class.java)

        when (statusUpdateData.senderUid) {
            user!!.uid -> binding.userViews.visibility = View.VISIBLE
            else -> {
                binding.replyRoot.visibility = View.VISIBLE
            }
        }
        when (statusUpdateData.statusType) {
            StatusType.TEXT -> {
                textStatus()
                binding.viewText.text = statusUpdateData.caption
                binding.viewText.setBackgroundColor(Color.parseColor(statusUpdateData.captionBackgroundColor))
                binding.captionRoot.visibility = View.GONE
            }
            StatusType.IMAGE -> {
                imageStatus()
                Glide.with(requireContext()).load(statusUpdateData.storageLink).centerInside().into(binding.viewImageView)
                binding.viewCaption.text = statusUpdateData.caption
                binding.captionRoot.visibility = if (statusUpdateData.caption == "") View.GONE else View.VISIBLE
            }
        }
        sibscription = requireActivity().addSoftKeyboardVisibilityListener { keyboardShown ->
            if (keyboardShown) {
                statusViewModel.setPlayCurrentStatus(false)
            } else {
                hideKeyboard()
                statusViewModel.setPlayCurrentStatus(true)
            }
        }
        return binding.root
    }

    override fun onResume() {
        hideKeyboard()
        binding.statusTextInput.visibility = View.GONE
        val ref = FirebaseDatabase.getInstance().reference.child(USER_DETAILS).child(user!!.uid)
        ref.get().addOnSuccessListener { if (it.exists()) userProfile = it.getValue(UserProfile::class.java)!! }

        val statusRef = FirebaseDatabase.getInstance().reference
            .child(STATUS_DETAILS)
            .child(statusUpdateData.senderUid)
            .child(statusUpdateData.serverTime)
        statusRef.get().addOnSuccessListener {
            if (it.exists()) {
                for (i in it.children) {
                    val md = i.getValue(MomentDetails::class.java)!!
                    if (md.comment.profileUid != "" && md !in commentList) commentList.add(md)
                    if (md.loves.profileUid != "" && md !in loveList) loveList.add(md)
                    if (md.likes.profileUid != "" && md !in likeList) likeList.add(md)
                    if (md.loves.profileUid == user!!.uid) binding.loveBtn.setImageResource(com.like.view.R.drawable.heart_on)
                    if (md.likes.profileUid == user!!.uid) binding.likeBtn.setImageResource(R.drawable.like_blue)
                }
                binding.tvLoveCount.text = (loveList.size * Const.LOVE_VALUE).toInt().toString()
                binding.tvLikeCount.text = (likeList.size * Const.LIKE_VALUE).toInt().toString()
            }
        }
        val statusViewsRef = FirebaseDatabase.getInstance().reference
            .child(STATUS_VIEWS)
            .child(statusUpdateData.senderUid)
            .child(statusUpdateData.serverTime)
        statusViewsRef.get().addOnSuccessListener {
            if (it.exists()) {
                for (i in it.children) {
                    val statusView = i.getValue(StatusView::class.java)!!
                    if (statusView !in viewList) {
                        viewList.add(statusView)
                        viewersIds.add(statusView.uid)
                    }
                }
                if (viewList.isNotEmpty()) {
                    if (user!!.uid == statusUpdateData.senderUid) {
                        val viewText = "${viewList.size} views"
                        binding.userViews.visibility = View.VISIBLE
                        binding.userViews.text = viewText
                    }
                }
            }
            if (user!!.uid != statusUpdateData.senderUid) {
                when (userProfile.uid) {
                    "" -> if (user!!.uid !in viewersIds) {
                        val myProfileRef = FirebaseDatabase.getInstance().reference.child(USER_DETAILS).child(user!!.uid)
                        myProfileRef.get().addOnSuccessListener { myProfile ->
                            if (myProfile.exists()) {
                                userProfile = myProfile.getValue(UserProfile::class.java)!!
                                sendMyStatusView()
                            }
                        }
                    }
                    else -> if (user!!.uid !in viewersIds) sendMyStatusView()
                }
            }
        }
        super.onResume()
    }

    private fun sendMyStatusView() {
        timeRef.setValue(ServerValue.TIMESTAMP).addOnSuccessListener {
            timeRef.get().addOnSuccessListener { timeSnapshot ->
                if (timeSnapshot.exists()) {
                    val timeSent = timeSnapshot.value.toString()
                    val myStatusView = StatusView(nameTemp = userProfile.name, imageTemp = userProfile.image, uid = userProfile.uid, timeViewed = timeSent)
                    when (statusUpdateData.isAdmin) {
                        true -> {
                            FirebaseDatabase.getInstance("https://howfar-b24ef.firebaseio.com")
                                .reference.child(STATUS_DETAILS).child(statusUpdateData.serverTime).setValue(myStatusView)
                        }
                        false -> {
                            val statusViewsRef = FirebaseDatabase.getInstance().reference
                                .child(STATUS_VIEWS)
                                .child(statusUpdateData.senderUid)
                                .child(statusUpdateData.serverTime)
                                .child(timeSent)
                            statusViewsRef.setValue(myStatusView)
                        }
                    }
                }
            }
        }
    }

    private fun textStatus() {
        binding.viewText.visibility = View.VISIBLE
        binding.viewImageView.visibility = View.GONE
        binding.viewVideo.visibility = View.GONE
        binding.viewProgress.visibility = View.GONE
    }

    private fun imageStatus() {
        binding.viewText.visibility = View.GONE
        binding.viewImageView.visibility = View.VISIBLE
        binding.viewVideo.visibility = View.GONE
        binding.viewProgress.visibility = View.GONE
    }

    private fun likePost(hfCoin: Float) {
        when (userProfile.uid) {
            "" -> {
                val ref = FirebaseDatabase.getInstance().reference.child(USER_DETAILS).child(user!!.uid)
                ref.get().addOnSuccessListener {
                    if (it.exists()) {
                        userProfile = it.getValue(UserProfile::class.java)!!
                        sendHFCoin(hfCoin)
                    }
                }
            }
            else -> sendHFCoin(hfCoin)
        }
    }

    private fun sendHFCoin(amount: Float, userP: UserProfile = userProfile) {
        var currency = Currency(senderUid = user!!.uid, receiverUid = statusUpdateData.senderUid, transactionType = TransactionType.SENT, hfcoin = amount)
        var md = when (amount) {
            Const.LOVE_VALUE -> MomentDetails(loves = MomentLove(profileName = userP.name, profilePhoto = userP.image, profileUid = user!!.uid))
            else -> MomentDetails(likes = MomentLike(profileName = userP.name, profilePhoto = userP.image, profileUid = user!!.uid))
        }
        timeRef.setValue(ServerValue.TIMESTAMP).addOnSuccessListener {
            timeRef.get().addOnSuccessListener { timeSnapshot ->
                if (timeSnapshot.exists()) {
                    val timeSent = timeSnapshot.value.toString()
                    md.time = timeSent
                    currency.timeOfTransaction = timeSent

                    FirebaseDatabase.getInstance().reference
                        .child(STATUS_DETAILS)
                        .child(statusUpdateData.senderUid)
                        .child(statusUpdateData.serverTime)
                        .child(timeSent).setValue(md)
                    val receiverRef = FirebaseDatabase.getInstance().reference.child(TRANSFER_HISTORY).child(statusUpdateData.senderUid).child(timeSent)
                    val senderRef = FirebaseDatabase.getInstance().reference.child(TRANSFER_HISTORY).child(user!!.uid).child(timeSent)
                    senderRef.setValue(currency).addOnSuccessListener {
                        currency.transactionType = TransactionType.EARNED
                        receiverRef.setValue(currency)
                    }
                }
            }
        }
    }

    private fun otherParticipant(chatUser: ChatData): String {
        val myAuth = FirebaseAuth.getInstance().currentUser!!.uid
        for (i in chatUser.participants) if (i != myAuth) return i
        return ""
    }

    private fun sendNotification(chatData: ChatData) {
        val pushNotification = PushNotification(
            title= "New message",
            body= chatData.msg,
            channelId= chatData.senderuid,
            priority= "",
            imageUrl= "",
            senderId= chatData.senderuid,
            receiverIds= arrayListOf(otherParticipant(chatData)),
            view= "Message",
        )
        FirebaseDatabase.getInstance().reference.child("PushNotifications").push().setValue(pushNotification)
    }

    private fun getOtherParticipant(chatData: ChatData): String {
        val myAuth = FirebaseAuth.getInstance().currentUser!!.uid
        for (i in chatData.participants) if (i != myAuth) return i
        return ""
    }

    private fun uploadText(message: String = "") {
        val myAuth = FirebaseAuth.getInstance().currentUser!!.uid
        var chatData = ChatData(
            timesent = System.currentTimeMillis().toString(),
            senderuid = user!!.uid, displaytitle = message,
            quotedChatData = QuoteChatData(
                imageData = if (statusUpdateData.storageLink != "") ImageData(storageLink = statusUpdateData.storageLink) else ImageData(),
                msg = if (statusUpdateData.caption != "") statusUpdateData.caption else "Image "
            ),
            replyFromStatus = true,
            participants = arrayListOf(user!!.uid, statusUpdateData.senderUid), msg = message,
        )
        val ref = FirebaseDatabase.getInstance().reference.child(ImageWorkManager.USER_DETAILS).child(myAuth)
        ref.get().addOnSuccessListener { userProfile ->
            if (userProfile.exists()) {
                val myProfile = userProfile.getValue(UserProfile::class.java)!!
                val myTemp = ParticipantTempData(
                    tempImage = myProfile.image, tempName = myProfile.name, uid = myProfile.uid,
                    phone = Util.formatNumber(myProfile.phone)
                )
                val otherRef = FirebaseDatabase.getInstance().reference.child(ImageWorkManager.USER_DETAILS).child(getOtherParticipant(chatData))
                otherRef.get().addOnSuccessListener { otherSnapshot ->
                    if (otherSnapshot.exists()) {
                        val otherProfile = otherSnapshot.getValue(UserProfile::class.java)!!
                        val otherTemp = ParticipantTempData(
                            tempImage = otherProfile.image, tempName = otherProfile.name, uid = otherProfile.uid,
                            phone = Util.formatNumber(otherProfile.phone)
                        )
                        chatData.participantsTempData = arrayListOf(myTemp, otherTemp)
                        timeRef = FirebaseDatabase.getInstance().reference.child("time").child(myAuth)
                        timeRef.setValue(ServerValue.TIMESTAMP).addOnSuccessListener {
                            timeRef.get().addOnSuccessListener { timeSnapshot ->
                                if (timeSnapshot.exists()) {
                                    val timeSent = timeSnapshot.value.toString()
                                    chatData.uniqueQuerableTime = timeSent
                                    chatData.sent = true
                                    FirebaseDatabase.getInstance().reference
                                        .child(FirebaseConstants.CHAT_DISPLAY)
                                        .child(statusUpdateData.senderUid)
                                        .child(myAuth)
                                        .child(timeSent)
                                        .setValue(chatData)
                                    FirebaseDatabase.getInstance().reference
                                        .child(FirebaseConstants.CHAT_DISPLAY)
                                        .child(myAuth)
                                        .child(statusUpdateData.senderUid)
                                        .child(timeSent)
                                        .setValue(chatData)
                                    Util.sendNotification(chatData)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.chatEmoji -> emojIcon!!.toggle()
            R.id.likeBtn -> {
                if (user!!.uid != statusUpdateData.senderUid) {
                    binding.likeBtn.setImageResource(com.like.view.R.drawable.thumb_on)
                    var like = binding.tvLikeCount.text.toString().toFloat() + Const.LIKE_VALUE
                    val originalLike = binding.tvLikeCount.text.toString().toInt().toString()
                    binding.tvLikeCount.text = like.toInt().toString()

                    val historyRef = FirebaseDatabase.getInstance().reference.child(TRANSFER_HISTORY).child(user!!.uid)
                    historyRef.get().addOnSuccessListener {
                        if (it.exists()) {
                            val available = HFCoinUtils.checkBalance(it)
                            when {
                                available < Const.LIKE_VALUE -> {
                                    binding.tvLikeCount.text = originalLike
                                    binding.likeBtn.setImageResource(R.drawable.like_white)
                                    Toast.makeText(requireContext(), "Insufficient HFCoin.", Toast.LENGTH_SHORT).show()
                                }
                                else -> likePost(Const.LIKE_VALUE)
                            }
                        } else {
                            binding.tvLikeCount.text = originalLike
                            binding.likeBtn.setImageResource(R.drawable.like_white)
                            Toast.makeText(requireContext(), "Insufficient HFCoin.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            R.id.loveBtn -> {
                if (user!!.uid != statusUpdateData.senderUid) {
                    binding.loveBtn.setImageResource(com.like.view.R.drawable.heart_on)
                    var love = binding.tvLoveCount.text.toString().toFloat() + Const.LOVE_VALUE
                    val originalLove = binding.tvLoveCount.text.toString().toInt().toString()
                    binding.tvLoveCount.text = love.toInt().toString()

                    val historyRef = FirebaseDatabase.getInstance().reference.child(TRANSFER_HISTORY).child(user!!.uid)
                    historyRef.get().addOnSuccessListener {
                        if (it.exists()) {
                            val available = HFCoinUtils.checkBalance(it)
                            when {
                                available < Const.LOVE_VALUE -> {
                                    binding.tvLoveCount.text = originalLove
                                    binding.loveBtn.setImageResource(R.drawable.heart_off)
                                    Toast.makeText(requireContext(), "Insufficient HFCoin.", Toast.LENGTH_SHORT).show()
                                }
                                else -> likePost(Const.LOVE_VALUE)
                            }
                        } else {
                            binding.tvLoveCount.text = originalLove
                            binding.loveBtn.setImageResource(R.drawable.heart_off)
                            Toast.makeText(requireContext(), "Insufficient HFCoin.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            R.id.user_views -> {
                statusViewModel.setPlayCurrentStatus(false)
                val modalBottomSheet = ViewsBottomSheet()
                val json = Gson().toJson(viewList)
                val bundle = Bundle()
                bundle.putString("views", json)
                modalBottomSheet.arguments = bundle
                modalBottomSheet.show(requireActivity().supportFragmentManager, ViewsBottomSheet.TAG)
            }
            R.id.reply_root -> {
                statusViewModel.setPlayCurrentStatus(false)
                binding.statusTextInput.visibility = View.VISIBLE
                binding.chatInput.requestFocus()
                binding.chatInput.postDelayed({
                    val imm: InputMethodManager = requireActivity().getSystemService(AppCompatActivity.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showSoftInput(binding.chatInput, InputMethodManager.HIDE_IMPLICIT_ONLY)
                }, 1000)
            }
            R.id.statusReplySend -> {
                val input = binding.chatInput.text.toString().trim()
                if (input == "") return
                when (statusUpdateData.isAdmin) {
                    false -> {
                        uploadText(input)
                        binding.chatInput.setText("")
                        Snackbar.make(binding.root, "Sending", Snackbar.LENGTH_LONG).show()
                    }
                    true -> {
                        Snackbar.make(binding.root, "This is a promotion", Snackbar.LENGTH_LONG).show()
                    }
                }
                hideKeyboard()
                binding.statusTextInput.visibility = View.GONE
                statusViewModel.setPlayCurrentStatus(true)
            }
        }
    }

    override fun onLongClick(v: View?): Boolean {
        when (v?.id) {
            R.id.my_view_status_root -> {
                statusViewModel.setPlayCurrentStatus(false)
                return true
            }
        }
        return false
    }

    override fun onTouch(v: View?, event: MotionEvent?): Boolean {
        when (v?.id) {
            R.id.my_view_status_root -> {
                when (event!!.action) {
                    MotionEvent.ACTION_UP -> {
                        statusViewModel.setPlayCurrentStatus(true)
                        return true
                    }
                }
            }
        }
        return false
    }

    companion object {
        const val TRANSFER_HISTORY = "user_coins_transfer"
        const val USER_DETAILS = "user_details"
        const val STATUS_DETAILS = "STATUS_DETAILS"
        const val STATUS_VIEWS = "STATUS_VIEWS"
        const val CHAT_REFERENCE = "chat_reference"
        const val TAG = "ModalBottomSheet"
        const val STATUS_UPDATE = "status_update"
    }
}

class ViewsBottomSheet : BottomSheetDialogFragment(), View.OnClickListener {
    private lateinit var binding: ViewsBottomSheetBinding
    private var user = FirebaseAuth.getInstance().currentUser
    private var timeRef = FirebaseDatabase.getInstance().reference
    private var viewsList = arrayListOf<StatusView>()
    private val statusViewModel by activityViewModels<StatusViewModel>()
    private var viewsAdapter = ViewsAdapter()

    @SuppressLint("NotifyDataSetChanged")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = ViewsBottomSheetBinding.inflate(inflater, container, false)
        binding.closeViews.setOnClickListener(this)
        timeRef = FirebaseDatabase.getInstance().reference.child("time").child(user!!.uid)
        viewsAdapter.viewsList = viewsList
        binding.rvViews.adapter = viewsAdapter
        binding.rvViews.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)

        val json = requireArguments().getString("views")
        val list = Gson().fromJson(json, ArrayList::class.java)
        for (i in list) {
            val view = Gson().fromJson(Gson().toJson(i), StatusView::class.java)
            if (view !in viewsList) viewsList.add(view)
            println("View status ******************************************* $view")
        }
        val viewsText = "${viewsList.size} views"
        binding.viewsCount.text = viewsText
        viewsList.sortWith(compareByDescending { data -> data.timeViewed })
        viewsAdapter.notifyDataSetChanged()
        return binding.root
    }

    override fun onDismiss(dialog: DialogInterface) {
        statusViewModel.setPlayCurrentStatus(true)
        super.onDismiss(dialog)
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.close_views -> this.dismiss()
        }
    }

    companion object {
        const val TRANSFER_HISTORY = "user_coins_transfer"
        const val USER_DETAILS = "user_details"
        const val VIDEO_REEL_DETAILS = "VIDEO_REEL_DETAILS"
        const val TAG = "ModalBottomSheet"
    }
}

class ViewsAdapter : RecyclerView.Adapter<ViewsAdapter.ViewHolder>() {
    var viewsList = arrayListOf<StatusView>()
    lateinit var context: Context

    init {
        setHasStableIds(true)
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val viewImage: ShapeableImageView = itemView.findViewById(R.id.view_image)
        val viewName: TextView = itemView.findViewById(R.id.view_name)
        val viewTime: TextView = itemView.findViewById(R.id.view_time)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        context = parent.context
        val view = LayoutInflater.from(context).inflate(R.layout.row_status_view, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val datum = viewsList[position]
        try {
            val instance = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            instance.timeInMillis = datum.timeViewed.toLong()
            val timePostedInMillis = instance.timeInMillis / 1000
            val nowInMill = Calendar.getInstance().timeInMillis / 1000
            val diff = nowInMill - timePostedInMillis
            if (diff < 86400) { // LESS THAN A DAY
                holder.viewTime.text = Util.statusTime(diff, datum.timeViewed.toLong())
            }
            holder.viewName.text = datum.nameTemp
            Glide.with(context).load(datum.imageTemp).into(holder.viewImage)
        } catch (e: Exception) {
        }
    }

    override fun getItemCount() = viewsList.size
    override fun getItemId(position: Int) = position.toLong()
}