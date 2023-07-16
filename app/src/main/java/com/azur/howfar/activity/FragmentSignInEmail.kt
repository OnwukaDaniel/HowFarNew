package com.azur.howfar.activity

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import com.azur.howfar.R
import com.azur.howfar.databinding.FragmentSignInEmailBinding
import com.azur.howfar.model.EmailOnly
import com.azur.howfar.model.SendOTPResponse
import com.google.android.material.snackbar.Snackbar
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
import java.util.concurrent.TimeUnit

class FragmentSignInEmail : Fragment(), View.OnClickListener {
    private lateinit var binding: FragmentSignInEmailBinding
    private lateinit var progressDialog: AlertDialog
    private var scope = CoroutineScope(Dispatchers.IO)
    private val BASE_URL = "https://howfar.up.railway.app/api/"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View{
        binding = FragmentSignInEmailBinding.inflate(inflater, container, false)
        binding.sendCode.setOnClickListener(this)
        val progress = AlertDialog.Builder(requireContext())
        progress.setView(R.layout.card_progress)
        progress.setCancelable(false)
        progressDialog = progress.create()
        return binding.root
    }

    private fun badStatus(dataResponse: SendOTPResponse) {
        var error = ""
        if (dataResponse.data?.message != null) error += "\n${dataResponse.data.message}"
        if(activity != null) requireActivity().runOnUiThread {
            progressDialog.dismiss()
            val dialog = AlertDialog.Builder(requireContext())
            dialog.setTitle("Message")
            dialog.setMessage(error)
            dialog.setPositiveButton("Ok") { d, _ ->
                d.dismiss()
            }
            dialog.create().show()
        }
    }

    private fun errorToast(input: String) {
        if (activity != null && isAdded) {
            Snackbar.make(binding.root, input, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun requestEmailOTP(input: EmailOnly)  {
        if(activity != null) requireActivity().runOnUiThread { progressDialog.show() }
        scope.launch {
            val client = OkHttpClient().newBuilder()
                .connectTimeout(30, TimeUnit.SECONDS) // Set the connect timeout (default: 10 seconds)
                .readTimeout(30, TimeUnit.SECONDS) // Set the read timeout (default: 10 seconds)
                .writeTimeout(30, TimeUnit.SECONDS) // Set the write timeout (default: 10 seconds)
                .build()


            val mediaType = "application/json".toMediaTypeOrNull()
            val requestBody = Gson().toJson(input).toRequestBody(mediaType)

            val request = Request.Builder()
                .url(BASE_URL + "request-otp")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    println("onFailure *************************** ${e.message}")
                    if(activity != null) requireActivity().runOnUiThread {
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
                    val dataResponse = Gson().fromJson(responseBody, SendOTPResponse::class.java)
                    if(activity != null) requireActivity().runOnUiThread {
                        progressDialog.dismiss()
                    }
                    if (dataResponse.success) {
                        val intent = Intent(requireContext(), ActivityEnterOTP::class.java)
                        intent.putExtra("data", input.email)
                        intent.putExtra("type", "email")
                        startActivity(intent)
                        if(activity != null) requireActivity().overridePendingTransition(R.anim.enter_right_to_left, R.anim.exit_right_to_left)
                    } else badStatus(dataResponse)
                }
            })
        }
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.send_code -> {
                val email = binding.emailInput.text.trim().toString()
                if (email == "") return errorToast("Input can't be empty")
                requestEmailOTP(EmailOnly(email = email))
            }
        }
    }
}