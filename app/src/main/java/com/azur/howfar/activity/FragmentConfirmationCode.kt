package com.azur.howfar.activity

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.azur.howfar.R
import com.azur.howfar.databinding.FragmentConfirmationCodeBinding
import com.azur.howfar.dilog.ProgressFragment
import com.azur.howfar.howfarchat.ChatLanding
import com.azur.howfar.models.UserProfile
import com.azur.howfar.models.UserSignUp
import com.azur.howfar.user.EditProfileActivity
import com.azur.howfar.utils.Keyboard.hideKeyboard
import com.azur.howfar.viewmodel.SignUpViewModel
import com.azur.howfar.workManger.HowFarAnalyticsTypes
import com.azur.howfar.workManger.OpenAppWorkManager
import com.google.android.gms.tasks.Task
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.FirebaseApiNotAvailableException
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.*
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.util.*
import java.util.concurrent.TimeUnit

class FragmentConfirmationCode : Fragment(), View.OnClickListener {
    private lateinit var binding: FragmentConfirmationCodeBinding
    private val signUpViewModel: SignUpViewModel by activityViewModels()
    private val auth = FirebaseAuth.getInstance()
    private var storedVerificationId = ""
    private var imageUri: String = ""
    private lateinit var pref: SharedPreferences
    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null
    private var ref = FirebaseDatabase.getInstance().reference
    private var userSignUp = UserSignUp()
    private lateinit var workManager: WorkManager

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        pref = requireActivity().getSharedPreferences(getString(R.string.ALL_PREFERENCE), Context.MODE_PRIVATE)
        binding = FragmentConfirmationCodeBinding.inflate(inflater, container, false)
        workManager = WorkManager.getInstance(requireContext())
        signUpViewModel.userSignUp.observe(viewLifecycleOwner) { userSignUp = it }
        signUpViewModel.startCountDown.observe(viewLifecycleOwner) {
            val dis = binding.verifyCountDown.text.toString()
            if (dis == "Resend" || dis == "60s") {
                flowCountDown()
            }
        }
        binding.verifyCountDown.setOnClickListener(this)
        binding.verifyBtn.setOnClickListener(this)
        return binding.root
    }

    private fun signInWithPhoneAuthCredential(credential: PhoneAuthCredential) {
        auth.signInWithCredential(credential).addOnCompleteListener(requireActivity()) { task ->
            if (task.isSuccessful) {
                val user = task.result?.user
                upload(task)
            } else {
                if (task.exception is FirebaseAuthInvalidCredentialsException) {
                    errorToast()
                    return@addOnCompleteListener
                }
                errorToast()
            }
        }.addOnFailureListener {
            errorToast(it.message!!)
        }
    }

    private fun attemptLogin(task: Task<AuthResult>, imageUri: String = "") {
        activeUserAnalytics()
        val user = task.result?.user
        val uid = FirebaseAuth.getInstance().currentUser!!.uid
        val u = UserProfile(
            name = userSignUp.name,
            phone = userSignUp.phone,
            countryCode = userSignUp.countryCode,
            gender = userSignUp.gender,
            uid = user!!.uid,
            image = imageUri
        )
        ref = ref.child("user_details").child(uid)
        ref.setValue(u).addOnSuccessListener {
            pref.edit().putString(getString(R.string.this_user), Gson().toJson(u)).apply()
            val intent = Intent(requireContext(), MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK

            startActivity(intent)
            requireActivity().overridePendingTransition(R.anim.enter_right_to_left, R.anim.exit_right_to_left)
        }.addOnFailureListener { errorToast("Error Occurred, Try Again") }
    }

    private fun activeUserAnalytics() {
        val workRequest = OneTimeWorkRequestBuilder<OpenAppWorkManager>().addTag("analytics")
            .setInputData(workDataOf("action" to HowFarAnalyticsTypes.SIGNUP))
            .build()
        workManager.enqueue(workRequest)
    }

    private fun errorToast(input: String = "Wrong code") {
        Snackbar.make(binding.root, input, Snackbar.LENGTH_LONG).show()
    }

    private fun flowCountDown() = try {
        CoroutineScope(Dispatchers.Main).launch {
            val range = (0..60).toList().reversed()
            val flow = range.asSequence().asFlow().onEach { delay(1_000) }

            flow.collect {
                val txt = "$it s"
                binding.verifyCountDown.text = txt
                if (it <= 1) binding.verifyCountDown.text = "Resend"
            }
        }
    } catch (e: Exception) {
    }

    private fun upload(authTask: Task<AuthResult>) {
        val fragmentDialog = ProgressFragment()
        requireActivity().supportFragmentManager.beginTransaction().addToBackStack("progress").replace(R.id.verify_root, fragmentDialog).commit()
        val timeSent = Calendar.getInstance().timeInMillis.toString()

        if (imageUri == ""){
            attemptLogin(authTask)
            return
        }

        val imageRef =
            FirebaseStorage.getInstance().reference.child(EditProfileActivity.PROFILE_IMAGE).child(FirebaseAuth.getInstance().currentUser!!.uid).child(timeSent)
        val uploadTask = imageRef.putFile(Uri.parse(imageUri))
        uploadTask.continueWith { task ->
            if (!task.isSuccessful) task.exception?.let { itId ->
                requireActivity().supportFragmentManager.beginTransaction().remove(fragmentDialog).commit()
                throw  itId
            }
            imageRef.downloadUrl.addOnSuccessListener {
                uploadTask.continueWith { task ->
                    if (!task.isSuccessful) task.exception?.let {
                        requireActivity().supportFragmentManager.beginTransaction().remove(fragmentDialog).commit()
                        throw  it
                    }
                    imageRef.downloadUrl.addOnSuccessListener { uri ->
                        requireActivity().supportFragmentManager.beginTransaction().remove(fragmentDialog).commit()
                        attemptLogin(authTask, imageUri = uri.toString())
                    }.addOnFailureListener {
                        requireActivity().supportFragmentManager.beginTransaction().remove(fragmentDialog).commit()
                        Toast.makeText(context, "Upload failed!!! Retry", Toast.LENGTH_LONG).show()
                        return@addOnFailureListener
                    }
                }
            }.addOnFailureListener {
                requireActivity().supportFragmentManager.beginTransaction().remove(fragmentDialog).commit()
                Toast.makeText(context, "Upload failed!!! Retry", Toast.LENGTH_LONG).show()
                return@addOnFailureListener
            }
        }
    }

    private fun auth() {
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                hideKeyboard()
                signInWithPhoneAuthCredential(credential)
            }

            override fun onVerificationFailed(e: FirebaseException) {
                hideKeyboard()
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
                storedVerificationId = verificationId
                resendToken = token
                val u = UserSignUp(name = userSignUp.name, phone = userSignUp.phone, countryCode = userSignUp.countryCode, verificationCode = verificationId)
                signUpViewModel.setUserSignUp(u)
            }
        }

        signUpViewModel.setStartCountdown(true)
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(userSignUp.phone)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(requireActivity())
            .setCallbacks(callbacks)
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    override fun onClick(p0: View?) {
        hideKeyboard()
        when (p0?.id) {
            R.id.verify_count_down -> {
                try {
                    val dis = binding.verifyCountDown.text.toString()
                    if (dis == "Resend") {
                        auth()
                    }
                } catch (e: Exception) {
                }
            }
            R.id.verify_btn -> {
                val input = binding.verifyInput.text.trim().toString()
                if (input == "") return
                if (userSignUp.verificationCode == "") {
                    errorToast("Please Wait for code")
                    return
                }
                val credential = PhoneAuthProvider.getCredential(userSignUp.verificationCode, input)
                signInWithPhoneAuthCredential(credential)
            }
        }
    }
}