package com.azur.howfar.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.azur.howfar.models.ChatData

class ChatDataViewModel : ViewModel() {
    var phoneData= MutableLiveData<Pair<String, String>>()
    fun setNamePhone(input: Pair<String, String>) {
        phoneData.value = input
    }
    var chatData = MutableLiveData<ArrayList<ChatData>>()
    fun setChatData(input: ArrayList<ChatData>){
        chatData.value = input
    }
}