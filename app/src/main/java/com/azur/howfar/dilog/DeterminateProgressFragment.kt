package com.azur.howfar.dilog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.azur.howfar.databinding.FragmentDeterminateDialogProgressBinding
import com.azur.howfar.viewmodel.DialogViewModel
import com.azur.howfar.viewmodel.VideoDialogViewModel

class DeterminateProgressFragment : Fragment() {
    private lateinit var binding: FragmentDeterminateDialogProgressBinding
    private val dialogViewModel: DialogViewModel by activityViewModels()
    private val videoDialogViewModel: VideoDialogViewModel by activityViewModels()
    private var callback: OnBackPressedCallback? = null
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentDeterminateDialogProgressBinding.inflate(inflater, container, false)
        dialogViewModel.dialogMessage.observe(viewLifecycleOwner){
            binding.dialogMsg.text = if (it == "") "Please wait" else it
        }
        videoDialogViewModel.videoUploadProgress.observe(viewLifecycleOwner){
            binding.determinateProgressBarProgress.progress = it
        }
        callback = requireActivity().onBackPressedDispatcher.addCallback(requireActivity()) {

        }

        callback!!.isEnabled = true
        return binding.root
    }

    override fun onDetach() {
        super.onDetach()
        callback!!.isEnabled = false
    }
}