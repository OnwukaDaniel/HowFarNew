package com.azur.howfar.model

data class EmailAuthResponse (
    val status: Boolean,
    val message: String,
    val errors: Errors?
)

data class Errors (
    val name: List<String>,
    val email: List<String>,
    val countryCode: List<String>,
    val phone: List<String>,
    val gender: List<String>
)