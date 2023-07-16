package com.azur.howfar.model

import com.fasterxml.jackson.annotation.JsonProperty

data class EmailAuthSignUp(
    val success: Boolean,
    val data: DataAuthSignUp,
)

data class DataAuthSignUp(
    val user: UserAuthSignUp,
    val message: String,
)

data class UserAuthSignUp(
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
    @JsonProperty("updated_at")
    val updatedAt: String,
    @JsonProperty("created_at")
    val createdAt: String,
    val id: Long,
)
