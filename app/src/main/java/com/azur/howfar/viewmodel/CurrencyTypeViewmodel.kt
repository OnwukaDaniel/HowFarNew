package com.azur.howfar.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class CurrencyTypeViewmodel : ViewModel() {
    var currency = MutableLiveData(Currencies.HFCOIN)
    fun setCurrency(input: Int) {
        currency.value = input
    }
}

object Currencies {
    val HFCOIN: Int = 0
    val HFCENT: Int = 1
}