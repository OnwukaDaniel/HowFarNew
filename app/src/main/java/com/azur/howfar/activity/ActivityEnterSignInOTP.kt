package com.azur.howfar.activity

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.azur.howfar.R
import com.azur.howfar.databinding.ActivityEnterOtpBinding
import com.azur.howfar.howfarchat.ChatLanding
import com.azur.howfar.model.*
import com.azur.howfar.models.UserProfile
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

class ActivityEnterSignInOTP : BaseActivity(), View.OnClickListener {
    private val binding by lazy { ActivityEnterOtpBinding.inflate(layoutInflater) }
    private lateinit var pref: SharedPreferences
    private var scope = CoroutineScope(Dispatchers.IO)
    private lateinit var progressDialog: AlertDialog
    private val BASE_URL = "https://howfar.up.railway.app/api/"
    private var phoneOrEmail = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        binding.verifyOTP.setOnClickListener(this)
        binding.resendOtp.setOnClickListener(this)
        pref = getSharedPreferences(getString(R.string.ALL_PREFERENCE), Context.MODE_PRIVATE)
        init()
    }

    private fun init() {
        val progress = AlertDialog.Builder(this)
        progress.setView(R.layout.card_progress)
        progress.setCancelable(false)
        progressDialog = progress.create()
        phoneOrEmail = intent.getStringExtra("data")!!
        if (intent.hasExtra("type")) {
            when (intent.getStringExtra("type")) {
                "email" -> {
                    binding.message.text = "A verification code was sent to your email\nEnter code here"
                }
                "phone" -> {
                    binding.message.text = "A verification code was sent to your phone\nEnter code here"
                }
            }
        }
    }

    private fun verifyPhoneOTP(input: PhoneAndOTP) {
        scope.launch {
            val client = OkHttpClient()

            val mediaType = "application/json".toMediaTypeOrNull()
            val requestBody = Gson().toJson(input).toRequestBody(mediaType)

            val request = Request.Builder()
                .url(BASE_URL + "verify-phone-otp")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    println("onFailure ***************************")
                }

                override fun onResponse(call: Call, response: okhttp3.Response) {
                    runOnUiThread { progressDialog.dismiss() }
                    val responseBody = response.body?.string()
                    val dataResponse = Gson().fromJson(responseBody, EmailAuthResponse::class.java)
                    println("onResponse 1 *************************** $responseBody")
                    println("onResponse 2 *************************** $dataResponse")
                    if (dataResponse.status) getProfile() else {
                        val responseX = Gson().fromJson(responseBody, SendOTPResponse::class.java)
                        badStatus(responseX)
                    }
                }
            })
        }
    }

    private fun verifyEmailOTP(input: EmailAndOTP) {
        runOnUiThread { progressDialog.show() }
        scope.launch {
            val client = OkHttpClient()

            val mediaType = "application/json".toMediaTypeOrNull()
            val requestBody = Gson().toJson(input).toRequestBody(mediaType)

            val request = Request.Builder()
                .url(BASE_URL + "verify-otp")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    println("onFailure ***************************")
                    runOnUiThread { progressDialog.dismiss() }
                }

                override fun onResponse(call: Call, response: okhttp3.Response) {
                    runOnUiThread { progressDialog.dismiss() }
                    val responseBody = response.body?.string()
                    val dataResponse = Gson().fromJson(responseBody, SuccessAccountCreation::class.java)
                    println("onResponse *************************** $responseBody")
                    if (dataResponse.success) getProfile() else {
                        val responseX = Gson().fromJson(responseBody, SendOTPResponse::class.java)
                        badStatus(responseX)
                    }
                }
            })
        }
    }

    private fun badStatus(dataResponse: SendOTPResponse) {
        var error = ""
        if (dataResponse.data?.message != null) error += "\n${dataResponse.data.message}"
        runOnUiThread {
            binding.verifyEmailProgress.visibility = View.GONE
            //supportFragmentManager.beginTransaction().detach(progressFragment).commit()
            //supportFragmentManager.popBackStack()
            val dialog = AlertDialog.Builder(this)
            dialog.setTitle("Message")
            dialog.setMessage(error)
            dialog.setPositiveButton("Ok") { d, _ ->
                d.dismiss()
            }
            if (error.trim() == "The email has already been taken.") {
                dialog.setNegativeButton("Verify Email instead") { d, _ ->
                    d.dismiss()
                }
            }
            dialog.create().show()
        }
    }

    private fun badStatus(dataResponse: SendPhoneOTPResponse) {
        runOnUiThread {
            binding.verifyEmailProgress.visibility = View.GONE
            progressDialog.dismiss()
            val dialog = AlertDialog.Builder(this)
            dialog.setTitle("Message")
            dialog.setMessage(dataResponse.message)
            dialog.setPositiveButton("Ok") { d, _ ->
                d.dismiss()
            }
            dialog.create().show()
        }
    }

    private fun requestEmailOTP(input: EmailOnly) = runOnUiThread {
        runOnUiThread { progressDialog.show() }
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
                    runOnUiThread {
                        binding.verifyEmailProgress.visibility = View.GONE
                        progressDialog.dismiss()

                        val dialog = AlertDialog.Builder(this@ActivityEnterSignInOTP)
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
                    println("onResponse 2 *************************** $dataResponse")
                    runOnUiThread { progressDialog.dismiss() }
                    if (dataResponse.success) error("OTP sent successfully") else badStatus(dataResponse)
                }
            })
        }
    }

    private fun getProfile() = runOnUiThread {
        runOnUiThread { progressDialog.show() }
        scope.launch {
            val client = OkHttpClient().newBuilder()
                .connectTimeout(30, TimeUnit.SECONDS) // Set the connect timeout (default: 10 seconds)
                .readTimeout(30, TimeUnit.SECONDS) // Set the read timeout (default: 10 seconds)
                .writeTimeout(30, TimeUnit.SECONDS) // Set the write timeout (default: 10 seconds)
                .build()


            val mediaType = "application/json".toMediaTypeOrNull()
            val requestBody = Gson().toJson("input").toRequestBody(mediaType)

            val request = Request.Builder()
                .url(BASE_URL + "request-otp")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    println("onFailure *************************** ${e.message}")
                    runOnUiThread {
                        binding.verifyEmailProgress.visibility = View.GONE
                        progressDialog.dismiss()

                        val dialog = AlertDialog.Builder(this@ActivityEnterSignInOTP)
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
                    runOnUiThread { progressDialog.dismiss() }
                    if (dataResponse.success) {
                        val userProfile = UserProfile(
                            name = "registerEmail.name",
                            phone = "registerEmail.phone",
                            countryCode = "registerEmail.countryCode",
                            email = "registerEmail.email",
                            uid = "",
                        )
                        val json = Gson().toJson(userProfile)
                        pref.edit().putString("userprofile", json).apply()
                        val intent = Intent(applicationContext, ChatLanding::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
                        startActivity(intent)
                        overridePendingTransition(R.anim.enter_right_to_left, R.anim.exit_right_to_left)
                    } else badStatus(dataResponse)
                }
            })
        }
    }

    private fun requestPhoneOTP(input: PhoneOnly) {
        runOnUiThread { progressDialog.show() }
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
                    runOnUiThread {
                        progressDialog.dismiss()
                        val dialog = AlertDialog.Builder(this@ActivityEnterSignInOTP)
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
                    runOnUiThread {
                        progressDialog.dismiss()
                    }
                    val dataResponse = Gson().fromJson(responseBody, SendPhoneOTPResponse::class.java)
                    println("onResponse 1 *************************** $responseBody")
                    println("onResponse 2 *************************** $dataResponse")
                    if (dataResponse.success) error("OTP sent successfully") else badStatus(dataResponse)
                }
            })
        }
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.verifyOTP -> {
                val input = binding.otpInput.text.trim().toString()
                if (input == "") return Toast.makeText(applicationContext, "Enter OTP", Toast.LENGTH_LONG).show()
                when (intent.getStringExtra("type")) {
                    "email" -> {
                        verifyEmailOTP(EmailAndOTP(email = phoneOrEmail, otp = input))
                    }
                    "phone" -> {
                        val countryCode = intent.getStringExtra("countryCode")!!
                        verifyPhoneOTP(PhoneAndOTP(countryCode = countryCode, phone = phoneOrEmail, otp = input))
                    }
                }
            }
            R.id.resend_otp -> {
                if (intent.hasExtra("type")) {
                    when (intent.getStringExtra("type")) {
                        "email" -> {
                            requestEmailOTP(EmailOnly(phoneOrEmail))
                        }
                        "phone" -> {
                            val countryCode = intent.getStringExtra("countryCode")!!
                            requestPhoneOTP(PhoneOnly(countryCode, phoneOrEmail))
                        }
                    }
                }
            }
        }
    }
}