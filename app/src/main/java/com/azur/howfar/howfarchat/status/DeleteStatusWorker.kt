package com.azur.howfar.howfarchat.status

import android.content.Context
import android.content.SharedPreferences
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.azur.howfar.R
import com.azur.howfar.models.StatusUpdateData
import com.azur.howfar.models.UserProfile
import com.azur.howfar.utils.Util
import com.azur.howfar.utils.Util.formatNumber
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class DeleteStatusWorker(val context: Context, param: WorkerParameters) : Worker(context, param) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val myAuth = FirebaseAuth.getInstance().currentUser!!.uid
    private var contactFormattedList: java.util.ArrayList<String> = arrayListOf()
    private val registeredContactList: java.util.ArrayList<UserProfile> = arrayListOf()
    private lateinit var pref: SharedPreferences
    private var statusUpdateData = StatusUpdateData()
    private var selectedData = arrayListOf<StatusUpdateData>()
    private val allUsersRef = FirebaseDatabase.getInstance().reference.child(USER_DETAILS)

    override fun doWork(): Result {
        pref = context.getSharedPreferences(context.getString(R.string.ALL_PREFERENCE), Context.MODE_PRIVATE)
        val timeSent = inputData.getString("time_sent")
        if (timeSent == "") return Result.failure()
        val statusDataJson = pref.getString("status_time_sent $timeSent", "")
        val lst = Gson().fromJson(statusDataJson, ArrayList::class.java)
        for (i in lst) selectedData.add(Gson().fromJson(Gson().toJson(i), StatusUpdateData::class.java))

        allUsersRef.get().addOnSuccessListener {
            if (it.exists()) {
                runBlocking {
                    scope.launch {
                        contactFormattedList = Util.getAllSavedContacts(context).second
                        for (i in it.children) {
                            val user = i.getValue(UserProfile::class.java)!!
                            if (formatNumber(user.phone) in contactFormattedList && user.uid != myAuth) registeredContactList.add(user)
                        }
                        for (i in selectedData) {
                            FirebaseDatabase.getInstance().reference.child(STATUS_UPDATE).child(MY_STATUS).child(myAuth).child(i.serverTime).removeValue()
                            for (x in registeredContactList) {
                                FirebaseDatabase.getInstance().reference.child(STATUS_UPDATE).child(OTHER_STATUS).child(x.uid).child(myAuth)
                                    .child(i.serverTime).removeValue()
                            }
                        }
                    }
                }
            }
        }.addOnFailureListener {
            return@addOnFailureListener
        }
        return Result.success()
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