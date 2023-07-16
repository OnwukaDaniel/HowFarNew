package com.azur.howfar.utils

import com.azur.howfar.models.*

interface ChatClickHelper {
    fun sendOnProfileFetched(
        myProfile: UserProfile,
        myChat: ChatData,
        imageData: ImageData,
        audioData: AudioData,
        videoData: VideoData,
        phoneData: PhoneData
    )
}