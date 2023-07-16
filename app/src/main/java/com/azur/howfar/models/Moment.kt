package com.azur.howfar.models

data class Moment(
    var caption: String= "",
    var timePosted: String = "",
    var image: String= "",
    var images: ArrayList<String> = arrayListOf(),
    var profileImage: String= "",
    var profileName: String= "",
    var creatorUid: String= "",
    var profilePhone: String= "",
    var allowedComment: Boolean = true,
    var seen: Boolean = false,
    var hashTags: List<String> = listOf(),
    var privacy: Int= MomentPrivacy.PUBLIC,
)

data class StatsMoment(
    var moment: Moment= Moment(),
    var comments: Int = 0,
    var likes: Int = 0,
    var loved: Int = 0,
)

object MomentPrivacy{
    const val PUBLIC = 0
    const val FOLLOWERS_ONLY = 1
    const val ME = 0
}