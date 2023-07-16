package com.azur.howfar.viewmodel

import android.net.Uri
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class VideoDialogViewModel : ViewModel() {
    val video = MutableLiveData<Pair<String, Uri>>()
    val videoUploadProgress = MutableLiveData<Int>()
    val sendVideo = MutableLiveData<Pair<Boolean, Uri>>()

    fun setVideoData(input: Pair<String, Uri>) {
        video.value = input
    }

    fun setSendVideo(input: Pair<Boolean, Uri>) {
        sendVideo.value = input
    }

    fun setUploadVideoProgress(input: Int) {
        videoUploadProgress.value = input
    }
}