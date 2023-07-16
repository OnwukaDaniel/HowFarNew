package com.azur.howfar.howfarchat.groupChat

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.azur.howfar.R
import com.azur.howfar.databinding.FragmentGroupChatBinding
import com.azur.howfar.howfarchat.chat.FragmentDisplayImage
import com.azur.howfar.livedata.ValueEventLiveData
import com.azur.howfar.models.ChatData
import com.azur.howfar.models.EventListenerType
import com.azur.howfar.models.GroupProfileData
import com.azur.howfar.viewmodel.BooleanViewModel
import com.bumptech.glide.Glide
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

class FragmentGroupChats : Fragment() {
    private lateinit var binding: FragmentGroupChatBinding
    private var dataset: ArrayList<ChatData> = arrayListOf()
    private var displayRef = FirebaseDatabase.getInstance().reference
    private val chatDisplayAdapter = ChatDisplayAdapter()
    private val booleanViewModel by activityViewModels<BooleanViewModel>()

    @SuppressLint("NotifyDataSetChanged")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentGroupChatBinding.inflate(inflater, container, false)
        //startShimmer()
        chatDisplayAdapter.viewLifecycleOwner = viewLifecycleOwner
        chatDisplayAdapter.booleanViewModel = booleanViewModel
        chatDisplayAdapter.activity = requireActivity()
        chatDisplayAdapter.dataset = dataset
        binding.groupRv.adapter = chatDisplayAdapter
        binding.groupRv.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
        /*var myGroupMessagesRef = FirebaseDatabase.getInstance().reference
            .child(MY_GROUPS_MESSAGES)
            .child(FirebaseAuth.getInstance().currentUser!!.uid)
        ValueEventLiveData(myGroupMessagesRef).observe(viewLifecycleOwner) {
            when (it.second) {
                EventListenerType.onDataChange -> {
                    dataset.clear()
                    for (i in it.first.children) dataset.add(i.children.last().getValue(ChatData::class.java)!!)
                    dataset.sortWith(compareByDescending { dataset -> dataset.uniqueQuerableTime })
                    showData()
                    chatDisplayAdapter.notifyDataSetChanged()
                }
            }
        }*/

        booleanViewModel.stopLoadingShimmer.observe(viewLifecycleOwner) {
            if (it == true) showData()
        }
        return binding.root
    }

    private fun showData() {
        binding.groupShimmer.visibility = View.GONE
        binding.groupRv.visibility = View.VISIBLE
    }

    companion object {
        const val TRANSFER_HISTORY = "user_coins_transfer"
        const val USER_DETAILS = "user_details"
        const val CHAT_REFERENCE = "chat_reference"
        const val MY_GROUPS_MESSAGES = "my_groups_messages"
        const val CHAT = "chat"
        const val GROUP_DISPLAY_DATA = "group_display_data"
    }
}

