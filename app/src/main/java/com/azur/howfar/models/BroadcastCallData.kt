package com.azur.howfar.models

data class BroadcastCallData(
    var callerUid: String = "",
    var timeCalled: String = "",
    var timeSpent: String = "",
    var channelName: String = "",
    var engagementType: Int = CallEngagementType.CREATE,
    var uids: ArrayList<String> = arrayListOf(),
    var comments: ArrayList<BroadcastCommentData> = arrayListOf(),
    var callType: Int = CallType.VIDEO,
    var index: Int= 0,
    var senderTempData: ParticipantTempData = ParticipantTempData(),
    var isPrivate: Boolean= false,
    var notificationDelivered: Boolean= false,
    var answerType: Int = CallAnswerType.AWAITING,
)

data class BroadcastCommentData(
    var uid: String = "",
    var user: UserProfile = UserProfile(),
    var comment: String = "",
    var timeSent: String = "",
    var isJoined: Boolean = false,
)