package com.azur.howfar.howfarchat.chat

import android.annotation.SuppressLint
import android.os.Bundle
import android.transition.TransitionInflater
import android.view.*
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.azur.howfar.R
import com.azur.howfar.databinding.FragmentDisplayImageBinding

class FragmentDisplayImage : Fragment() {
    private lateinit var binding: FragmentDisplayImageBinding
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentDisplayImageBinding.inflate(inflater, container, false)
        sharedElementEnterTransition = TransitionInflater.from(requireContext()).inflateTransition(R.transition.shared_image)
        return binding.root
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onResume() {
        super.onResume()
        val image = requireArguments().getString("image")
        Glide.with(requireContext()).load(image).error(R.drawable.ic_avatar).into(binding.displayImage)
        requireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)

        binding.root.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                }
                else -> {}
            }
            return@setOnTouchListener false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        requireActivity().window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
    }
}