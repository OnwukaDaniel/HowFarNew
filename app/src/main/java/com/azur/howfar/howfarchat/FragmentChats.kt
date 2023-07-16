package com.azur.howfar.howfarchat

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.azur.howfar.R
import com.azur.howfar.databinding.FragmentChatsBinding
import com.azur.howfar.howfarchat.chat.ChatActivity
import com.azur.howfar.howfarchat.chat.ChatActivitySupport
import com.azur.howfar.howfarchat.chat.FragmentDisplayImage
import com.azur.howfar.livedata.ChildEventLiveData
import com.azur.howfar.livedata.ValueEventLiveData
import com.azur.howfar.models.*
import com.azur.howfar.utils.FirebaseConstants
import com.azur.howfar.utils.Util
import com.azur.howfar.viewmodel.BooleanViewModel
import com.bumptech.glide.Glide
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.imageview.ShapeableImageView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class FragmentChats : Fragment() {
    private lateinit var binding: FragmentChatsBinding
    private var dataset: ArrayList<ChatData> = arrayListOf()
    private val booleanViewModel by activityViewModels<BooleanViewModel>()
    private val chatDisplayAdapter = ChatDisplayAdapter()

    @SuppressLint("NotifyDataSetChanged")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentChatsBinding.inflate(inflater, container, false)
        /*val displayRef = FirebaseDatabase.getInstance().reference.child(FirebaseConstants.CHAT_DISPLAY).child(myAuth)

        ValueEventLiveData(displayRef).observe(viewLifecycleOwner) {
            when (it.second) {
                EventListenerType.onDataChange -> {
                    dataset.clear()
                    for(x in it.first.children){
                        val chatData = x.children.last().getValue(ChatData::class.java)!!
                        var unReadMessages = 0
                        for (i in x.children) {
                            val chat = i.getValue(ChatData::class.java)!!
                            if (!chat.read && chat.senderuid != myAuth) unReadMessages++
                        }
                        chatData.newMessages = unReadMessages
                        dataset.add(chatData)
                    }
                    dataset.sortWith(compareByDescending { value -> value.uniqueQuerableTime })
                    showData()
                    chatDisplayAdapter.notifyDataSetChanged()
                }
            }
        }*/

        chatDisplayAdapter.viewLifecycleOwner = viewLifecycleOwner
        chatDisplayAdapter.activity = requireActivity()
        chatDisplayAdapter.dataset = dataset
        chatDisplayAdapter.booleanViewModel = booleanViewModel
        binding.chatRv.adapter = chatDisplayAdapter
        binding.chatRv.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)

        booleanViewModel.stopLoadingShimmer.observe(viewLifecycleOwner) {
            if (it == true) {
                showData()
            }
        }
        return binding.root
    }

    private fun showData() {
        binding.chatShimmer.visibility = View.GONE
        binding.chatRv.visibility = View.VISIBLE
    }

    companion object {
        const val TRANSFER_HISTORY = "user_coins_transfer"
        const val USER_DETAILS = "user_details"
        const val CHAT_REFERENCE = "chat_reference"
        const val CHAT = "chat"
        val CHAT_REF = "chat_reference2"
        val CHAT_ROOM = "chat_room_reference2"
    }
}

