package com.azur.howfar.models

data class UserSignUp (
    var name: String = "",
    var phone: String = "",
    var countryCode: String = "",
    var verificationCode: String = "",
    var photo: String = "",
    var gender: String = "",
)
