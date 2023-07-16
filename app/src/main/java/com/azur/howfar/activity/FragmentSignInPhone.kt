package com.azur.howfar.activity

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.telephony.TelephonyManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.azur.howfar.R
import com.azur.howfar.databinding.FragmentSignInPhoneBinding
import com.azur.howfar.howfarchat.ChatLanding
import com.azur.howfar.model.*
import com.azur.howfar.models.Countries
import com.azur.howfar.models.UserProfile
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.*
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class FragmentSignInPhone : Fragment(), ClickHelper, View.OnClickListener {
    private lateinit var binding: FragmentSignInPhoneBinding
    private var json = ""
    private var phoneNumber = ""
    private var countryCode = ""
    private var verifiedPresentUserProfile = UserProfile()
    private lateinit var pref: SharedPreferences
    private var countryCodesAdapter = CountryCodesAdapter()
    private var countriesSelected = Countries()
    private lateinit var dialog: Dialog
    private lateinit var progressDialog: AlertDialog
    private var scope = CoroutineScope(Dispatchers.IO)
    private val BASE_URL = "https://howfar.up.railway.app/api/"
    private var signInType = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentSignInPhoneBinding.inflate(inflater, container, false)
        pref = requireActivity().getSharedPreferences(getString(R.string.ALL_PREFERENCE), Context.MODE_PRIVATE)
        signInType = requireArguments().getString("data")!!
        dialog = Dialog(requireContext())
        binding.phoneCode.setOnClickListener(this)
        binding.sendCode.setOnClickListener(this)
        val progress = AlertDialog.Builder(requireContext())
        progress.setView(R.layout.card_progress)
        progress.setCancelable(false)
        progressDialog = progress.create()
        return binding.root
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        countryCodesAdapter.clickHelper = this
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.country_codes_dialog, binding.root, false)
        dialog.setContentView(dialogView)
        val dialogRv: RecyclerView = dialogView.findViewById(R.id.dialog_rv)
        dialogRv.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
        dialogRv.adapter = countryCodesAdapter
        val tm = requireActivity().getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        countryCode = (tm.networkCountryIso.lowercase())
        try {
            val stream = requireContext().assets.open("country_codes.json")
            json = stream.bufferedReader().use { it.readText() }
            val jj = ObjectMapper().readValue(json, object : TypeReference<List<Countries>?>() {})!!
            countryCodesAdapter.dataset = jj
            countryCodesAdapter.notifyDataSetChanged()
            for (i in jj) {
                if (i.code.lowercase() == countryCode) {
                    binding.phoneCode.text = i.dial_code
                    return
                }
            }
        } catch (e: Exception) {
            println("This is a stack *************************************** $e")
        }
    }

    private fun attemptLogin() {
        val json = Gson().toJson(verifiedPresentUserProfile)
        pref.edit().putString(getString(R.string.this_user), json).apply()
        val intent = Intent(requireContext(), ChatLanding::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        requireActivity().overridePendingTransition(
            R.anim.enter_right_to_left,
            R.anim.exit_right_to_left
        )
    }

    private fun badStatus2(dataResponse: SendPhoneOTPResponse) {
        if (activity != null) requireActivity().runOnUiThread {
            progressDialog.dismiss()
            val dialog = AlertDialog.Builder(requireContext())
            dialog.setTitle("Message")
            dialog.setMessage(dataResponse.message)
            dialog.setPositiveButton("Ok") { d, _ ->
                d.dismiss()
            }
            dialog.create().show()
        }
    }

    private fun errorToast(input: String = "Wrong code") {
        if (activity != null && isAdded) {
            Snackbar.make(binding.root, input, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun requestPhoneOTP(input: PhoneOnly) {
        if (activity != null) requireActivity().runOnUiThread { progressDialog.show() }
        scope.launch {
            val client = OkHttpClient()

            val mediaType = "application/json".toMediaTypeOrNull()
            val requestBody = Gson().toJson(input).toRequestBody(mediaType)

            val request = Request.Builder()
                .url(BASE_URL + "request-phone-otp")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    println("onFailure *************************** ${e.message}")
                    if (activity != null) requireActivity().runOnUiThread {
                        progressDialog.dismiss()

                        val dialog = AlertDialog.Builder(requireContext())
                        dialog.setTitle("Message")
                        dialog.setMessage("Error, \nPlease retry")
                        dialog.setPositiveButton("Ok") { d, _ ->
                            d.dismiss()
                        }
                        dialog.create().show()
                    }
                }

                override fun onResponse(call: Call, response: okhttp3.Response) {
                    val responseBody = response.body?.string()
                    println("onResponse 1 *************************** $responseBody")
                    if (activity != null) requireActivity().runOnUiThread {
                        progressDialog.dismiss()
                    }
                    val dataResponse = Gson().fromJson(responseBody, SendPhoneOTPResponse::class.java)
                    if (dataResponse.success) {
                        val intent = Intent(requireContext(), ActivityEnterOTP::class.java)
                        intent.putExtra("data", input.phone)
                        intent.putExtra("type", "phone")
                        intent.putExtra("countryCode", input.countryCode)
                        startActivity(intent)
                        if (activity != null) requireActivity().overridePendingTransition(R.anim.enter_right_to_left, R.anim.exit_right_to_left)
                    } else {
                        val dataResponseX = Gson().fromJson(responseBody, PhoneOTPResponse2::class.java)
                        println("onResponse 2 *************************** $dataResponseX")
                        badStatus2(dataResponse)
                    }
                }
            })
        }
    }

    override fun onClickedHelp(datum: Countries) {
        countriesSelected = datum
        binding.phoneCode.text = datum.dial_code
        dialog.dismiss()
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.phone_code -> dialog.show()
            R.id.send_code -> {
                if (binding.phoneNumber.text.trim().toString() == "") {
                    errorToast("Input can't be empty")
                    return
                }
                val input = binding.phoneNumber.text.trim().toString().removePrefix("0")
                var countryCode = binding.phoneCode.text.toString()
                requestPhoneOTP(PhoneOnly(countryCode, input))
            }
        }
    }

    companion object {
        const val CALL_REFERENCE = "call_reference"
        const val TRANSFER_HISTORY = "user_coins_transfer"
        const val USER_DETAILS = "user_details"
    }
}