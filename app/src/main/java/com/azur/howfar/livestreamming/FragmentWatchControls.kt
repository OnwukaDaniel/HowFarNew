package com.azur.howfar.livestreamming

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.azur.howfar.databinding.FragmentWatchControlsBinding

class FragmentWatchControls : Fragment() {
    private lateinit var binding: FragmentWatchControlsBinding
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentWatchControlsBinding.inflate(inflater, container, false)
        return binding.root
    }
}