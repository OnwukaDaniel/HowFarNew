package com.azur.howfar.models

data class TransactionDisplayData(
    var recipientUid: String = "",
    var datetime: String = "",
    var quantity: String = "",
    var item: String = "",
    var transactionType: Int = 0,
)
