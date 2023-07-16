package com.azur.howfar.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.azur.howfar.howfarchat.groupChat.Contact
import com.azur.howfar.models.GroupProfileData
import com.azur.howfar.models.UserProfile
import java.io.ByteArrayInputStream

class ContactViewModel : ViewModel() {
    var userProfile = MutableLiveData<UserProfile>()
    var userprofileList = MutableLiveData<ArrayList<UserProfile>>()
    var contacts = MutableLiveData<ArrayList<Contact>>()
    var imagePair = MutableLiveData<Pair<ByteArrayInputStream, ByteArray>>()
    var groupProfile = MutableLiveData<GroupProfileData>()
    var userprofile = MutableLiveData<UserProfile>()
    var userContact = MutableLiveData<Contact>()

    fun setUserProfile(input: UserProfile) {
        userProfile.value = input
    }
    fun setUserProfiles(input: ArrayList<UserProfile>) {
        userprofileList.value = input
    }
    fun setContacts(input: ArrayList<Contact>) {
        contacts.value = input
    }


    fun setUserContact(input: Contact){
        userContact.value = input
    }

    fun setGroupData(input: GroupProfileData) {
        groupProfile.value = input
    }

    fun setImagePair(input: Pair<ByteArrayInputStream, ByteArray>) {
        imagePair.value = input
    }
}