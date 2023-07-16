package com.azur.howfar.howfarchat.status

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.azur.howfar.R
import com.azur.howfar.models.StatusUpdateData
import com.azur.howfar.models.UserProfile
import com.azur.howfar.utils.Util
import com.azur.howfar.utils.Util.formatNumber
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.storage.FirebaseStorage
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class CreateStatusWorker(val context: Context, param: WorkerParameters) : Worker(context, param) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val myAuth = FirebaseAuth.getInstance().currentUser!!.uid
    private var contactFormattedList: java.util.ArrayList<String> = arrayListOf()
    private val registeredContactList: java.util.ArrayList<UserProfile> = arrayListOf()
    private lateinit var pref: SharedPreferences
    private var statusUpdateData = StatusUpdateData()
    private var timeRef = FirebaseDatabase.getInstance().reference
    private val allUsersRef = FirebaseDatabase.getInstance().reference.child(USER_DETAILS)

    override fun doWork(): Result {
        pref = context.getSharedPreferences(context.getString(R.string.ALL_PREFERENCE), Context.MODE_PRIVATE)
        val timeSent = inputData.getString("time_sent")
        if (timeSent == "") return Result.failure()
        val statusDataJson = pref.getString("status_time_sent $timeSent", "")
        statusUpdateData = Gson().fromJson(statusDataJson, StatusUpdateData::class.java)

        allUsersRef.get().addOnSuccessListener {
            if (it.exists()) {
                runBlocking {
                    scope.launch {
                        contactFormattedList = Util.getAllSavedContacts(context).second
                        for (i in it.children) {
                            val user = i.getValue(UserProfile::class.java)!!
                            if (formatNumber(user.phone) in contactFormattedList && user.uid != myAuth) registeredContactList.add(user)
                        }
                        when (statusUpdateData.statusType) {
                            StatusType.TEXT -> sendStatus(registeredContactList)
                            StatusType.IMAGE -> storeImage()
                        }
                    }
                }
            }
        }.addOnFailureListener {
            return@addOnFailureListener
        }
        return Result.success()
    }

    private fun sendStatus(registeredContact: ArrayList<UserProfile>) {
        timeRef = timeRef.child("time").child(myAuth)
        timeRef.setValue(ServerValue.TIMESTAMP).addOnSuccessListener {
            timeRef.get().addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    val rawTime = snapshot.value.toString()
                    statusUpdateData.serverTime = rawTime
                    FirebaseDatabase.getInstance().reference.child(USER_DETAILS).child(myAuth).get().addOnSuccessListener { profile_snap->
                        if (profile_snap.exists()){
                            val myProfile = profile_snap.getValue(UserProfile::class.java)!!
                            statusUpdateData.senderPhone = myProfile.phone
                            val myRef = FirebaseDatabase.getInstance().reference.child(STATUS_UPDATE).child(MY_STATUS).child(myAuth).child(rawTime)
                            myRef.setValue(statusUpdateData).addOnSuccessListener {}
                            if (registeredContact.isNotEmpty()) {
                                for (i in registeredContact) {
                                    val ref = FirebaseDatabase.getInstance().reference.child(STATUS_UPDATE)
                                        .child(OTHER_STATUS).child(i.uid).child(myAuth).child(rawTime)
                                    ref.setValue(statusUpdateData).addOnSuccessListener {}
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun storeImage() {
        val imageUri = statusUpdateData.imageUri

        val imageRef = FirebaseStorage.getInstance().reference.child(IMAGE_REFERENCE).child("statuses")
            .child(myAuth)
            .child(statusUpdateData.timeSent)
        val imageUploadTask = imageRef.putFile(Uri.parse(imageUri))
        imageUploadTask.continueWith { task ->
            if (!task.isSuccessful) task.exception?.let { it ->
                throw  it
            }
            imageRef.metadata.addOnSuccessListener { metadata ->
                imageRef.downloadUrl.addOnSuccessListener { uri ->
                    statusUpdateData.storageLink = uri.toString()
                    sendStatus(registeredContactList)
                }.addOnFailureListener {
                    return@addOnFailureListener
                }
            }
        }
    }

    companion object {
        const val OTHER_STATUS = "other_status"
        const val STATUS_UPDATE = "status_update"
        const val IMAGE_REFERENCE = "IMAGE_REFERENCE"
        const val MY_STATUS = "my_status"
        const val TRANSFER_HISTORY = "user_coins_transfer"
        const val USER_DETAILS = "user_details"
    }
}