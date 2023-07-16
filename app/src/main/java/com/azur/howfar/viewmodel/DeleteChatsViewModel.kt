package com.azur.howfar.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.azur.howfar.models.ChatData

class DeleteChatsViewModel : ViewModel() {
    var selectedChats = MutableLiveData<ArrayList<ChatData>>()
    var containsMedia = MutableLiveData(false)
    var dismissed = MutableLiveData<Boolean>()
    fun setSelectedChats(input: ArrayList<ChatData>) {
        selectedChats.value = input
    }

    fun setContainsMedia(input: Boolean) {
        containsMedia.value = input
    }

    fun setDismissed(input: Boolean){
        dismissed.value = input
    }
}