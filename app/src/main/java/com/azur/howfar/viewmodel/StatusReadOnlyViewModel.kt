package com.azur.howfar.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.azur.howfar.models.StatusView

class StatusReadOnlyViewModel : ViewModel() {
    var statusViews = MutableLiveData<ArrayList<StatusView>>()

    fun setStatusViews(input: ArrayList<StatusView>) {
        statusViews.value = input
    }
}