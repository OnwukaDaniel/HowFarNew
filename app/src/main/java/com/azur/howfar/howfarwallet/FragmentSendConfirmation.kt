package com.azur.howfar.howfarwallet

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.azur.howfar.R
import com.azur.howfar.databinding.FragmentSendConfirmationBinding
import com.azur.howfar.models.TranBank
import com.azur.howfar.models.TranDirection
import com.azur.howfar.models.UserProfile
import com.azur.howfar.models.WalletHistoryData
import com.azur.howfar.utils.Util
import com.azur.howfar.viewmodel.BooleanViewModel
import com.bumptech.glide.Glide
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
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.SocketTimeoutException
import java.util.*

class FragmentSendConfirmation : Fragment(), View.OnClickListener {
    private lateinit var binding: FragmentSendConfirmationBinding
    private val scope = CoroutineScope(Dispatchers.IO)
    private var vFDTransferData = VFDTransferData()
    private val myAuth = FirebaseAuth.getInstance().currentUser!!.uid
    var userProfile: UserProfile = UserProfile()
    private var historyRef = FirebaseDatabase.getInstance("https://howfar-b24ef-wallet.firebaseio.com").reference.child("history")
    private var otherHistoryRef = FirebaseDatabase.getInstance("https://howfar-b24ef-wallet.firebaseio.com").reference.child("history")
    private var timeRef = FirebaseDatabase.getInstance().reference
    private val booleanViewModel by activityViewModels<BooleanViewModel>()
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentSendConfirmationBinding.inflate(inflater, container, false)
        getFragmentData()
        initWallet()
        showProceedText()
        binding.inputProceed.setOnClickListener(this)
        binding.inputBack.setOnClickListener(this)
        return binding.root
    }

    private fun getFragmentData() {
        val json = requireArguments().getString("data")
        vFDTransferData = Gson().fromJson(json, VFDTransferData::class.java)
        val profileJson = requireArguments().getString("profileJson")
        userProfile = Gson().fromJson(profileJson, UserProfile::class.java)

        Glide.with(requireContext()).load(vFDTransferData.image).into(binding.usersImage)
        binding.confirmDate.text = Util.formatTime(Calendar.getInstance().timeInMillis.toString())
        binding.inputAmount.text = vFDTransferData.amount.toFloat().toString()
        binding.confirmName.text = vFDTransferData.firstname
    }

    private fun initWallet() {
        historyRef = historyRef.child(myAuth)
        otherHistoryRef = otherHistoryRef.child(userProfile.uid)
        timeRef = FirebaseDatabase.getInstance().reference.child("time").child(myAuth)
    }

    private fun showProgress() {
        if (activity != null && isAdded) requireActivity().runOnUiThread {
            binding.proceedProgress.visibility = View.VISIBLE
            binding.proceedText.visibility = View.GONE
        }
    }

    private fun showProceedText() {
        if (activity != null && isAdded) requireActivity().runOnUiThread {
            binding.proceedProgress.visibility = View.GONE
            binding.proceedText.visibility = View.VISIBLE
        }
    }

    private fun showMsg(msg: String) {
        if (activity != null && isAdded) requireActivity().runOnUiThread { Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show() }
    }

    private fun initTransfer() {
        scope.launch {
            try {
                val data = Gson().toJson(TransferPayload(vFDTransferData.phone, vFDTransferData.amount.toInt()))
                val header = "Authorization"
                val key = "Bearer ${vFDTransferData.token}"
                val body: RequestBody = data.toRequestBody("application/json".toMediaTypeOrNull())
                val url = "https://howfarserver.online/v1/transfer/wallet/init"
                val client = OkHttpClient()
                val request = Request.Builder().url(url).addHeader(header, key).post(body).build()
                val response = client.newCall(request).execute()
                val jsonResponse = response.body?.string()
                val responseData = Gson().fromJson(jsonResponse, ResolveResponse::class.java)
                println("responseData ********************************************** $responseData")
                if (response.code == 200) {
                    doTransfer(responseData.data)
                } else if (response.code in 400..499) {
                    showMsg(response.message)
                    failure(response.message)
                    showProceedText()
                } else {
                    showMsg(response.message)
                    failure(response.message)
                    showProceedText()
                }
            } catch (e: SocketTimeoutException) {
                showMsg("${e.message}")
                failure(e.message!!)
                showProceedText()
            }
        }
    }

    private fun failure(message: String) {
        if (activity != null && isAdded) {
            requireActivity().runOnUiThread {
                requireActivity().finish()
                startActivity(
                    Intent(requireContext(), SuccessFailure::class.java)
                        .putExtra("amount", vFDTransferData.amount)
                        .putExtra("message", message)
                        .putExtra("isSuccess", false)
                )
                requireActivity().overridePendingTransition(R.anim.enter_right_to_left, R.anim.exit_right_to_left)
            }
        }
    }

    private fun success(historyData: WalletHistoryData) {
        if (activity != null && isAdded) {
            requireActivity().runOnUiThread {
                requireActivity().finish()
                startActivity(
                    Intent(requireContext(), SuccessFailure::class.java)
                        .putExtra("amount", historyData.amount)
                        .putExtra("name", userProfile.name)
                        .putExtra("isSuccess", true)
                )
                requireActivity().overridePendingTransition(R.anim.enter_right_to_left, R.anim.exit_right_to_left)
            }
        }
    }

    private fun doTransfer(data: ResolveData) {
        try {
            FirebaseDatabase.getInstance().reference.child(USER_DETAILS).child(myAuth).get().addOnSuccessListener { my ->
                if (my.exists()) {
                    val myProfile = my.getValue(UserProfile::class.java)!!
                    scope.launch {
                        val header = "Authorization"
                        val key = "Bearer ${vFDTransferData.token}"
                        val body: RequestBody = Gson().toJson(data).toRequestBody("application/json".toMediaTypeOrNull())
                        val url = "https://howfarserver.online/v1/transfer/wallet/complete"
                        val client = OkHttpClient()
                        val request = Request.Builder().url(url).addHeader(header, key).post(body).build()
                        val response = client.newCall(request).execute()
                        val jsonResponse = response.body?.string()
                        val responseData = Gson().fromJson(jsonResponse, ResolveResponse::class.java)
                        if (response.code == 200) {
                            var historyData = WalletHistoryData(
                                myUid = myAuth,
                                amount = vFDTransferData.amount,
                                reference = responseData.data.reference,
                                bankWallet = TranBank.WALLET,
                                direction = TranDirection.SENT,
                                description = "Debit Bank transaction to ${userProfile.name}",
                                otherUid = userProfile.uid,
                            )
                            success(historyData)
                            Util.sendNotification(message = "Credit alert", body = "You received ${vFDTransferData.amount}", receiverUid = userProfile.uid, view = "credit")
                            Util.sendNotification(message = "Debit alert", body = "You sent ${vFDTransferData.amount} to ${userProfile.name}", receiverUid = myAuth,  view = "debit")
                        } else if (response.code in 400..499) {
                            showMsg(response.message)
                            failure(response.message)
                        } else {
                            showMsg(response.message)
                            failure(response.message)
                        }
                    }
                }
            }
        } catch (e: SocketTimeoutException) {
            showMsg("${e.message}")
            failure(e.message!!)
        }
    }

    companion object {
        const val USER_DETAILS = "user_details"
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.input_proceed -> {
                showProgress()
                initTransfer()
            }
            R.id.input_back -> requireActivity().onBackPressed()
        }
    }
}

data class TransferPayload(
    var phoneNumber: String = "",
    var amount: Int = 0,
)

data class ResolveResponse(
    var message: String = "",
    var status: Int = 0,
    var data: ResolveData = ResolveData(),
)

data class ResolveData(
    var reference: String = "",
)

data class TransferReferenceData(
    var reference: String = "",
)

data class PhoneOnly(
    var phoneNumber: String = "",
)

data class VFDTransferData(
    var firstname: String = "",
    var email: String = "",
    var image: String = "",
    var token: String = "",
    var lastname: String = "",
    var amount: String = "",
    var username: String = "",
    var phone: String = "",
    var accountNumber: String = "",
)