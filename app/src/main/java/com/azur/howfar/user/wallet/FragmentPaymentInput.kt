package com.azur.howfar.user.wallet

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.gson.Gson
import com.azur.howfar.R
import com.azur.howfar.databinding.FragmentPaymentInputBinding
import com.azur.howfar.dilog.ProgressFragment
import com.azur.howfar.models.*
import com.azur.howfar.models.Currency
import com.azur.howfar.posts.FeedAdapter
import com.azur.howfar.utils.HFCoinUtils
import com.azur.howfar.utils.Keyboard.hideKeyboard
import com.azur.howfar.viewmodel.DialogViewModel

class FragmentPaymentInput : Fragment() {
    private lateinit var binding: FragmentPaymentInputBinding
    private var userProfile = UserProfile()
    private val auth = FirebaseAuth.getInstance().currentUser
    private val progressFragment = ProgressFragment()
    private var timeRef = FirebaseDatabase.getInstance().reference
    private val dialogViewModel: DialogViewModel by activityViewModels()
    private lateinit var alertDialog: AlertDialog.Builder

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentPaymentInputBinding.inflate(inflater, container, false)
        val json = requireArguments().getString("data")
        userProfile = Gson().fromJson(json, UserProfile::class.java)!!
        timeRef = FirebaseDatabase.getInstance().reference.child("time").child(auth!!.uid)
        binding.displayUser.text = userProfile.name
        alertDialog = AlertDialog.Builder(requireContext())
        binding.sendHf.setOnClickListener {
            hideKeyboard()
            send(userProfile)
        }
        return binding.root
    }

    private fun send(receiver: UserProfile) {
        if (binding.tvAmount.text.trim().toString() == "") return
        loading()
        val input = binding.tvAmount.text.trim().toString().toFloat()

        var currency = Currency(hfcoin = input, senderUid = auth!!.uid, receiverUid = receiver.uid, transactionType = TransactionType.SENT)
        val historyRef = FirebaseDatabase.getInstance().reference.child(FeedAdapter.TRANSFER_HISTORY).child(auth.uid)
        historyRef.get().addOnSuccessListener {
            if (it.exists()) {
                var available = HFCoinUtils.checkBalance(it)
                when {
                    available < currency.hfcoin -> showDialog("Insufficient HFCoin.")
                    else -> sendHFCoin(currency)
                }
            }
        }
    }

    private fun sendHFCoin(currency: Currency) {
        var curr = currency
        timeRef = FirebaseDatabase.getInstance().reference.child("time").child(auth!!.uid)
        timeRef.setValue(ServerValue.TIMESTAMP).addOnSuccessListener {
            timeRef.get().addOnSuccessListener { timeSnapshot ->
                if (timeSnapshot.exists()) {
                    val timeSent = timeSnapshot.value.toString()
                    curr.timeOfTransaction = timeSent
                    val senderRef = FirebaseDatabase.getInstance().reference.child(FeedAdapter.TRANSFER_HISTORY).child(curr.senderUid).child(timeSent)
                    val receiverRef = FirebaseDatabase.getInstance().reference.child(FeedAdapter.TRANSFER_HISTORY).child(curr.receiverUid).child(timeSent)
                    senderRef.setValue(curr).addOnSuccessListener {
                        curr.transactionType = TransactionType.RECEIVED
                        receiverRef.setValue(curr)
                        showDialog("Transaction Successful")
                    }.addOnFailureListener { showDialog("Please retry.") }
                }
            }
        }
    }

    private fun showDialog(msg: String) {
        hideKeyboard()
        dialogViewModel.setDialogMessage(msg)
        if (requireActivity().supportFragmentManager.backStackEntryCount > 1) requireActivity().supportFragmentManager.popBackStack()
        try {
            alertDialog = AlertDialog.Builder(requireContext())
            alertDialog.setTitle("Transaction")
            alertDialog.setMessage(msg)
            alertDialog.setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            alertDialog.setPositiveButton("Ok") { dialog, _ ->
                dialog.dismiss()
            }
            alertDialog.create().show()
        } catch (e: Exception) {
        }
    }

    private fun loading(msg: String = "Please wait") {
        hideKeyboard()
        dialogViewModel.setDialogMessage(msg)
        requireActivity().supportFragmentManager.beginTransaction().addToBackStack("progress").replace(R.id.payment_root, progressFragment).commit()
    }

    companion object {
        val TRANSFER_HISTORY = "user_coins_transfer"
        val USER_DETAILS = "user_details"
    }
}