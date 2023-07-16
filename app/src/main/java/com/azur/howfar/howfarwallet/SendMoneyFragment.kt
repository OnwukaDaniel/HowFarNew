package com.azur.howfar.howfarwallet

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.RecyclerView
import com.azur.howfar.R
import com.azur.howfar.databinding.FragmentSendMoney2Binding
import com.azur.howfar.howfarchat.chat.ActivitySearchChat
import com.azur.howfar.howfarchat.groupChat.Contact
import com.azur.howfar.livedata.ValueEventLiveData
import com.azur.howfar.models.*
import com.azur.howfar.user.wallet.TouchHelper
import com.azur.howfar.utils.CallUtils
import com.azur.howfar.utils.Util
import com.azur.howfar.viewmodel.BooleanViewModel
import com.azur.howfar.viewmodel.TimeStringViewModel
import com.azur.howfar.viewmodel.VFDAccreditedBanksVieModel
import com.azur.howfar.viewmodel.VFDTransferInitViewModel
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.imageview.ShapeableImageView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.SocketTimeoutException
import java.util.*

class SendMoneyFragment : Fragment(), PaymentHelper, View.OnClickListener {
    private lateinit var binding: FragmentSendMoney2Binding
    private var selectedTimeMillis = 0L
    private var vfdData = VFDData()
    private val myAuth = FirebaseAuth.getInstance().currentUser!!.uid
    private var formattedDob = ""
    private lateinit var callUtils: CallUtils
    private val adapter = SearchUserAdapter()
    private var contacts: ArrayList<Contact> = arrayListOf()
    private val allUsers: ArrayList<UserProfile> = arrayListOf()
    private var phoneList: ArrayList<String> = arrayListOf()
    private var vFDBanksList = VFDBanksList()
    private val vFDTransferToVieModel by activityViewModels<VFDAccreditedBanksVieModel>()
    private val booleanViewModel by activityViewModels<BooleanViewModel>()
    private val vFDTransferInitViewModel by activityViewModels<VFDTransferInitViewModel>()
    private val timeStringViewModel by activityViewModels<TimeStringViewModel>()
    private var walletsRef = FirebaseDatabase.getInstance("https://howfar-b24ef-wallet.firebaseio.com").reference
    private var frequentRef = FirebaseDatabase.getInstance("https://howfar-b24ef-wallet.firebaseio.com").reference.child(MY_FREQUENT_SENDS)
    private var walletsSecurityRef = FirebaseDatabase.getInstance("https://howfar-b24ef-wallet.firebaseio.com").reference
    private val scope = CoroutineScope(Dispatchers.IO)
    private var frequentSendsAdapter = FrequentSendsAdapter()

