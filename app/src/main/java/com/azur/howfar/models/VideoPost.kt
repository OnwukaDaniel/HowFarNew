package com.azur.howfar.models

data class VideoPost(
    var caption: String= "",
    var timePosted: String = "",
    var videoUrl: String= "",
    var profileImage: String= "",
    var profileName: String= "",
    var creatorUid: String= "",
    var hashTags: List<String> = listOf(),
)
