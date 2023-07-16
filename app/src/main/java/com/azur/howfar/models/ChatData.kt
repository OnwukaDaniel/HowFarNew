package com.azur.howfar.models

import com.azur.howfar.models.MessageType.TEXT

data class ChatData(
    var senderuid: String = "",
    var groupUid: String = "",
    var tempProfile: ParticipantTempData= ParticipantTempData(),
    var participants: ArrayList<String> = arrayListOf(),
    var participantsTempData: ArrayList<ParticipantTempData> = arrayListOf(),
    var timesent: String = "",
    var timeInitial: String = "",
    var timeseen: String = "",
    var myPhone: String = "",
    var timeddelivered: String = "",
    var sent: Boolean = false,
    var read: Boolean = false,
    var delivered: Boolean = false,
    var status: Int = ChatStatus.NEW,
    var msg: String = "",
    var replyFromStatus: Boolean = false,
    var quotedChatData: QuoteChatData = QuoteChatData(),
    var day: String = "",
    var uniqueQuerableTime: String = "",
    var displaytitle: String = "",
    var messagetype: Int = TEXT,
    var newMessages: Int = 0,
    var isAdmin: Boolean = false,
    var isSupport: Boolean = false,
    var phoneData: PhoneData = PhoneData(),
    var imageData: ImageData = ImageData(),
    var audioData: AudioData = AudioData(),
    var videoData: VideoData = VideoData(),
)

data class ParticipantTempData(
    var tempName: String = "",
    var uid: String = "",
    var tempImage: String = "",
    var phone: String = "",
)

data class QuoteChatData(
    var senderuid: String = "",
    var participants: ArrayList<String> = arrayListOf(),
    var participantsTempData: ArrayList<ParticipantTempData> = arrayListOf(),
    var timesent: String = "",
    var timeInitial: String = "",
    var timeseen: String = "",
    var myPhone: String = "",
    var timeddelivered: String = "",
    var sent: Boolean = false,
    var read: Boolean = false,
    var delivered: Boolean = false,
    var msg: String = "",
    var day: String = "",
    var uniqueQuerableTime: String = "",
    var displaytitle: String = "",
    var messagetype: Int = 0,
    var phoneData: PhoneData = PhoneData(),
    var imageData: ImageData = ImageData(),
    var audioData: AudioData = AudioData(),
    var videoData: VideoData = VideoData(),
)

data class GroupChatData(
    var senderuid: String = "",
    var timesent: String = "",
    var timeInitial: String = "",
    var timeseen: String = "",
    val timeCreated: String = "",
    var phone: String = "",
    var timeddelivered: String = "",
    var sent: Boolean = false,
    var read: Boolean = false,
    var newMessages: Int = 0,
    var quotedChatData: QuoteGroupChatData = QuoteGroupChatData(),
    var delivered: Boolean = false,
    var msg: String = "",
    var uuid: String = "",
    var day: String = "",
    var uniqueQuerableTime: String = "",
    var displaytitle: String = "",
    var messagetype: Int = 0,
    var phoneData: PhoneData = PhoneData(),
    var imageData: ImageData = ImageData(),
    var audioData: AudioData = AudioData(),
    var videoData: VideoData = VideoData(),
)

data class QuoteGroupChatData(
    var senderuid: String = "",
    var timesent: String = "",
    var timeInitial: String = "",
    var timeseen: String = "",
    var phone: String = "",
    var timeddelivered: String = "",
    var sent: Boolean = false,
    var read: Boolean = false,
    var delivered: Boolean = false,
    var msg: String = "",
    var uuid: String = "",
    var day: String = "",
    var uniqueQuerableTime: String = "",
    var displaytitle: String = "",
    var messagetype: Int = 0,
    var phoneData: PhoneData = PhoneData(),
    var imageData: ImageData = ImageData(),
    var audioData: AudioData = AudioData(),
    var videoData: VideoData = VideoData(),
)

object MessageType {
    const val TEXT = 0
    const val PHOTO = 1
    const val VIDEO = 2
    const val AUDIO = 3
    const val CONTACT = 4
    const val CHAT_DAY = 5
    const val ADDED_TO_GROUP = 6
    const val REMOVED_FROM_GROUP = 7
    const val LEAVE_GROUP = 8
    const val GROUP_ADMIN = 9
    const val CREATED_GROUP = 10
}

data class PhoneData(
    val number: String = "",
    val countryCode: String = "",
    val name: String = "",
)

data class ImageData(
    var storageLink: String = "",
    var localLink: String = "",
    var fileName: String = "",
    var fileSize: String = "",
    var displayMessage: String = "",
)

data class AudioData(
    var storageLink: String = "",
    var localLink: String = "",
    var fileName: String = "",
    var fileSize: String = "",
    var displayMessage: String = "",
)

data class VideoData(
    var storageLink: String = "",
    var localLink: String = "",
    var fileName: String = "",
    var fileSize: String = "",
    var displayMessage: String = "",
)

object ChatStatus {
    const val READ = 0
    const val SEEN = 1
    const val DELIVERED = 2
    const val NEW = 3
}