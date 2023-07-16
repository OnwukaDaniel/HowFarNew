package com.azur.howfar.howfarchat.chat

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import com.bumptech.glide.Glide
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.gson.Gson
import com.azur.howfar.R
import com.azur.howfar.activity.BaseActivity
import com.azur.howfar.databinding.ActivityUserProfileBinding
import com.azur.howfar.dilog.IncomingCallDialog
import com.azur.howfar.dilog.ProgressFragment
import com.azur.howfar.models.CallData
import com.azur.howfar.models.CallType
import com.azur.howfar.models.UserProfile
import com.azur.howfar.utils.CallUtils
import com.azur.howfar.utils.Util
import kotlinx.coroutines.*

class UserProfileActivity : BaseActivity(), View.OnClickListener {
    private val binding by lazy { ActivityUserProfileBinding.inflate(layoutInflater) }
    private var receiverProfile = UserProfile()
    private lateinit var callUtils: CallUtils
    private val listOfFollowers: ArrayList<String> = arrayListOf()
    private val listOfFollowing: ArrayList<String> = arrayListOf()
    private var followFlag = 0
    private var userUid = ""
    private val myAuth = FirebaseAuth.getInstance().currentUser!!.uid
    private val user = FirebaseAuth.getInstance().currentUser
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        callUtils = CallUtils(this, this)
        setContentView(binding.root)
        userUid = intent.getStringExtra("data")!!
        binding.profileCall.setOnClickListener(this)
        binding.profileBlockUser.setOnClickListener(this)
        binding.profileChat.setOnClickListener(this)
        binding.profileImage.setOnClickListener(this)
        binding.profileVideo.setOnClickListener(this)
        binding.profileBack.setOnClickListener(this)
        binding.profileFollowButton.setOnClickListener(this)
        initFollow(false)
        val myRef = FirebaseDatabase.getInstance().reference.child("user_details").child(userUid)
        myRef.get().addOnSuccessListener {
            receiverProfile = it.getValue(UserProfile::class.java)!!
            try {
                Glide.with(this).load(receiverProfile.image).centerCrop().into(binding.profileImage)
            } catch (e: Exception) {
            }
            setData()
        }
    }

    override fun onResume() {
        window.statusBarColor = Color.parseColor("#1C1C2E")
        super.onResume()
    }

    private fun setData() {
        binding.profileName.text = receiverProfile.name
        binding.profilePhone.text = receiverProfile.phone
        binding.profileUsername.text = receiverProfile.name
        if (receiverProfile.image == "") binding.profileImage.setImageResource(R.drawable.ic_avatar) else try {
            Glide.with(this).load(receiverProfile.image).centerCrop().into(binding.profileImage)
        } catch (e: Exception) {
        }
    }

    private fun doneLoadingFollow() {
        binding.pdFollow.visibility = View.GONE
        binding.profileFollowText.visibility = View.VISIBLE
    }

    private fun initFollow(onClicked: Boolean) {
        if (userUid != user!!.uid) {
            val followers = FirebaseDatabase.getInstance().reference.child(FOLLOWERS).child(userUid)
            val following = FirebaseDatabase.getInstance().reference.child(FOLLOWING).child(userUid)
            followers.keepSynced(false)
            following.keepSynced(false)

            followers.get().addOnSuccessListener { follower ->
                doneLoadingFollow()
                if (follower.exists()) {
                    listOfFollowers.clear()
                    for (i in follower.children) listOfFollowers.add(i.value.toString())
                }
                when (user.uid) {
                    !in listOfFollowers -> {
                        followFlag = FOLLOW
                        binding.profileFollowText.text = "Follow"
                    }
                    in listOfFollowers -> {
                        followFlag = I_AM_FOLLOWING
                        binding.profileFollowText.text = "You are following"
                    }
                }
                following.get().addOnSuccessListener { followingShot ->
                    doneLoadingFollow()
                    if (followingShot.exists()) {
                        listOfFollowing.clear()
                        for (i in followingShot.children) listOfFollowing.add(i.value.toString())
                        if (onClicked) followLogic()
                    } else if (onClicked) followLogic()
                }.addOnFailureListener { doneLoadingFollow() }
            }.addOnFailureListener { doneLoadingFollow() }
        }
    }

    private fun followLogic() = try {
        val alertDialog = AlertDialog.Builder(this)
        alertDialog.setTitle("Follow ${if (receiverProfile.name != "") receiverProfile.name else "this user."}")
        when (followFlag) {
            FOLLOW -> {
                alertDialog.setMessage("Follow this user?")
                alertDialog.setPositiveButton("Ok") { dialog, _ ->
                    val listOfFollowingTemp: ArrayList<String> = arrayListOf()
                    val myFollowing = FirebaseDatabase.getInstance().reference.child(FOLLOWING).child(user!!.uid)
                    val followersOfOther = FirebaseDatabase.getInstance().reference.child(FOLLOWERS).child(userUid)

                    followersOfOther.get().addOnSuccessListener { followersOfOtherSnap ->
                        if (followersOfOtherSnap.exists()) {
                            val lst: ArrayList<String> = arrayListOf()
                            for (i in followersOfOtherSnap.children) lst.add(i.value.toString())
                            lst.add(user.uid)
                            followersOfOther.setValue(lst).addOnSuccessListener{
                                Util.sendNotification(message = "New Follower", body = "You have a new follower", receiverUid = userUid,  view = "New Follower")
                            }
                        } else followersOfOther.setValue(arrayListOf(user.uid)).addOnSuccessListener{
                            Util.sendNotification(message = "New Follower", body = "You have a new follower", receiverUid = userUid,  view = "New Follower")
                        }
                    }
                    myFollowing.get().addOnSuccessListener { followingSnap ->
                        doneLoadingFollow()
                        if (followingSnap.exists()) {
                            for (i in followingSnap.children) listOfFollowingTemp.add(i.value.toString())
                            listOfFollowingTemp.add(userUid)
                            listOfFollowing.add(userUid)
                            myFollowing.setValue(listOfFollowingTemp).addOnSuccessListener {
                                followFlag = I_AM_FOLLOWING
                                binding.profileFollowText.text = "You are following"
                                dialog.dismiss()
                            }.addOnFailureListener {
                                dialog.dismiss()
                                showSnackBar(binding.root, "Unable to follow. Try again.")
                            }
                        } else myFollowing.setValue(arrayListOf(userUid)).addOnSuccessListener {
                            followFlag = I_AM_FOLLOWING
                            binding.profileFollowText.text = "You are following"
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
                alertDialog.setMessage("${if (receiverProfile.name != "") receiverProfile.name else "This user"} is following you")
                alertDialog.setPositiveButton("Ok") { dialog, _ ->
                    doneLoadingFollow()
                    dialog.dismiss()
                }
            }
            I_AM_FOLLOWING -> {
                alertDialog.setMessage("Unfollow  ${if (receiverProfile.name != "") receiverProfile.name else "this user"}")
                alertDialog.setPositiveButton("Ok") { dialog, _ ->
                    val listOfFollowingTemp: ArrayList<String> = arrayListOf()
                    val myFollowing = FirebaseDatabase.getInstance().reference.child(FOLLOWING).child(user!!.uid)
                    val followersOfOther = FirebaseDatabase.getInstance().reference.child(FOLLOWERS).child(userUid)

                    followersOfOther.get().addOnSuccessListener { followersOfOtherSnap ->
                        if (followersOfOtherSnap.exists()) {
                            val lst: ArrayList<String> = arrayListOf()
                            for (i in followersOfOtherSnap.children) lst.add(i.value.toString())
                            lst.remove(user.uid)
                            followersOfOther.setValue(lst)
                        }
                    }
                    myFollowing.get().addOnSuccessListener { followingSnap ->
                        doneLoadingFollow()
                        if (followingSnap.exists()) {
                            for (i in followingSnap.children) listOfFollowingTemp.add(i.value.toString())
                            listOfFollowingTemp.remove(userUid)
                            listOfFollowing.remove(userUid)
                            myFollowing.setValue(listOfFollowingTemp).addOnSuccessListener {
                                followFlag = FOLLOW
                                binding.profileFollowText.text = "Follow"
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

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.profile_follow_button -> initFollow(true)
            R.id.profile_image -> {
                val fragment = FragmentDisplayImage()
                val bundle = Bundle()
                bundle.putString("image", receiverProfile.image)
                fragment.arguments = bundle
                supportFragmentManager.beginTransaction().addToBackStack("image").replace(R.id.user_profile_root, fragment).commit()
            }
            R.id.profile_call -> {
                val callData = CallData(channelName = myAuth, callerUid = myAuth, callType = CallType.VOICE, uids = arrayListOf(myAuth, receiverProfile.uid))
                val intent = Intent(this, IncomingCallDialog::class.java)
                intent.putExtra("callData", Gson().toJson(callData))
                startActivity(intent)
            }
            R.id.profile_video -> {
                val callData = CallData(channelName = myAuth, callerUid = myAuth, callType = CallType.VIDEO, uids = arrayListOf(myAuth, receiverProfile.uid))
                val intent = Intent(this, IncomingCallDialog::class.java)
                intent.putExtra("callData", Gson().toJson(callData))
                startActivity(intent)
            }
            R.id.profile_chat -> onBackPressed()
            R.id.profile_back -> onBackPressed()
            R.id.profile_block_user -> try {
                val alertBuilder = AlertDialog.Builder(this)
                val blockedRef = FirebaseDatabase.getInstance().reference.child(MY_BLOCKED_CONTACTS).child(myAuth)
                blockedRef.keepSynced(false)
                val progressFragment = ProgressFragment()
                supportFragmentManager.beginTransaction().replace(R.id.user_profile_root, progressFragment).commit()
                runBlocking {
                    scope.launch {
                        delay(10_000)
                        runOnUiThread {
                            try {
                                supportFragmentManager.beginTransaction().remove(progressFragment).commit()
                            } catch (e: Exception) {
                            }
                        }
                    }
                }
                blockedRef.get().addOnSuccessListener { blocked ->
                    supportFragmentManager.beginTransaction().remove(progressFragment).commit()
                    val blockedList: ArrayList<String> = arrayListOf()
                    if (blocked.exists()) {
                        for (i in blocked.children) blockedList.add(i.value.toString())
                        if (receiverProfile.uid in blockedList) alertBuilder.setMessage("Unblock user")
                        else alertBuilder.setMessage("Block user")
                    } else alertBuilder.setMessage("Block user")

                    alertBuilder.setPositiveButton("Ok") { dialog, _ ->
                        supportFragmentManager.beginTransaction().remove(progressFragment).commit()
                        blockedRef.get().addOnSuccessListener { blocked ->
                            val blockedList: ArrayList<String> = arrayListOf()
                            if (blocked.exists()) {
                                for (i in blocked.children) blockedList.add(i.value.toString())
                                if (receiverProfile.uid !in blockedList) {
                                    blockedList.add(receiverProfile.uid)
                                    blockedRef.setValue(blockedList).addOnSuccessListener {
                                        Snackbar.make(binding.root, "This contact can no longer message or call you.", Snackbar.LENGTH_LONG).show()
                                        dialog.cancel()
                                    }.addOnFailureListener {
                                        Snackbar.make(binding.root, "Failed!! Retry.", Snackbar.LENGTH_LONG).show()
                                        dialog.cancel()
                                    }
                                } else {
                                    blockedList.remove(receiverProfile.uid)
                                    blockedRef.setValue(blockedList).addOnSuccessListener {
                                        Snackbar.make(binding.root, "This contact has been unblocked.", Snackbar.LENGTH_LONG).show()
                                        dialog.cancel()
                                    }.addOnFailureListener {
                                        Snackbar.make(binding.root, "Failed!! Retry.", Snackbar.LENGTH_LONG).show()
                                        dialog.cancel()
                                    }
                                }

                            } else blockedRef.setValue(arrayListOf(receiverProfile.uid)).addOnSuccessListener {
                                Snackbar.make(binding.root, "This contact can no longer message or call you.", Snackbar.LENGTH_LONG).show()
                                dialog.cancel()
                            }.addOnFailureListener {
                                Snackbar.make(binding.root, "Failed!! Retry", Snackbar.LENGTH_LONG).show()
                                dialog.cancel()
                            }
                        }
                    }
                    alertBuilder.setNegativeButton("Cancel") { dialog, _ ->
                        supportFragmentManager.beginTransaction().remove(progressFragment).commit()
                        dialog.cancel()
                    }
                    val dialog = alertBuilder.create()
                    if (!dialog.isShowing) dialog.show()
                }
            } catch (e: Exception){}
        }
    }

    companion object {
        const val CALL_REFERENCE = "call_reference"
        const val USER_DETAILS = "user_details"
        const val TRANSFER_HISTORY = "user_coins_transfer"
        const val CHAT_REFERENCE = "chat_reference"
        const val MY_BLOCKED_CONTACTS = "blocked_contacts"
        const val PERMISSION_CODE = 22
        const val FOLLOWERS = "followers"
        const val FOLLOWING = "following"
        const val PERMISSION_CAMERA = 11
        val REQUESTED_PERMISSIONS = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
        )

        const val FOLLOW = 0
        const val IS_FOLLOWING = 1
        const val I_AM_FOLLOWING = 2
    }
}