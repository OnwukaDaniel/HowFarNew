package com.azur.howfar.models

data class Currency(
    var hfcoin: Float = 0F,
    var timeOfTransaction: String = "",
    var description: String = "",
    var transactionType: Int = 0,
    var senderUid: String = "",
    var receiverUid: String = "",
    var transactionSeen: Boolean = false,
)
