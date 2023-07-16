package com.azur.howfar.activity

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.telephony.TelephonyManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.azur.howfar.R
import com.azur.howfar.databinding.FragmentVerifyPhoneBinding
import com.azur.howfar.howfarchat.ChatLanding
import com.azur.howfar.models.Countries
import com.azur.howfar.models.UserProfile
import com.azur.howfar.utils.Keyboard.hideKeyboard
import com.azur.howfar.utils.Util.formatNumber
import com.azur.howfar.viewmodel.SignUpViewModel
import com.bumptech.glide.Glide
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.*
import com.google.firebase.database.FirebaseDatabase
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*

class FragmentVerifyPhone : Fragment(), ClickHelper, View.OnClickListener {
    private lateinit var binding: FragmentVerifyPhoneBinding
    private var json = ""
    private var phoneNumber = ""
    private var countryCode = ""
    private var name = ""
    private var gender = ""
    private var imageUri = Uri.EMPTY
    private var storedVerificationId = ""
    private lateinit var pref: SharedPreferences
    private var ref = FirebaseDatabase.getInstance().reference
    private var countryCodesAdapter = CountryCodesAdapter()
    private var countriesSelected = Countries()
    private lateinit var dialog: Dialog
    private val signUpViewModel: SignUpViewModel by activityViewModels()
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentVerifyPhoneBinding.inflate(inflater, container, false)
        dialog = Dialog(requireContext())
        showSendText()
        pref = requireActivity().getSharedPreferences(getString(R.string.ALL_PREFERENCE), Context.MODE_PRIVATE)
        binding.phoneCode.setOnClickListener(this)
        binding.sendCode.setOnClickListener(this)
        binding.verifyCancel.setOnClickListener(this)
        return binding.root
    }

    private fun showSendText() {
        binding.sendCodeProgress.visibility = View.GONE
        binding.sendCodeText.visibility = View.VISIBLE
    }

    private fun showProgress() {
        binding.sendCodeProgress.visibility = View.VISIBLE
        binding.sendCodeText.visibility = View.GONE
    }

    private fun checkUser() {
        showProgress()
        val phone = binding.phoneNumber.text.trim().toString()
        if (phone == "") return
        val inputFormattedNumber = formatNumber(phone)
        createAccount(inputFormattedNumber)
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

            for (i in jj) if (i.code.lowercase() == countryCode) {
                binding.phoneCode.text = i.dial_code
                return
            }
        } catch (e: Exception) {
            println("This is a stack *************************************** ${e.printStackTrace()}")
        }
    }

    private fun errorToast(input: String = "Wrong code") {
        hideKeyboard()
        Toast.makeText(requireContext(), input, Toast.LENGTH_LONG).show()
    }

    private fun navigate(task: Task<AuthResult>, imageUri: String) {
        val user = task.result?.user
        val u = UserProfile(name = name, phone = phoneNumber, countryCode = countryCode, gender = gender, uid = user!!.uid, image = imageUri)

        ref = ref.child("user_details").child(user.uid)
        ref.setValue(u).addOnSuccessListener {
            val json = Gson().toJson(u)
            pref.edit().putString(getString(R.string.this_user), json).apply()
            val intent = Intent(requireContext(), ChatLanding::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            requireActivity().overridePendingTransition(R.anim.enter_right_to_left, R.anim.exit_right_to_left)

        }.addOnFailureListener { errorToast("Error Occurred, Try Again") }
    }

    override fun onClickedHelp(datum: Countries) {
        countriesSelected = datum
        binding.phoneCode.text = datum.dial_code
        dialog.dismiss()
    }

    private fun createAccount(phoneNumber: String) {
        scope.launch {

        }
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.verify_cancel -> requireActivity().onBackPressed()
            R.id.phone_code -> dialog.show()
            R.id.send_code -> checkUser()
        }
    }
}