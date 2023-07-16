package com.azur.howfar.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.azur.howfar.models.GroupProfileData

class GroupProfileViewModel: ViewModel() {
    var groupProfile = MutableLiveData<GroupProfileData>()
    fun setGroupProfile(input: GroupProfileData){
        groupProfile.value = input
    }
}