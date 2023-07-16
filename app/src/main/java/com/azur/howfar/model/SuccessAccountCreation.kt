package com.azur.howfar.model

data class SuccessAccountCreation(
    val success: Boolean,
    val data: SuccessAccountCreationData?,
)

data class SuccessAccountCreationData(
    val data: SuccessAccountCreationDataData?,
)

data class SuccessAccountCreationDataData(
    val token: String?,
    val data: SuccessAccountCreationDataDataData?,
)

data class SuccessAccountCreationDataDataData(
    val success: Boolean,
    val message: String?,
)

data class SuccessAccountCreationPhone(
    val success: Boolean,
    val data: SuccessAccountCreationDataPhone?,
)

data class SuccessAccountCreationDataPhone(
    val token: String?,
    val status: String?,
)