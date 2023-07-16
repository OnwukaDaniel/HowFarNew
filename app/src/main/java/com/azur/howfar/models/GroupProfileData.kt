package com.azur.howfar.models

data class GroupProfileData(
    var groupName: String = "",
    var groupImage: String = "",
    var timeCreated: String = "",
    var uuid: String = "",
    var admins: ArrayList<String> = arrayListOf(),
    var creatorProfileLink: String = "",
    var members: ArrayList<String> = arrayListOf(),
)
