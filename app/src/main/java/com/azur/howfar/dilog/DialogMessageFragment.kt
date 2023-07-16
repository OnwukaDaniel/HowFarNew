package com.azur.howfar.dilog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.azur.howfar.databinding.FragmentDialogMessageBinding
import com.azur.howfar.viewmodel.DialogViewModel

class DialogMessageFragment : Fragment() {
    private lateinit var binding: FragmentDialogMessageBinding
    private val dialogViewModel: DialogViewModel by activityViewModels()
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentDialogMessageBinding.inflate(inflater, container, false)
        dialogViewModel.dialogMessage.observe(viewLifecycleOwner){
            binding.dialogMsg.text = it
        }
        binding.dialogCancel.setOnClickListener {
            requireActivity().onBackPressed()
        }
        binding.dialogOk.setOnClickListener {
            requireActivity().onBackPressed()
        }
        return binding.root
    }
}