    @SuppressLint("NotifyDataSetChanged")
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { permissions ->
        when (permissions) {
            true -> {
                runBlocking {
                    scope.launch {
                        val pair = Util.getAllSavedContacts(requireContext())
                        contacts = pair.first
                        phoneList = pair.second
                        if (isAdded && activity != null) requireActivity().runOnUiThread { initFirebase() }
                    }
                }
            }
            false -> {
                if (ContextCompat.checkSelfPermission(requireActivity(), Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_DENIED) {
                    callUtils.permissionRationale(message = "HowFar needs CONTACTS permission to deliver best notification experience\nGrant app permission")
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentSendMoney2Binding.inflate(inflater, container, false)
        val json = requireArguments().getString("data")
        binding.accountCopy.setOnClickListener(this)
        binding.friendSend.setOnClickListener(this)
        binding.bankSend.setOnClickListener(this)
        binding.banksNameCard.setOnClickListener(this)
        binding.transferProceed.setOnClickListener(this)
        toggleFriendView()
        hideProgress()
        callUtils = CallUtils(viewLifecycleOwner, requireActivity())
        walletsRef = walletsRef.child("wallets").child(myAuth)
        walletsSecurityRef = walletsSecurityRef.child("wallets_security").child(myAuth)
        selectedTimeMillis = Calendar.getInstance().timeInMillis
        vfdData = Gson().fromJson(json, VFDData::class.java)
        binding.accountNumber.text = vfdData.account_number
        binding.accountName.text = "${vfdData.firstname} ${vfdData.lastname}"
        checkForBVN()
        getFrequents()
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_DENIED) {
            askPermission()
        } else askPermission()
        booleanViewModel.setSwitch(true)
        binding.sendMoneyRootSwipe.setOnRefreshListener {
            requireActivity().recreate()
        }
        return binding.root
    }

    private fun getBalance() {
        scope.launch {
            try {
                val header = "Authorization"
                val key = "Bearer ${vfdData.token}"
                val url = "https://howfarserver.online/v1/profile"
                val body: RequestBody = Gson().toJson(EmailOnly(vfdData.email)).toRequestBody("application/json".toMediaTypeOrNull())
                val client = OkHttpClient()
                val request = Request.Builder().url(url).addHeader(header, key).post(body).build()
                val response = client.newCall(request).execute()
                val jsonResponse = response.body?.string()
                val responseData = Gson().fromJson(jsonResponse, VfdProfile::class.java)
                if (response.code == 200) {
                    requireActivity().runOnUiThread {
                        binding.balance.text = responseData.balance
                        binding.sendMoneyRootSwipe.isRefreshing = false
                    }
                } else if (response.code in 400..499) {
                    requireActivity().runOnUiThread {
                        binding.sendMoneyRootSwipe.isRefreshing = false
                    }
                } else {
                    requireActivity().runOnUiThread {
                        binding.sendMoneyRootSwipe.isRefreshing = false
                    }
                }
            } catch (e: SocketTimeoutException) {
                showMsg("${e.message}")
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        vFDTransferToVieModel.banks.observe(viewLifecycleOwner) {
            binding.banksName.text = it.bankName
            vFDBanksList = it
        }
        booleanViewModel.switch.observe(viewLifecycleOwner) {
            if (it == true) {
                getBalance()
            }
        }
    }

    private fun toggleFriendView() {
        binding.friendSend.setCardBackgroundColor(Color.parseColor("#650681"))
        binding.friendText.setTextColor(Color.WHITE)
        binding.friendIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.sky_blue), android.graphics.PorterDuff.Mode.MULTIPLY)

        binding.bankSend.setCardBackgroundColor(Color.parseColor("#EFEFEF"))
        binding.bankText.setTextColor(Color.parseColor("#650681"))
        binding.bankIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.appPrimaryColor), android.graphics.PorterDuff.Mode.MULTIPLY)
        binding.rvRecentSend.visibility = View.VISIBLE
        binding.banksRoot.visibility = View.GONE
    }

    private fun toggleBankView() {
        binding.bankSend.setCardBackgroundColor(Color.parseColor("#650681"))
        binding.bankText.setTextColor(Color.WHITE)
        binding.bankIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.sky_blue), android.graphics.PorterDuff.Mode.MULTIPLY)

