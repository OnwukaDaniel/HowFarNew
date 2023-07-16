package com.azur.howfar.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class UploadPostSpecialViewModel : ViewModel() {
    val images = MutableLiveData<ArrayList<String>>()
    val imagesStringList = MutableLiveData<ArrayList<String>>()

    fun setImagesData(input: ArrayList<String>) {
        images.value = input
    }

    fun setStringImagesData(input: ArrayList<String>) {
        imagesStringList.value = input
    }
}