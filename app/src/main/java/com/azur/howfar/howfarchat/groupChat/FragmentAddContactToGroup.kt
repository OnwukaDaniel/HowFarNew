package com.azur.howfar.howfarchat.groupChat

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.azur.howfar.R
import com.azur.howfar.databinding.FragmentAddContactToGroupBinding
import com.azur.howfar.dilog.ProgressFragment
import com.azur.howfar.models.*
import com.azur.howfar.utils.Util
import com.azur.howfar.viewmodel.DialogViewModel
import com.azur.howfar.viewmodel.GroupProfileViewModel
import com.bumptech.glide.Glide
import com.google.android.material.imageview.ShapeableImageView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FragmentAddContactToGroup : Fragment(), SelectedContactHelper, View.OnClickListener {
    private lateinit var binding: FragmentAddContactToGroupBinding
    private val scope = CoroutineScope(Dispatchers.Main)
    private val myAuth = FirebaseAuth.getInstance().currentUser!!.uid
    private var timeRef = FirebaseDatabase.getInstance().reference
    private val listOfUsers: ArrayList<UserProfile> = arrayListOf()
    private val myUsers: ArrayList<UserProfile> = arrayListOf()
    private val phoneList: ArrayList<String> = arrayListOf()
    private var groupProfile = GroupProfileData()
    private val progressFragment = ProgressFragment()
    private val dialogViewModel: DialogViewModel by activityViewModels()
    private val groupProfileViewModel by activityViewModels<GroupProfileViewModel>()
    private var allUsersRef = FirebaseDatabase.getInstance().reference.child("user_details")
    private val addGroupMembersAdapter = AddGroupMembersAdapter()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentAddContactToGroupBinding.inflate(inflater, container, false)
        binding.groupAddContactBack.setOnClickListener(this)
        timeRef = FirebaseDatabase.getInstance().reference.child("time").child(myAuth)
        groupProfileViewModel.groupProfile.observe(viewLifecycleOwner) {
            groupProfile = it
            addGroupMembersAdapter.dataset = myUsers
            addGroupMembersAdapter.selectedContactHelper = this
            addGroupMembersAdapter.members = groupProfile.members
            binding.groupProfileMembersRv.adapter = addGroupMembersAdapter
            binding.groupProfileMembersRv.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
        }
        scope.launch {
            val contactList = Util.getAllSavedContacts(requireContext()).first
            for (cont in contactList) {
                phoneList.add(Util.formatNumber(cont.mobileNumber))
            }

            allUsersRef.get().addOnSuccessListener {
                if (it.exists()) {
                    for (i in it.children) {
                        val user = i.getValue(UserProfile::class.java)!!
                        if (user.uid == myAuth) continue
                        if (user !in listOfUsers) listOfUsers.add(user)
                        if (Util.formatNumber(user.phone) in phoneList) {
                            myUsers.add(user)
                            if (isAdded && activity != null) requireActivity().runOnUiThread { addGroupMembersAdapter.notifyItemInserted(myUsers.size) }
                        }
                    }
                }
            }
        }
        return binding.root
    }

    override fun selected(datum: ArrayList<UserProfile>) {
        if (groupProfile.uuid == "") return

        val txt = "(${datum.size}) contacts selected"
        binding.groupSelectedText.text = txt
        binding.groupAdd.setOnClickListener {
            if (datum.isEmpty()) return@setOnClickListener
            if (myUsers.isEmpty()) return@setOnClickListener
            dialogViewModel.setDisableBackPress(false)
            dialogViewModel.setDialogMessage("Adding. Please wait")
            requireActivity().supportFragmentManager.beginTransaction().addToBackStack("dialog")
                .replace(R.id.group_chat_root, progressFragment).commit()

            timeRef.setValue(ServerValue.TIMESTAMP).addOnSuccessListener {
                timeRef.get().addOnSuccessListener { snapshot ->
                    if (snapshot.exists()) {
                        val rawTime = snapshot.value.toString()
                        var uniqueQueryableTime = rawTime.toLong()
                        var chatData = ChatData(
                            senderuid = myAuth,
                            uniqueQuerableTime = rawTime,
                            timesent = rawTime,
                            groupUid = groupProfile.uuid,
                            messagetype = MessageType.ADDED_TO_GROUP
                        )

                        for ((index, auths) in datum.withIndex()) {
                            uniqueQueryableTime += 1
                            val myGroupChatRef = FirebaseDatabase.getInstance().reference
                                .child(MY_GROUPS_MESSAGES)
                                .child(myAuth)
                                .child(groupProfile.uuid)
                                .child(uniqueQueryableTime.toString())
                            chatData.timesent = uniqueQueryableTime.toString()
                            chatData.uniqueQuerableTime = uniqueQueryableTime.toString()
                            chatData.msg = "You added ${auths.name}"
                            myGroupChatRef.setValue(chatData)

                            val groupAddedUserRef = FirebaseDatabase.getInstance().reference
                                .child(MY_GROUPS_MESSAGES)
                                .child(auths.uid)
                                .child(groupProfile.uuid)
                                .child(uniqueQueryableTime.toString())
                            chatData.msg = "You were added to ${groupProfile.groupName}"
                            chatData.timesent = uniqueQueryableTime.toString()
                            chatData.uniqueQuerableTime = uniqueQueryableTime.toString()
                            groupAddedUserRef.setValue(chatData)

                            if (index == datum.size - 1) {
                                val groupRef = FirebaseDatabase.getInstance().reference.child(GROUPS).child(groupProfile.uuid)
                                groupRef.get().addOnSuccessListener { groupSnapshot ->
                                    var groupData = groupSnapshot.getValue(GroupProfileData::class.java)!!
                                    for (d in datum) groupData.members.add(d.uid)
                                    groupRef.setValue(groupData).addOnSuccessListener {
                                        requireActivity().supportFragmentManager.beginTransaction().remove(progressFragment).commit()
                                        Toast.makeText(requireContext(), "Successful", Toast.LENGTH_LONG).show()
                                        requireActivity().finish()
                                    }.addOnFailureListener { removeProgress() }
                                }.addOnFailureListener { removeProgress() }
                            }
                        }
                    }
                }.addOnFailureListener { removeProgress() }
            }.addOnFailureListener { removeProgress() }
        }
    }

    private fun removeProgress(failed: Boolean = true) {
        requireActivity().supportFragmentManager.beginTransaction().remove(progressFragment).commit()
        if (failed) {
            Toast.makeText(requireContext(), "Please retry", Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        const val TRANSFER_HISTORY = "user_coins_transfer"
        const val USER_DETAILS = "user_details"
        const val CHAT_REFERENCE = "chat_reference"
        const val CHAT = "chat"
        const val GROUPS = "groups"
        const val MY_GROUPS_MESSAGES = "my_groups_messages"
        const val CHAT_DISPLAY_DATA = "ChatDisplayData"
        const val GROUP_DISPLAY_DATA = "group_display_data"
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.group_add_contact_back -> requireActivity().onBackPressed()
        }
    }
}

