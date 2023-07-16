package com.azur.howfar.model

data class UserSignUpModel(
    var name: String = "tester user3",
    var phone: String = "08141836706",
    var countryCode: String = "+234",
    var gender: String = "MALE",
    var email: String = "admin@howfar.com",
    var isAdmin: Int = 0,
    var isSuperAdmin: Int = 0
)