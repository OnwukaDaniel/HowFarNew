package com.azur.howfar.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.azur.howfar.models.VFDTransferInitData

class VFDTransferInitViewModel: ViewModel() {
    var vFDTransferInitData = MutableLiveData<VFDTransferInitData>()
    fun setVFDTransferInitData(input: VFDTransferInitData){
        vFDTransferInitData.value = input
    }
}