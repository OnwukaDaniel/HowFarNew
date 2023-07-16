package com.azur.howfar.user.wallet

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.azur.howfar.R
import com.azur.howfar.databinding.FragmentRechargeBinding
import com.azur.howfar.howfarchat.ChatLanding
import com.azur.howfar.models.CoinPlan
import com.azur.howfar.models.Currency
import com.azur.howfar.models.TransactionType
import com.azur.howfar.models.UserProfile
import com.azur.howfar.payment.FlutterWaveResponse
import com.azur.howfar.user.EditProfileActivity
import com.azur.howfar.viewmodel.FloatViewModel
import com.azur.howfar.viewmodel.UserProfileViewmodel
import com.flutterwave.raveandroid.RavePayActivity
import com.flutterwave.raveandroid.RaveUiManager
import com.flutterwave.raveandroid.rave_java_commons.RaveConstants
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.gson.Gson
import org.jetbrains.annotations.Nullable

class RechargeFragment : Fragment(), CoinPurchaseAdapter.OnCoinPlanClickListener {
    private lateinit var binding: FragmentRechargeBinding
    private var coinPurchaseAdapter = CoinPurchaseAdapter()
    private val myAuth = FirebaseAuth.getInstance().currentUser!!.uid
    private val floatViewModel: FloatViewModel by activityViewModels()
    private val userProfileViewmodel: UserProfileViewmodel by activityViewModels()
    private var userProfile = UserProfile()
    private lateinit var progressDialog: AlertDialog

