package com.azur.howfar.howfarwallet

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.hardware.biometrics.BiometricManager.*
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.azur.howfar.R
import com.azur.howfar.activity.ContactUsActivity
import com.azur.howfar.databinding.ActivityFingerPrintBinding
import com.azur.howfar.howfarwallet.Functions.hasBiometricCapability
import com.azur.howfar.models.FingerprintRoute.CASH_OUT_HF_COIN
import com.azur.howfar.models.FingerprintRoute.HOW_FAR_PAY
import com.azur.howfar.user.wallet.CashOutActivity
import com.azur.howfar.viewmodel.KeypadViewModel
import com.bumptech.glide.Glide
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class ActivityFingerPrint : AppCompatActivity(), BiometricAuthListener, View.OnClickListener {
    private val binding by lazy { ActivityFingerPrintBinding.inflate(layoutInflater) }
    private var walletsKeyRef = FirebaseDatabase.getInstance("https://howfar-b24ef-wallet.firebaseio.com").reference.child("wallet_secret_key")
    private val keypadViewModel by viewModels<KeypadViewModel>()
    private var intentCode = 0
    private val myAuth = FirebaseAuth.getInstance().currentUser!!.uid
    private lateinit var pref: SharedPreferences
    private var isFirstTime: Boolean = false
    private var isConfirmation: Boolean = false
    private var passwordConfirmation = ""
    private var pass = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        pref = getSharedPreferences(getString(R.string.ALL_PREFERENCE), Context.MODE_PRIVATE)
        if (intent.hasExtra("data")) intentCode = intent.getIntExtra("data", intentCode)
        walletsKeyRef = walletsKeyRef.child(myAuth)
        showProgress()
        clickListeners()
        startFingerprintAuth()
    }

    private fun clickListeners() {
        binding.number0.setOnClickListener(this)
        binding.number1.setOnClickListener(this)
        binding.number2.setOnClickListener(this)
        binding.number3.setOnClickListener(this)
        binding.number4.setOnClickListener(this)
        binding.number5.setOnClickListener(this)
        binding.number6.setOnClickListener(this)
        binding.number7.setOnClickListener(this)
        binding.number8.setOnClickListener(this)
        binding.number9.setOnClickListener(this)
        binding.numberDot.setOnClickListener(this)
        binding.clear.setOnClickListener(this)
    }

    override fun onResume() {
        super.onResume()
        var retries = 0
        keypadViewModel.value.observe(this) {
            if (it == "-1") {
                pass = ""
                Glide.with(this).load(R.drawable.empty_password).into(binding.passwordImage)
                return@observe
            }
            pass += it
            when (pass.length) {
                0 -> Glide.with(this).load(R.drawable.empty_password).into(binding.passwordImage)
                1 -> Glide.with(this).load(R.drawable.one_password).into(binding.passwordImage)
                2 -> Glide.with(this).load(R.drawable.two_password).into(binding.passwordImage)
                3 -> Glide.with(this).load(R.drawable.three_password).into(binding.passwordImage)
                4 -> Glide.with(this).load(R.drawable.four_password).into(binding.passwordImage)
                5 -> Glide.with(this).load(R.drawable.five_password).into(binding.passwordImage)
                6 -> {
                    Glide.with(this).load(R.drawable.six_password).into(binding.passwordImage)
                    when (isConfirmation) {
                        true -> if (passwordConfirmation == pass) navigationInstruction(isFirstTime = isFirstTime) else {
                            val snackBar = Snackbar.make(binding.root, "", Snackbar.LENGTH_LONG)
                            val message = if (retries > 1) {
                                snackBar.setAction("Contact support") {
                                    startActivity(Intent(this, ContactUsActivity::class.java))
                                    overridePendingTransition(R.anim.enter_right_to_left, R.anim.exit_right_to_left)
                                }
                                "Passwords don't match\nHaving problems contact support"
                            } else "Passwords don't match"
                            snackBar.setText(message)
                            snackBar.show()
                            keypadViewModel.setValue("-1")
                            retries += 1
                        }
                        else -> {
                            passwordConfirmation = pass
                            password("Re-type password", isConfirmation = true, isFirstTime = true)
                        }
                    }
                    pass = ""
                }
            }
        }
    }

    private fun password(msg: String = "Enter Password", isConfirmation: Boolean, isFirstTime: Boolean = false) {
        binding.passwordType.text = msg
        this.isFirstTime = isFirstTime
        this.isConfirmation = isConfirmation
        keypadViewModel.setValue("-1")
    }

    private fun navigationInstruction(isFirstTime: Boolean = false) {
        when (intentCode) {
            0 -> {
                Toast.makeText(this, "No action", Toast.LENGTH_LONG).show()
                finish()
            }
            HOW_FAR_PAY -> {
                when (isFirstTime) {
                    true -> {
                        walletsKeyRef.setValue(passwordConfirmation).addOnSuccessListener {
                            pref.edit().putString(getString(R.string.wallet_secret_key), passwordConfirmation).apply()
                            startActivity(Intent(this, ActivityWallet::class.java).apply {
                                if (intent.hasExtra("other profile")) putExtra("other profile", intent.getStringExtra("other profile"))
                            })
                        }
                    }
                    false -> {
                        startActivity(Intent(this, ActivityWallet::class.java).apply {
                            if (intent.hasExtra("other profile")) putExtra("other profile", intent.getStringExtra("other profile"))
                        })
                    }
                }
            }
            CASH_OUT_HF_COIN -> startActivity(Intent(this, CashOutActivity::class.java))
        }
        overridePendingTransition(R.anim.enter_right_to_left, R.anim.exit_right_to_left)
    }

    private fun startFingerprintAuth() {
        when (hasBiometricCapability(this)) {
            BIOMETRIC_SUCCESS -> {
                showBiometricPrompt(activity = this, listener = this)
            }
            BIOMETRIC_ERROR_NONE_ENROLLED -> {
                authCheck()
            }
            BIOMETRIC_ERROR_NO_HARDWARE -> {
                authCheck()
                /*when (intentCode) {
                    0 -> onBackPressed()
                    1 -> startActivity(Intent(this, ActivityWallet::class.java))
                }*/
                overridePendingTransition(R.anim.enter_right_to_left, R.anim.exit_right_to_left)
            }
        }
    }

    private fun showProgress() {
        binding.passwordRoot.visibility = View.GONE
        binding.progressRoot.visibility = View.VISIBLE
    }

    private fun showKeypad() {
        binding.passwordRoot.visibility = View.VISIBLE
        binding.progressRoot.visibility = View.GONE
    }

    private fun authCheck() {
        when (val savedPassword = pref.getString(getString(R.string.wallet_secret_key), "")!!) {
            "" -> {
                walletsKeyRef.keepSynced(false)
                walletsKeyRef.get().addOnSuccessListener {
                    showKeypad()
                    if (it.exists()) {
                        passwordConfirmation = it.value.toString()
                        password(msg = "Enter Password", isConfirmation = true, isFirstTime = false)
                    } else password(msg = "Enter Password", isConfirmation = false, isFirstTime = true)
                }.addOnFailureListener { error ->
                    Toast.makeText(this, error.message, Toast.LENGTH_LONG).show()
                    finish()
                }
            }
            else -> {
                showKeypad()
                password(msg = "Enter Password", isConfirmation = true, isFirstTime = false)
                passwordConfirmation = savedPassword
            }
        }
    }

    private fun setBiometricPromptInfo(): BiometricPrompt.PromptInfo {
        val desc = "Set up fingerprint to secure your HowFar wallet"
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Biometric Authentication")
            .setSubtitle("")
            .setDescription(desc)

        // Use Device Credentials if allowed, otherwise show Cancel Button
        builder.apply {
            setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            setNegativeButtonText("Cancel")
        }

        return builder.build()
    }

    private fun initBiometricPrompt(activity: AppCompatActivity, listener: BiometricAuthListener): BiometricPrompt {
        // 1
        val executor = ContextCompat.getMainExecutor(activity)

        // 2
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                listener.onBiometricAuthenticationError(errorCode, errString.toString())
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                Log.w(this.javaClass.simpleName, "Authentication failed for an unknown reason")
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                listener.onBiometricAuthenticationSuccess(result)
            }
        }

        // 3
        return BiometricPrompt(activity, executor, callback)
    }

    private fun showBiometricPrompt(activity: AppCompatActivity, listener: BiometricAuthListener) {
        val promptInfo = setBiometricPromptInfo()
        val biometricPrompt = initBiometricPrompt(activity, listener)
        biometricPrompt.apply { authenticate(promptInfo) }
    }

    override fun onBiometricAuthenticationError(errorCode: Int, toString: String) {
        finish()
    }

    override fun onBiometricAuthenticationSuccess(result: BiometricPrompt.AuthenticationResult) {
        navigationInstruction()
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.number0 -> keypadViewModel.setValue("0")
            R.id.number1 -> keypadViewModel.setValue("1")
            R.id.number2 -> keypadViewModel.setValue("2")
            R.id.number3 -> keypadViewModel.setValue("3")
            R.id.number4 -> keypadViewModel.setValue("4")
            R.id.number5 -> keypadViewModel.setValue("5")
            R.id.number6 -> keypadViewModel.setValue("6")
            R.id.number7 -> keypadViewModel.setValue("7")
            R.id.number8 -> keypadViewModel.setValue("8")
            R.id.number9 -> keypadViewModel.setValue("9")
            R.id.number_dot -> startFingerprintAuth()
            R.id.clear -> keypadViewModel.setValue("-1")
        }
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.enter_left_to_right, R.anim.exit_left_to_right)
    }
}

interface BiometricAuthListener {
    fun onBiometricAuthenticationError(errorCode: Int, toString: String)
    fun onBiometricAuthenticationSuccess(result: BiometricPrompt.AuthenticationResult)
}