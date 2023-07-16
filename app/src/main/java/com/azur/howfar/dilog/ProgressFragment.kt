package com.azur.howfar.dilog

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.azur.howfar.databinding.FragmentDialogProgressBinding
import com.azur.howfar.viewmodel.DialogMode
import com.azur.howfar.viewmodel.DialogViewModel

class ProgressFragment : Fragment() {
    private lateinit var binding: FragmentDialogProgressBinding
    private val dialogViewModel: DialogViewModel by activityViewModels()
    private var callback: OnBackPressedCallback? = null
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentDialogProgressBinding.inflate(inflater, container, false)
        observeModes()
        observeMessage()
        observeCloseCall()
        observeCardColor()
        observeDisableBackPress()
        observeProgress()
        observeHideProgress()
        return binding.root
    }

    private fun observeHideProgress() {
        dialogViewModel.hideProgress.observe(viewLifecycleOwner) { hide ->
            binding.progressBar.visibility = if (hide == true) View.GONE else View.VISIBLE
        }
    }

    private fun observeProgress() {
        dialogViewModel.progress.observe(viewLifecycleOwner) { progress ->
            binding.progressBar.progress = progress
        }
    }

    private fun observeCardColor() {
        dialogViewModel.cardColor.observe(viewLifecycleOwner) { color ->
            binding.dialogCard.setCardBackgroundColor(Color.parseColor(color))
        }
    }

    private fun observeCloseCall() {
        dialogViewModel.closeDialog.observe(viewLifecycleOwner) { close ->
            if (close == true) requireActivity().onBackPressed()
        }
    }

    private fun observeDisableBackPress() {
        dialogViewModel.disableBackPress.observe(viewLifecycleOwner) { disabled ->
            if (!disabled) {
                callback = requireActivity().onBackPressedDispatcher.addCallback(requireActivity()) {

                }
                callback!!.isEnabled = !disabled != true
            } else {
                binding.progressDialogRoot.setOnClickListener { requireActivity().onBackPressed() }
            }
        }
    }

    private fun observeMessage() {
        dialogViewModel.dialogMessage.observe(viewLifecycleOwner) {
            binding.dialogMsg.text = if (it == "") "Please wait" else it
        }
    }

    private fun observeModes() {
        dialogViewModel.dialogMode.observe(viewLifecycleOwner) {
            when (it) {
                DialogMode.NORMAL_PROGRESS -> {
                    binding.dialogControlsRoot.visibility = View.GONE
                }
                DialogMode.CHAT_FETCH_PROGRESS -> {
                    binding.dialogControlsRoot.visibility = View.VISIBLE
                    binding.dialogCancel.setOnClickListener {
                        requireActivity().onBackPressed()
                    }
                }
            }
        }
    }
}