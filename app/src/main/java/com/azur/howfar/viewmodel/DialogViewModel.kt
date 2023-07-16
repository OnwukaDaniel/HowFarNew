package com.azur.howfar.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.azur.howfar.models.CallData

class DialogViewModel : ViewModel() {
    var cardColor = MutableLiveData<String>()
    var dialogMessage = MutableLiveData("")
    var callerName = MutableLiveData<String>()
    var dialogMode = MutableLiveData<Int>()
    var closeDialog = MutableLiveData<Boolean>()
    var disableBackPress = MutableLiveData(true)
    var hideProgress = MutableLiveData(false)
    var callData = MutableLiveData<CallData>()
    var callDirection = MutableLiveData<Int>()
    var progress = MutableLiveData<Int>()

    fun setDialogMessage(input: String) {
        dialogMessage.value = input
    }

    fun setDialogMode(input: Int) {
        dialogMode.value = input
    }

    fun setCloseFunction(input: Boolean) {
        closeDialog.value = input
    }

    fun setCardColor(input: String) {
        cardColor.value = input
    }

    fun setDisableBackPress(input: Boolean) {
        disableBackPress.value = input
    }

    fun setCallerName(input: String) {
        callerName.value = input
    }

    fun setCallData(input: CallData) {
        callData.value = input
    }

    fun setCallDirection(input: Int) {
        callDirection.value = input
    }

    fun setProgress(input: Int) {
        progress.value = input
    }

    fun setHideProgress(input: Boolean) {
        hideProgress.value = input
    }
}

object DialogMode {
    const val NORMAL_PROGRESS = 0
    const val CHAT_FETCH_PROGRESS = 1
}