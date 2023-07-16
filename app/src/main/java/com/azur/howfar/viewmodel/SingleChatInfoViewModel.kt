package com.azur.howfar.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.azur.howfar.models.ChatData

class SingleChatInfoViewModel : ViewModel() {
    var chatData = MutableLiveData<ChatData>()
    fun setChatData(input: ChatData) {
        chatData.value = input
    }
}