package com.azur.howfar.models

data class MomentDetails(
    var time: String = "",
    var timeMomentPosted: String = "",
    var localTime: String = "",
    var likes: MomentLike = MomentLike(),
    var loves: MomentLove = MomentLove(),
    var comment: MomentComment = MomentComment(),
    var creatorSean: Boolean = false,
)

data class MomentLike(
    var profileUid: String = "",
    var profileName: String = "",
    var profilePhoto: String = "",
)

data class MomentLove(
    var profileUid: String = "",
    var profileName: String = "",
    var profilePhoto: String = "",
)

data class MomentComment(
    var profileUid: String = "",
    var profileName: String = "",
    var profilePhoto: String = "",
    var profileComment: String = "",
)