package com.azur.howfar.howfarchat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.firebase.database.FirebaseDatabase
import com.azur.howfar.databinding.FragmentTvBinding
import com.azur.howfar.livedata.ValueEventLiveData
import com.azur.howfar.models.EventListenerType

class FragmentTv : Fragment() {
    private lateinit var binding: FragmentTvBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentTvBinding.inflate(inflater, container, false)
        initChat()
        return binding.root
    }

    private fun initChat() {
        val displayChatRef = FirebaseDatabase.getInstance().reference.child(FragmentChats.CHAT).child("c")
        val liveData = ValueEventLiveData(displayChatRef)
        liveData.observe(viewLifecycleOwner) {
            when (it.second) {
                EventListenerType.onDataChange -> {

                }
            }
        }
    }

    companion object {
        val CHAT = "chat"
    }
}