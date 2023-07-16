package com.azur.howfar.activity

import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE
import com.azur.howfar.R
import com.azur.howfar.chat.MessageFragment
import com.azur.howfar.databinding.ActivityMainBinding
import com.azur.howfar.databinding.BottomSheetChoicesBinding
import com.azur.howfar.howfarchat.ChatLanding
import com.azur.howfar.livedata.ValueEventLiveData
import com.azur.howfar.livestreamming.GotoLiveActivity
import com.azur.howfar.livestreamming.LiveFragmentMain
import com.azur.howfar.match.RandomMatchActivity
import com.azur.howfar.models.*
import com.azur.howfar.models.EventListenerType.onDataChange
import com.azur.howfar.posts.FeedFragmentMain
import com.azur.howfar.posts.UploadPostActivity
import com.azur.howfar.reels.VideoListActivity
import com.azur.howfar.user.ProfileFragment
import com.azur.howfar.utils.HFCoinUtils
import com.azur.howfar.videos.ChooseVideoTypeActivity
import com.azur.howfar.viewmodel.*
import com.azur.howfar.workManger.VideoPostWorkManager
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase

class MainActivity : BaseActivity(), View.OnClickListener {
    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }
    private var bottomSheetDialog: BottomSheetDialog? = null
    private var myAuth = FirebaseAuth.getInstance().currentUser
    private var myProfile = UserProfile()
    private var dataset: ArrayList<ChatData> = arrayListOf()
    private var user = FirebaseAuth.getInstance().currentUser
    private val momentViewModel by viewModels<MomentViewModel>()
    private val videoPostViewModel by viewModels<VideoPostsViewModel>()
    private val userProfileViewModel by viewModels<UserProfileViewmodel>()
    private val timeStringViewModel by viewModels<TimeStringViewModel>()
    private val chatDataViewModel by viewModels<ChatDataViewModel>()

    private val timeHandler = Handler(Looper.getMainLooper())
    private val timeRunnable = object : Runnable {
        override fun run() {
            timeStringViewModel.setStringValue(onlineTime)
            timeHandler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        initBottomBar()
        setDefaultBottomBar()
        defaultHome()
        getVideos()
        getMomentPost()
        getChats()
        bannerDisplay()
        giftUser()
        timeHandler.postDelayed(timeRunnable, 1000)
        binding.imgBall.setOnClickListener(this)

        /*val profileRef = FirebaseDatabase.getInstance().reference.child(USER_DETAILS).child(user!!.uid)
        profileRef.keepSynced(false)
        ValueEventLiveData(profileRef).observe(this) {
            when (it.second) {
                onDataChange -> {
                    myProfile = it.first.getValue(UserProfile::class.java)!!
                    userProfileViewModel.setUserProfile(myProfile)
                }
            }
        }*/
    }

    private fun giftUser() {
        /*val ref = Firebase.database("https://howfar-b24ef-overtime-change.firebaseio.com/").reference.child("promotion_app_gift")
        ref.get().addOnSuccessListener {
            if (it.exists()) {
                val promotionName = it.value.toString()
                val promoRef = Firebase.database("https://howfar-b24ef-overtime-change.firebaseio.com/").reference.child(promotionName)
                promoRef.get().addOnSuccessListener { promoSnap ->
                    val list: ArrayList<String> = arrayListOf()
                    if (promoSnap.exists()) for (i in promoSnap.children) list.add(i.value.toString()) else sendGiftFinal(promoRef)
                    if (myAuth!!.uid !in list) sendGiftFinal(promoRef)
                }
            }
        }*/
    }

    private fun sendGiftFinal(promoRef: DatabaseReference) {
        HFCoinUtils.sendGift(200F, myAuth!!.uid)
        val alertDialog = AlertDialog.Builder(this)
        alertDialog.setTitle("A gift from HowFar team.")
        alertDialog.setMessage("You were gifted 200F.")
        alertDialog.setPositiveButton("Ok"){dialog, _ -> dialog.dismiss()}
        alertDialog.create().show()
        promoRef.push().setValue(myAuth!!.uid)
    }

    private fun getVideos() {
        /*var videoPosts: ArrayList<VideoPost> = arrayListOf()
        val postRecord = FirebaseDatabase.getInstance().reference.child(VideoPostWorkManager.VIDEO_POST_RECORD).child(myAuth!!.uid)
        ValueEventLiveData(postRecord).observe(this){ it->
            when(it.second){
                onDataChange->{
                    for (i in it.first.children) {
                        val video = i.getValue(VideoPost::class.java)!!
                        if(video !in videoPosts) videoPosts.add(video)
                    }
                    videoPostViewModel.setVideoPostListList(videoPosts)
                }
            }
        }*/
    }

    private fun getMomentPost() {
        /*val momentList: ArrayList<Moment> = arrayListOf()
        val postRecord = FirebaseDatabase.getInstance().reference.child(PERSONAL_POST_RECORD).child(myAuth!!.uid)
        ValueEventLiveData(postRecord).observe(this){ it->
            when(it.second){
                onDataChange->{
                    for (i in it.first.children) {
                        val moment = i.getValue(Moment::class.java)!!
                       if (moment !in momentList) momentList.add(moment)
                    }
                    momentViewModel.setMomentList(momentList)
                }
            }
        }*/
    }

    private fun getChats() {
        /*val myChatsRef = FirebaseDatabase.getInstance().reference.child(GUEST_USERS_CHAT).child(user!!.uid)
        ValueEventLiveData(myChatsRef).observe(this) { chatSnap ->
            when (chatSnap.second) {
                onDataChange -> {
                    var unReadMessagesAll = 0
                    dataset.clear()
                    for (i in chatSnap.first.children) {
                        var unReadMessages = 0
                        for (msg in i.children) {
                            val chat = msg.getValue(ChatData::class.java)!!
                            if (!chat.read && chat.senderuid != user!!.uid) {
                                unReadMessages++
                                unReadMessagesAll++
                            }
                        }
                        val chatData = i.children.last().getValue(ChatData::class.java)!!
                        chatData.newMessages = unReadMessages
                        dataset.add(chatData)
                    }
                    dataset.sortWith(compareByDescending { it.uniqueQuerableTime })
                    binding.chatUnreadCount.text = unReadMessagesAll.toString()
                    chatDataViewModel.setChatData(dataset)
                }
            }
        }*/
    }

    private fun defaultHome() {
        supportFragmentManager.beginTransaction().replace(R.id.frame, FeedFragmentMain()).commit()
        binding.animHome.imageTintList = ContextCompat.getColorStateList(this, R.color.offwhite)
    }

    private fun goToChat() {
        val intent = Intent(this, ChatLanding::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        overridePendingTransition(R.anim.enter_left_to_right, R.anim.exit_left_to_right)
        return
    }

    private fun bannerDisplay() {
        /*val BANNER_PROMOTION = "BANNER_PROMOTION"
        val bannerRef = FirebaseDatabase.getInstance().reference.child(BANNER_PROMOTION).child(myAuth!!.uid)
        ValueEventLiveData(bannerRef).observe(this) {
            when (it.second) {
                onDataChange -> {
                    val bannerData = it.first.getValue(BannerData::class.java)!!
                    when (bannerData.seen) {
                        false -> {
                            val view = layoutInflater.inflate(R.layout.banner_message, binding.root, false)
                            val param = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                            view.layoutParams = param
                            binding.root.addView(view)
                            val msgText: TextView = view.findViewById(R.id.banner_message)

                            val bannerImage: ImageView = view.findViewById(R.id.banner_image)
                            when (bannerData.image) {
                                "" -> {
                                    msgText.minLines = 12
                                    bannerImage.visibility = View.GONE
                                }
                                else -> {
                                    msgText.minLines = 1
                                    bannerImage.visibility = View.VISIBLE
                                    Glide.with(this).load(bannerData.image).centerCrop().into(bannerImage)
                                }
                            }
                            val bannerCancel: ImageView = view.findViewById(R.id.banner_cancel)
                            msgText.text = bannerData.message
                            bannerCancel.setOnClickListener { binding.root.removeView(view) }
                            bannerRef.removeValue()
                        }
                        else ->{}
                    }
                }
            }
        }*/
    }

    override fun onBackPressed() {
        if (supportFragmentManager.backStackEntryCount >= 1) {
            super.onBackPressed()
            setDefaultBottomBar()
            when (supportFragmentManager.findFragmentById(R.id.frame)) {
                is FeedFragmentMain -> binding.animDiscover.imageTintList = ContextCompat.getColorStateList(this, R.color.offwhite)
                is MessageFragment -> binding.animChat.imageTintList = ContextCompat.getColorStateList(this, R.color.offwhite)
                is ProfileFragment -> binding.animProfile.imageTintList = ContextCompat.getColorStateList(this, R.color.offwhite)
                else -> binding.animHome.imageTintList = ContextCompat.getColorStateList(this, R.color.offwhite)
            }
        } else goToChat()
    }

    private fun setDefaultBottomBar() {
        binding.animHome.imageTintList = ContextCompat.getColorStateList(this, R.color.graylight)
        binding.animDiscover.imageTintList = ContextCompat.getColorStateList(this, R.color.graylight)
        binding.animChat.imageTintList = ContextCompat.getColorStateList(this, R.color.graylight)
        binding.animProfile.imageTintList = ContextCompat.getColorStateList(this, R.color.graylight)
    }

    private fun setUpFragment(fragment: Fragment, animHome: ImageView, fragmentName: String) {
        setDefaultBottomBar()
        animHome.imageTintList = ContextCompat.getColorStateList(this, R.color.offwhite)
        supportFragmentManager.beginTransaction().addToBackStack(fragmentName).replace(R.id.frame, fragment).commit()
    }

    private fun initBottomBar() {
        binding.lytHome.setOnClickListener {
            supportFragmentManager.popBackStack(null, POP_BACK_STACK_INCLUSIVE)
            setDefaultBottomBar()
            binding.animHome.imageTintList = ContextCompat.getColorStateList(this, R.color.offwhite)
        }
        binding.lytDiscover.setOnClickListener { setUpFragment(LiveFragmentMain(), binding.animDiscover, "animDiscover") }
        binding.lytChat.setOnClickListener { setUpFragment(MessageFragment(), binding.animChat, "animChat") }
        binding.lytProfile.setOnClickListener { setUpFragment(ProfileFragment(), binding.animProfile, "animProfile") }
    }

    override fun onResume() {
        super.onResume()
        window.statusBarColor = resources!!.getColor(R.color.black_back)
        /*FirebaseAuth.getInstance().addAuthStateListener { p0 ->
            if (p0.currentUser == null) {
                val intent = Intent(this@MainActivity, LoginActivityActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                overridePendingTransition(R.anim.enter_right_to_left, R.anim.exit_right_to_left)
            }
        }*/
    }

    companion object {
        const val TRANSFER_HISTORY = "user_coins_transfer"
        const val GUEST_USERS_CHAT = "GUEST_USERS_CHAT"
        const val MOMENT_DATA = "MOMENT_DATA"
        const val PERSONAL_POST_RECORD = "PERSONAL_POST_RECORD"
        const val USER_DETAILS = "user_details"
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.imgBall -> {
                bottomSheetDialog = BottomSheetDialog(this, R.style.CustomBottomSheetDialogTheme)
                bottomSheetDialog!!.setOnShowListener { dialog: DialogInterface ->
                    val d = dialog as BottomSheetDialog
                    val bottomSheet = d.findViewById<FrameLayout>(androidx.navigation.ui.R.id.design_bottom_sheet)
                    if (bottomSheet != null) {
                        BottomSheetBehavior.from(bottomSheet).state = BottomSheetBehavior.STATE_EXPANDED
                    }
                }
                val bottomSheetChoicesBinding: BottomSheetChoicesBinding =
                    DataBindingUtil.inflate(LayoutInflater.from(this), R.layout.bottom_sheet_choices, null, false)
                bottomSheetDialog!!.setContentView(bottomSheetChoicesBinding.root)
                bottomSheetDialog!!.show()
                bottomSheetChoicesBinding.imgClose.setOnClickListener { v1: View? ->
                    bottomSheetDialog!!.dismiss()
                }
                bottomSheetChoicesBinding.lytLive.setOnClickListener { v1: View? ->
                    bottomSheetDialog!!.dismiss()
                    startActivity(Intent(this, GotoLiveActivity::class.java))
                }
                bottomSheetChoicesBinding.lytVideos.setOnClickListener { v1: View? ->
                    bottomSheetDialog!!.dismiss()
                    startActivity(Intent(this, ChooseVideoTypeActivity::class.java))
                }
                bottomSheetChoicesBinding.lytMoments.setOnClickListener { v1: View? ->
                    bottomSheetDialog!!.dismiss()
                    startActivity(Intent(this, UploadPostActivity::class.java))
                }
            }
        }
    }
}