class ChatDisplayAdapter : RecyclerView.Adapter<ChatDisplayAdapter.ChatDisplayViewHolder>() {
    lateinit var context: Context
    lateinit var activity: Activity
    lateinit var parent: ViewGroup
    lateinit var booleanViewModel: BooleanViewModel
    lateinit var viewLifecycleOwner: LifecycleOwner
    var dataset: ArrayList<ChatData> = arrayListOf()
    private var selectedChats: ArrayList<ChatData> = arrayListOf()
    private var actionMode: ActionMode? = null

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int) = position.toLong()

    inner class ChatDisplayViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var displayImage: ImageView = itemView.findViewById(R.id.displayImage)
        var noneText: ImageView = itemView.findViewById(R.id.none_text)
        var allRoot: LinearLayout = itemView.findViewById(R.id.all_root_root)
        var displayName: com.vanniktech.emoji.EmojiTextView = itemView.findViewById(R.id.displayName)
        var displayMsg: com.vanniktech.emoji.EmojiTextView = itemView.findViewById(R.id.display_msg)
        var displayTime: TextView = itemView.findViewById(R.id.display_time)
        var displayUnread: ImageView = itemView.findViewById(R.id.display_unread)
        //var displayUnreadCount: TextView = itemView.findViewById(R.id.display_unread_count)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatDisplayViewHolder {
        this.parent = parent
        context = parent.context
        return ChatDisplayViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_chat_display, parent, false))
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onBindViewHolder(holder: ChatDisplayViewHolder, position: Int) {
        val datum = dataset.elementAt(position)
        val selectionColor = if (datum in selectedChats) Color.parseColor("#A7A7B3") else Color.TRANSPARENT
        holder.allRoot.setBackgroundColor(selectionColor)

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

            @SuppressLint("NotifyDataSetChanged")
            override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
                when (item!!.itemId) {
                    R.id.delete -> {
                        val alertBuilder = AlertDialog.Builder(context)
                        alertBuilder.setTitle("Leave group")
                        alertBuilder.setPositiveButton("Leave") { dialog, which ->
                            for (i in selectedChats) {
                                /*FirebaseDatabase.getInstance().reference
                                    .child(GROUP_DISPLAY_DATA)
                                    .child(myAuth)
                                    .child(i.groupUid)
                                    .removeValue()
                                    .addOnSuccessListener {
                                        Toast.makeText(context, "Deleted", Toast.LENGTH_LONG).show()
                                        notifyDataSetChanged()
                                    }
                                val groupRef = FirebaseDatabase.getInstance().reference.child(GROUPS).child(i.groupUid)
                                groupRef.get().addOnSuccessListener { groupSnapshot ->
                                    if (groupSnapshot.exists()) {
                                        var groupProfile2 = groupSnapshot.getValue(GroupProfileData::class.java)!!
                                        groupProfile2.members.remove(myAuth)
                                        if (myAuth in groupProfile2.admins && groupProfile2.admins.size == 1 && groupProfile2.members.isNotEmpty()) {
                                            groupProfile2.members.shuffle()
                                            groupProfile2.admins.add(groupProfile2.members.first())
                                        }
                                        groupRef.setValue(groupProfile2).addOnSuccessListener {
                                            FirebaseDatabase.getInstance().reference
                                                .child(GROUP_DISPLAY_DATA).child(myAuth).child(datum.groupUid)
                                                .removeValue().addOnSuccessListener {
                                                    dialog.dismiss()
                                                }.addOnFailureListener {
                                                    showSnackBar(holder.itemView, "Failed to leave group")
                                                    dialog.dismiss()
                                                }
                                        }.addOnFailureListener {
                                            showSnackBar(holder.itemView, "Failed to leave group")
                                            dialog.dismiss()
                                        }
                                    } else {
                                        showSnackBar(holder.itemView, "Group doesn't exist")
                                        dialog.dismiss()
                                    }
                                }.addOnFailureListener {
                                    showSnackBar(holder.itemView, "Failed to leave group")
                                    dialog.dismiss()
                                }*/
                                dataset.remove(i)
                                mode!!.finish()
                                notifyDataSetChanged()
                            }
                        }
                        alertBuilder.setNegativeButton("No") { dialog, which ->
                            dialog.dismiss()
                            mode!!.finish()
                        }
                        alertBuilder.setNeutralButton("Cancel") { dialog, which ->
                            dialog.dismiss()
                            mode!!.finish()
                        }
                        val alertDialog = alertBuilder.create()
                        alertDialog.show()
                        notifyDataSetChanged()
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
        holder.displayMsg.text = if (datum.participantsTempData.isNotEmpty()){
            val display = if (datum.participantsTempData.first().tempName != ""){
                var tempName = datum.participantsTempData.first().tempName
                //if (datum.participantsTempData.first().uid == myAuth) tempName = "You"
                "$tempName: ${datum.msg}"
            } else datum.msg
            display
        } else datum.msg

        mediaType(datum, holder)

        val z = Instant.ofEpochMilli(datum.uniqueQuerableTime.toLong()).atZone(ZoneId.systemDefault())
        val date = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
        val formattedTime = date.format(z)
        holder.displayTime.text = formattedTime
        FirebaseDatabase.getInstance().reference.child("$GROUPS/${datum.groupUid}").get().addOnSuccessListener {
            if (it.exists()) {
                val groupProfileData = it.getValue(GroupProfileData::class.java)!!
                holder.displayName.text = groupProfileData.groupName
                if (!activity.isDestroyed)
                    Glide.with(context).load(groupProfileData.groupImage).centerCrop().error(R.drawable.ic_avatar).into(holder.displayImage)
                holder.displayImage.setOnClickListener {
                    val fragment = FragmentDisplayImage()
                    val bundle = Bundle()
                    bundle.putString("image", groupProfileData.groupImage)
                    fragment.arguments = bundle
                    (activity as AppCompatActivity).supportFragmentManager.beginTransaction().addToBackStack("image")
                        .replace(R.id.chat_landing_root, fragment).commit()
                }
            }
        }
        holder.itemView.setOnClickListener {
            if (actionMode != null) {
                if (datum in selectedChats) selectedChats.remove(datum) else selectedChats.add(datum)
                if (selectedChats.isEmpty()) actionMode!!.finish()
                notifyDataSetChanged()
                return@setOnClickListener
            }
            val intent = Intent(context, GroupChatActivity::class.java)
            intent.putExtra("data", datum.groupUid)
            context.startActivity(intent)
            activity.overridePendingTransition(R.anim.enter_right_to_left, R.anim.exit_right_to_left)
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
        holder.displayImage.setOnClickListener {
            val fragment = FragmentDisplayImage()
            val bundle = Bundle()
            fragment.arguments = bundle
            (activity as AppCompatActivity).supportFragmentManager.beginTransaction().addToBackStack("image")
                .replace(R.id.chat_landing_root, fragment).commit()
        }
    }

    private fun showSnackBar(root: View, msg: String) {
        Snackbar.make(root, msg, Snackbar.LENGTH_LONG).show()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun mediaType(datum: ChatData, holder: ChatDisplayViewHolder) {
        var display = datum.msg
        if (datum.participantsTempData.isNotEmpty()){
            display = if (datum.participantsTempData.first().tempName != ""){
                var tempName = datum.participantsTempData.first().tempName
                //if (datum.participantsTempData.first().uid == myAuth) tempName = "You"
                "$tempName: ${datum.msg}"
            } else datum.msg
        }

        when {
            datum.imageData.storageLink != "" -> {
                holder.noneText.visibility = View.VISIBLE
                holder.noneText.setImageResource(R.drawable.image_placeholder)
                holder.displayMsg.text = "$display ${datum.imageData.displayMessage}"
            }
            datum.videoData.storageLink != "" -> {
                holder.noneText.visibility = View.VISIBLE
                holder.noneText.setImageResource(R.drawable.videocamara)
                holder.displayMsg.text = "$display ${datum.videoData.displayMessage}"
            }
            datum.audioData.storageLink != "" -> {
                holder.noneText.visibility = View.VISIBLE
                holder.noneText.setImageResource(R.drawable.ic_audiotrack_gray)
                holder.displayMsg.text = "$display ${datum.audioData.displayMessage}"
            }
        }
        if (datum.imageData.storageLink == "" && datum.videoData.storageLink == "" && datum.audioData.storageLink == "") {
            holder.noneText.visibility = View.GONE
        }
        if (datum.senderuid != "myAuth") {
            holder.displayUnread.visibility = View.GONE
        } else {
            holder.displayUnread.visibility = View.VISIBLE
            //if (datum.sent) holder.displayUnread.setImageResource(R.drawable.ic_sent_msg)
            //if (datum.delivered) holder.displayUnread.setImageResource(R.drawable.ic_delivered)
            //if (datum.read) holder.displayUnread.setImageResource(R.drawable.ic_read)
        }
        booleanViewModel.switch.observe(viewLifecycleOwner) {
            if (it == true) {
                if (actionMode != null) {
                    actionMode!!.finish()
                    selectedChats.clear()
                    notifyDataSetChanged()
                }
                booleanViewModel.setSwitch(false)
            }
        }
        //holder.displayUnreadCount.text = if (datum.newMessages == 0) "" else datum.newMessages.toString()
    }

    override fun getItemCount() = dataset.size

    override fun getItemViewType(position: Int): Int {
        return position
    }

    companion object {
        const val GROUP_DISPLAY_DATA = "group_display_data"
        val TRANSFER_HISTORY = "user_coins_transfer"
        const val GROUPS = "groups"
        val USER_DETAILS = "user_details"
    }
}