        binding.friendSend.setCardBackgroundColor(Color.parseColor("#EFEFEF"))
        binding.friendText.setTextColor(Color.parseColor("#650681"))
        binding.friendIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.appPrimaryColor), android.graphics.PorterDuff.Mode.MULTIPLY)
        binding.rvRecentSend.visibility = View.GONE
        binding.banksRoot.visibility = View.VISIBLE
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun initFirebase() {
        adapter.dataset = allUsers
        adapter.activity = requireActivity()
        binding.rvRecentSend.adapter = adapter
        allUsers.clear()
        adapter.notifyDataSetChanged()
        val pref = requireContext().getSharedPreferences(getString(R.string.ALL_PREFERENCE), Context.MODE_PRIVATE)
        val json = pref.getString(getString(R.string.this_user), "")
        val thisUser = Gson().fromJson(json, UserProfile::class.java)
        val ref = FirebaseDatabase.getInstance().reference.child(ActivitySearchChat.USER_DETAILS)
        ref.get().addOnSuccessListener {
            if (it.exists()) {
                for (i in it.children) {
                    val userProfile = i.getValue(UserProfile::class.java)!!
                    if (userProfile.phone != thisUser.phone && userProfile !in allUsers) {
                        if (Util.formatNumber(userProfile.phone) in phoneList) {
                            allUsers.add(userProfile)
                            adapter.notifyItemInserted(allUsers.size)
                        }
                    }
                }
            }
        }
        ValueEventLiveData(walletsRef).observe(viewLifecycleOwner) {
            when (it.second) {
                EventListenerType.onDataChange -> {
                    vfdData = it.first.getValue(VFDData::class.java)!!
                    binding.accountNumber.text = vfdData.account_number
                    binding.accountName.text = "${vfdData.firstname} ${vfdData.lastname}"
                    binding.balance.text = vfdData.balance
                }
            }
        }
    }

    private fun showProgress() {
        if (activity != null && isAdded) {
            requireActivity().runOnUiThread {
                binding.transferProceedProgress.visibility = View.VISIBLE
                binding.transferProceed.visibility = View.GONE
            }
        }
    }

    private fun hideProgress() {
        if (activity != null && isAdded) {
            requireActivity().runOnUiThread {
                binding.transferProceedProgress.visibility = View.GONE
                binding.transferProceed.visibility = View.VISIBLE
            }
        }
    }

    private fun askPermission() {
        permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
    }

    private fun checkForBVN() {
        if (vfdData.account_number == "") {
            val alertDialog = AlertDialog.Builder(requireContext())
            alertDialog.setTitle("BVN not set")
            alertDialog.setMessage("You need to set your BVN to send money to another user")
            alertDialog.setCancelable(false)
            alertDialog.setPositiveButton("Set BVN") { dialog, _ ->
                dialog.dismiss()
                setBVN()
            }
            alertDialog.setNegativeButton("Not now") { dialog, _ ->
                dialog.dismiss()
                requireActivity().onBackPressed()
                redirectToSendMoney()
            }
            alertDialog.create().show()
        } else redirectToSendMoney()
    }

    private fun datePicker(view: TextView) {
        view.setOnClickListener {
            val datePicker =
                MaterialDatePicker.Builder.datePicker()
                    .setTitleText("Select date of birth")
                    .setSelection(selectedTimeMillis)
                    .build()
            datePicker.show(requireActivity().supportFragmentManager, "dob")
            datePicker.addOnPositiveButtonClickListener {
                selectedTimeMillis = it
                val instance = Calendar.getInstance()
                instance.timeInMillis = it
                val day = instance.get(Calendar.DAY_OF_MONTH)
                var month = instance.get(Calendar.MONTH).toString()
                val year = instance.get(Calendar.YEAR)
                month = Util.getShortMonth(month.toInt())
                val modDay = if (day.toString().length == 1) "0$day" else day
                formattedDob = "$modDay-$month-$year"
                view.text = formattedDob
            }
            datePicker.addOnCancelListener { }
        }
    }

    private fun setBVN() {
        val alertDialog = AlertDialog.Builder(requireContext())
        alertDialog.setTitle("Set BVN")
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.set_bvn_layout, binding.root, false)
        val dob: TextView = view.findViewById(R.id.dob)
        val bvn: EditText = view.findViewById(R.id.bvn)
        datePicker(dob)
        alertDialog.setView(view)
        alertDialog.setCancelable(false)
        alertDialog.setNegativeButton("Not now") { dialog, _ ->
            dialog.dismiss()
            requireActivity().onBackPressed()
        }
        alertDialog.setPositiveButton("Set BVN") { dialog, _ ->
            if (bvn.text.trim().toString() == "") return@setPositiveButton
            if (formattedDob == "") return@setPositiveButton
            val bVN = bvn.text.trim().toString()
            val data = VfdBvnDob(dateOfBirth = formattedDob, bvn = bVN)
            registerGetAccount(data, dialog)
        }
        alertDialog.create().show()
    }

    private fun getFrequents() {
        frequentSendsAdapter.dataset
        frequentSendsAdapter.activity = requireActivity()
        binding.rvRecentSend.adapter = frequentSendsAdapter
        frequentRef.get().addOnSuccessListener {
            if (it.exists()) {
                val data = it.value
            }
        }
    }

    private fun registerGetAccount(data: VfdBvnDob, dialog: DialogInterface) {
        scope.launch {
            try {
                val header = "Authorization"
                val key = "Bearer ${vfdData.token}"
                val body: RequestBody = Gson().toJson(data).toRequestBody("application/json".toMediaTypeOrNull())
                val url = "https://howfarserver.online/v1/account-number/generate"
                val client = OkHttpClient()
                val request = Request.Builder().url(url).addHeader(header, key).post(body).build()
                val response = client.newCall(request).execute()
                val jsonResponse = response.body?.string()
                val responseData = Gson().fromJson(jsonResponse, VfdBvnDobResponse::class.java)
                if (response.code == 200) {
                    var howFarPayDataUpdate = HowFarPayData()
                    vfdData.account_number = responseData.data.accountNumber
                    vfdData.dob = selectedTimeMillis.toString()

                    walletsSecurityRef.get().addOnSuccessListener {
                        if (it.exists()) howFarPayDataUpdate = it.getValue(HowFarPayData::class.java)!!
                        howFarPayDataUpdate.accountNumber = responseData.data.accountNumber
                        howFarPayDataUpdate.bvn = data.bvn
                        howFarPayDataUpdate.dob = selectedTimeMillis.toString()
                        walletsSecurityRef.setValue(howFarPayDataUpdate).addOnSuccessListener {
                            showMsg("Updated successfully")
                            // REDIRECT TO SEND MONEY TO USER
                            if(requireArguments().getString("other profile") != "") redirectToSendMoney()

                        }.addOnFailureListener { ee ->
                            showMsg(ee.message!!)
                            dialog.dismiss()
                        }
                    }.addOnFailureListener { ee ->
                        showMsg(ee.message!!)
                        dialog.dismiss()
                    }
                    walletsRef.setValue(vfdData).addOnSuccessListener {
                        showMsg("Updated successfully")
                    }.addOnFailureListener { ee ->
                        showMsg(ee.message!!)
                        dialog.dismiss()
                    }
                } else if (response.code in 400..499) {
                    dialog.dismiss()
                    requireActivity().onBackPressed()
                    showMsg(responseData.message)
                } else {
                    dialog.dismiss()
                    requireActivity().onBackPressed()
                    showMsg(response.message)
                    showMsg(responseData.message)
                }
            } catch (e: SocketTimeoutException) {
                dialog.dismiss()
                requireActivity().onBackPressed()
                showMsg("${e.message}")
            }
        }
    }

    private fun redirectToSendMoney() {
        val fragment = FragmentInputAmount()
        val bundle = Bundle()
        val datum = requireArguments().getString("other profile")
        bundle.putString("data", datum)
        fragment.arguments = bundle
        requireActivity().supportFragmentManager.beginTransaction()
            .addToBackStack("input")
            .setCustomAnimations(
                R.anim.enter_right_to_left,
                R.anim.exit_right_to_left,
                R.anim.enter_left_to_right,
                R.anim.exit_left_to_right
            )
            .replace(R.id.send_money_root, fragment).commit()
    }

    private fun initiatePayment(initData: VFDTransferInitData) {
        showProgress()
        val data = VFDTransferBank(accountNumber = initData.accountNumber, bankCode = vFDBanksList.bankCode)
        scope.launch {
            try {
                val header = "Authorization"
                val key = "Bearer ${vfdData.token}"
                val body: RequestBody = Gson().toJson(data).toRequestBody("application/json".toMediaTypeOrNull())
                val url = "https://howfarserver.online/v1/bank/validate"
                val client = OkHttpClient()
                val request = Request.Builder().url(url).addHeader(header, key).post(body).build()
                val response = client.newCall(request).execute()
                val jsonResponse = response.body?.string()
                val responseData = Gson().fromJson(jsonResponse, VfdAccountDetailsResponse::class.java)
                if (response.code == 200) {
                    hideProgress()
                    val fragment = FragmentConfirmBankTransfer()
                    val bundle = Bundle()
                    println("responseData ************************************** $responseData")
                    if (activity != null && isAdded) {
                        requireActivity().runOnUiThread {
                            timeStringViewModel.setStringValue(vfdData.token)
                            vFDTransferInitViewModel.setVFDTransferInitData(initData)
                        }
                    }
                    bundle.putString("data", Gson().toJson(responseData))
                    fragment.arguments = bundle
                    requireActivity().supportFragmentManager.beginTransaction().addToBackStack("transfer bank")
                        .setCustomAnimations(R.anim.enter_right_to_left, R.anim.exit_right_to_left, R.anim.enter_left_to_right, R.anim.exit_left_to_right)
                        .replace(R.id.send_money_root, fragment).commit()
                } else if (response.code in 400..499) {
                    hideProgress()
                    showMsg(responseData.message)
                } else {
                    hideProgress()
                    showMsg(response.message)
                }
            } catch (e: SocketTimeoutException) {
                hideProgress()
                showMsg("${e.message}")
            }
        }
    }

    private fun showMsg(msg: String) {
        if (activity != null && isAdded) {
            requireActivity().runOnUiThread {
                Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    companion object {
        const val MY_FREQUENT_SENDS = "MY_FREQUENT_SENDS"
    }

    override fun onClickHelper(dataset: ArrayList<UserProfile>) {
        binding.transferProceed.visibility = if (dataset.isNotEmpty()) {
            binding.transferProceed.setOnClickListener {
                val fragment = FragmentInputAmount()
                val bundle = Bundle()
                bundle.putString("data", Gson().toJson(dataset))
                fragment.arguments = bundle
                childFragmentManager.beginTransaction().addToBackStack("input")
                    .setCustomAnimations(R.anim.enter_right_to_left, R.anim.exit_right_to_left, R.anim.enter_left_to_right, R.anim.exit_left_to_right)
                    .replace(R.id.send_money_root, fragment).commit()
            }
            View.VISIBLE
        } else View.GONE
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.transfer_proceed -> {
                val accountNumber = binding.transferAccountNum.text.trim().toString()
                val amount = binding.transferAmount.text.trim().toString()
                if (accountNumber == "") return
                if (vFDBanksList.bankCode == "") return
                if (amount == "") return
                initiatePayment(VFDTransferInitData(amount = amount, accountNumber = accountNumber, bankCode = vFDBanksList.bankCode))
            }
            R.id.banks_name_card -> {
                val fragment = FragmentBankList()
                val bundle = Bundle()
                bundle.putString("token", vfdData.token)
                fragment.arguments = bundle
                requireActivity().supportFragmentManager.beginTransaction().addToBackStack("send").replace(R.id.send_money_root, fragment)
                    .commit()
            }
            R.id.friend_send -> toggleFriendView()
            R.id.bank_send -> toggleBankView()
            R.id.account_copy -> {
                var text = binding.accountNumber.text.trim().toString()
                if (text == "") {
                    Toast.makeText(requireContext(), "No account number", Toast.LENGTH_LONG).show()
                    return
                }
                val clipboard: ClipboardManager = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Copied", text)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(requireContext(), "Copied", Toast.LENGTH_LONG).show()
            }
            R.id.number0 -> askPermission()
        }
    }
}

interface PaymentHelper {
    fun onClickHelper(dataset: ArrayList<UserProfile>)
}

class FrequentSendsAdapter : RecyclerView.Adapter<FrequentSendsAdapter.ViewHolder>() {
    var dataset: ArrayList<UserProfile> = arrayListOf()
    var selected: ArrayList<UserProfile> = arrayListOf()
    private lateinit var context: Context
    lateinit var activity: Activity

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val image: ShapeableImageView = itemView.findViewById(R.id.image_recent_send)
        val name: TextView = itemView.findViewById(R.id.name_recent_send)
        val checkBox: CheckBox = itemView.findViewById(R.id.check_recent_send)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.transaction_friends_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val datum = dataset[position]
        holder.checkBox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) selected.add(datum) else selected.remove(datum)
        }
        holder.itemView.setOnClickListener {
            val fragment = FragmentInputAmount()
            val bundle = Bundle()
            bundle.putString("data", Gson().toJson(arrayListOf(datum)))
            fragment.arguments = bundle
            (activity as AppCompatActivity).supportFragmentManager.beginTransaction().addToBackStack("input")
                .setCustomAnimations(R.anim.enter_right_to_left, R.anim.exit_right_to_left, R.anim.enter_left_to_right, R.anim.exit_left_to_right)
                .replace(R.id.send_money_root, fragment).commit()
        }
    }

    override fun getItemCount() = dataset.size
}

