package com.azur.howfar.models

data class PushNotification(
    val title: String = "",
    val body: String,
    val data: String = "",
    val channelId: String = "General",
    val channelName: String = "General",
    val priority: String = "HIGH",
    val imageUrl: String = "",
    val senderId: String,
    var receiverIds: ArrayList<String>,
    val view: String = "",
    val timeStamp: String = System.currentTimeMillis().toString(),
)
