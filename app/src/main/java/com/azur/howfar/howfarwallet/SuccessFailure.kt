package com.azur.howfar.howfarwallet

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.azur.howfar.R
import com.azur.howfar.databinding.FragmentSuccessFailureBinding
import com.azur.howfar.utils.Util
import java.util.*

class SuccessFailure : AppCompatActivity() {
    private val binding by lazy { FragmentSuccessFailureBinding.inflate(layoutInflater) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        val amount = intent.getStringExtra("amount")!!
        when (intent.getBooleanExtra("isSuccess", false)) {
            true -> success(amount)
            false -> failure(amount)
        }
    }

    private fun success(amount: String) {
        binding.successScreen.visibility = View.VISIBLE
        binding.failureScreen.visibility = View.GONE
        val name = intent.getStringExtra("name")!!
        binding.otherName.text = name
        binding.amount.text = amount
        binding.date.text = Util.formatDate(Calendar.getInstance().timeInMillis.toString())
        binding.time.text = Util.formatDateTime(Calendar.getInstance().timeInMillis.toString())
    }

    private fun failure(amount: String) {
        binding.successScreen.visibility = View.GONE
        binding.failureScreen.visibility = View.VISIBLE
        val message = intent.getStringExtra("message")!!
        binding.message.text = message
        binding.amountF.text = amount
        binding.dateF.text = Util.formatDate(Calendar.getInstance().timeInMillis.toString())
        binding.timeF.text = Util.formatDateTime(Calendar.getInstance().timeInMillis.toString())
    }

    override fun onBackPressed() {
        startActivity(Intent(this, ActivityWallet::class.java).apply {
        })
        overridePendingTransition(R.anim.enter_right_to_left, R.anim.exit_right_to_left)
        super.onBackPressed()
    }
}