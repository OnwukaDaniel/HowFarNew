package com.azur.howfar.livestreamming

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.azur.howfar.R
import com.azur.howfar.databinding.FragmentLiveListBinding
import com.azur.howfar.databinding.ItemVideoGridBinding
import com.azur.howfar.livedata.ChildEventLiveData
import com.azur.howfar.models.BroadcastCallData
import com.azur.howfar.models.CallAnswerType
import com.azur.howfar.models.EventListenerType.onChildAdded
import com.azur.howfar.models.EventListenerType.onChildChanged
import com.azur.howfar.models.EventListenerType.onChildRemoved
import com.azur.howfar.models.UserProfile
import com.azur.howfar.retrofit.Const
import com.azur.howfar.viewmodel.TimeStringViewModel
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class LiveListFragment : Fragment() {
    private lateinit var binding: FragmentLiveListBinding
    private val myAuth = FirebaseAuth.getInstance().currentUser!!.uid
    private var videoListAdapter = VideoListAdapter()
    private var baseTimeOnline = ""
    private var liveUsers: ArrayList<String> = arrayListOf()
    private val timeStringViewModel by activityViewModels<TimeStringViewModel>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentLiveListBinding.inflate(inflater, container, false)
        timeStringViewModel.time.observe(viewLifecycleOwner) { baseTimeOnline = it }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initView()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun initView() {
        videoListAdapter.users = liveUsers
        binding.rvVideos.adapter = videoListAdapter
        binding.rvVideos.layoutManager = GridLayoutManager(requireContext(), 2, GridLayoutManager.VERTICAL, false)

        val ref = FirebaseDatabase.getInstance().reference.child(LIVE_BROADCAST_CREATOR)
        ref.keepSynced(false)
        ChildEventLiveData(ref).observe(viewLifecycleOwner) {
            when (it.second) {
                onChildAdded, onChildChanged -> {
                    val bd = it.first.getValue(BroadcastCallData::class.java)!!
                    if (bd.isPrivate) return@observe
                    if (bd.callerUid == myAuth) return@observe
                    if (bd.answerType == CallAnswerType.ENDED) {
                        liveUsers.remove(bd.callerUid)
                        videoListAdapter.notifyDataSetChanged()
                    }

                    if (bd.answerType != CallAnswerType.ENDED && bd.callerUid !in liveUsers) {
                        liveUsers.add(bd.callerUid)
                        videoListAdapter.notifyDataSetChanged()
                    }
                    binding.broadcastDummyImage.visibility = if (liveUsers.isEmpty()) View.VISIBLE else View.GONE
                }
                onChildRemoved -> {
                    val broadcastData = it.first.getValue(BroadcastCallData::class.java)!!
                    if (broadcastData.isPrivate) return@observe
                    if (broadcastData.callerUid == myAuth) return@observe
                    liveUsers.remove(broadcastData.callerUid)
                    videoListAdapter.notifyDataSetChanged()
                    binding.broadcastDummyImage.visibility = if (liveUsers.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    companion object {
        const val TAG = "hostliveactivity"
        const val CALL_REFERENCE = "call_reference"
        const val USER_DETAILS = "user_details"
        const val TRANSFER_HISTORY = "user_coins_transfer"
        const val LIVE_BROADCAST_CREATOR = "live_broadcast_creator"
        const val LIVE_PRESENCE = "LIVE_PRESENCE"
    }
}

class VideoListAdapter : RecyclerView.Adapter<VideoListAdapter.VideoListViewHolder>() {
    private var context: Context? = null
    var users: ArrayList<String> = arrayListOf()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoListViewHolder {
        context = parent.context
        return VideoListViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_video_grid, parent, false))
    }

    override fun onBindViewHolder(holder: VideoListViewHolder, position: Int) {
        holder.setData(position)
    }

    override fun getItemCount() = users.size

    inner class VideoListViewHolder(itemView: View?) : RecyclerView.ViewHolder(itemView!!) {
        var binding = ItemVideoGridBinding.bind(itemView!!)
        fun setData(position: Int) {
            val user = users.toList()[position]
            FirebaseDatabase.getInstance().reference.child(USER_DETAILS).child(user).get().addOnSuccessListener {
                if (it.exists()) {
                    val userProfile = it.getValue(UserProfile::class.java)!!
                    binding.tvName.text = userProfile.name
                    Glide.with(context!!).load(userProfile.image).error(R.drawable.ic_avatar).centerCrop().into(binding.image)
                }
            }
            binding.root.setOnClickListener { v: View? ->
                val intent = Intent(context, WatchLiveActivity::class.java)
                intent.putExtra(Const.USER_STR, user)
                context!!.startActivity(intent)
            }
        }
    }

    companion object {
        const val TAG = "hostliveactivity"
        const val CALL_REFERENCE = "call_reference"
        const val USER_DETAILS = "user_details"
        const val TRANSFER_HISTORY = "user_coins_transfer"
    }
}