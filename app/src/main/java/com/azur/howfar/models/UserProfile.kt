package com.azur.howfar.models

data class UserProfile(
    var name: String = "",
    var phone: String = "",
    var countryCode: String = "",
    var uid: String = "",
    var age: String = "",
    var bio: String = "",
    var email: String = "",
    var image: String = "",
    var serverTimeFetchHelper: String = "",
    var gender: String = "",
    var isAdmin: Boolean = false,
    var isSuperAdmin: Boolean = false,
)