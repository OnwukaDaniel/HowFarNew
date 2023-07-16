package com.azur.howfar.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class BooleanViewModel: ViewModel() {
    var switch = MutableLiveData<Boolean>()
    fun setSwitch(input: Boolean){
        switch.value = input
    }
    var stopLoadingShimmer = MutableLiveData<Boolean>()
    fun setStopLoadingShimmer(input: Boolean){
        stopLoadingShimmer.value = input
    }
}