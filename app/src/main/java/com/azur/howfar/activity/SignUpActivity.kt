package com.azur.howfar.activity

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.telephony.TelephonyManager
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.azur.howfar.R
import com.azur.howfar.databinding.ActivitySignUpBinding
import com.azur.howfar.model.*
import com.azur.howfar.models.Countries
import com.azur.howfar.utils.CanHubImage
import com.azur.howfar.utils.ImageCompressor
import com.azur.howfar.utils.Keyboard.hideKeyboard
import com.bumptech.glide.Glide
import com.canhub.cropper.CropImageContract
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
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
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

class SignUpActivity : AppCompatActivity(), View.OnClickListener, ClickHelper {
    private val binding by lazy { ActivitySignUpBinding.inflate(layoutInflater) }
    private var selectedGender = ""
    private var countryCodesAdapter = CountryCodesAdapter()
    private var countryCode = ""
    private lateinit var phoneCodesDialog: Dialog
    private var imageUri: Uri = Uri.EMPTY
    private lateinit var canHubImage: CanHubImage
    private var countriesSelected = Countries()
    private var scope = CoroutineScope(Dispatchers.IO)
    private lateinit var progressDialog: AlertDialog
    private val BASE_URL = "https://howfar.up.railway.app/api/"
    private var registerEmail = RegisterEmail()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        countryCodesAdapter.clickHelper = this
        canHubImage = CanHubImage(this)
        binding.male.setOnClickListener(this)
        binding.female.setOnClickListener(this)
        binding.addImage.setOnClickListener(this)
        binding.verifyPhone.setOnClickListener(this)
        binding.verifyEmail.setOnClickListener(this)
        binding.male.background = ContextCompat.getDrawable(this, R.drawable.bg_stok_round)
        binding.female.background = ContextCompat.getDrawable(this, R.drawable.bg_stok_round)
        loadCountries()
        val progress = AlertDialog.Builder(this)
        progress.setView(R.layout.card_progress)
        progress.setCancelable(false)
        progressDialog = progress.create()
        binding.phoneCode.setOnClickListener {
            phoneCodesDialog.show()
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun loadCountries() {
        val dialog = AlertDialog.Builder(this)
        val dialogView = LayoutInflater.from(this).inflate(R.layout.country_codes_dialog, binding.root, false)
        dialog.setView(dialogView)
        phoneCodesDialog = dialog.create()
        val dialogRv: RecyclerView = dialogView.findViewById(R.id.dialog_rv)
        dialogRv.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        dialogRv.adapter = countryCodesAdapter

        val tm = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        countryCode = (tm.networkCountryIso.lowercase())
        try {
            val stream = assets.open("country_codes.json")
            val json = stream.bufferedReader().use { it.readText() }
            val jj = ObjectMapper().readValue(json, object : TypeReference<List<Countries>?>() {})!!
            countryCodesAdapter.dataset = jj
            countryCodesAdapter.notifyDataSetChanged()
            for (i in jj) if (i.code.lowercase() == countryCode) {
                binding.phoneCode.text = i.dial_code
                countryCode = i.dial_code
                return
            }
        } catch (e: Exception) {
            println("This is a stack *************************************** ${e.printStackTrace()}")
        }
    }

    private val cropImage = registerForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) {
            val uriContent = result.uriContent!!
            //val uriFilePath = result.getUriFilePath(this) // optional usage
            try {
                val pair: Pair<ByteArrayInputStream, ByteArray> = ImageCompressor.compressImage(uriContent, this, null)
                imageUri = uriContent
                Glide.with(this).load(pair.second).circleCrop().into(binding.addImage)
            } catch (e: Exception) {
                println("Exception ******************************************* ${e.printStackTrace()}")
            }
        } else {
            val exception = result.error
            exception!!.printStackTrace()
        }
    }

    private fun prepOTP(): Boolean {
        val name = binding.etName.text.toString()
        val email = binding.etEmail.text.toString()
        val phone = binding.etPhone.text.toString()
        if (name.isEmpty()) {
            Toast.makeText(this, "Enter Name", Toast.LENGTH_SHORT).show()
            return false
        }
        if (selectedGender.isEmpty()) {
            Toast.makeText(this, "Select Gender", Toast.LENGTH_SHORT).show()
            return false
        }
        if (email.isEmpty()) {
            Toast.makeText(this, "Enter Email", Toast.LENGTH_SHORT).show()
            return false
        }
        if (phone.isEmpty()) {
            Toast.makeText(this, "Enter Phone", Toast.LENGTH_SHORT).show()
            return false
        }
        registerEmail = RegisterEmail(name, phone, countryCode, selectedGender, email)
        return true
    }

    private fun requestEmailOTP(input: EmailOnly) = runOnUiThread {
        binding.verifyEmailProgress.visibility = View.VISIBLE
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

                        val dialog = AlertDialog.Builder(this@SignUpActivity)
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
                    val dataResponse = Gson().fromJson(responseBody, SendOTPResponse::class.java)
                    runOnUiThread {
                        binding.verifyEmailProgress.visibility = View.GONE
                        progressDialog.dismiss()
                    }
                    println("onResponse 1 *************************** $responseBody")
                    if (dataResponse.success) {
                        val intent = Intent(applicationContext, ActivityEnterOTP::class.java)
                        val json = Gson().toJson(registerEmail)
                        intent.putExtra("data", json)
                        intent.putExtra("OnBoardingType", OnBoardingType.sign_in)
                        intent.putExtra("type", "email")
                        startActivity(intent)
                        overridePendingTransition(R.anim.enter_right_to_left, R.anim.exit_right_to_left)
                    } else badStatus(dataResponse)
                }
            })
        }
    }

    private fun requestPhoneOTP(input: PhoneOnly) {
        runOnUiThread { binding.verifyPhoneProgress.visibility = View.VISIBLE }
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
                        binding.verifyPhoneProgress.visibility = View.GONE
                        progressDialog.dismiss()

                        val dialog = AlertDialog.Builder(this@SignUpActivity)
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
                        binding.verifyPhoneProgress.visibility = View.GONE
                        progressDialog.dismiss()
                    }
                    val dataResponse = Gson().fromJson(responseBody, SendPhoneOTPResponse::class.java)
                    println("onResponse 1 *************************** $responseBody")
                    println("onResponse 2 *************************** $dataResponse")
                    if (dataResponse.success) {
                        val intent = Intent(applicationContext, ActivityEnterOTP::class.java)
                        val json = Gson().toJson(registerEmail)
                        intent.putExtra("data", json)
                        intent.putExtra("OnBoardingType", OnBoardingType.sign_in)
                        intent.putExtra("type", "phone")
                        startActivity(intent)
                        overridePendingTransition(R.anim.enter_right_to_left, R.anim.exit_right_to_left)
                    } else {
                        badStatus2(dataResponse)
                    }
                }
            })
        }
    }

    private fun badStatus(dataResponse: EmailAuthResponse, type: String) {
        val errorList = arrayListOf<String>()
        var error = ""
        if (dataResponse.errors?.name?.first() != null) {
            error += "\n${dataResponse.errors.name.first()}"
            errorList.add(dataResponse.errors.name.first())
        }
        if (dataResponse.errors?.countryCode?.first() != null) {
            error += "\n${dataResponse.errors.countryCode.first()}"
            errorList.add(dataResponse.errors.countryCode.first())
        }
        if (dataResponse.errors?.phone?.first() != null) {
            error += "\n${dataResponse.errors.phone.first()}"
            errorList.add(dataResponse.errors.phone.first())
        }
        if (dataResponse.errors?.gender?.first() != null) {
            error += "\n${dataResponse.errors.gender.first()}"
            errorList.add(dataResponse.errors.gender.first())
        }
        if (dataResponse.errors?.email?.first() != null) {
            error += "\n${dataResponse.errors.email.first()}"
            errorList.add(dataResponse.errors.email.first())
        }
        runOnUiThread {
            binding.verifyEmailProgress.visibility = View.GONE
            binding.verifyPhoneProgress.visibility = View.GONE
            progressDialog.dismiss()
            //supportFragmentManager.beginTransaction().detach(progressFragment).commit()
            //supportFragmentManager.popBackStack()
            val dialog = AlertDialog.Builder(this)
            dialog.setTitle("Message")
            dialog.setMessage(error)
            dialog.setPositiveButton("Ok") { d, _ ->
                d.dismiss()
            }
            when (type) {
                "email" -> {
                    if (errorList.contains("The email has already been taken.")) {
                        dialog.setNegativeButton("Verify Email instead") { d, _ ->
                            requestEmailOTP(EmailOnly(registerEmail.email))
                            d.dismiss()
                        }
                    }
                }
                "phone" -> {
                    if (errorList.contains("The phone has already been taken.")) {
                        dialog.setNegativeButton("Verify phone instead") { d, _ ->
                            requestPhoneOTP(PhoneOnly(registerEmail.countryCode, registerEmail.phone))
                            d.dismiss()
                        }
                    }
                }
            }
            dialog.create().show()
        }
    }

    private fun badStatus(dataResponse: SendOTPResponse) {
        var error = ""
        if (dataResponse.data?.message != null) error += "\n${dataResponse.data.message}"
        runOnUiThread {
            binding.verifyEmailProgress.visibility = View.GONE
            binding.verifyPhoneProgress.visibility = View.GONE
            progressDialog.dismiss()
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
                    requestEmailOTP(EmailOnly(registerEmail.email))
                    d.dismiss()
                }
            }
            dialog.create().show()
        }
    }

    private fun badStatus2(dataResponse: SendPhoneOTPResponse) {
        var error = ""
        runOnUiThread {
            binding.verifyEmailProgress.visibility = View.GONE
            binding.verifyPhoneProgress.visibility = View.GONE
            progressDialog.dismiss()
            val dialog = AlertDialog.Builder(this)
            dialog.setTitle("Message")
            dialog.setMessage(dataResponse.message)
            dialog.setPositiveButton("Ok") { d, _ ->
                d.dismiss()
            }
            if (error.trim() == "The email has already been taken.") {
                dialog.setNegativeButton("Verify Email instead") { d, _ ->
                    requestEmailOTP(EmailOnly(registerEmail.email))
                    d.dismiss()
                }
            }
            dialog.create().show()
        }
    }

    override fun onClick(p0: View?) {
        when (p0?.id) {
            R.id.verifyEmail -> {
                if (!prepOTP()) return
                hideKeyboard()
                runOnUiThread {
                    binding.verifyEmailProgress.visibility = View.VISIBLE
                    progressDialog.show()
                }
                scope.launch {
                    val client = OkHttpClient()

                    val mediaType = "application/json".toMediaTypeOrNull()
                    val requestBody = Gson().toJson(registerEmail).toRequestBody(mediaType)

                    val request = Request.Builder()
                        .url(BASE_URL + "register")
                        .addHeader("Content-Type", "application/json")
                        .post(requestBody)
                        .build()

                    client.newCall(request).enqueue(object : Callback {
                        override fun onFailure(call: Call, e: IOException) {
                            println("onFailure *************************** ${e.message}")
                            runOnUiThread {
                                binding.verifyEmailProgress.visibility = View.GONE
                                progressDialog.dismiss()

                                val dialog = AlertDialog.Builder(applicationContext)
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
                            val dataResponse = Gson().fromJson(responseBody, EmailAuthSignUp::class.java)
                            println("onResponse *************************** $dataResponse")
                            println("onResponse *************************** $responseBody")
                            runOnUiThread { binding.verifyEmailProgress.visibility = View.GONE }
                            if (dataResponse.success) {
                                requestEmailOTP(EmailOnly(registerEmail.email))
                            } else {
                                val dataResponseX = Gson().fromJson(responseBody, EmailAuthResponse::class.java)
                                badStatus(dataResponseX, "email")
                            }
                        }
                    })
                }
            }
            R.id.verifyPhone -> {
                if (!prepOTP()) return
                runOnUiThread { binding.verifyPhoneProgress.visibility = View.VISIBLE }
                runOnUiThread { progressDialog.show() }
                hideKeyboard()
                scope.launch {
                    val client = OkHttpClient()

                    val mediaType = "application/json".toMediaTypeOrNull()
                    val requestBody = Gson().toJson(registerEmail).toRequestBody(mediaType)

                    val request = Request.Builder()
                        .url(BASE_URL + "register")
                        .addHeader("Content-Type", "application/json")
                        .post(requestBody)
                        .build()

                    client.newCall(request).enqueue(object : Callback {
                        override fun onFailure(call: Call, e: IOException) {
                            runOnUiThread { binding.verifyPhoneProgress.visibility = View.GONE }
                            println("onFailure *************************** ${e.message}")
                        }

                        override fun onResponse(call: Call, response: okhttp3.Response) {
                            runOnUiThread { binding.verifyPhoneProgress.visibility = View.GONE }
                            val responseBody = response.body?.string()
                            val dataResponse = Gson().fromJson(responseBody, PhoneSuccess::class.java)
                            println("onResponse 1 *************************** $responseBody")
                            if (dataResponse.success) {
                                requestPhoneOTP(PhoneOnly(registerEmail.countryCode, registerEmail.phone))
                            } else {
                                val dataResponseX = Gson().fromJson(responseBody, EmailAuthResponse::class.java)
                                badStatus(dataResponseX, "phone")
                            }
                        }
                    })
                }
            }
            R.id.male -> {
                selectedGender = "MALE"
                binding.male.background =
                    ContextCompat.getDrawable(this, R.drawable.bg_stok_round_pink)
                binding.female.background =
                    ContextCompat.getDrawable(this, R.drawable.bg_stok_round)
            }
            R.id.female -> {
                selectedGender = "FEMALE"
                binding.female.background =
                    ContextCompat.getDrawable(this, R.drawable.bg_stok_round_pink)
                binding.male.background =
                    ContextCompat.getDrawable(this, R.drawable.bg_stok_round)
            }
            R.id.add_image -> canHubImage.openCanHub(cropImage)
        }
    }

    override fun onClickedHelp(datum: Countries) {
        countriesSelected = datum
        countryCode = datum.dial_code
        binding.phoneCode.text = datum.dial_code
        phoneCodesDialog.dismiss()
    }
}