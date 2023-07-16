package com.azur.howfar.user.wallet

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.azur.howfar.R
import com.azur.howfar.databinding.FragmentMyRecordBinding
import com.azur.howfar.models.TransactionDisplayData
import com.azur.howfar.models.TransactionType
import com.azur.howfar.viewmodel.TransactionHistoryViewModel
import java.util.*

class RecordFragment : Fragment() {
    private lateinit var binding: FragmentMyRecordBinding
    private var bottomSheetDialog: BottomSheetDialog? = null
    private val transactionHistoryViewModel: TransactionHistoryViewModel by activityViewModels()
    private var transactionData: ArrayList<TransactionDisplayData> = arrayListOf()
    private var selectedType = 1
    private val selectedDate = ""
    private var income = 0
    private var outcome = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        // Inflate the layout for this fragment
        binding = FragmentMyRecordBinding.inflate(inflater, container, false)
        initDatePiker()
        initListner()
        binding.tvDateRcoin.text = selectedDate
        transactionHistoryViewModel.transactionDisplayData.observe(viewLifecycleOwner) {
            for (i in it) if (i !in transactionData) {
                transactionData.add(i)
                if (i.transactionType == TransactionType.SENT) outcome += 1
                if (i.transactionType == TransactionType.RECEIVED ||
                    i.transactionType == TransactionType.BOUGHT ||
                    i.transactionType == TransactionType.APP_GIFT ||
                    i.transactionType == TransactionType.EARNED
                ) income += 1
                binding.tvRcoinIncome.text = income.toString()
                binding.tvRcoinOutcome.text = outcome.toString()
            }
        }
        binding.lytRcoins.setOnClickListener {
            requireActivity().supportFragmentManager.beginTransaction()
                .addToBackStack("records")
                .replace(R.id.record_root, HistoryFragment())
                .commit()
        }
        return binding.root
    }

    private fun initDatePiker() {
        binding.lytDatePicker.lytDatePicker.visibility = View.GONE
        binding.lytDatePicker.tvCancel.setOnClickListener { v: View? ->
            binding.lytDatePicker.lytDatePicker.visibility = View.GONE
        }
        binding.lytDatePicker.tvConfirm.setOnClickListener { v: View? ->
            binding.lytDatePicker.lytDatePicker.visibility = View.GONE
            if (selectedType == 1) {
            } else {
                binding.tvDateRcoin.text = selectedDate
            }
        }
    }

    private fun initListner() {
        binding.lytDateRcoins.setOnClickListener { v: View? ->
            selectedType = 2
            binding.lytDatePicker.lytDatePicker.visibility = View.VISIBLE
        }
    }
}