    private var selectedPlan: CoinPlan? = null
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentRechargeBinding.inflate(inflater, container, false)
        initMain()
        return binding.root
    }

    private fun initMain() {
        val progress = AlertDialog.Builder(requireContext())
        progress.setView(R.layout.card_progress)
        progress.setCancelable(false)
        progressDialog = progress.create()
        userProfileViewmodel.userProfile.observe(viewLifecycleOwner) {
            userProfile = it
        }
        floatViewModel.float.observe(viewLifecycleOwner) { binding.tvHFCoin.text = it.toString() }
        val list = arrayListOf(
            CoinPlan(coin = 500, amount = 500, label = "# 500"),
            CoinPlan(coin = 1000, amount = 1000, label = "# 1000"),
            CoinPlan(coin = 2000, amount = 2000, label = "# 2000"),
            CoinPlan(coin = 3000, amount = 3000, label = "# 3000"),
            CoinPlan(coin = 5000, amount = 5000, label = "# 5000"),
            CoinPlan(coin = 100000, amount = 100000, label = "# 100000"),
            CoinPlan(coin = 200000, amount = 200000, label = "# 200000"),
            CoinPlan(coin = 500000, amount = 500000, label = "# 500000"),
        )
        coinPurchaseAdapter.onCoinPlanClickListener = this
        coinPurchaseAdapter.coinList = list
        binding.rvRecharge.adapter = coinPurchaseAdapter
    }

    override fun onPlanClick(coinPlan: CoinPlan) {
        progressDialog.show()
        val myAuth = FirebaseAuth.getInstance().currentUser!!.uid
        val myProfileRef = FirebaseDatabase.getInstance().reference.child(USER_DETAILS).child(myAuth)
        myProfileRef.get().addOnSuccessListener {
            if (it.exists()) {
                progressDialog.dismiss()
                val myProfile = it.getValue(UserProfile::class.java)!!
                when (myProfile.email) {
                    "" -> {
                        val alert = AlertDialog.Builder(requireContext())
                        alert.setTitle("Email receipt")
                        alert.setMessage("You don't have your email set up.\nSet up your email to get payment receipt.")
                        alert.setPositiveButton("Set Up Email") { dialog, _ ->
                            startActivity(Intent(requireContext(), EditProfileActivity::class.java))
                            dialog.dismiss()
                        }
                        alert.setNegativeButton("Continue without receipt") { dialog, _ ->
                            startFlutterWave(coinPlan, userProfile, "NGN")
                            dialog.dismiss()
                        }
                        alert.create().show()
                    }
                    else -> startFlutterWave(coinPlan, userProfile, "NGN")
                }
            }
        }.addOnFailureListener {
            progressDialog.dismiss()
        }
        selectedPlan = coinPlan
    }

    private fun startFlutterWave(coinPlan: CoinPlan, userProfile: UserProfile, currency: String){
        progressDialog.show()
        val key = FirebaseDatabase.getInstance("https://howfar-b24ef-overtime-change.firebaseio.com/").reference.child(FLUTTER_WAVE_KEY_REFERENCE)
        key.get().addOnSuccessListener {
            if (it.exists()){
                progressDialog.dismiss()
                val fData = it.getValue(FData::class.java)!!
                RaveUiManager(this).setAmount(/*5.0 */coinPlan.coin.toDouble())
                    .setCurrency(currency)
                    .setEmail(userProfile.email)
                    .setfName("HowFar user")
                    .setlName(userProfile.name)
                    .setNarration("Purchase HFCoin")
                    .setPublicKey(fData.publicKey)
                    .setEncryptionKey(fData.encryptionKey)
                    .setTxRef(System.currentTimeMillis().toString() + "Ref")
                    .setPhoneNumber(userProfile.phone, true)
                    .acceptAccountPayments(true)
                    .acceptCardPayments(true)
                    .acceptMpesaPayments(false)
                    .acceptAchPayments(true)
                    .acceptGHMobileMoneyPayments(true)
                    .acceptUgMobileMoneyPayments(true)
                    .acceptZmMobileMoneyPayments(true)
                    .acceptRwfMobileMoneyPayments(true)
                    .acceptSaBankPayments(true)
                    .acceptUkPayments(true)
                    .acceptBankTransferPayments(true)
                    .acceptUssdPayments(true)
                    .acceptBarterPayments(true)
                    .allowSaveCardFeature(true)
                    .onStagingEnv(fData.onStagingEnv)
                    .withTheme(R.style.Theme_HowFar)
                    .shouldDisplayFee(true)
                    .showStagingLabel(true)
                    .initialize()
            }
        }.addOnFailureListener {
            progressDialog.dismiss()
            Toast.makeText(requireContext(), "${it.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, @Nullable data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        println("onActivityResult ****************************************************** $resultCode")
        try{
            when (resultCode) {
                RavePayActivity.RESULT_SUCCESS -> {
                    if (requestCode == RaveConstants.RAVE_REQUEST_CODE && data != null) {
                        val message = data.getStringExtra("response")
                        val flutterWaveResponse = Gson().fromJson(message, FlutterWaveResponse::class.java)
                        val timeRef = FirebaseDatabase.getInstance().reference.child("time").child(myAuth)
                        if (resultCode == RavePayActivity.RESULT_SUCCESS) {
                            timeRef.setValue(ServerValue.TIMESTAMP).addOnSuccessListener {
                                timeRef.get().addOnSuccessListener { timeSnapshot ->
                                    if (timeSnapshot.exists()) {
                                        val timeSent = timeSnapshot.value.toString()
                                        val historyRef = FirebaseDatabase.getInstance().reference.child(ChatLanding.TRANSFER_HISTORY).child(myAuth).child(timeSent)
                                        val currency = Currency(
                                            timeOfTransaction = timeSent,
                                            senderUid = myAuth,
                                            receiverUid = myAuth,
                                            transactionType = TransactionType.BOUGHT,
                                            hfcoin = flutterWaveResponse.data.amount
                                        )
                                        historyRef.setValue(currency).addOnSuccessListener {
                                            Toast.makeText(requireContext(), "${flutterWaveResponse.data.chargeResponseMessage} SUCCESS ", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            }
                        } else if (resultCode == RavePayActivity.RESULT_ERROR) {
                            println("Flutterwave message ****************************************************** $flutterWaveResponse")
                            Toast.makeText(requireContext(), "FAILED ${flutterWaveResponse.message}", Toast.LENGTH_LONG).show()
                        } else if (resultCode == RavePayActivity.RESULT_CANCELLED) {
                            println("Flutterwave message ****************************************************** $flutterWaveResponse")
                            Toast.makeText(requireContext(), "CANCELLED ${flutterWaveResponse.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                RavePayActivity.RESULT_CANCELLED -> {}
                RavePayActivity.RESULT_ERROR -> {
                    Toast.makeText(requireContext(), "FAILED.", Toast.LENGTH_LONG).show()
                }
            }
        }catch(e :Exception){

        }
    }

    companion object {
        const val TRANSFER_HISTORY = "user_coins_transfer"
        const val USER_DETAILS = "user_details"
        const val CHAT_REFERENCE = "chat_reference"
        const val FLUTTER_WAVE_KEY_REFERENCE = "FLUTTER_WAVE_KEY_REFERENCE"
    }
}

data class FData(
    var encryptionKey: String = "",
    var onStagingEnv: Boolean = false,
    var publicKey: String = "",
)