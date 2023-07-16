package com.azur.howfar.viewmodel

import android.net.Uri
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class DocumentDialogViewModel : ViewModel() {
    val documentx = MutableLiveData<Pair<String, Uri>>()
    val document = MutableLiveData<Pair<Boolean, Uri>>()
    //fun setDocument(input: Pair<String, Uri>) {
    //    document.value = input
    //}

    fun setSendDocument(input: Pair<Boolean, Uri>) {
        document.value = input
    }
}