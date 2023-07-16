package com.azur.howfar.bottomsheets

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import com.azur.howfar.R
import com.azur.howfar.databinding.BottomSheetUserProfileBinding
import com.azur.howfar.howfarchat.chat.UserProfileActivity
import com.azur.howfar.livedata.ValueEventLiveData
import com.azur.howfar.models.EventListenerType
import com.azur.howfar.models.Moment
import com.azur.howfar.models.UserProfile
import com.azur.howfar.models.VideoPost
import com.azur.howfar.retrofit.Const
import com.azur.howfar.user.guestuser.GuestActivity
import com.azur.howfar.utils.Util
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.gson.Gson

class UserProfileBottomSheet : BottomSheetDialogFragment(), View.OnClickListener {
    lateinit var binding: BottomSheetUserProfileBinding
    private var user = FirebaseAuth.getInstance().currentUser
    private var userStr = ""
    private var userProfile = UserProfile()
    private var followFlag = 0
    private val listOfFollowers: ArrayList<String> = arrayListOf()
    private val observedFollowers: ArrayList<String> = arrayListOf()
    private val listOfFollowing: ArrayList<String> = arrayListOf()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = BottomSheetUserProfileBinding.inflate(inflater, container, false)
        val json = requireArguments().getString("data")!!
        userProfile = Gson().fromJson(json, UserProfile::class.java)!!
        userStr = userProfile.uid
        if (userStr == user!!.uid) {
            binding.tvFollowStatus.text = "YOU"
            binding.btnMessage.visibility = View.GONE
        }
        binding.btnClose.setOnClickListener(this)
        binding.btnMessage.setOnClickListener(this)
        binding.lytFollowUnfollow.setOnClickListener(this)
        binding.lytFollowrs.setOnClickListener(this)
        binding.lytMyVideos2.setOnClickListener(this)
        binding.lytMyPost2.setOnClickListener(this)
        binding.imgUser.setOnClickListener(this)
        binding.pdFollow.visibility = View.GONE
        getThisUser()
        getFollowers()
        getVideos()
        getMomentPost()
        return binding.root
    }

    private fun getThisUser() = try {
        Glide.with(this).load(userProfile.image).error(R.drawable.ic_avatar).centerCrop().into(binding.imgUser)
        binding.tvName.text = userProfile.name
        binding.tvCountry.text = userProfile.countryCode.uppercase()
        binding.tvLevel.text = " Lv."
        if (userProfile.gender.equals("male", ignoreCase = true)) {
            binding.tvGender.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.male))
        } else {
            binding.tvGender.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.female))
        }

        val profileRef = FirebaseDatabase.getInstance().reference.child(GuestActivity.USER_DETAILS).child(userStr)
        profileRef.get().addOnSuccessListener {
            if (it.exists()) {
                userProfile = it.getValue(UserProfile::class.java)!!
                Glide.with(this).load(userProfile.image).error(R.drawable.ic_avatar).centerCrop().into(binding.imgUser)
                binding.tvName.text = userProfile.name
                binding.tvCountry.text = userProfile.countryCode.uppercase()
                binding.tvLevel.text = " Lv."

                val following = FirebaseDatabase.getInstance().reference.child(GuestActivity.FOLLOWERS).child(userStr)
                ValueEventLiveData(following).observe(this) { followersSnap ->
                    when (followersSnap.second) {
                        EventListenerType.onDataChange -> {
                            for (i in followersSnap.first.children) if (i.value.toString() !in observedFollowers) observedFollowers.add(i.value.toString())
                            binding.tvFollowrs.text = observedFollowers.size.toString()
                        }
                    }
                }
            }
        }
    } catch (e: Exception) {
    }

    private fun getFollowers() {
        val listOfFollowers: ArrayList<String> = arrayListOf()
        val listOfFollowing: ArrayList<String> = arrayListOf()
        ValueEventLiveData(FirebaseDatabase.getInstance().reference.child(GuestActivity.FOLLOWERS).child(user!!.uid)).observe(this) {
            when (it.second) {
                EventListenerType.onDataChange -> {
                    for (i in it.first.children) {
                        if (i.value.toString() !in listOfFollowers) listOfFollowers.add(i.value.toString())
                        if (userStr != user!!.uid) followIndicator(listOfFollowers = listOfFollowers)
                    }
                }
            }
        }
        ValueEventLiveData(FirebaseDatabase.getInstance().reference.child(GuestActivity.FOLLOWING).child(user!!.uid)).observe(this) {
            when (it.second) {
                EventListenerType.onDataChange -> {
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
            }
        }
    }

    private fun getMomentPost() {
        val postRecord = FirebaseDatabase.getInstance().reference.child(PERSONAL_POST_RECORD).child(userStr)
        postRecord.get().addOnSuccessListener {
            val momentList: ArrayList<Moment> = arrayListOf()
            if (it.exists()) {
                for (i in it.children) momentList.add(i.getValue(Moment::class.java)!!)
                binding.tvPosts.text = momentList.size.toString()
            }
        }
    }

    private fun followIndicator(listOfFollowers: ArrayList<String> = arrayListOf(), listOfFollowing: ArrayList<String> = arrayListOf()) {
        when (userStr) {
            in listOfFollowers -> {
                followFlag = GuestActivity.IS_FOLLOWING
                binding.tvFollowStatus.text = "FOLLOWER"
                binding.lytFollowUnfollow.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.graylight)
            }
            in listOfFollowing -> {
                followFlag = GuestActivity.I_AM_FOLLOWING
                binding.tvFollowStatus.text = "FOLLOWING"
                binding.lytFollowUnfollow.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.pink)
            }
            else -> {
                followFlag = GuestActivity.FOLLOW
                binding.tvFollowStatus.text = "FOLLOW"
            }
        }
    }

    private fun followLogic() = try {
        val alertDialog = AlertDialog.Builder(requireContext())
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

    private fun showSnackBar(root: View, msg: String) {
        Snackbar.make(root, msg, Snackbar.LENGTH_LONG).show()
    }

    private fun doneLoadingFollow() {
        binding.pdFollow.visibility = View.GONE
        binding.tvFollowStatus.visibility = View.VISIBLE
    }

    private fun initListener() {
        if (userStr != user!!.uid) {
            val followers = FirebaseDatabase.getInstance().reference.child(GuestActivity.FOLLOWERS).child(userStr)
            val following = FirebaseDatabase.getInstance().reference.child(GuestActivity.FOLLOWING).child(userStr)
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

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.btnClose -> this.dismiss()
            R.id.lytFollowUnfollow -> {
                if (userStr != user!!.uid) {
                    binding.pdFollow.visibility = View.VISIBLE
                    binding.tvFollowStatus.visibility = View.INVISIBLE
                    initListener()
                }
            }
            R.id.lytMyPost2 -> startActivity(Intent(requireActivity(), GuestActivity::class.java).putExtra(Const.USER_STR, userStr))
            R.id.lytMyVideos2 -> startActivity(Intent(requireActivity(), GuestActivity::class.java).putExtra(Const.USER_STR, userStr).putExtra("video", true))
        }
    }

    companion object {
        const val TRANSFER_HISTORY = "user_coins_transfer"
        const val MOMENT_DETAILS = "MOMENT_DETAILS"
        const val MOMENT_DATA = "MOMENT_DATA"
        const val PERSONAL_POST_RECORD = "PERSONAL_POST_RECORD"
        const val MOMENT_IMAGES = "MOMENT IMAGES"
        const val USER_DETAILS = "user_details"
        const val VIDEO_POST_RECORD = "VIDEO_POST_RECORD"
        const val FOLLOWERS = "followers"
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