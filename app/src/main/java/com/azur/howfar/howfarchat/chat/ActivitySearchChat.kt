package com.azur.howfar.howfarchat.chat

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.azur.howfar.R
import com.azur.howfar.activity.BaseActivity
import com.azur.howfar.databinding.ActivitySearchChatBinding
import com.azur.howfar.howfarchat.groupChat.Contact
import com.azur.howfar.models.UserProfile
import com.azur.howfar.user.wallet.TouchHelper
import com.azur.howfar.utils.CallUtils
import com.azur.howfar.utils.Util
import com.azur.howfar.viewmodel.UserProfileViewmodel
import com.google.firebase.database.FirebaseDatabase
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class ActivitySearchChat : BaseActivity(), TouchHelper {
    private val binding by lazy { ActivitySearchChatBinding.inflate(layoutInflater) }
    private val profileViewModel: UserProfileViewmodel by viewModels()
    private val adapter = SearchUserAdapter()
    private var contacts: ArrayList<Contact> = arrayListOf()
    private val allUsers: ArrayList<UserProfile> = arrayListOf()
    private var phoneList: ArrayList<String> = arrayListOf()
    private val scope = CoroutineScope(Dispatchers.IO)
    private lateinit var callUtils: CallUtils
    private val dataset: ArrayList<UserProfile> = arrayListOf()

    @SuppressLint("NotifyDataSetChanged")
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { permissions ->
        when (permissions) {
            true -> {
                runBlocking {
                    scope.launch {
                        val pair = Util.getAllSavedContacts(this@ActivitySearchChat)
                        contacts = pair.first
                        phoneList = pair.second
                        runOnUiThread { initFirebase() }
                    }
                }
            }
            false -> {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_DENIED) {
                    callUtils.permissionRationale(message = "HowFar needs CONTACTS permission to deliver best notification experience\nGrant app permission")
                }
            }
        }
    }

    private fun askPermission() {
        permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
    }

    inner class InputTextWatcher : TextWatcher {
        override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) = Unit
        override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {

        }

        override fun afterTextChanged(p0: Editable?) {
            searchUser(p0.toString().trim().lowercase())
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun searchUser(input: String) {
        clearAdapter()
        if (input != "") for (i in allUsers) {
            if (input in i.name.lowercase() || input in i.phone) dataset.add(i)
            adapter.notifyDataSetChanged()
        } else {
            dataset.clear()
            adapter.notifyDataSetChanged()
            for (i in allUsers) dataset.add(i)
        }
        adapter.dataset = dataset
        binding.searchRv.adapter = adapter
        adapter.notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun clearAdapter() {
        dataset.clear()
        adapter.notifyDataSetChanged()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        callUtils = CallUtils(this, this)
        adapter.activity = this
        adapter.dataset = dataset
        binding.searchRv.adapter = adapter
        adapter.touchHelper = this
        binding.searchRv.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        binding.userSearch.addTextChangedListener(InputTextWatcher())
        binding.searchBack.setOnClickListener { onBackPressed() }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_DENIED) askPermission()
        else runBlocking {
            scope.launch {
                val pair = Util.getAllSavedContacts(this@ActivitySearchChat)
                contacts = pair.first
                phoneList = pair.second
                runOnUiThread { initFirebase() }
            }
        }
    }

    private fun initFirebase() {
        val pref = getSharedPreferences(getString(R.string.ALL_PREFERENCE), Context.MODE_PRIVATE)
        val json = pref.getString(getString(R.string.this_user), "")
        val thisUser = Gson().fromJson(json, UserProfile::class.java)
        val ref = FirebaseDatabase.getInstance().reference.child(USER_DETAILS)
        ref.get().addOnSuccessListener {
            if (it.exists()) {
                for (i in it.children) {
                    hasData()
                    val userProfile = i.getValue(UserProfile::class.java)!!
                    if (userProfile.phone != thisUser.phone && userProfile !in allUsers) {
                        if (Util.formatNumber(userProfile.phone) in phoneList) {
                            allUsers.add(userProfile)
                            dataset.add(userProfile)
                            adapter.notifyItemInserted(dataset.size)
                        }
                    }
                }
            }
        }
    }

    private fun hasData() {
        binding.userProgress.visibility = View.INVISIBLE
    }

    override fun helpTouch(datum: UserProfile) {
        profileViewModel.setUserProfile(datum)
    }

    companion object {
        const val TRANSFER_HISTORY = "user_coins_transfer"
        const val USER_DETAILS = "user_details"
    }
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
        // CHANGE COLOR.
        //holder.contactCard.setCardBackgroundColor(Color.WHITE)
        //holder.name.setTextColor(Color.BLACK)
        //holder.phone.setTextColor(Color.BLACK)

        // SET DATA.
        holder.name.text = datum.name
        holder.phone.text = datum.phone
        holder.itemView.setOnClickListener {
            val intent = Intent(context, ChatActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            intent.putExtra("data", datum.uid)
            context.startActivity(intent)
            activity.overridePendingTransition(R.anim.enter_right_to_left, R.anim.exit_right_to_left)
        }
    }

    override fun getItemCount() = dataset.size
}