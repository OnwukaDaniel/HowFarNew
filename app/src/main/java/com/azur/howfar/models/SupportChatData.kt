package com.azur.howfar.models

data class SupportChatData(
    var senderUid: String = "",
    var subject: String = "",
    var content: String = "",
    var images: ArrayList<String> = arrayListOf(),
    var replies: ArrayList<SupportChatData> = arrayListOf(),
    var time: String = "",
    var supportType: Int = SupportType.USER,
    var read: Boolean = false,
    var timeRead: String = "",
)

object SupportType{
    const val USER = 0
    const val ADMIN = 1
    const val WORKER= 2
}
