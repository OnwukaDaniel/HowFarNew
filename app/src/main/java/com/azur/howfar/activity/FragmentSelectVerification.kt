package com.azur.howfar.activity

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.azur.howfar.R
import com.azur.howfar.databinding.FragmentSelectVerificationBinding

class FragmentSelectVerification : Fragment() {
    private lateinit var binding: FragmentSelectVerificationBinding
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentSelectVerificationBinding.inflate(inflater, container, false)
        return binding.root
    }
}