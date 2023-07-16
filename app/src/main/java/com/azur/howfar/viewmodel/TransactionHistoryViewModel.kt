package com.azur.howfar.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.azur.howfar.models.TransactionDisplayData
import com.azur.howfar.models.UserProfile

class TransactionHistoryViewModel : ViewModel() {
    val transactionDisplayData = MutableLiveData(ArrayList<TransactionDisplayData>())
    val userProfileList = MutableLiveData(ArrayList<UserProfile>())
    fun setTransactionDisplayData(input: ArrayList<TransactionDisplayData>) {
        transactionDisplayData.value = input
    }
    fun setUserProfile(input: ArrayList<UserProfile>) {
        userProfileList.value = input
    }
}