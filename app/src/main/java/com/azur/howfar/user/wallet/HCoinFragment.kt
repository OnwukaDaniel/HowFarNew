package com.azur.howfar.user.wallet

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.azur.howfar.R
import com.azur.howfar.databinding.FragmentRcoinBinding
import com.azur.howfar.howfarwallet.ActivityFingerPrint
import com.azur.howfar.models.FingerprintRoute.CASH_OUT_HF_COIN
import com.azur.howfar.viewmodel.FloatViewModel

class HCoinFragment : Fragment(), View.OnClickListener {
    private lateinit var binding: FragmentRcoinBinding
    private val floatViewModel: FloatViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentRcoinBinding.inflate(inflater, container, false)
        initMain()
        floatViewModel.float.observe(viewLifecycleOwner) { binding.tvHFCoin.text = it.toString() }
        return binding.root
    }

    private fun initMain() {
        binding.btnTransfer.setOnClickListener(this)
        binding.btnCashout.setOnClickListener {
            startActivity(Intent(requireActivity(), ActivityFingerPrint::class.java).putExtra("data", CASH_OUT_HF_COIN))
            requireActivity().overridePendingTransition(R.anim.enter_right_to_left, R.anim.exit_right_to_left)
        }
    }

    override fun onClick(p0: View?) {
        when (p0?.id) {
            R.id.btnTransfer -> {
                startActivity(Intent(requireActivity(), ActivityHFCoinTransfer::class.java))
                requireActivity().overridePendingTransition(R.anim.enter_left_to_right, R.anim.exit_left_to_right)
            }
        }
    }
}