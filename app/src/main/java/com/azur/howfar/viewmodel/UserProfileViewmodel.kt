package com.azur.howfar.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.azur.howfar.models.UserProfile

class UserProfileViewmodel : ViewModel(){
    var userProfile = MutableLiveData(UserProfile())
    fun setUserProfile(input: UserProfile){
        userProfile.value = input
    }
}