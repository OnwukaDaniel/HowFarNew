package com.azur.howfar.workManger

import android.content.Context
import android.icu.util.Calendar
import android.icu.util.Calendar.*
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.azur.howfar.workManger.HowFarAnalyticsTypes.GO_LIVE
import com.azur.howfar.workManger.HowFarAnalyticsTypes.LOG_IN
import com.azur.howfar.workManger.HowFarAnalyticsTypes.NO_ACTION
import com.azur.howfar.workManger.HowFarAnalyticsTypes.OPEN_APP
import com.azur.howfar.workManger.HowFarAnalyticsTypes.SEND_MESSAGE
import com.azur.howfar.workManger.HowFarAnalyticsTypes.SIGNUP
import com.azur.howfar.workManger.HowFarAnalyticsTypes.WATCH_LIVE
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue

class OpenAppWorkManager(private val context: Context, private val params: WorkerParameters) : Worker(context, params) {
    private val myAuth = FirebaseAuth.getInstance().currentUser!!.uid
    private var timeRef = FirebaseDatabase.getInstance().reference
    private var ref = FirebaseDatabase.getInstance("https://howfar-b24ef.firebaseio.com").reference.child("ANALYTICS")
    private var act = HowFarAnalytics()
    private var action = NO_ACTION
    private var timeMillis = 0L

    override fun doWork(): Result {
        action = params.inputData.getInt("action", NO_ACTION)
        timeMillis = params.inputData.getLong("time", 0L)
        timeRef = FirebaseDatabase.getInstance().reference.child("time").child(myAuth)
        act = HowFarAnalytics(uid = myAuth, types = action)

        when (timeMillis) {
            0L -> {
                timeRef.setValue(ServerValue.TIMESTAMP).addOnSuccessListener {
                    timeRef.get().addOnSuccessListener {
                        val time = it.value.toString()
                        act.time = time
                        val instance = Calendar.getInstance()
                        instance.timeInMillis = time.toLong()
                        val ins = Calendar.getInstance()
                        ins.set(YEAR, instance.get(YEAR))
                        ins.set(MONTH, instance.get(MONTH))
                        ins.set(DAY_OF_WEEK, instance.get(DAY_OF_WEEK))
                        ins.set(HOUR, 0)
                        ins.set(MINUTE, 0)
                        ins.set(SECOND, 0)
                        ins.set(MILLISECOND, 0)
                        val timeDay = ins.timeInMillis.toString()
                        refPush(timeDay)
                    }
                }
            }
            else -> {
                val time = timeMillis.toString()
                act.time = time
                val instance = Calendar.getInstance()
                instance.timeInMillis = time.toLong()
                val ins = Calendar.getInstance()
                ins.set(YEAR, instance.get(YEAR))
                ins.set(MONTH, instance.get(MONTH))
                ins.set(DAY_OF_WEEK, instance.get(DAY_OF_WEEK))
                ins.set(HOUR, 0)
                ins.set(MINUTE, 0)
                ins.set(SECOND, 0)
                ins.set(MILLISECOND, 0)
                val timeDay = ins.timeInMillis.toString()
                refPush(timeDay)
            }
        }
        return Result.success()
    }

    private fun refPush(timeDay: String) {
        var secondPath: String = ""
        println("Action captured *********************** $action")
        val actionString = when (action) {
            OPEN_APP -> "OPEN_APP"
            LOG_IN -> "LOG_IN"
            GO_LIVE -> {
                secondPath = timeMillis.toString()
                "GO_LIVE"
            }
            SIGNUP -> "SIGNUP"
            SEND_MESSAGE -> "SEND_MESSAGE"
            WATCH_LIVE -> {
                secondPath = timeMillis.toString()
                "WATCH_LIVE"
            }
            else -> ""
        }
        when (secondPath) {
            "" -> ref.child(actionString).child(timeDay).child(myAuth).setValue(act)
            else -> ref.child(actionString).child(timeDay).child(secondPath).child(myAuth).setValue(act)
        }
    }
}

object HowFarAnalyticsTypes {
    const val LOG_IN = 0
    const val SIGNUP = 1
    const val OPEN_APP = 2
    const val GO_LIVE = 3
    const val SEND_MESSAGE = 4
    const val NO_ACTION = 5
    const val WATCH_LIVE = 6
}

data class HowFarAnalytics(
    var uid: String = "",
    var time: String = "",
    var types: Int = NO_ACTION,
)