class SearchUserAdapter : RecyclerView.Adapter<SearchUserAdapter.ViewHolder>() {
    private lateinit var context: Context
    lateinit var activity: Activity
    lateinit var touchHelper: TouchHelper
    var dataset: ArrayList<UserProfile> = arrayListOf()

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val name: TextView = itemView.findViewById(R.id.transfer_name)
        val phone: TextView = itemView.findViewById(R.id.transfer_phone)
        val contactCard: CardView = itemView.findViewById(R.id.contact_card)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        context = parent.context
        val view = LayoutInflater.from(context).inflate(R.layout.row_hf_transfer_contact_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val datum = dataset[position]

        // SET DATA.
        holder.name.text = datum.name
        holder.itemView.setOnClickListener {
            val fragment = FragmentInputAmount()
            val bundle = Bundle()
            bundle.putString("data", Gson().toJson(datum))
            fragment.arguments = bundle
            (activity as AppCompatActivity).supportFragmentManager.beginTransaction()
                .addToBackStack("input")
                .setCustomAnimations(R.anim.enter_right_to_left, R.anim.exit_right_to_left, R.anim.enter_left_to_right, R.anim.exit_left_to_right)
                .replace(R.id.send_money_root, fragment).commit()
        }
    }

    override fun getItemCount() = dataset.size
}