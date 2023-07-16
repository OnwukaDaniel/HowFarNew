package com.azur.howfar.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class KeypadViewModel: ViewModel() {
    var value = MutableLiveData<String>()
    fun setValue(input: String){
        value.value = input
    }
}