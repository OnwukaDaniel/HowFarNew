package com.azur.howfar.models

data class VfdBvnDobResponse(
    val message: String,
    val status: String,
    val data: DataBvnVfd = DataBvnVfd()
)

data class DataBvnVfd(
    val accountNumber: String = "",
    val accountName: String = ""
)


data class VfdAccountDetailsResponse(
    val message: String = "",
    val status: String = "",
    val data: VFDAccountDetails = VFDAccountDetails()
)

data class VFDAccountDetails(
    val accountNumber: String = "",
    val accountName: String = "",
    val bankName: String = "",
)

data class VFDTransferInitData(
    val accountNumber: String = "",
    val bankCode: String = "",
    val amount: String = "",
)


data class VfdTransferInitResponse(
    val message: String = "",
    val status: String = "",
    val data: VFDInitData = VFDInitData()
)

data class VfdTransferHistory(
    val message: String = "",
    val status: String = "",
    val data: ArrayList<VFDHistory> = arrayListOf()
)

data class VFDHistory(
    val user_id: String = "",
    val details: String = "",
    val amount: String = "",
    val type: String = "",
    val reference: String = "",
    val date: String = "",
)

data class VFDInitData(
    val reference: String = "",
)

data class VfdReference(
    var reference: String = ""
)