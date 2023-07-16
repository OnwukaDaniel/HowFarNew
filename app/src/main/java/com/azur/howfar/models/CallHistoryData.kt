package com.azur.howfar.models

data class CallHistoryData(
    var callTime: String = "",
    var uids: ArrayList<String> = arrayListOf(),
    var callType: Int = CallType.VIDEO,
    var engagementType: Int = CallEngagementType.CREATE,
    var answerType: Int = CallAnswerType.AWAITING,
)
