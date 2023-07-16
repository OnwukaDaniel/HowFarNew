package com.azur.howfar.user

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.azur.howfar.R
import com.azur.howfar.activity.SearchUsersActivity
import com.azur.howfar.activity.SettingActivity
import com.azur.howfar.databinding.FragmentProfileBinding
import com.azur.howfar.livedata.ValueEventLiveData
import com.azur.howfar.models.EventListenerType.onDataChange
import com.azur.howfar.models.UserProfile
import com.azur.howfar.retrofit.Const
import com.azur.howfar.user.freecoins.FreeHFCentsActivity
import com.azur.howfar.user.guestuser.GuestActivity
import com.azur.howfar.user.vip.VipPlanActivity
import com.azur.howfar.user.wallet.MyWalletActivity
import com.azur.howfar.viewmodel.MomentViewModel
import com.azur.howfar.viewmodel.UserProfileViewmodel
import com.azur.howfar.viewmodel.VideoPostsViewModel
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class ProfileFragment : Fragment(), View.OnClickListener {
    private lateinit var binding: FragmentProfileBinding
    private var myAuth = FirebaseAuth.getInstance().currentUser
    private val userProfileViewModel by activityViewModels<UserProfileViewmodel>()
    private val momentViewModel by activityViewModels<MomentViewModel>()
    private val videoPostViewModel by activityViewModels<VideoPostsViewModel>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentProfileBinding.inflate(inflater, container, false)
        initListener()
        getViewModelData()
        Glide.with(requireActivity()).load(R.drawable.ic_avatar).circleCrop().into(binding.imgUser)
        userProfileViewModel.userProfile.observe(viewLifecycleOwner) { data ->
            Glide.with(requireActivity()).load(data!!.image).circleCrop().into(binding.imgUser)
            val phone = "Phone: ${data.phone}"
            binding.tvUserName.text = data.name
            binding.tvAge.text = data.age
            binding.tvUserId.text = phone
            if (data.gender.equals("male", ignoreCase = true)) {
                binding.imgGender.setImageDrawable(ContextCompat.getDrawable(requireActivity(), R.drawable.male))
            } else {
                binding.imgGender.setImageDrawable(ContextCompat.getDrawable(requireActivity(), R.drawable.female))
            }
        }

        val listOfFollowing: ArrayList<String> = arrayListOf()
        val following = FirebaseDatabase.getInstance().reference.child(GuestActivity.FOLLOWERS).child(myAuth!!.uid)
        ValueEventLiveData(following).observe(requireActivity()) { followersSnap ->
            when (followersSnap.second) {
                onDataChange -> {
                    for (i in followersSnap.first.children) if (i.value.toString() !in listOfFollowing) listOfFollowing.add(i.value.toString())
                    binding.tvFollowrs.text = listOfFollowing.size.toString()
                }
            }
        }
        return binding.root
    }

    private fun getViewModelData() {
        videoPostViewModel.videoPostList.observe(viewLifecycleOwner) { binding.tvVideos.text = it.size.toString() }
        momentViewModel.momentList.observe(viewLifecycleOwner) { binding.tvPosts.text = it.size.toString() }
    }

    override fun onResume() {
        requireActivity().window.statusBarColor = resources.getColor(R.color.black_back)
        super.onResume()
        binding.imgUser.setOnClickListener {
        }
    }

    private fun initListener() {
        binding.btnSetting.setOnClickListener(this)
        binding.lytMyPost.setOnClickListener(this)
        binding.lytMyVideos2.setOnClickListener(this)
        binding.lytMyPost2.setOnClickListener(this)
        binding.lytFollowrs.setOnClickListener(this)
        binding.btnEditProfile.setOnClickListener(this)
        binding.tvLevel.setOnClickListener(this)
        binding.lytVIP.setOnClickListener(this)
        binding.lytWallet.setOnClickListener(this)
        binding.lytFreeDimonds.setOnClickListener(this)
        binding.lytMyVideos.setOnClickListener(this)
        binding.btnSearch.setOnClickListener(this)
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.btnSetting -> startActivity(Intent(activity, SettingActivity::class.java))
            R.id.btnSearch -> startActivity(Intent(activity, SearchUsersActivity::class.java))
            R.id.lytMyPost -> startActivity(Intent(activity, GuestActivity::class.java).putExtra(Const.USER_STR, myAuth!!.uid))
            R.id.lytMyVideos2 -> startActivity(Intent(activity, GuestActivity::class.java).putExtra(Const.USER_STR, myAuth!!.uid).putExtra("video", true))
            R.id.lytMyPost2 -> startActivity(Intent(activity, GuestActivity::class.java).putExtra(Const.USER_STR, myAuth!!.uid))
            R.id.lytFollowrs -> startActivity(Intent(activity, FollowrsListActivity::class.java).putExtra(Const.USER_STR, myAuth!!.uid))
            R.id.btnEditProfile -> startActivity(Intent(activity, EditProfileActivity::class.java))
            //R.id.tvLevel -> startActivity(Intent(activity, MyLevelListActivity::class.java))
            R.id.lytVIP -> startActivity(Intent(activity, VipPlanActivity::class.java))
            R.id.lytWallet -> startActivity(Intent(activity, MyWalletActivity::class.java))
            R.id.lytFreeDimonds -> startActivity(Intent(activity, FreeHFCentsActivity::class.java))
            R.id.lytMyVideos -> startActivity(Intent(activity, GuestActivity::class.java).putExtra(Const.USER_STR, myAuth!!.uid).putExtra("video", true))
        }
    }
}