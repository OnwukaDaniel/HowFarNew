package com.azur.howfar.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class TimeStringViewModel: ViewModel() {
    var time = MutableLiveData<String>()
    fun setStringValue(input: String){
        time.value = input
    }
}