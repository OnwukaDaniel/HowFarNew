package com.azur.howfar.models

import com.azur.howfar.models.StatusDeliveryType.PENDING

data class StatusUpdateData(
    var serverTime: String = "",
    var storageLink: String = "",
    var statusType: Int = 0,
    var caption: String = "",
    var timeSent: String = "",
    var senderUid: String = "",
    var imageUri: String = "",
    var videoUri: String = "",
    var senderPhone: String = "",
    var isAdmin: Boolean = false,
    var captionBackgroundColor: String = "#660099",
    var statusDeliveryType: Int = PENDING,
)

object StatusUpdateType {
    const val TEXT = 0
    const val VIDEO = 1
    const val IMAGE = 2
}

object StatusDeliveryType {
    const val SENT = 0
    const val PENDING = 1
}