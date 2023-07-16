package com.azur.howfar.howfarchat

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.azur.howfar.R
import com.azur.howfar.databinding.FragmentCallBinding
import com.azur.howfar.howfarchat.chat.ChatActivity2
import com.azur.howfar.howfarchat.chat.FragmentDisplayImage
import com.azur.howfar.howfarchat.chat.UserProfileActivity
import com.azur.howfar.livedata.ValueEventLiveData
import com.azur.howfar.models.*
import com.azur.howfar.models.EventListenerType.onDataChange
import com.azur.howfar.utils.TimeUtils
import com.azur.howfar.utils.Util
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class FragmentCall : Fragment() {
    private lateinit var binding: FragmentCallBinding
    private var callsAdapter = CallsAdapter()
    //private val myAuth = FirebaseAuth.getInstance().currentUser!!.uid
    private var dataset: ArrayList<CallHistoryData> = arrayListOf()

    @SuppressLint("NotifyDataSetChanged")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentCallBinding.inflate(inflater, container, false)
        callsAdapter.activity = requireActivity()
        callsAdapter.dataset = dataset
        callsAdapter.viewLifecycleOwner = viewLifecycleOwner
        binding.callsAdapter.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
        binding.callsAdapter.adapter = callsAdapter
        /*val myCallerDetails = FirebaseDatabase.getInstance().reference.child(ChatActivity2.CALL_HISTORY).child(myAuth)
        val callsLiveData = ValueEventLiveData(myCallerDetails)
        callsLiveData.observe(viewLifecycleOwner) { callSnap ->
            when (callSnap.second) {
                onDataChange -> {
                    for (i in callSnap.first.children) {
                        val callHistory = i.getValue(CallHistoryData::class.java)!!
                        if (callHistory !in dataset) dataset.add(callHistory)
                    }
                    dataset.sortWith(compareByDescending { it.callTime })
                    callsAdapter.notifyDataSetChanged()
                }
            }
        }*/
        return binding.root
    }
}

class CallsAdapter : RecyclerView.Adapter<CallsAdapter.ChatDisplayViewHolder>() {
    lateinit var context: Context
    lateinit var activity: Activity
    lateinit var viewLifecycleOwner: LifecycleOwner
    private var actionMode: ActionMode? = null
    var dataset: ArrayList<CallHistoryData> = arrayListOf()
    private var selectedChats: java.util.ArrayList<CallHistoryData> = arrayListOf()

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int) = position.toLong()

    inner class ChatDisplayViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var displayImage: ImageView = itemView.findViewById(R.id.call_image)
        var callName: com.vanniktech.emoji.EmojiTextView = itemView.findViewById(R.id.call_name)
        var callTime: TextView = itemView.findViewById(R.id.call_time)
        var callIcon: ImageView = itemView.findViewById(R.id.call_icon)
        var callType: ImageView = itemView.findViewById(R.id.call_type)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatDisplayViewHolder {
        context = parent.context
        return ChatDisplayViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_calls_display, parent, false))
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onBindViewHolder(holder: ChatDisplayViewHolder, position: Int) {
        val datum = dataset.elementAt(position)
        if (datum in selectedChats) {
            holder.itemView.setBackgroundColor(Color.parseColor("#A7A7B3"))
        } else {
            holder.itemView.setBackgroundColor(Color.TRANSPARENT)
        }
        val time = TimeUtils.UTCToLocal(datum.callTime)
        holder.callTime.text = Util.formatSmartDateTime(time)
        val ref = FirebaseDatabase.getInstance().reference.child(USER_DETAILS).child(otherParticipant(datum.uids))
        val livedata = ValueEventLiveData(ref)
        livedata.observe(viewLifecycleOwner) {
            when (it.second) {
                onDataChange -> {
                    val userProfile = it.first.getValue(UserProfile::class.java)!!
                    holder.callName.text = userProfile.name
                    Glide.with(context).load(userProfile.image).centerCrop().error(R.drawable.ic_avatar).into(holder.displayImage)
                    holder.displayImage.setOnClickListener {
                        val fragment = FragmentDisplayImage()
                        val bundle = Bundle()
                        bundle.putString("image", userProfile.image)
                        fragment.arguments = bundle
                        (activity as AppCompatActivity).supportFragmentManager.beginTransaction().addToBackStack("image")
                            .replace(R.id.chat_landing_root, fragment).commit()
                    }
                }
            }
        }
        when (datum.engagementType) {
            CallEngagementType.CREATE -> {
                holder.callIcon.setImageResource(R.drawable.ic_call_made)
            }
            CallEngagementType.JOIN -> {
                holder.callIcon.setImageResource(R.drawable.call_received)
            }
        }
        if (datum.answerType == CallAnswerType.MISSED) {
            holder.callIcon.setImageResource(R.drawable.ic_call_missed)
        }
        when (datum.callType) {
            CallType.VIDEO -> holder.callType.setImageResource(R.drawable.videocamara)
            CallType.VOICE -> holder.callType.setImageResource(R.drawable.call)
        }
        holder.itemView.setOnClickListener {
            if (actionMode != null) {
                if (datum in selectedChats) selectedChats.remove(datum) else selectedChats.add(datum)
                if (selectedChats.isEmpty()) actionMode!!.finish()
                notifyDataSetChanged()
                return@setOnClickListener
            }
            val intent = Intent(context, UserProfileActivity::class.java)
            intent.putExtra("data", otherParticipant(datum.uids))
            context.startActivity(intent)
        }

        var statusBarColor = activity.window.statusBarColor
        val actionContextModeCallback: ActionMode.Callback = object : ActionMode.Callback {

            override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                mode!!.menuInflater.inflate(R.menu.chats_action_mode_menu, menu)
                mode.title = "Select"
                activity.window.statusBarColor = context.resources.getColor(R.color.black)
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                return true
            }

            override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
                when (item!!.itemId) {
                    R.id.delete -> {
                        for ((index, i) in selectedChats.withIndex()) {
                            /*FirebaseDatabase.getInstance().reference
                                .child(ChatActivity2.CALL_HISTORY)
                                .child(myAuth)
                                .child(i.callTime)
                                .removeValue()
                                .addOnSuccessListener {
                                    if (index == selectedChats.size - 1) Toast.makeText(context, "Deleted", Toast.LENGTH_LONG).show()
                                }*/
                            dataset.remove(i)
                        }
                        notifyDataSetChanged()
                        mode!!.finish()
                    }
                }
                return true
            }

            @SuppressLint("NotifyDataSetChanged")
            override fun onDestroyActionMode(mode: ActionMode?) {
                actionMode = null
                selectedChats.clear()
                notifyDataSetChanged()
                activity.window.statusBarColor = statusBarColor
            }
        }
        holder.itemView.setOnLongClickListener {
            if (actionMode != null) return@setOnLongClickListener false
            if (actionMode == null) {
                selectedChats.add(datum)
                actionMode = (activity as AppCompatActivity).startSupportActionMode(actionContextModeCallback)
                notifyDataSetChanged()
                return@setOnLongClickListener true
            }
            return@setOnLongClickListener true
        }
    }

    override fun getItemCount() = dataset.size

    override fun getItemViewType(position: Int): Int {
        return position
    }

    companion object {
        val USER_DETAILS = "user_details"
    }

    private fun otherParticipant(participants: ArrayList<String>): String {
        //for (i in participants) return if (i != myAuth) i else participants[1]
        return ""
    }
}