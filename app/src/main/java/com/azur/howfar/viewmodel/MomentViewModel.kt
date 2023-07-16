package com.azur.howfar.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.azur.howfar.models.Moment

class MomentViewModel : ViewModel() {
    var momentList = MutableLiveData<ArrayList<Moment>>()
    fun setMomentList(input: ArrayList<Moment>) {
        momentList.value = input
    }
}