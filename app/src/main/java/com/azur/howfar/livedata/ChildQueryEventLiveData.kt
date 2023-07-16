package com.azur.howfar.livedata

import androidx.lifecycle.MutableLiveData
import com.google.firebase.database.*
import com.azur.howfar.models.EventListenerType.onChildAdded
import com.azur.howfar.models.EventListenerType.onChildChanged
import com.azur.howfar.models.EventListenerType.onChildMoved
import com.azur.howfar.models.EventListenerType.onChildRemoved

class ChildQueryEventLiveData(private val ref: Query): MutableLiveData<Pair<DataSnapshot, Int>>() {
    val listener = ValueEvent()
    override fun onActive() {
        super.onActive()
        ref.addChildEventListener(listener)
    }

    override fun onInactive() {
        super.onInactive()
        ref.removeEventListener(listener)
    }

    inner class ValueEvent: ChildEventListener{
        override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
            value = snapshot to onChildAdded
        }

        override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
            value = snapshot to onChildChanged
        }

        override fun onChildRemoved(snapshot: DataSnapshot) {
            value = snapshot to onChildRemoved
        }

        override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {
            value = snapshot to onChildMoved
        }

        override fun onCancelled(error: DatabaseError) {
        }
    }
}