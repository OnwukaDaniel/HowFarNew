package com.azur.howfar.livestreamming

import android.Manifest
import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.azur.howfar.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.azur.howfar.databinding.FragmentLiveListBinding
import com.azur.howfar.livedata.ChildEventLiveData
import com.azur.howfar.models.BroadcastCallData
import com.azur.howfar.models.CallAnswerType
import com.azur.howfar.models.EventListenerType.onChildAdded
import com.azur.howfar.models.EventListenerType.onChildChanged
import com.azur.howfar.models.EventListenerType.onChildRemoved

class LiveListFollowingFragment : Fragment() {
    private lateinit var binding: FragmentLiveListBinding
    private val myAuth = FirebaseAuth.getInstance().currentUser!!.uid
    private var videoListAdapter = VideoListAdapter()
    private var liveUsers: ArrayList<String> = arrayListOf()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentLiveListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        videoListAdapter.users = liveUsers
        binding.rvVideos.adapter = videoListAdapter
        binding.rvVideos.layoutManager = GridLayoutManager(requireContext(), 2, GridLayoutManager.VERTICAL, false)
        initView()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun initView() {
        val ref = FirebaseDatabase.getInstance().reference.child(LIVE_BROADCAST).child(myAuth)
        ref.keepSynced(false)
        ChildEventLiveData(ref).observe(viewLifecycleOwner) {
            when (it.second) {
                onChildAdded, onChildChanged -> {
                    val bd = it.first.getValue(BroadcastCallData::class.java)!!
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
                    if (broadcastData.callerUid == myAuth) return@observe
                    liveUsers.remove(broadcastData.callerUid)
                    videoListAdapter.notifyDataSetChanged()
                    binding.broadcastDummyImage.visibility = if (liveUsers.isEmpty()) View.VISIBLE else View.GONE
                }
            }
            binding.broadcastDummyImage.visibility = if (liveUsers.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    companion object {
        const val LIVE_BROADCAST = "live_broadcast"
    }
}