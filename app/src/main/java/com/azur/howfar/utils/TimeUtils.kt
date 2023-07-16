package com.azur.howfar.utils

import java.util.*

object TimeUtils {
    fun localToUTC(input: String): String{
        'a'.isLowerCase()
        if (input == "") return ""
        val instanceTime = Calendar.getInstance()
        instanceTime.timeInMillis = input.toLong()
        instanceTime.timeZone = TimeZone.getTimeZone("UTC")
        return instanceTime.timeInMillis.toString()
    }

    fun UTCToLocal(input: String): String{
        if (input == "") return ""
        val instanceTime = Calendar.getInstance()
        instanceTime.timeInMillis = input.toLong()
        instanceTime.timeZone = TimeZone.getDefault()
        return instanceTime.timeInMillis.toString()
    }

    fun milliSecondsToTimer(milliseconds: Long): String? {
        var finalTimerString = ""
        var secondsString: String

        // Convert total duration into time
        val hours = (milliseconds / (1000 * 60 * 60)).toInt()
        val minutes = (milliseconds % (1000 * 60 * 60)).toInt() / (1000 * 60)
        val seconds = (milliseconds % (1000 * 60 * 60) % (1000 * 60) / 1000).toInt()
        // Add hours if there
        if (hours > 0) {
            finalTimerString = "$hours:"
        }

        // Prepending 0 to seconds if it is one digit
        secondsString = if (seconds < 10) {
            "0$seconds"
        } else {
            "" + seconds
        }
        finalTimerString = "$finalTimerString$minutes:$secondsString"

        // return timer string
        return finalTimerString
    }

    fun timeDiffFromNow(milliseconds: String): Long {
        val timePostedInMillis = UTCToLocal(milliseconds).toLong() / 1000
        val nowInMill = Calendar.getInstance().timeInMillis / 1000
        return nowInMill - timePostedInMillis
    }

    fun timeDiff(smaller: String, larger: String): Long {
        val timePostedInMillis = UTCToLocal(smaller).toLong() / 1000
        val nowInMill = larger.toLong() / 1000
        return nowInMill - timePostedInMillis
    }
}