package com.azur.howfar.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.azur.howfar.models.VideoPost

class VideoPostsViewModel : ViewModel() {
    var videoPostList = MutableLiveData<ArrayList<VideoPost>>()
    var videoPost = MutableLiveData<VideoPost>()
    var downloadPost = MutableLiveData<VideoPost>()
    fun setVideoPostListList(input: ArrayList<VideoPost>) {
        videoPostList.value = input
    }
    fun setVideoPost(input: VideoPost) {
        videoPost.value = input
    }

    fun downLoadVideo(input: VideoPost){
        downloadPost.value = input
    }
}