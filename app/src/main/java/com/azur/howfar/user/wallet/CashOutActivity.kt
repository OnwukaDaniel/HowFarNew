package com.azur.howfar.user.wallet

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.azur.howfar.R
import com.azur.howfar.activity.BaseActivity
import com.azur.howfar.activity.ContactUsActivity
import com.azur.howfar.databinding.ActivityCashOutBinding
import com.azur.howfar.howfarchat.ChatLanding
import com.azur.howfar.howfarwallet.ActivityFingerPrint
import com.azur.howfar.howfarwallet.VFDData
import com.azur.howfar.livedata.ValueEventLiveData
import com.azur.howfar.models.EmailPassword
import com.azur.howfar.models.EventListenerType.onDataChange
import com.azur.howfar.models.FingerprintRoute
import com.azur.howfar.models.FingerprintRoute.HOW_FAR_PAY
import com.azur.howfar.models.SignUpResponse
import com.azur.howfar.utils.HFCoinUtils
import com.azur.howfar.utils.Util
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.SocketTimeoutException

class CashOutActivity : BaseActivity(), View.OnClickListener {
    private val binding by lazy { ActivityCashOutBinding.inflate(layoutInflater) }
    private var myAuth = FirebaseAuth.getInstance().currentUser!!.uid
    private val redeemHistoryAdapter = RedeemHistoryAdapter()
    private var dataset = arrayListOf<WithDrawRequestData>()
    private val scope = CoroutineScope(Dispatchers.IO)
    private lateinit var progressDialog: AlertDialog
    private lateinit var alertDialog: AlertDialog.Builder

