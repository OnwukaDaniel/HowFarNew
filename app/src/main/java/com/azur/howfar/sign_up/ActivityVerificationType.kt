package com.azur.howfar.sign_up

import android.os.Bundle
import android.view.View
import android.view.View.OnClickListener
import androidx.appcompat.app.AppCompatActivity
import com.azur.howfar.R
import com.azur.howfar.databinding.ActivityVerificationTypeBinding
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

class ActivityVerificationType : AppCompatActivity(), OnClickListener {
    private val binding by lazy { ActivityVerificationTypeBinding.inflate(layoutInflater) }
    private val scope = CoroutineScope(Dispatchers.IO)
    private val appKey = "sbp_46ec086f23c6d2bda857900409857175430e9047"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        binding.cancel.setOnClickListener(this)
        binding.messageVerify.setOnClickListener(this)
        binding.callVerify.setOnClickListener(this)
        binding.flashVerify.setOnClickListener(this)
    }

    private fun createAccount() {
        scope.launch {
            try {
                val header = "Authorization"
                val key = appKey
                val body: RequestBody = Gson().toJson(PhonePassword(phone = "2347019292046", password = "Azure@16"))
                    .toRequestBody("application/json".toMediaTypeOrNull())
                val url = "https://czqberlmwinmzwgdrozc.supabase.co/auth/v1/signup"
                val client = OkHttpClient()
                val request = Request.Builder().url(url).addHeader(header, key).post(body).build()
                val response = client.newCall(request).execute()
                val jsonResponse = response.body?.string()
                when (response.code) {
                    200 -> {
                        println("Success *********************************** ${response.message}")
                        println("Success jsonResponse *********************************** $jsonResponse")
                    }
                    in 400..499 -> {
                        println("Error *********************************** ${response}")
                    }
                    else -> {
                        println("SocketTimeoutException *********************************** ${response.message}")
                    }
                }
            } catch (e: SocketTimeoutException) {
                println("SocketTimeoutException *********************************** ${e.message}")
            }
        }
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.cancel -> {
            }
            R.id.message_verify -> createAccount()
            R.id.call_verify -> {

            }
            R.id.flash_verify -> {

            }
        }
    }
}

data class PhonePassword(
    var phone: String = "",
    var password: String = ""
)