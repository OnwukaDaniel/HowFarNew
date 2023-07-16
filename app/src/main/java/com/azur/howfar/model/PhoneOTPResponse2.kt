package com.azur.howfar.model

data class PhoneOTPResponse2 (
    val success: Boolean,
    val error: PhoneOTPError2?
)
data class PhoneOTPError2 (
    val message: String?
)