@SuppressLint("NotifyDataSetChanged")
class ChatDisplayAdapter : RecyclerView.Adapter<ChatDisplayAdapter.ChatDisplayViewHolder>() {
    lateinit var booleanViewModel: BooleanViewModel
    lateinit var viewLifecycleOwner: LifecycleOwner
    lateinit var context: Context
    lateinit var activity: Activity
    lateinit var parent: ViewGroup
    var dataset: ArrayList<ChatData> = arrayListOf()
    private var selectedChats: java.util.ArrayList<ChatData> = arrayListOf()
    private var actionMode: ActionMode? = null
    val mediaTypes = arrayListOf(MessageType.PHOTO, MessageType.AUDIO, MessageType.VIDEO)

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int) = position.toLong()

    inner class ChatDisplayViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var displayImage: ImageView = itemView.findViewById(R.id.displayImage)
        var blueTick: ShapeableImageView = itemView.findViewById(R.id.blue_tick)
        var root: LinearLayout = itemView.findViewById(R.id.layout_root)
        var allRoot: LinearLayout = itemView.findViewById(R.id.all_root_root)
        var noneText: ImageView = itemView.findViewById(R.id.none_text)
        var displayName: com.vanniktech.emoji.EmojiTextView = itemView.findViewById(R.id.displayName)
        var displayMsg: com.vanniktech.emoji.EmojiTextView = itemView.findViewById(R.id.display_msg)
        var displayTime: TextView = itemView.findViewById(R.id.display_time)
        var displayUnread: ImageView = itemView.findViewById(R.id.display_unread)
        var displayUnreadCount: TextView = itemView.findViewById(R.id.display_unread_count)
        var unreadRoot: FrameLayout = itemView.findViewById(R.id.display_unread_root)

        fun setTempData(datum: ChatData) {
            val otherTemp = otherParticipant(datum)
            if (otherTemp.uid != "") {
                displayName.text = otherTemp.tempName
                Glide.with(context).load(otherTemp.tempImage).into(displayImage)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatDisplayViewHolder {
        this.parent = parent
        context = parent.context
        return ChatDisplayViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_chat_display, parent, false))
    }

    override fun onBindViewHolder(holder: ChatDisplayViewHolder, position: Int) {
        val datum = dataset.elementAt(position)
        holder.displayMsg.text = datum.msg
        holder.displayTime.text = Util.formatSmartDateTime(datum.uniqueQuerableTime, short = true)
        val selectionColor = if (datum in selectedChats) Color.parseColor("#A7A7B3") else Color.TRANSPARENT
        val otherParticipant = when (datum.participants.size) {
            1 -> datum.participants.first()
            2 -> otherParticipant(datum.participants)
            else -> ""
        }
        holder.setTempData(datum)

        if (datum.isSupport) {
            holder.displayName.text = "HowFar-Admin"
            holder.blueTick.visibility = View.VISIBLE
            Glide.with(context).load(R.drawable.app_icon_sec).centerCrop().into(holder.displayImage)
        } else {
            holder.blueTick.visibility = View.GONE
            /*FirebaseDatabase.getInstance().reference.child(USER_DETAILS).child(otherParticipant).get().addOnSuccessListener {
                if (it.exists()) {
                    holder.allRoot.setBackgroundColor(selectionColor)
                    mediaType(datum, holder)
                    val user = it.getValue(UserProfile::class.java)!!

                    when (user.isAdmin) {
                        true -> holder.blueTick.visibility = View.VISIBLE
                        false -> holder.blueTick.visibility = View.GONE
                    }
                    holder.displayName.text = user.name
                    Glide.with(context).load(user.image).centerCrop().error(R.drawable.ic_avatar).into(holder.displayImage)
                    holder.displayImage.setOnClickListener {
                        val fragment = FragmentDisplayImage()
                        val bundle = Bundle()
                        bundle.putString("image", user.image)
                        fragment.arguments = bundle
                        (activity as AppCompatActivity).supportFragmentManager.beginTransaction().addToBackStack("image")
                            .replace(R.id.chat_landing_root, fragment).commit()
                    }
                }
            }*/
        }

        val statusBarColor = activity.window.statusBarColor
        val actionContextModeCallback: ActionMode.Callback = object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                mode!!.menuInflater.inflate(R.menu.chats_action_mode_menu, menu)
                mode.title = "Select"
                activity.window.statusBarColor = context.resources.getColor(R.color.black, Resources.getSystem().newTheme())
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                return true
            }

            override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
                when (item!!.itemId) {
                    R.id.delete -> {
                        for (i in selectedChats) {
                            val chatKey = Util.sortTwoUIDs(i.participants.first(), i.participants.last())
                            /*FirebaseDatabase.getInstance().reference
                                .child(FirebaseConstants.CHAT_DISPLAY)
                                .child(myAuth)
                                .child(otherParticipant(i.participants))
                                .removeValue().addOnSuccessListener {
                                    Toast.makeText(context, "Deleted", Toast.LENGTH_LONG).show()
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

        holder.itemView.setOnClickListener {
            if (actionMode != null) {
                if (datum in selectedChats) selectedChats.remove(datum) else selectedChats.add(datum)
                if (selectedChats.isEmpty()) actionMode!!.finish()
                notifyDataSetChanged()
                return@setOnClickListener
            }
            when (datum.isSupport) {
                true -> context.startActivity(Intent(context, ChatActivitySupport::class.java))
                else -> context.startActivity(Intent(context, ChatActivity::class.java).apply { putExtra("data", otherParticipant) })
            }
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
    }

    private fun mediaType(datum: ChatData, holder: ChatDisplayAdapter.ChatDisplayViewHolder) {
        when (datum.messagetype) {
            MessageType.TEXT -> {
                holder.noneText.visibility = View.GONE
            }
            MessageType.PHOTO -> {
                holder.noneText.visibility = View.VISIBLE
                holder.noneText.setImageResource(R.drawable.image_placeholder)
                holder.displayMsg.text = datum.imageData.displayMessage
            }
            MessageType.VIDEO -> {
                holder.noneText.visibility = View.VISIBLE
                holder.noneText.setImageResource(R.drawable.videocamara)
                holder.displayMsg.text = datum.videoData.displayMessage
            }
            MessageType.AUDIO -> {
                holder.noneText.visibility = View.VISIBLE
                holder.noneText.setImageResource(R.drawable.ic_audiotrack_gray)
                holder.displayMsg.text = datum.audioData.displayMessage
            }
            MessageType.CONTACT -> {
                holder.noneText.visibility = View.VISIBLE
                holder.noneText.setImageResource(R.drawable.ic_user_place)
                holder.displayMsg.text = datum.audioData.displayMessage
            }
        }
        if (datum.senderuid != "myAuth") {
            holder.displayUnread.visibility = View.GONE
        } else {
            holder.displayUnread.visibility = View.VISIBLE
            if (datum.sent) holder.displayUnread.setImageResource(R.drawable.ic_sent_msg)
            if (datum.delivered) holder.displayUnread.setImageResource(R.drawable.ic_delivered)
            if (datum.read) holder.displayUnread.setImageResource(R.drawable.ic_read)
        }
        holder.unreadRoot.visibility = View.GONE
        when {
            datum.newMessages > 0 -> {
                holder.unreadRoot.visibility = View.VISIBLE
                holder.displayUnreadCount.text = if (datum.newMessages == 0) "" else datum.newMessages.toString()
            }
        }
    }

    override fun getItemCount() = dataset.size

    override fun getItemViewType(position: Int) = position

    private fun otherParticipant(chatUser: ChatData): ParticipantTempData {
        //for (i in chatUser.participantsTempData) if (i.uid != myAuth) return i
        return ParticipantTempData()
    }

    private fun otherParticipant(participants: java.util.ArrayList<String>): String {
        //for (i in participants) return if (i != myAuth) i else participants[1]
        return ""
    }

    companion object {
        const val CHAT_REFERENCE = "chat_reference"
        const val TRANSFER_HISTORY = "user_coins_transfer"
        const val USER_DETAILS = "user_details"
    }
}