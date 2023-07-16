package com.azur.howfar.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class FloatViewModel: ViewModel() {
    var float = MutableLiveData<Float>()
    fun setFloatValue(input: Float){
        float.value = input
    }
}