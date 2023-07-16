package com.azur.howfar.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.azur.howfar.models.MomentDetails

class MomentDetailsViewModel : ViewModel() {
    var momentDetailsList = MutableLiveData<ArrayList<MomentDetails>>()
    fun setMomentList(input: ArrayList<MomentDetails>) {
        momentDetailsList.value = input
    }
}