class AddGroupMembersAdapter : RecyclerView.Adapter<AddGroupMembersAdapter.ViewHOlder>() {
    lateinit var selectedContactHelper: SelectedContactHelper
    var dataset: ArrayList<UserProfile> = arrayListOf()
    var selected: ArrayList<UserProfile> = arrayListOf()
    var members: ArrayList<String> = arrayListOf()
    lateinit var context: Context

    init {
        setHasStableIds(true)
    }

    class ViewHOlder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val rowGroupImage: ShapeableImageView = itemView.findViewById(R.id.row_group_image)
        val rowGroupName: TextView = itemView.findViewById(R.id.row_group_name)
        val groupMemberTag: TextView = itemView.findViewById(R.id.row_group_member_tag)
        val rowGroupNumber: TextView = itemView.findViewById(R.id.row_group_number)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHOlder {
        context = parent.context
        val view = LayoutInflater.from(context).inflate(R.layout.row_group_members, parent, false)
        return ViewHOlder(view)
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onBindViewHolder(holder: ViewHOlder, position: Int) {
        val datum = dataset[position]
        Glide.with(context).load(datum.image).error(R.drawable.ic_avatar).centerCrop().into(holder.rowGroupImage)
        holder.rowGroupNumber.text = datum.phone
        holder.rowGroupName.text = datum.name
        if (datum.uid in members) {
            holder.itemView.isEnabled = false
            holder.groupMemberTag.text = "Member"
        } else {
            holder.groupMemberTag.text = "Add"
        }
        holder.itemView.setOnClickListener {
            if (datum.uid in members) return@setOnClickListener
            if (datum in selected) selected.remove(datum) else selected.add(datum)
            notifyDataSetChanged()
            selectedContactHelper.selected(selected)
        }
        if (datum in selected) holder.itemView.setBackgroundColor(Color.parseColor("#1DC194"))
        else holder.itemView.setBackgroundColor(Color.TRANSPARENT)
    }

    override fun getItemCount() = dataset.size

    override fun getItemId(position: Int) = position.toLong()
}

interface SelectedContactHelper {
    fun selected(datum: ArrayList<UserProfile>)
}