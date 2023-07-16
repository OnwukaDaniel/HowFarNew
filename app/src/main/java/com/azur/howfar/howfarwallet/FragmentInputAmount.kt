package com.azur.howfar.howfarwallet

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.azur.howfar.R
import com.azur.howfar.databinding.FragmentInputAmountBinding
import com.azur.howfar.models.UserProfile
import com.azur.howfar.viewmodel.KeypadViewModel
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
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

class FragmentInputAmount : Fragment(), View.OnClickListener {
    private lateinit var binding: FragmentInputAmountBinding
    private val scope = CoroutineScope(Dispatchers.IO)
    private val keypadViewModel: KeypadViewModel by activityViewModels()
    private var defaultKeypad = ""
    private val myAuth = FirebaseAuth.getInstance().currentUser!!.uid
    private var previousVal = ""
    private var walletsRef = FirebaseDatabase.getInstance("https://howfar-b24ef-wallet.firebaseio.com").reference.child("wallets")
    var userProfile: UserProfile = UserProfile()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentInputAmountBinding.inflate(inflater, container, false)
        init()
        showDoneText()
        clickListeners()
        keypadViewModel.value.observe(viewLifecycleOwner) {
            previousVal = binding.inputAmount.text.trim().toString()
            if (it == "-1") binding.inputAmount.text = defaultKeypad else {
                previousVal += it
                binding.inputAmount.text = previousVal
            }
        }
        return binding.root
    }

    private fun init() {
        val json = requireArguments().getString("data")
        userProfile = Gson().fromJson(json, UserProfile::class.java)
        Glide.with(requireContext()).load(userProfile.image).centerCrop().into(binding.usersImage)
        binding.inputUsername.text = userProfile.name
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
        binding.inputDone.setOnClickListener(this)
        binding.inputBack.setOnClickListener(this)
    }

    override fun onResume() {
        binding.inputAmount.text = ""
        super.onResume()
    }

    private fun showProgress() {
        if (activity != null && isAdded) requireActivity().runOnUiThread {
            binding.doneProgress.visibility = View.VISIBLE
            binding.doneText.visibility = View.GONE
        }
    }

    private fun showDoneText() {
        if (activity != null && isAdded) requireActivity().runOnUiThread {
            binding.doneProgress.visibility = View.GONE
            binding.doneText.visibility = View.VISIBLE
        }
    }

    private fun showMsg(msg: String) {
        if (activity != null && isAdded) requireActivity().runOnUiThread { Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show() }
    }

    private fun checkUser(input: String, vfdData: VFDData) {
        scope.launch {
            try {
                val data = Gson().toJson(PhoneOnly(userProfile.phone))
                val header = "Authorization"
                val key = "Bearer ${vfdData.token}"
                val body: RequestBody = data.toRequestBody("application/json".toMediaTypeOrNull())
                val url = "https://howfarserver.online/v1/wallet/resolve"
                val client = OkHttpClient()
                val request = Request.Builder().url(url).addHeader(header, key).post(body).build()
                val response = client.newCall(request).execute()
                val jsonResponse = response.body?.string()
                val responseData = Gson().fromJson(jsonResponse, ResolveResponse::class.java)
                println("jsonResponse resolve *************** ${vfdData.token}******************************* $jsonResponse")
                println("responseData resolve ********************************************** $responseData")
                if (response.code == 200) {
                    showDoneText()
                    completeTransfer(input, vfdData)
                } else if (response.code in 400..499) {
                    showMsg(response.message)
                    showDoneText()
                } else {
                    showMsg(response.message)
                    showDoneText()
                }
            } catch (e: SocketTimeoutException) {
                showMsg("${e.message}")
                showDoneText()
            }
        }
    }

    private fun completeTransfer(input: String, vfdData: VFDData) {
        val vFDTransferData = VFDTransferData(
            firstname= userProfile.name,
            phone = userProfile.phone,
            image = userProfile.image,
            token = vfdData.token,
            amount = input,
        )
        val profileJson = Gson().toJson(userProfile)
        val json = Gson().toJson(vFDTransferData)
        val fragment = FragmentSendConfirmation()
        val bundle = Bundle()
        bundle.putString("data", json)
        bundle.putString("profileJson", profileJson)
        fragment.arguments = bundle
        requireActivity().supportFragmentManager.beginTransaction().addToBackStack("confirm")
            .setCustomAnimations(
                R.anim.enter_right_to_left,
                R.anim.exit_right_to_left,
                R.anim.enter_left_to_right,
                R.anim.exit_left_to_right
            ).replace(R.id.input_root, fragment).commit()
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
            R.id.number_dot -> if (previousVal.contains(".")) return else keypadViewModel.setValue(".")
            R.id.clear -> keypadViewModel.setValue("-1")
            R.id.input_done -> {
                val input = binding.inputAmount.text.trim().toString()
                if (input == "") return
                showProgress()
                walletsRef.child(myAuth).get().addOnSuccessListener {
                    if (it.exists()) {
                        var vfdData = it.getValue(VFDData::class.java)!!
                        checkUser(input, vfdData)
                    } else {
                        showMsg("No account set up")
                        showDoneText()
                    }
                }.addOnFailureListener { error ->
                    showMsg(error.message!!)
                }
            }
            R.id.input_back -> requireActivity().onBackPressed()
        }
    }
}