package com.azur.howfar.viewmodel

import android.net.Uri
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class AudioDialogViewModel : ViewModel() {
    val audio = MutableLiveData<Pair<String, Uri>>()
    val sendAudio = MutableLiveData<Pair<Boolean, Uri>>()
    fun setAudioData(input: Pair<String, Uri>) {
        audio.value = input
    }

    fun setSendAudio(input: Pair<Boolean, Uri>) {
        sendAudio.value = input
    }
}