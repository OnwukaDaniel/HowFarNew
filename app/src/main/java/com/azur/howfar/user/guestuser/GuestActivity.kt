package com.azur.howfar.user.guestuser

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.azur.howfar.R
import com.azur.howfar.activity.BaseActivity
import com.azur.howfar.chat.GuestChatActivity
import com.azur.howfar.databinding.ActivityGuestBinding
import com.azur.howfar.howfarchat.chat.FragmentDisplayImage
import com.azur.howfar.howfarchat.chat.UserProfileActivity
import com.azur.howfar.livedata.ChildEventLiveData
import com.azur.howfar.livedata.ValueEventLiveData
import com.azur.howfar.models.EventListenerType
import com.azur.howfar.models.EventListenerType.onDataChange
import com.azur.howfar.models.Moment
import com.azur.howfar.models.UserProfile
import com.azur.howfar.models.VideoPost
import com.azur.howfar.retrofit.Const
import com.azur.howfar.user.FollowrsListActivity
import com.azur.howfar.utils.Util
import com.azur.howfar.viewmodel.MomentViewModel
import com.azur.howfar.viewmodel.VideoPostsViewModel
import com.bumptech.glide.Glide
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.OnTabSelectedListener
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class GuestActivity : BaseActivity(), View.OnClickListener {
    private val binding by lazy { ActivityGuestBinding.inflate(layoutInflater) }
    private lateinit var guestTabAdapter: GuestTabAdapter
    private var userProfile: UserProfile = UserProfile()
    private var userStr = ""
    private var user = FirebaseAuth.getInstance().currentUser
    private val listOfFollowers: ArrayList<String> = arrayListOf()
    private val observedFollowers: ArrayList<String> = arrayListOf()
    private val listOfFollowing: ArrayList<String> = arrayListOf()
    private var followFlag = 0
    private var guestUserPostsFragment = GuestUserPostsFragment()
    private var guestUserReelsFragment = GuestUserReelsFragment()
    private val momentViewModel by viewModels<MomentViewModel>()
    private val videoPostViewModel by viewModels<VideoPostsViewModel>()
    private var bioDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        userStr = intent.getStringExtra(Const.USER_STR)!!
        if (userStr == user!!.uid) {
            binding.tvFollowStatus.text = "YOU"
            binding.lytMessage.visibility = View.GONE
        }
        binding.tvBio.setOnClickListener(this)
        binding.lytMessage.setOnClickListener(this)
        binding.lytFollowUnfollow.setOnClickListener(this)

        binding.lytFollowrs.setOnClickListener(this)
        binding.lytMyVideos2.setOnClickListener(this)
        binding.lytMyPost2.setOnClickListener(this)

        binding.imgUser.setOnClickListener(this)
        guestTabAdapter = GuestTabAdapter(this)
        binding.pdFollow.visibility = View.GONE
        getFollowers()
        getThisUser()
        getMomentPost()
        getVideos()
        initTabLayout()
    }

    override fun onResume() {
        window.statusBarColor = resources!!.getColor(R.color.black_back)
        super.onResume()
    }

    private fun getThisUser() = try {
        val profileRef = FirebaseDatabase.getInstance().reference.child(USER_DETAILS).child(userStr)
        profileRef.get().addOnSuccessListener {
            if (it.exists()) {
                userProfile = it.getValue(UserProfile::class.java)!!
                Glide.with(this).load(userProfile.image).error(R.drawable.ic_avatar).centerCrop().into(binding.imgUser)
                binding.tvName.text = userProfile.name
                binding.tvBio.text = userProfile.bio
                binding.tvCountry.text = userProfile.countryCode.uppercase()
                binding.tvLevel.text = " Lv. "

                val following = FirebaseDatabase.getInstance().reference.child(FOLLOWERS).child(userStr)
                ValueEventLiveData(following).observe(this) { followersSnap ->
                    when (followersSnap.second) {
                        onDataChange -> {
                            for (i in followersSnap.first.children) if (i.value.toString() !in observedFollowers) observedFollowers.add(i.value.toString())
                            binding.tvFollowrs.text = observedFollowers.size.toString()
                        }
                    }
                }
                if (userProfile.gender.equals("male", ignoreCase = true)) {
                    binding.imgGender.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.male))
                } else {
                    binding.imgGender.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.female))
                }
            }
        }
    } catch (e: Exception) {
    }

    private fun getFollowers() {
        val listOfFollowers: ArrayList<String> = arrayListOf()
        val listOfFollowing: ArrayList<String> = arrayListOf()
        ValueEventLiveData(FirebaseDatabase.getInstance().reference.child(FOLLOWERS).child(user!!.uid)).observe(this) {
            when (it.second) {
                onDataChange -> {
                    for (i in it.first.children) {
                        if (i.value.toString() !in listOfFollowers) listOfFollowers.add(i.value.toString())
                        if (userStr != user!!.uid) followIndicator(listOfFollowers = listOfFollowers)
                    }
                }
            }
        }
        ValueEventLiveData(FirebaseDatabase.getInstance().reference.child(FOLLOWING).child(user!!.uid)).observe(this) {
            when (it.second) {
                onDataChange -> {
                    for (i in it.first.children) {
                        if (i.value.toString() !in listOfFollowing) listOfFollowing.add(i.value.toString())
                        if (userStr != user!!.uid) followIndicator(listOfFollowing = listOfFollowing)
                    }
                }
            }
        }
    }

    private fun getVideos() {
        val postRecord = FirebaseDatabase.getInstance().reference.child(VIDEO_POST_RECORD).child(userStr)
        postRecord.get().addOnSuccessListener {
            val videoPosts: ArrayList<VideoPost> = arrayListOf()
            if (it.exists()) {
                for (i in it.children) videoPosts.add(i.getValue(VideoPost::class.java)!!)
                binding.tvVideos.text = videoPosts.size.toString()
                videoPostViewModel.setVideoPostListList(videoPosts)
            }
        }
    }

    private fun getMomentPost() {
        val postRecord = FirebaseDatabase.getInstance().reference.child(PERSONAL_POST_RECORD).child(userStr)
        ValueEventLiveData(postRecord).observe(this){
            val momentList: ArrayList<Moment> = arrayListOf()
            when (it.second) {
                onDataChange -> {
                    for (i in it.first.children) if (i.getValue(Moment::class.java)!! !in momentList) momentList.add(i.getValue(Moment::class.java)!!)
                    binding.tvPosts.text = momentList.size.toString()
                    momentViewModel.setMomentList(momentList)
                }
            }
        }
    }

    private fun doneLoadingFollow() {
        binding.pdFollow.visibility = View.GONE
        binding.tvFollowStatus.visibility = View.VISIBLE
    }

    private fun followIndicator(listOfFollowers: ArrayList<String> = arrayListOf(), listOfFollowing: ArrayList<String> = arrayListOf()) {
        when (userStr) {
            in listOfFollowers -> {
                followFlag = IS_FOLLOWING
                binding.tvFollowStatus.text = "FOLLOWER"
                binding.lytFollowUnfollow.backgroundTintList = ContextCompat.getColorStateList(this, R.color.graylight)
            }
            in listOfFollowing -> {
                followFlag = I_AM_FOLLOWING
                binding.tvFollowStatus.text = "FOLLOWING"
                binding.lytFollowUnfollow.backgroundTintList = ContextCompat.getColorStateList(this, R.color.pink)
            }
            else -> {
                followFlag = FOLLOW
                binding.tvFollowStatus.text = "FOLLOW"
            }
        }
    }

    private fun followLogic() = try {
        val alertDialog = AlertDialog.Builder(this)
        alertDialog.setTitle("Follow ${userProfile.name}")
        when (followFlag) {
            FOLLOW -> {
                alertDialog.setMessage("Follow this user?")
                alertDialog.setPositiveButton("Ok") { dialog, _ ->
                    val listOfFollowingTemp: ArrayList<String> = arrayListOf()
                    val myFollowing = FirebaseDatabase.getInstance().reference.child(FOLLOWING).child(user!!.uid)
                    val followersOfOther = FirebaseDatabase.getInstance().reference.child(FOLLOWERS).child(userProfile.uid)

                    followersOfOther.get().addOnSuccessListener { followersOfOtherSnap ->
                        if (followersOfOtherSnap.exists()) {
                            val lst: ArrayList<String> = arrayListOf()
                            for (i in followersOfOtherSnap.children) lst.add(i.value.toString())
                            lst.add(user!!.uid)
                            followersOfOther.setValue(lst).addOnSuccessListener{
                                Util.sendNotification(message = "New Follower", body = "You have a new follower", receiverUid = userProfile.uid,  view = "New Follower")
                            }
                        } else followersOfOther.setValue(arrayListOf(user!!.uid)).addOnSuccessListener{
                            Util.sendNotification(message = "New Follower", body = "You have a new follower", receiverUid = userProfile.uid,  view = "New Follower")
                        }
                    }
                    myFollowing.get().addOnSuccessListener { followingSnap ->
                        doneLoadingFollow()
                        if (followingSnap.exists()) {
                            for (i in followingSnap.children) listOfFollowingTemp.add(i.value.toString())
                            listOfFollowingTemp.add(userProfile.uid)
                            listOfFollowing.add(userProfile.uid)
                            myFollowing.setValue(listOfFollowingTemp).addOnSuccessListener {
                                followFlag = UserProfileActivity.I_AM_FOLLOWING
                                dialog.dismiss()
                            }.addOnFailureListener {
                                dialog.dismiss()
                                showSnackBar(binding.root, "Unable to follow. Try again.")
                            }
                        } else myFollowing.setValue(arrayListOf(userProfile.uid)).addOnSuccessListener {
                            followFlag = UserProfileActivity.I_AM_FOLLOWING
                            dialog.dismiss()
                        }.addOnFailureListener {
                            dialog.dismiss()
                            showSnackBar(binding.root, "Unable to follow. Try again.")
                        }
                    }
                    dialog.dismiss()
                }
            }
            IS_FOLLOWING -> {
                alertDialog.setMessage("${userProfile.name} is following you")
                alertDialog.setPositiveButton("Ok") { dialog, _ ->
                    doneLoadingFollow()
                    dialog.dismiss()
                }
            }
            I_AM_FOLLOWING -> {
                alertDialog.setMessage("Unfollow  ${userProfile.name}")
                alertDialog.setPositiveButton("Ok") { dialog, _ ->
                    val listOfFollowingTemp: ArrayList<String> = arrayListOf()
                    val myFollowing = FirebaseDatabase.getInstance().reference.child(FOLLOWING).child(user!!.uid)
                    val followersOfOther = FirebaseDatabase.getInstance().reference.child(FOLLOWERS).child(userProfile.uid)

                    followersOfOther.get().addOnSuccessListener { followersOfOtherSnap ->
                        if (followersOfOtherSnap.exists()) {
                            val lst: ArrayList<String> = arrayListOf()
                            for (i in followersOfOtherSnap.children) lst.add(i.value.toString())
                            lst.remove(user!!.uid)
                            followersOfOther.setValue(lst)
                        }
                    }
                    myFollowing.get().addOnSuccessListener { followingSnap ->
                        doneLoadingFollow()
                        if (followingSnap.exists()) {
                            for (i in followingSnap.children) listOfFollowingTemp.add(i.value.toString())
                            listOfFollowingTemp.remove(userProfile.uid)
                            listOfFollowing.remove(userProfile.uid)
                            myFollowing.setValue(listOfFollowingTemp).addOnSuccessListener {
                                followFlag = UserProfileActivity.FOLLOW
                                dialog.dismiss()
                            }.addOnFailureListener {
                                dialog.dismiss()
                                showSnackBar(binding.root, "Unable to follow. Try again.")
                            }
                        }
                    }.addOnFailureListener {
                        doneLoadingFollow()
                        dialog.dismiss()
                        showSnackBar(binding.root, "Unable to Fetch data. Please retry")
                    }
                }
            }
        }
        alertDialog.create().show()
        alertDialog.setOnDismissListener {
            doneLoadingFollow()
        }
    } catch (e: Exception) {
    }

    private fun initListener() {
        if (userStr != user!!.uid) {
            val followers = FirebaseDatabase.getInstance().reference.child(FOLLOWERS).child(userStr)
            val following = FirebaseDatabase.getInstance().reference.child(FOLLOWING).child(userStr)
            followers.keepSynced(false)
            following.keepSynced(false)

            followers.get().addOnSuccessListener { follower ->
                doneLoadingFollow()
                if (follower.exists()) {
                    for (i in follower.children) if (i.value.toString() !in listOfFollowers) listOfFollowers.add(i.value.toString())
                }
                following.get().addOnSuccessListener { followingShot ->
                    doneLoadingFollow()
                    if (followingShot.exists()) {
                        for (i in followingShot.children) if (i.value.toString() !in listOfFollowing) listOfFollowing.add(i.value.toString())
                        followLogic()
                    } else followLogic()
                }.addOnFailureListener { doneLoadingFollow() }
            }.addOnFailureListener { doneLoadingFollow() }
        }
    }

    private fun initTabLayout() {
        val tabsText = arrayListOf("Posts", "Videos")
        guestTabAdapter.dataset = arrayListOf(guestUserPostsFragment, guestUserReelsFragment)
        binding.viewPager.adapter = guestTabAdapter
        if (intent.hasExtra("video")) binding.viewPager.setCurrentItem(1, true)
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tabs, position -> tabs.text = tabsText[position] }.attach()

        binding.tabLayout.addOnTabSelectedListener(object : OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val v = tab.customView
                if (v != null) {
                    val tv = v.findViewById<View>(R.id.tvTab) as TextView
                    tv.setTextColor(ContextCompat.getColor(this@GuestActivity, R.color.white))
                    val indicator = v.findViewById(R.id.indicator) as View
                    indicator.visibility = View.VISIBLE
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {
                //ll
                val v = tab.customView
                if (v != null) {
                    val tv = v.findViewById<View>(R.id.tvTab) as TextView
                    tv.setTextColor(ContextCompat.getColor(this@GuestActivity, R.color.graylight))
                    val indicator = v.findViewById(R.id.indicator) as View
                    indicator.visibility = View.INVISIBLE
                }
            }

            override fun onTabReselected(tab: TabLayout.Tab) {
            }
        })
    }

    inner class GuestTabAdapter(fa: FragmentActivity) : FragmentStateAdapter(fa) {
        lateinit var dataset: ArrayList<Fragment>
        override fun getItemCount(): Int = dataset.size
        override fun createFragment(position: Int): Fragment {
            return dataset[position]
        }
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.lytFollowrs -> startActivity(Intent(this, FollowrsListActivity::class.java).putExtra(Const.USER_STR, userStr))
            R.id.lytMyVideos2 -> binding.viewPager.setCurrentItem(1, true)
            R.id.lytMyPost2 -> binding.viewPager.setCurrentItem(0, true)
            R.id.lytMessage -> startActivity(Intent(this, GuestChatActivity::class.java).putExtra("userId", userStr))
            R.id.tvBio -> try {
                val alertDialog = AlertDialog.Builder(this)
                alertDialog.setTitle("Bio")
                alertDialog.setMessage(if (userProfile.bio == "") "No bio" else userProfile.bio)
                bioDialog = alertDialog.create()
                if (bioDialog != null) {
                    bioDialog!!.dismiss()
                    bioDialog!!.show()
                }
            } catch (e: Exception) {
            }
            R.id.imgUser -> {
                val fragment = FragmentDisplayImage()
                val bundle = Bundle()
                bundle.putString("image", userProfile.image)
                fragment.arguments = bundle
                supportFragmentManager.beginTransaction().addToBackStack("image").replace(R.id.guest_root, fragment).commit()
            }
            R.id.lytFollowUnfollow -> {
                if (userStr != user!!.uid) {
                    binding.pdFollow.visibility = View.VISIBLE
                    binding.tvFollowStatus.visibility = View.INVISIBLE
                    initListener()
                }
            }
        }
    }

    companion object {
        const val MOMENT_DATA = "MOMENT_DATA"
        const val PERSONAL_POST_RECORD = "PERSONAL_POST_RECORD"
        const val CALL_REFERENCE = "call_reference"
        const val USER_DETAILS = "user_details"
        const val TRANSFER_HISTORY = "user_coins_transfer"
        const val CHAT_REFERENCE = "chat_reference"
        const val MY_BLOCKED_CONTACTS = "blocked_contacts"
        const val PERMISSION_CODE = 22
        const val FOLLOWERS = "followers"
        const val VIDEO_POST_RECORD = "VIDEO_POST_RECORD"
        const val FOLLOWING = "following"
        val REQUESTED_PERMISSIONS = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
        )

        const val FOLLOW = 0
        const val IS_FOLLOWING = 1
        const val I_AM_FOLLOWING = 2
    }
}