package com.azur.howfar.activity

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.azur.howfar.R
import com.azur.howfar.databinding.FragmentConfirmationCodeBinding
import com.azur.howfar.howfarchat.ChatLanding
import com.azur.howfar.howfarchat.ChatLanding.Companion.USER_DETAILS
import com.azur.howfar.models.UserProfile
import com.azur.howfar.models.UserSignUp
import com.azur.howfar.utils.Keyboard.hideKeyboard
import com.azur.howfar.utils.Util
import com.azur.howfar.viewmodel.SignUpViewModel
import com.azur.howfar.workManger.HowFarAnalyticsTypes
import com.azur.howfar.workManger.OpenAppWorkManager
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.FirebaseApiNotAvailableException
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.*
import com.google.firebase.database.FirebaseDatabase
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class FragmentConfirmationCodeSignIn : Fragment(), View.OnClickListener {
    private lateinit var binding: FragmentConfirmationCodeBinding
    private val signUpViewModel: SignUpViewModel by activityViewModels()
    private val auth = FirebaseAuth.getInstance()
    private var storedVerificationId = ""
    private var phoneNumber = ""
    private lateinit var pref: SharedPreferences
    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null
    private var userSignUp = UserSignUp()
    private lateinit var workManager : WorkManager

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        pref = requireActivity().getSharedPreferences(getString(R.string.ALL_PREFERENCE), Context.MODE_PRIVATE)
        binding = FragmentConfirmationCodeBinding.inflate(inflater, container, false)
        workManager = WorkManager.getInstance(requireContext())
        phoneNumber = requireArguments().getString("phoneNumber", "")
        signUpViewModel.userSignUp.observe(viewLifecycleOwner) {
            userSignUp = it
            binding.confirmationDisplayPhone.text = it.phone
        }
        signUpViewModel.startCountDown.observe(viewLifecycleOwner) {
            val dis = binding.verifyCountDown.text.toString()
            if (dis == "Resend" || dis == "60s") flowCountDown()
        }
        binding.verifyCountDown.setOnClickListener(this)
        binding.verifyBtn.setOnClickListener(this)
        binding.verifyCancel.setOnClickListener(this)
        return binding.root
    }

    private fun signInWithPhoneAuthCredential(credential: PhoneAuthCredential) {
        hideKeyboard()
        auth.signInWithCredential(credential).addOnCompleteListener(requireActivity()) { task ->
            if (task.isSuccessful) {
                attemptLogin(task.result.user!!.uid)
            } else {
                if (task.exception is FirebaseAuthInvalidCredentialsException) {
                    errorToast("Invalid details")
                    return@addOnCompleteListener
                }
                errorToast()
            }
        }
    }

    private fun attemptLogin(auth: String) {
        val ref = FirebaseDatabase.getInstance().reference.child(USER_DETAILS).child(auth)
        ref.get().addOnSuccessListener {
            if (it.exists()) {
                activeUserAnalytics()
                val userProfile = it.getValue(UserProfile::class.java)!!
                pref.edit().putString(getString(R.string.this_user), Gson().toJson(userProfile)).apply()
                val intent = Intent(requireContext(), MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
                requireActivity().overridePendingTransition(
                    R.anim.enter_right_to_left,
                    R.anim.exit_right_to_left
                )
                startActivity(intent)
            }
        }
    }

    private fun activeUserAnalytics() {
        val workRequest = OneTimeWorkRequestBuilder<OpenAppWorkManager>().addTag("analytics")
            .setInputData(workDataOf("action" to HowFarAnalyticsTypes.LOG_IN))
            .build()
        workManager.enqueue(workRequest)
    }

    private fun errorToast(input: String = "Wrong code") {
        if (activity != null && isAdded) {
            Snackbar.make(binding.root, input, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun flowCountDown() = try {
        CoroutineScope(Dispatchers.Main).launch {
            val range = (0..60).toList().reversed()
            val flow = range.asSequence().asFlow().onEach { delay(1_000) }

            flow.collect {
                val txt = "$it s"
                binding.verifyCountDown.text = txt
                if (it <= 1) {
                    binding.verifyCountDown.text = "Resend"
                }
            }
        }
    } catch (e: Exception) {
    }

    private fun auth() {
        val inputFormattedNumber = Util.formatNumber(phoneNumber)
        val ref = FirebaseDatabase.getInstance().reference.child(USER_DETAILS)
        ref.get().addOnSuccessListener {
            if (it.exists()) {
                for (i in it.children) {
                    val userProfile = i.getValue(UserProfile::class.java)!!
                    val singleFormattedPhone = Util.formatNumber(userProfile.phone)
                    if (singleFormattedPhone == inputFormattedNumber) {
                        hideKeyboard()
                        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

                            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                                signInWithPhoneAuthCredential(credential)
                            }

                            override fun onVerificationFailed(e: FirebaseException) {
                                hideKeyboard()
                                println("Sign in exception *************************************** ${e.printStackTrace()}")
                                when (e) {
                                    is FirebaseAuthInvalidCredentialsException -> errorToast("Invalid details. Retry")
                                    is FirebaseTooManyRequestsException -> errorToast("Too many Requests, Try Again")
                                    is FirebaseAuthException -> errorToast("App not registered, Try Again")
                                    is FirebaseApiNotAvailableException -> errorToast("Google Play service not installed")
                                }
                            }

                            override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
                                hideKeyboard()
                                errorToast("Code sent to ${userSignUp.phone}")
                                binding.confirmationDisplayPhone.text = userSignUp.phone
                                storedVerificationId = verificationId
                                resendToken = token
                                val u = UserSignUp(phone = userSignUp.phone, verificationCode = verificationId)
                                signUpViewModel.setUserSignUp(u)
                            }
                        }

                        signUpViewModel.setStartCountdown(true)
                        val options = PhoneAuthOptions.newBuilder(auth)
                            .setPhoneNumber(phoneNumber)
                            .setTimeout(60L, TimeUnit.SECONDS)
                            .setActivity(requireActivity())
                            .setCallbacks(callbacks)
                            .build()
                        PhoneAuthProvider.verifyPhoneNumber(options)
                        return@addOnSuccessListener
                    }
                }
                errorToast("User not registered")
            }
        }.addOnFailureListener {
            hideKeyboard()
            //it.localizedMessage?.let { it1 -> errorToast(it1) }
        }
    }

    override fun onClick(p0: View?) {
        hideKeyboard()
        when (p0?.id) {
            R.id.verify_count_down -> {
                val dis = binding.verifyCountDown.text.toString()
                if (dis == "Resend") {
                    auth()
                }
            }
            R.id.verify_cancel -> requireActivity().onBackPressed()
            R.id.verify_btn -> {
                val input = binding.verifyInput.text.trim().toString()
                if (input == "") return
                if (userSignUp.verificationCode == "") {
                    errorToast("Please wait for verification code")
                    return
                }
                val credential = PhoneAuthProvider.getCredential(userSignUp.verificationCode, input)
                signInWithPhoneAuthCredential(credential)
            }
        }
    }
}