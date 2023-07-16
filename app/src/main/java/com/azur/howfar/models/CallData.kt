package com.azur.howfar.models

data class CallData(
    var callerUid: String = "",
    var receiverUid: String = "",
    var timeCalled: String = "",
    var timeSpent: String = "",
    var channelName: String = "",
    var engagementType: Int = CallEngagementType.CREATE,
    var uids: ArrayList<String> = arrayListOf(),
    var callType: Int = CallType.VIDEO,
    var answerType: Int = CallAnswerType.AWAITING,
)

object CallType {
    const val VOICE = 0
    const val VIDEO = 1
}

object CallEngagementType {
    const val JOIN = 0
    const val CREATE = 1
}

object CallAnswerType {
    const val ANSWERED = 0
    const val NO_RESPONSE = 1
    const val AWAITING = 2
    const val CANCELLED = 3
    const val ENDED = 4
    const val RECEIVED = 5
    const val MISSED = 7
}