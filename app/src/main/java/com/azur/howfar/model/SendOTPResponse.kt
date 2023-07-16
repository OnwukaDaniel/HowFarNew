package com.azur.howfar.model

data class SendOTPResponse (
    val success: Boolean,
    val data: SendOTPResponseData?
)

data class SendOTPResponseData (
    val message: String,
)