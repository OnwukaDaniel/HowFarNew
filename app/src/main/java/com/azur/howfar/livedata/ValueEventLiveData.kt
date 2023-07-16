package com.azur.howfar.livedata

import androidx.lifecycle.MutableLiveData
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import com.azur.howfar.models.EventListenerType.onDataChange

class ValueEventLiveData(private val ref: DatabaseReference): MutableLiveData<Pair<DataSnapshot, Int>>() {
    val listener = ValueEvent()
    override fun onActive() {
        super.onActive()
        ref.addValueEventListener(listener)
    }

    override fun onInactive() {
        super.onInactive()
        ref.removeEventListener(listener)
    }

    inner class ValueEvent: ValueEventListener{
        override fun onDataChange(snapshot: DataSnapshot) {
            if (snapshot.exists()) value = snapshot to onDataChange
        }

        override fun onCancelled(error: DatabaseError) {
        }
    }
}