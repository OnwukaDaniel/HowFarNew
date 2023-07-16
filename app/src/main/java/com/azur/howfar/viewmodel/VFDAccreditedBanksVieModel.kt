package com.azur.howfar.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.azur.howfar.howfarwallet.VFDAccreditedBanks
import com.azur.howfar.howfarwallet.VFDBanksList

class VFDAccreditedBanksVieModel : ViewModel() {
    var banks = MutableLiveData<VFDBanksList>()
    fun setBank(input: VFDBanksList) {
        banks.value = input
    }
}