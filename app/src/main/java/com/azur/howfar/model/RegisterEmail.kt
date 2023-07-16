package com.azur.howfar.model

data class RegisterEmail(
    var name: String = "",
    var phone: String = "",
    var countryCode: String = "",
    var gender: String = "",
    var email: String = "",
    var isAdmin: Int = 0,
    var isSuperAdmin: Int = 0
)