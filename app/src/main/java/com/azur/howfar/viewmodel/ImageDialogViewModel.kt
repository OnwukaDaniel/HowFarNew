package com.azur.howfar.viewmodel

import android.net.Uri
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class ImageDialogViewModel : ViewModel() {
    val imageName = MutableLiveData("")
    fun setImageName(input: String) {
        imageName.value = input
    }

    val send = MutableLiveData<Uri>()
    fun setSendImage(input: Uri) {
        send.value = input
    }

    val imageUri = MutableLiveData<Uri>()
    fun setImageUri(input: Uri) {
        imageUri.value = input
    }
}