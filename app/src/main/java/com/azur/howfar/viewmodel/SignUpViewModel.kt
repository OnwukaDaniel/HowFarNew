package com.azur.howfar.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.azur.howfar.model.UserSignUpModel
import com.azur.howfar.models.UserSignUp

class SignUpViewModel : ViewModel() {
    var startCountDown = MutableLiveData(false)
    var image = MutableLiveData("")
    var userSignUpModel = MutableLiveData<UserSignUpModel>()
    var userSignUp = MutableLiveData(UserSignUp())
    fun setUserSignUp(input: UserSignUp) {
        userSignUp.value = input
    }

    fun setStartCountdown(input: Boolean) {
        startCountDown.value = input
    }

    fun setImage(input: String) {
        image.value = input
    }

    fun setUserSignUpModel(input: UserSignUpModel) {
        userSignUpModel.value = input
    }
}