package com.azur.howfar.utils.progressbar

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.azur.howfar.databinding.FragmentSpinnerBinding

class Spinner : Fragment() {
    private lateinit var binding: FragmentSpinnerBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentSpinnerBinding.inflate(inflater, container, false)
        return binding.root
    }
}