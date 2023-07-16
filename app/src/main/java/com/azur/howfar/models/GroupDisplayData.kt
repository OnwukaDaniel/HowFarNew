package com.azur.howfar.models

data class GroupDisplayData(
    var groupName: String = "",
    var groupImage: String = "",
    var timeCreated: String = "",
    var recent_msg: String = "",
    var recentTime: String = "",
    var uuid: String = "",

    var phoneData: PhoneData = PhoneData(),
    var imageData: ImageData = ImageData(),
    var audioData: AudioData = AudioData(),
    var videoData: VideoData = VideoData(),
)
