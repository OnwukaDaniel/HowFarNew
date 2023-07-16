package com.azur.howfar.utils

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.azur.howfar.R
import com.azur.howfar.howfarchat.groupChat.Contact
import com.azur.howfar.models.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.gson.Gson
import com.tonyodev.fetch2.*
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

object Util {

    val digitToAmPm = arrayListOf("AM", "PM")

    val months = arrayListOf(
        "January", "February", "March", "April", "May", "June", "July",
        "August", "September", "October", "November", "December"
    )
    val shortMonth = arrayListOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul",
        "Aug", "Sep", "Oct", "Nov", "Dec"
    )

    fun getShortMonth(input: Int): String {
        return shortMonth[input]
    }

    fun getMyRegisteredContacts(registered: ArrayList<UserProfile>, phoneList: ArrayList<String>): ArrayList<UserProfile> {
        val myUsers: ArrayList<UserProfile> = arrayListOf()
        for (user in registered) if (formatNumber(user.phone) in phoneList) myUsers.add(user)
        return myUsers
    }

    fun hideSystemUI(window: Window, view: View) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, view).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    @SuppressLint("Range")
    fun getAllSavedContacts(context: Context): Pair<ArrayList<Contact>, ArrayList<String>> {
        val contactList: ArrayList<Contact> = arrayListOf()
        val phoneList: ArrayList<String> = arrayListOf()
        val contentResolver: ContentResolver = context.contentResolver

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val cursor =
            contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                ContactsContract.Contacts.HAS_PHONE_NUMBER + ">0 AND LENGTH(" + ContactsContract.CommonDataKinds.Phone.NUMBER + ")>0",
                null,
                "display_name ASC"
            )

        if (cursor != null && cursor.count > 0) {
            while (cursor.moveToNext()) {
                val id = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID))
                val person = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, id.toLong())
                //val uri = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI))
                val name = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME))
                val mobileNumber = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER))
                val contact = Contact(id, name, person.toString(), "", mobileNumber)
                contactList.add(contact)
                phoneList.add(formatNumber(mobileNumber))
            }
            cursor.close()
        }
        return contactList to phoneList
    }

    private fun removeWhiteSpace(input: String): String {
        var output = ""
        for (i in input) {
            if (i.toString() == " ") continue
            output += i
        }
        return output
    }

    fun formatNumber(input: String): String {
        val accepted = removeWhiteSpace(input)
        if (accepted.length < 10) return accepted
        return accepted.substring(accepted.length - 10, accepted.length)
    }

    fun statusTime(time: Long, toLong: Long): String {
        var value = ""
        if (time <= 60L) {
            value = "Just now"
        } else if (time in 61L..3600L) {
            var minutes = time / 60
            if (minutes <= 60) value = "$minutes minutes ago"
        } else if (time in 3601L..86399L) {
            val instance = Calendar.getInstance()
            instance.timeInMillis = time
            val z = Instant.ofEpochMilli(toLong).atZone(ZoneId.systemDefault())
            val date = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
            val formattedTime = date.format(z)
            value = formattedTime
        }
        return value
    }

    private fun time(time: Long): String {
        val calenderInstance: Calendar = Calendar.getInstance()
        calenderInstance.timeInMillis = time
        var minute = calenderInstance.get(Calendar.MINUTE).toString()
        minute = if (minute.length > 1) minute else "0$minute"
        val hour = calenderInstance.get(Calendar.HOUR)
        val amPm = digitToAmPm[calenderInstance.get(Calendar.AM_PM)]
        return "$hour:$minute $amPm"
    }

    fun convertLongToTime(time: Long): String {
        val date = Date(time)
        val format = SimpleDateFormat("HH:mm")
        return format.format(date).split(" ")[0]
    }

    fun convert24HrTo12Hr(hourOfDay: Int): Pair<String, Int> {
        val amPm = if (hourOfDay > 12) "PM" else "AM"
        val hour = if (hourOfDay > 12) hourOfDay - 12 else hourOfDay
        val pair = Pair(amPm, hour)
        return pair
    }

    fun formatTime(time: String): String {
        if (time.isEmpty()) return ""
        try {
            time.toLong()
        } catch (e: Exception) {
            return ""
        }
        val dateTime = time.toLong()
        val calenderInstance: Calendar = Calendar.getInstance()
        calenderInstance.timeInMillis = dateTime
        var minute = calenderInstance.get(Calendar.MINUTE).toString()
        minute = if (minute.length > 1) minute else "0$minute"
        val hour = calenderInstance.get(Calendar.HOUR)
        val day = calenderInstance.get(Calendar.DAY_OF_MONTH)
        val month = calenderInstance.get(Calendar.MONTH) + 1
        val year = calenderInstance.get(Calendar.YEAR)
        val amPm = digitToAmPm[calenderInstance.get(Calendar.AM_PM)]
        return "$day/$month/$year at $hour:$minute $amPm"
    }

    fun formatDate(time: String): String {
        if (time.isEmpty()) return ""
        try {
            time.toLong()
        } catch (e: Exception) {
            return ""
        }
        val dateTime = time.toLong()
        val calenderInstance: Calendar = Calendar.getInstance()
        calenderInstance.timeInMillis = dateTime
        val day = calenderInstance.get(Calendar.DAY_OF_MONTH)
        val month = calenderInstance.get(Calendar.MONTH) + 1
        val year = calenderInstance.get(Calendar.YEAR)
        return "$day/$month/$year"
    }

    fun formatDateTime(time: String): String {
        if (time.isEmpty()) return ""
        try {
            time.toLong()
        } catch (e: Exception) {
            return ""
        }
        val dateTime = time.toLong()
        val calenderInstance: Calendar = Calendar.getInstance()
        calenderInstance.timeInMillis = dateTime
        var minute = calenderInstance.get(Calendar.MINUTE).toString()
        minute = if (minute.length > 1) minute else "0$minute"
        val hour = calenderInstance.get(Calendar.HOUR)
        //val day = calenderInstance.get(Calendar.DAY_OF_MONTH)
        //val month = calenderInstance.get(Calendar.MONTH) + 1
        //val year = calenderInstance.get(Calendar.YEAR)
        val amPm = digitToAmPm[calenderInstance.get(Calendar.AM_PM)]
        return "$hour:$minute $amPm"
    }

    fun Context.getUriFromResources(resourceId: Int): Uri = with(resources) {
        return@with Uri.Builder()
            .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
            .authority(getResourcePackageName(resourceId))
            .appendPath(getResourceTypeName(resourceId))
            .appendPath(getResourceEntryName(resourceId))
            .build()
    }

    fun secsMinHour(time: String): String {
        if (time.isEmpty()) return ""
        try {
            time.toLong()
        } catch (e: Exception) {
            return ""
        }
        val dateTime = time.toLong()
        val calenderInstance: Calendar = Calendar.getInstance()
        calenderInstance.timeInMillis = dateTime
        var minute = calenderInstance.get(Calendar.MINUTE).toString()
        minute = if (minute.length > 1) minute else "0$minute"
        val hour = calenderInstance.get(Calendar.HOUR)
        val seconds = calenderInstance.get(Calendar.SECOND)
        //val day = calenderInstance.get(Calendar.DAY_OF_MONTH)
        return if (hour == 0) "$minute:$seconds" else "$hour:$minute:$seconds"
    }

    fun formatSmartDateTime(time: String, short: Boolean = false): String {
        if (time.isEmpty()) return ""
        try {
            time.toLong()
        } catch (e: Exception) {
            return ""
        }
        val dateTime = time.toLong()
        val calenderInstance: Calendar = Calendar.getInstance()
        calenderInstance.timeInMillis = dateTime
        var minute = calenderInstance.get(Calendar.MINUTE).toString()
        minute = if (minute.length > 1) minute else "0$minute"
        val hour = calenderInstance.get(Calendar.HOUR)
        val day = calenderInstance.get(Calendar.DAY_OF_MONTH)
        val month = calenderInstance.get(Calendar.MONTH) + 1
        val year = calenderInstance.get(Calendar.YEAR)
        val amPm = digitToAmPm[calenderInstance.get(Calendar.AM_PM)]

        val currentInstance = Calendar.getInstance()
        val currentDay = currentInstance.get(Calendar.DAY_OF_MONTH)
        val currentMonth = calenderInstance.get(Calendar.MONTH) + 1
        val currentYear = calenderInstance.get(Calendar.YEAR)
        return when {
            currentDay + 1 == day -> "Yesterday $hour:$minute $amPm"
            currentDay == day && currentMonth == month && currentYear == year -> "$hour:$minute $amPm"
            short -> "$hour:$minute $amPm $day/$month"
            else -> "$hour:$minute $amPm $day/$month/$year"
        }
    }

    fun hoursMinSecs(time: String): String {
        if (time.isEmpty()) return ""
        try {
            time.toLong()
        } catch (e: Exception) {
            return ""
        }
        val dateTime = time.toLong()
        val calenderInstance: Calendar = Calendar.getInstance()
        calenderInstance.timeInMillis = dateTime
        var minute = calenderInstance.get(Calendar.MINUTE).toString()
        var secs = calenderInstance.get(Calendar.SECOND).toString()
        minute = if (minute.length > 1) minute else "0$minute"
        secs = if (secs.length > 1) secs else "0$secs"
        return "$minute $secs"
    }

    fun permissionsAvailable(permissions: Array<String>, context: Context): Boolean {
        for (permission in permissions) if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) return false
        return true
    }

    fun View.margin(left: Float? = null, top: Float? = null, right: Float? = null, bottom: Float? = null) {
        layoutParams<ViewGroup.MarginLayoutParams> {
            left?.run { leftMargin = dpToPx(this) }
            top?.run { topMargin = dpToPx(this) }
            right?.run { rightMargin = dpToPx(this) }
            bottom?.run { bottomMargin = dpToPx(this) }
        }
    }

    inline fun <reified T : ViewGroup.LayoutParams> View.layoutParams(block: T.() -> Unit) {
        if (layoutParams is T) block(layoutParams as T)
    }

    fun View.dpToPx(dp: Float): Int = context.dpToPx(dp)
    fun Context.dpToPx(dp: Float): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics).toInt()

    fun pxToDp(px: Float, context: Context): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, px, context.resources.displayMetrics).toInt()

    fun showDeterminateProgressBar(view: View, context: Context): AlertDialog? {
        val progressLayout = LayoutInflater.from(context).inflate(R.layout.fragment_dialog_progress, null, false)
        val dialog: AlertDialog.Builder = AlertDialog.Builder(context)
        dialog.setView(view).setCancelable(false)
        dialog.setView(progressLayout)
        return dialog.create()
    }

    fun sortTwoUIDList(uid1: String, uid2: String): List<String> {
        return arrayListOf(uid1, uid2).sorted()
    }

    fun sortTwoUIDs(uid1: String, uid2: String): String {
        val list = sortTwoUIDList(uid1, uid2)
        return "-${list.first()}-${list.last()}"
    }

    private fun otherParticipant(chatUser: ChatData): String {
        val myAuth = FirebaseAuth.getInstance().currentUser!!.uid
        for (i in chatUser.participants) if (i != myAuth) return i
        return ""
    }

    fun sendNotification(chatData: ChatData, view: String = "Message", imageUri: String = "") {
        var msg = ""
        when(chatData.messagetype){
            MessageType.TEXT-> msg = chatData.msg
            MessageType.VIDEO-> msg = "Video"
            MessageType.AUDIO-> msg = "Audio"
            MessageType.PHOTO-> msg = "Photo"
        }
        val chatAndMessage = ChatAndMessage(chatData= chatData, message = msg)
        val pushNotification = PushNotification(
            title = "New message",
            body= msg,
            data = Gson().toJson(chatAndMessage),
            channelId = otherParticipant(chatData),
            imageUrl = imageUri,
            senderId = chatData.senderuid,
            receiverIds = arrayListOf(otherParticipant(chatData)),
            view = view,
        )
        FirebaseDatabase.getInstance().reference.child("PushNotifications").push().setValue(pushNotification)
    }

    fun sendNotification(message: String, body: String, data: String = "", receiverUid: String, view: String = "Message", imageUri: String = "") {
        val myAuth = FirebaseAuth.getInstance().currentUser!!.uid
        val pushNotification = PushNotification(
            title = message,
            body = body,
            data = data,
            channelId = receiverUid,
            imageUrl = imageUri,
            senderId = myAuth,
            receiverIds = arrayListOf(receiverUid),
            view = view,
        )
        FirebaseDatabase.getInstance().reference.child("PushNotifications").push().setValue(pushNotification)
    }
}