    @SuppressLint("NotifyDataSetChanged")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        showCoinRoot()
        binding.lytBank.setOnClickListener(this)
        binding.lytBitcoin.setOnClickListener(this)
        binding.submitBitcoinAddress.setOnClickListener(this)
        val alert = AlertDialog.Builder(this)
        alert.setTitle("Info")
        alert.setMessage("Two forms of withdrawal:\n1. Bank (VFD)\n2. Bitcoin wallet.\n" +
                "\nCash will be sent to your HowFar Pay Wallet.\nEnsure the following:\n\n1. You have a HowFar Wallet Account.\n2. You enter the " +
                "correct details:\n" +
                "   Account number\n" +
                "   Account name before you submit your cash-out request.")
        alert.setPositiveButton("Go to HowFar Pay Wallet.") { dialog, _ ->
            startActivity(Intent(this, ActivityFingerPrint::class.java).putExtra("data", HOW_FAR_PAY))
            overridePendingTransition(R.anim.enter_right_to_left, R.anim.exit_right_to_left)
            dialog.dismiss()
        }
        alert.setNegativeButton("I have HowFar Wallet.") { dialog, _ ->
            dialog.dismiss()
        }
        alert.create().show()
        alertDialog = AlertDialog.Builder(this)
        alertDialog.setCancelable(false)
        alertDialog.setView(R.layout.card_progress)
        progressDialog = alertDialog.create()
        binding.cashOutBack.setOnClickListener(this)
        binding.btncountinue.setOnClickListener(this)
        redeemHistoryAdapter.dataset = dataset
        binding.rvHistory.adapter = redeemHistoryAdapter
        binding.rvHistory.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        val reqRef = FirebaseDatabase.getInstance().reference.child(MY_WITHDRAW_REQUEST).child(myAuth)
        ValueEventLiveData(reqRef).observe(this) {
            when (it.second) {
                onDataChange -> {
                    dataset.clear()
                    for (i in it.first.children) {
                        val withDrawRequestData = i.getValue(WithDrawRequestData::class.java)!!
                        if (withDrawRequestData !in dataset) dataset.add(withDrawRequestData)
                        redeemHistoryAdapter.notifyDataSetChanged()
                    }
                }
            }
        }
    }

    private fun showCoinRoot(){
        binding.bankLayout.visibility = View.GONE
        binding.bitcoinLayout.visibility = View.VISIBLE
    }

    private fun showBank(){
        binding.bankLayout.visibility = View.VISIBLE
        binding.bitcoinLayout.visibility = View.GONE
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.cash_out_Back -> onBackPressed()
            R.id.btncountinue -> {
                val bank = "VFD MFB"
                val account = binding.etAccount.text.trim().toString()
                val amount = binding.etAmount.text.trim().toString()
                val accountName = binding.etAccountName.text.trim().toString()
                if (account == "" || amount == "" || accountName == "") return
                binding.etAccount.text.clear()
                binding.etAmount.text.clear()
                binding.etAccountName.text.clear()

                val alertDialog = AlertDialog.Builder(this)
                alertDialog.setTitle("Message")
                alertDialog.setMessage("You will be credited ₦ ${amount.toInt() * 0.9} in few minutes")
                alertDialog.setPositiveButton("OK") { dialog, _ ->
                    dialog.dismiss()
                    progressDialog.show()
                    val historyRef = FirebaseDatabase.getInstance().reference.child(ChatLanding.TRANSFER_HISTORY).child(myAuth)
                    historyRef.keepSynced(false)
                    historyRef.get().addOnSuccessListener {
                        if (it.exists()) {
                            val available = HFCoinUtils.checkBalance(it)
                            val balance = available.toString()
                            if (amount.toFloat() > balance.toFloat()) {
                                showToast("Insufficient fund")
                                progressDialog.dismiss()
                            } else {
                                val amountCredited = (amount.toInt() * 0.9).toString()
                                //val amountDebited = amount.toFloat() - amountCredited.toFloat()
                                val withDrawRequest = WithDrawRequestData(
                                    amount = amountCredited, accountName = accountName,
                                    bank = bank, account = account, uid = myAuth
                                )
                                sendFinally(withDrawRequest, amount.toFloat(), amount.toFloat())
                            }
                        }
                    }.addOnFailureListener {
                        progressDialog.dismiss()
                    }
                }
                alertDialog.create().show()
            }
            R.id.submit_bitcoin_address->{
                val address = binding.bitcoinAddress.text.trim().toString()
                val amount = binding.bitcoinAmount.text.trim().toString()
                if (address == "" || amount =="") return
                binding.bitcoinAddress.text.clear()
                binding.bitcoinAmount.text.clear()
                val amountCredited = (amount.toInt() * 0.9).toString()
                val alertDialog = AlertDialog.Builder(this)
                alertDialog.setTitle("Message")
                alertDialog.setMessage("You will be credited ₦ $amountCredited in few minutes")
                alertDialog.setPositiveButton("OK") { dialog, _ ->
                    dialog.dismiss()
                    progressDialog.show()
                    val historyRef = FirebaseDatabase.getInstance().reference.child(ChatLanding.TRANSFER_HISTORY).child(myAuth)
                    historyRef.keepSynced(false)
                    historyRef.get().addOnSuccessListener {
                        if (it.exists()) {
                            val available = HFCoinUtils.checkBalance(it)
                            val balance = available.toString()
                            if (amount.toFloat() > balance.toFloat()) {
                                showToast("Insufficient fund")
                                progressDialog.dismiss()
                            } else {
                                val withDrawRequest = WithDrawRequestData(amount = amountCredited, account = address, uid = myAuth, address = address)
                                sendFinally(withDrawRequest, amount.toFloat(), amount.toFloat())
                            }
                        }
                    }.addOnFailureListener {
                        progressDialog.dismiss()
                    }
                }
                alertDialog.create().show()

            }
            R.id.lytBitcoin-> showCoinRoot()
            R.id.lytBank-> showBank()
        }
    }

    private fun send(){

    }

    private fun sendFinally(withDrawRequest: WithDrawRequestData, amtCredit: Float, amtDebit: Float) {
        var requestData = withDrawRequest
        val timeRef = FirebaseDatabase.getInstance().reference.child("time").child(myAuth)
        timeRef.setValue(ServerValue.TIMESTAMP).addOnSuccessListener {
            timeRef.get().addOnSuccessListener { timeSnap ->
                val time = timeSnap.value.toString()
                requestData.time = time
                val creditChargesRef = FirebaseDatabase.getInstance(OVERTIME_CHANGE_URL).reference.child(CASH_OUT_ACCOUNT_UID)
                creditChargesRef.get().addOnSuccessListener { cashUidSnap ->
                    if (cashUidSnap.exists()) {
                        when (val uid = cashUidSnap.value.toString()) {
                            "" -> {
                                runOnUiThread {
                                    showToast("Unable to perform transaction now. Please retry\nError code $CASH_OUT_CODE_NO_UID")
                                    progressDialog.dismiss()
                                }
                            }
                            else -> {
                                HFCoinUtils.send(amountCredit = amtCredit, amountDebit = amtDebit, receiverUid = uid, senderUid = myAuth)
                                val reqRef = FirebaseDatabase.getInstance().reference.child(MY_WITHDRAW_REQUEST).child(myAuth).child(time)
                                reqRef.setValue(requestData).addOnSuccessListener {
                                    runOnUiThread {
                                        showToast("Request sent")
                                        progressDialog.dismiss()
                                    }
                                }.addOnFailureListener {
                                    runOnUiThread {
                                        showToast("Unable to perform transaction now. Please retry\nError code $CASH_OUT_CODE_NO_UID")
                                        progressDialog.dismiss()
                                    }
                                }
                            }
                        }
                    } else {
                        runOnUiThread {
                            showToast("Unable to perform transaction now. Please retry later.\nError code $CASH_OUT_CODE_NO_UID")
                            progressDialog.dismiss()
                        }
                    }
                }.addOnFailureListener {
                    runOnUiThread {
                        showToast(it.message!!.toString())
                        progressDialog.dismiss()
                    }
                }
            }
        }
    }

    private fun showToast(message: String = "Please retry") {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    companion object {
        const val MY_WITHDRAW_REQUEST = "MY_WITHDRAW_REQUEST"
        const val CASH_OUT_ACCOUNT_UID = "CASH_OUT_ACCOUNT_UID"

        const val OVERTIME_CHANGE_URL = "https://howfar-b24ef-overtime-change.firebaseio.com"
        const val CASH_OUT_CODE_NO_UID = 100
    }
}

