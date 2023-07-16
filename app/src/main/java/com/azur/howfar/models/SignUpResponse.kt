package com.azur.howfar.models

data class SignUpResponse(
    val message: String = "",
    val status: Long = 0L,
    val data: Data = Data(),
)

data class Data(
    val token: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val balance: String = "",
    val account_number: String = "",
    val account_name: String = "",
    val bank_name: String = "",
)