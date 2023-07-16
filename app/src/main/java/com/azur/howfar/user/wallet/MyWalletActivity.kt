package com.azur.howfar.user.wallet

import android.graphics.Color
import android.os.Bundle
import androidx.activity.viewModels
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.azur.howfar.R
import com.azur.howfar.activity.BaseActivity
import com.azur.howfar.databinding.ActivityMyWalletBinding
import com.azur.howfar.livedata.ValueEventLiveData
import com.azur.howfar.models.Currency
import com.azur.howfar.models.EventListenerType.onDataChange
import com.azur.howfar.models.TransactionDisplayData
import com.azur.howfar.models.TransactionType
import com.azur.howfar.models.UserProfile
import com.azur.howfar.utils.HFCoinUtils
import com.azur.howfar.viewmodel.FloatViewModel
import com.azur.howfar.viewmodel.TransactionHistoryViewModel
import com.azur.howfar.viewmodel.UserProfileViewmodel
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.gson.Gson

class MyWalletActivity : BaseActivity() {
    private val binding by lazy { ActivityMyWalletBinding.inflate(layoutInflater) }
    private lateinit var valueEventLiveData: ValueEventLiveData
    private val auth = FirebaseAuth.getInstance().currentUser
    private val userProfileViewmodel: UserProfileViewmodel by viewModels()
    private val floatViewModel: FloatViewModel by viewModels()
    private val transactionHistoryViewModel: TransactionHistoryViewModel by viewModels()
    private lateinit var tranHisLiveData: ValueEventLiveData
    private lateinit var usersLiveData: ValueEventLiveData
    private val userDetailsList: ArrayList<UserProfile> = arrayListOf()
    private val transactionData: ArrayList<TransactionDisplayData> = arrayListOf()
    private var historyRef = FirebaseDatabase.getInstance().reference
    private lateinit var wallerTabAdapter: WallerTabAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        historyRef = FirebaseDatabase.getInstance().reference.child(TRANSFER_HISTORY).child(auth!!.uid)
        val usersRef = FirebaseDatabase.getInstance().reference.child(USER_DETAILS)
        wallerTabAdapter = WallerTabAdapter(this)
        tranHisLiveData = ValueEventLiveData(historyRef)
        usersLiveData = ValueEventLiveData(usersRef)
        tranHisLiveData.observe(this) {
            when (it.second) {
                onDataChange -> {
                    val json = Gson().toJson((it.first.value as HashMap<*, *>).values)
                    val list = Gson().fromJson(json, ArrayList::class.java)
                    for (i in list) {
                        val dataCurrency: Currency = Gson().fromJson(Gson().toJson(i), Currency::class.java)
                        val data = TransactionDisplayData(
                            recipientUid = if (dataCurrency.transactionType == TransactionType.SENT) dataCurrency.receiverUid else dataCurrency.senderUid,
                            datetime = dataCurrency.timeOfTransaction,
                            quantity = dataCurrency.hfcoin.toString(),
                            item = "HFCoin",
                            transactionType = dataCurrency.transactionType
                        )
                        if (data !in transactionData) transactionData.add(data)
                    }
                    transactionHistoryViewModel.setTransactionDisplayData(transactionData)
                }
            }
        }
        usersLiveData.observe(this) {
            when (it.second) {
                onDataChange -> {
                    val json = Gson().toJson((it.first.value as HashMap<*, *>).values)
                    val list = Gson().fromJson(json, ArrayList::class.java)
                    for (i in list) {
                        val userProfile: UserProfile = Gson().fromJson(Gson().toJson(i), UserProfile::class.java)
                        if (i !in userDetailsList) {
                            userDetailsList.add(userProfile)
                            transactionHistoryViewModel.setUserProfile(userDetailsList)
                        }
                    }
                }
            }
        }
        balance()
        initFirebase()
        setViewPager()
        listenForIntent()
    }

    private fun listenForIntent() {
        if (intent.hasExtra(WalletNavigation.TAG)){
            when(intent.getIntExtra(WalletNavigation.TAG, 0)){
                WalletNavigation.RechargeFragment-> binding.viewPager.currentItem =0
                WalletNavigation.HCoinFragment-> binding.viewPager.currentItem = 1
                WalletNavigation.RecordFragment-> binding.viewPager.currentItem =2
            }
        }
    }

    override fun onResume() {
        window.statusBarColor = resources.getColor(R.color.purple_blue)
        super.onResume()
    }

    override fun onDestroy() {
        window.statusBarColor = resources.getColor(R.color.colorPrimary)
        super.onDestroy()
    }

    private fun balance() {
        ValueEventLiveData(historyRef).observe(this) {
            when (it.second) {
                onDataChange -> {
                    var available = HFCoinUtils.checkBalance(it.first)
                    floatViewModel.setFloatValue(available)
                }
            }
        }
    }

    inner class WallerTabAdapter(fa: FragmentActivity) : FragmentStateAdapter(fa) {
        lateinit var dataset: ArrayList<Fragment>
        override fun getItemCount(): Int = dataset.size
        override fun createFragment(position: Int): Fragment {
            return dataset[position]
        }
    }

    private fun setViewPager() {
        val tabsText = arrayListOf("Recharge", "Income", "Record")
        wallerTabAdapter.dataset = arrayListOf(RechargeFragment(), HCoinFragment(), RecordFragment())
        binding.tablayout1.setBackgroundColor(Color.TRANSPARENT)
        binding.viewPager.adapter = wallerTabAdapter
        binding.viewPager.offscreenPageLimit = 3
        TabLayoutMediator(binding.tablayout1, binding.viewPager) { tabs, position -> tabs.text = tabsText[position] }.attach()
    }

    private fun initFirebase() {
        if (auth == null) return
        val ref = FirebaseDatabase.getInstance().reference.child("user_details").child(auth.uid)
        valueEventLiveData = ValueEventLiveData(ref)
        valueEventLiveData.observe(this) {
            if (it.second == onDataChange) {
                val data = it.first.getValue(UserProfile::class.java)!!
                userProfileViewmodel.setUserProfile(data)
            }
        }
    }

    companion object {
        val TRANSFER_HISTORY = "user_coins_transfer"
        val USER_DETAILS = "user_details"
    }
}

object WalletNavigation{
    const val TAG = "WalletNavigation"

    const val RechargeFragment= 0
    const val HCoinFragment= 1
    const val RecordFragment = 2
}