package com.azur.howfar.livedata

import androidx.lifecycle.MutableLiveData
import com.azur.howfar.models.EventListenerType.onDataChange
import com.google.firebase.database.*

class ValueQueryEventLiveData(private val ref: Query): MutableLiveData<Pair<DataSnapshot, Int>>() {
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