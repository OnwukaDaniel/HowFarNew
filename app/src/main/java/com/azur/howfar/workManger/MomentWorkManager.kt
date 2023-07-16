package com.azur.howfar.workManger

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.widget.Toast
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.azur.howfar.R
import com.azur.howfar.models.Moment
import com.azur.howfar.models.UserProfile
import com.azur.howfar.utils.ImageCompressor
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.storage.FirebaseStorage
import com.google.gson.Gson
import java.io.ByteArrayInputStream

class MomentWorkManager(private val context: Context, private val params: WorkerParameters) : Worker(context, params) {
    private lateinit var pref: SharedPreferences
    private var user = FirebaseAuth.getInstance().currentUser
    private var timeRef = FirebaseDatabase.getInstance().reference
    private var moment = Moment()
    private var EDITED = false

    override fun doWork(): Result {
        pref = context.getSharedPreferences(context.getString(R.string.ALL_PREFERENCE), Context.MODE_PRIVATE)
        val json = pref.getString(context.getString(R.string.moment_data), "")
        moment = Gson().fromJson(json, Moment::class.java)
        EDITED = params.inputData.getBoolean("edited", false)
        when {
            moment.images.isEmpty() -> uploadMoment()
            else -> storeImage()
        }
        return Result.success()
    }

    private fun storeImage() {
        timeRef = FirebaseDatabase.getInstance().reference.child("time").child(user!!.uid)
        timeRef.setValue(ServerValue.TIMESTAMP).addOnSuccessListener {
            timeRef.get().addOnSuccessListener { timeSnapshot ->
                if (timeSnapshot.exists()) {
                    var timeSent = timeSnapshot.value.toString()
                    if (!EDITED) moment.timePosted = timeSent
                    var unChanged = 0
                    var successCount = 0
                    for ((index, i) in moment.images.withIndex()) {
                        if (i.contains("https:")) {
                            unChanged++
                            continue
                        }
                        val imageRef = FirebaseStorage.getInstance().reference.child(MOMENT_IMAGES).child(user!!.uid).child(timeSent)
                        val imageUploadTask = imageRef.putFile(Uri.parse(i))
                        timeSent += 1000
                        imageUploadTask.continueWith { task ->
                            if (!task.isSuccessful) task.exception?.let { it ->
                                throw  it
                            }
                            imageRef.metadata.addOnSuccessListener {
                                imageRef.downloadUrl.addOnSuccessListener { uri ->
                                    moment.images[index] = uri.toString()
                                    successCount++
                                    if (successCount == moment.images.size) upload()
                                }.addOnFailureListener {
                                    Toast.makeText(context, "Moment upload failed!!! Retry", Toast.LENGTH_LONG).show()
                                    return@addOnFailureListener
                                }
                            }
                        }
                    }
                    if (unChanged == moment.images.size - 1) uploadMoment()
                }
            }
        }
    }

    private fun uploadMoment() {
        val ref = FirebaseDatabase.getInstance().reference.child(USER_DETAILS).child(user!!.uid)
        ref.get().addOnSuccessListener { userProfile ->
            if (userProfile.exists()) {
                val myProfile = userProfile.getValue(UserProfile::class.java)!!
                moment.profileImage = myProfile.image
                moment.profileName = myProfile.name
                moment.profilePhone = myProfile.phone
                if (moment.timePosted == "") {
                    timeRef = FirebaseDatabase.getInstance().reference.child("time").child(user!!.uid)
                    timeRef.setValue(ServerValue.TIMESTAMP).addOnSuccessListener {
                        timeRef.get().addOnSuccessListener { snapshot ->
                            if (!EDITED) moment.timePosted = snapshot.value.toString()
                            if (snapshot.exists()) upload()
                        }
                    }
                } else upload()
            }
        }
    }

    private fun upload() {
        val refM = FirebaseDatabase.getInstance().reference.child(MOMENT_DATA).child(moment.timePosted + moment.creatorUid)
        val postRecord = FirebaseDatabase.getInstance().reference.child(PERSONAL_POST_RECORD).child(user!!.uid).child(moment.timePosted)
        refM.setValue(moment).addOnSuccessListener {
            postRecord.setValue(moment)
            Toast.makeText(context, "Moment post successfully uploaded", Toast.LENGTH_LONG).show()
            getFollowers()
        }.addOnFailureListener {
            Toast.makeText(context, "Moment post Failed. Please retry", Toast.LENGTH_LONG).show()
        }
    }

    private fun getFollowers() {
        val followers = FirebaseDatabase.getInstance().reference.child(FOLLOWERS).child(user!!.uid)
        val following = FirebaseDatabase.getInstance().reference.child(FOLLOWING).child(user!!.uid)
        followers.keepSynced(false)
        following.keepSynced(false)
        followers.get().addOnSuccessListener {
            var listOfFollowers: Set<String> = setOf()
            if (it.exists()) for (i in it.children) listOfFollowers = listOfFollowers.plus(i.value.toString())

            following.get().addOnSuccessListener { following ->
                if (following.exists()) for (i in following.children) listOfFollowers = listOfFollowers.plus(i.value.toString())
                for (i in listOfFollowers){
                    FirebaseDatabase.getInstance().reference.child(MOMENT_DATA_INVITE).child(i).child(user!!.uid).setValue(moment)
                }
            }
        }
    }

    companion object {
        const val FOLLOWERS = "followers"
        const val FOLLOWING = "following"
        const val MOMENT_DATA = "MOMENT_DATA"
        const val MOMENT_DATA_INVITE = "MOMENT_DATA_INVITE"
        const val PERSONAL_POST_RECORD = "PERSONAL_POST_RECORD"
        const val MOMENT_IMAGES = "MOMENT IMAGES"
        const val USER_DETAILS = "user_details"
    }
}