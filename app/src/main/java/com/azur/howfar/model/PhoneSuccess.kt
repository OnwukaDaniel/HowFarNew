package com.azur.howfar.model

data class PhoneSuccess(
    val success: Boolean,
    val data: Data,
)

data class Data(
    val user: User,
    val message: String,
)

data class User(
    val uid: Map<String, Any>,
    val name: String,
    val email: String,
    val countryCode: String,
    val phone: String,
    val age: Any?,
    val bio: Any?,
    val image: Any?,
    val serverTimeFetchHelper: Any?,
    val gender: String,
    val isAdmin: Long,
    val isSuperAdmin: Long,
    val updated_at: String,
    val updatedAt: String,
    val created_at: String,
    val createdAt: String,
    val id: Long,
)