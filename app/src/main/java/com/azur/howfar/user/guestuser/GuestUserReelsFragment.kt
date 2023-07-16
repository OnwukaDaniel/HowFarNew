package com.azur.howfar.user.guestuser

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.GridLayoutManager
import com.azur.howfar.databinding.FragmentGuestUserReelsBinding
import com.azur.howfar.models.VideoPost
import com.azur.howfar.reels.ProfileVideoGridAdapter
import com.azur.howfar.viewmodel.VideoPostsViewModel

class GuestUserReelsFragment : Fragment() {
    private lateinit var binding: FragmentGuestUserReelsBinding
    private var profileVideoGridAdapter = ProfileVideoGridAdapter()
    private var videoPostList: ArrayList<VideoPost> = arrayListOf()
    private val videoPostViewModel by activityViewModels<VideoPostsViewModel>()

    @SuppressLint("NotifyDataSetChanged")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentGuestUserReelsBinding.inflate(inflater, container, false)
        binding.emptyPlaceHolder.visibility = View.GONE
        videoPostViewModel.videoPostList.observe(viewLifecycleOwner) {
            videoPostList = it
            profileVideoGridAdapter.activity = requireActivity()
            profileVideoGridAdapter.reels = videoPostList
            binding.rvFeed.adapter = profileVideoGridAdapter
            binding.rvFeed.layoutManager = GridLayoutManager(requireContext(), 2, GridLayoutManager.VERTICAL, false)
            profileVideoGridAdapter.notifyDataSetChanged()
        }
        return binding.root
    }
}