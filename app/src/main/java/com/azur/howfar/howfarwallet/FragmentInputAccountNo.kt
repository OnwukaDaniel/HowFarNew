package com.azur.howfar.howfarwallet

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.azur.howfar.databinding.FragmentInputAccountNoBinding
import com.azur.howfar.viewmodel.ContactViewModel

class FragmentInputAccountNo : Fragment() {
    private lateinit var binding: FragmentInputAccountNoBinding
    private val contactViewModel: ContactViewModel by activityViewModels()
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentInputAccountNoBinding.inflate(inflater, container, false)
        contactViewModel.userProfile.observe(viewLifecycleOwner) {
            //binding.inputUsername.text = it.name
        }
        return binding.root
    }
}