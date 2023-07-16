package com.azur.howfar.utils

import com.azur.howfar.models.*
import com.azur.howfar.retrofit.Const
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue

object HFCoinUtils {
    const val TRANSFER_HISTORY = "user_coins_transfer"

    fun checkBalance(it: DataSnapshot): Float {
        var available = 0F
        for (i in it.children) {
            val dataCurrency: Currency = i.getValue(Currency::class.java)!!
            when (dataCurrency.transactionType) {
                TransactionType.SENT -> available -= dataCurrency.hfcoin
                TransactionType.EARNED -> available += dataCurrency.hfcoin
                TransactionType.BOUGHT -> available += dataCurrency.hfcoin
                TransactionType.RECEIVED -> available += dataCurrency.hfcoin
                TransactionType.APP_GIFT -> available += dataCurrency.hfcoin
            }
        }
        return available
    }

    fun sendLoveLikeHFCoin(amount: Float, othersRef: DatabaseReference, creatorUid: String, myProfile: UserProfile, time: String) {
        var currency = Currency(senderUid = myProfile.uid, receiverUid = creatorUid, transactionType = TransactionType.SENT, hfcoin = amount)
        var md = when (amount) {
            Const.LIKE_VALUE -> MomentDetails(
                timeMomentPosted = time,
                likes = MomentLike(profileName = myProfile.name, profilePhoto = myProfile.image, profileUid = myProfile.uid)
            )
            Const.LOVE_VALUE -> MomentDetails(loves = MomentLove(profileName = myProfile.name, profilePhoto = myProfile.image, profileUid = myProfile.uid))
            else -> MomentDetails(loves = MomentLove(profileName = myProfile.name, profilePhoto = myProfile.image, profileUid = myProfile.uid))
        }
        val timeRef = FirebaseDatabase.getInstance().reference.child("time").child(myProfile.uid)
        timeRef.setValue(ServerValue.TIMESTAMP).addOnSuccessListener {
            timeRef.get().addOnSuccessListener { timeSnapshot ->
                if (timeSnapshot.exists()) {
                    val timeSent = timeSnapshot.value.toString()
                    md.time = timeSent
                    currency.timeOfTransaction = timeSent
                    othersRef.child(timeSent).setValue(md)
                    val receiverRef = FirebaseDatabase.getInstance().reference.child(TRANSFER_HISTORY).child(creatorUid).child(timeSent)
                    val senderRef = FirebaseDatabase.getInstance().reference.child(TRANSFER_HISTORY).child(myProfile.uid).child(timeSent)
                    senderRef.setValue(currency).addOnSuccessListener {
                        currency.transactionType = TransactionType.EARNED
                        receiverRef.setValue(currency)
                    }
                }
            }
        }
    }

    /**
     * Send HFCoin as a transaction.
     * Transaction will be posted to
     * reference.child(TRANSFER_HISTORY).child(UID).child(timeSent)
     * @param amountDebit: Float (Required)
     * @param amountCredit: Float (Required)
     * @param receiverUid: String (Required)
     * @param senderUid: String (Required)
     * */
    fun send(amountDebit: Float, amountCredit: Float, receiverUid: String, senderUid: String) {
        var currency = Currency(senderUid = senderUid, receiverUid = receiverUid, transactionType = TransactionType.SENT, hfcoin = amountDebit)
        val timeRef = FirebaseDatabase.getInstance().reference.child("time").child(senderUid)
        timeRef.setValue(ServerValue.TIMESTAMP).addOnSuccessListener {
            timeRef.get().addOnSuccessListener { timeSnapshot ->
                val timeSent = timeSnapshot.value.toString()
                currency.timeOfTransaction = timeSent
                val senderRef = FirebaseDatabase.getInstance().reference.child(TRANSFER_HISTORY).child(senderUid).child(timeSent)
                val receiverRef = FirebaseDatabase.getInstance().reference.child(TRANSFER_HISTORY).child(receiverUid).child(timeSent)
                senderRef.setValue(currency).addOnSuccessListener {
                    currency.transactionType = TransactionType.RECEIVED
                    currency.hfcoin = amountCredit
                    receiverRef.setValue(currency)
                }
            }
        }
    }
    fun sendGift(amountDebit: Float, receiverUid: String) {
        var currency = Currency(senderUid = "?admin", receiverUid = receiverUid, transactionType = TransactionType.SENT, hfcoin = amountDebit)
        val timeRef = FirebaseDatabase.getInstance().reference.child("time").child(receiverUid)
        timeRef.setValue(ServerValue.TIMESTAMP).addOnSuccessListener {
            timeRef.get().addOnSuccessListener { timeSnapshot ->
                val timeSent = timeSnapshot.value.toString()
                currency.timeOfTransaction = timeSent
                val receiverRef = FirebaseDatabase.getInstance().reference.child(TRANSFER_HISTORY).child(receiverUid).child(timeSent)
                currency.transactionType = TransactionType.APP_GIFT
                receiverRef.setValue(currency)
            }
        }
    }
}