data class WithDrawRequestData(
    var amount: String = "",
    var accountName: String = "",
    var bank: String = "",
    var account: String = "",
    var time: String = "",
    var reply: String = "",
    var uid: String = "",
    var status: Int = WithDrawRequestStatus.PENDING,
    var address: String = "",
)

object WithDrawRequestStatus {
    const val PENDING = 0
    const val APPROVED = 1
    const val REJECTED = 2
}

class RedeemHistoryAdapter : RecyclerView.Adapter<RedeemHistoryAdapter.RedeemHistoryViewHolder>() {
    lateinit var context: Context
    var dataset = arrayListOf<WithDrawRequestData>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RedeemHistoryViewHolder {
        context = parent.context
        return RedeemHistoryViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_reedem_history, parent, false))
    }

    override fun onBindViewHolder(holder: RedeemHistoryViewHolder, position: Int) = holder.setData(dataset[position])

    override fun getItemCount() = dataset.size

    inner class RedeemHistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvCoin: TextView = itemView.findViewById(R.id.tvCoin)
        private val tvAddress: TextView = itemView.findViewById(R.id.tvAddress)
        private val tvTime: TextView = itemView.findViewById(R.id.tvTime)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        fun setData(datum: WithDrawRequestData) {
            tvCoin.text = "${datum.amount} HFCoin"
            tvTime.text = Util.formatSmartDateTime(datum.time)
            tvStatus.text = when (datum.status) {
                WithDrawRequestStatus.PENDING -> "PENDING"
                WithDrawRequestStatus.REJECTED -> {
                    itemView.setOnClickListener {
                        val alert = AlertDialog.Builder(context)
                        alert.setTitle("Message")
                        alert.setMessage("${datum.reply}\n\nContact support if you have further questions.")
                        alert.setPositiveButton("Contact support") { dialog, _ ->
                            dialog.dismiss()
                            val intent = Intent(context, ContactUsActivity::class.java)
                            context.startActivity(intent)
                        }
                        alert.create().show()
                    }
                    "REJECTED"
                }
                else -> "APPROVED"
            }
        }
    }
}