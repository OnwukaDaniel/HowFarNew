package com.azur.howfar.howfarchat.groupChat

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.azur.howfar.R
import com.azur.howfar.databinding.FragmentGroupProfileBinding
import com.azur.howfar.howfarchat.chat.ChatActivity2
import com.azur.howfar.models.ChatData
import com.azur.howfar.models.GroupProfileData
import com.azur.howfar.models.MessageType
import com.azur.howfar.models.UserProfile
import com.azur.howfar.utils.ImageCompressor
import com.azur.howfar.utils.Util
import com.azur.howfar.viewmodel.DialogViewModel
import com.azur.howfar.viewmodel.GroupProfileViewModel
import com.bumptech.glide.Glide
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageView
import com.canhub.cropper.options
import com.google.android.material.imageview.ShapeableImageView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.storage.FirebaseStorage
import java.io.ByteArrayInputStream

class FragmentGroupProfile : Fragment(), View.OnClickListener {
    private lateinit var binding: FragmentGroupProfileBinding
    private val groupProfileViewModel by activityViewModels<GroupProfileViewModel>()
    private val dialogViewModel: DialogViewModel by activityViewModels()
    private val groupMembersAdapter = GroupMembersAdapter()
    private val myAuth = FirebaseAuth.getInstance().currentUser!!.uid
    private var imagePair: Pair<ByteArrayInputStream, ByteArray>? = null
    private val dataset: ArrayList<UserProfile> = arrayListOf()
    private var groupProfileData = GroupProfileData()

    private var permissionsStorage = arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)

    @SuppressLint("NotifyDataSetChanged")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentGroupProfileBinding.inflate(inflater, container, false)
        binding.groupProfileBack.setOnClickListener(this)
        binding.groupAddMembers.setOnClickListener(this)
        binding.groupProfileImage.setOnClickListener(this)
        groupProfileViewModel.groupProfile.observe(viewLifecycleOwner) {
            groupProfileData = it
            if (myAuth in it.admins) binding.groupAddMembers.visibility = View.VISIBLE
            var count = 0
            for (i in it.members) {
                val userPref = FirebaseDatabase.getInstance().reference.child(USER_DETAILS).child(i)
                userPref.get().addOnSuccessListener { snapshot ->
                    if (snapshot.exists()) {
                        val userProfile = snapshot.getValue(UserProfile::class.java)!!
                        if (userProfile !in dataset) {
                            dataset.add(userProfile)
                            count++
                        }
                    }
                    if (count >= it.members.size) {
                        dataset.sortWith(compareByDescending { pro -> pro.name })
                        dataset.reverse()
                        groupMembersAdapter.notifyDataSetChanged()
                    }
                }
            }
            groupMembersAdapter.activity = requireActivity()
            groupMembersAdapter.groupProfileData = groupProfileData
            groupMembersAdapter.dataset = dataset
            binding.groupProfileMembersRv.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
            binding.groupProfileMembersRv.adapter = groupMembersAdapter
            binding.groupName.text = it.groupName
            binding.groupProfileMembers.text = it.members.size.toString()
            if (isAdded && activity != null) Glide.with(requireContext()).load(it.groupImage).error(R.drawable.ic_avatar).into(binding.groupProfileImage)

        }
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        requireActivity().window.statusBarColor = Color.parseColor("#282B39")
    }

    private fun openImagePick() {
        if (Util.permissionsAvailable(permissionsStorage, requireContext())) {
            cropImage.launch(
                options {
                    this.setActivityTitle("Group image")
                    this.setAllowFlipping(true)
                    this.setAllowRotation(true)
                    this.setAutoZoomEnabled(true)
                    this.setBackgroundColor(Color.BLACK)
                    this.setImageSource(includeGallery = true, includeCamera = true)
                    setGuidelines(CropImageView.Guidelines.ON)
                }
            )
        } else {
            ActivityCompat.requestPermissions(requireActivity(), permissionsStorage, 36)
        }
    }

    private val cropImage = registerForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) {
            val uriContent = result.uriContent!!
            val uriFilePath = result.getUriFilePath(requireContext()) // optional usage
            try {
                imagePair = ImageCompressor.compressImage(uriContent, requireContext(), null)
                Glide.with(this).load(imagePair!!.second).centerCrop().into(binding.groupProfileImage)
                createGroup(imagePair!!)
            } catch (e: Exception) {
                println("Exception *********************************************************** ${e.printStackTrace()}")
            }
        } else {
            // an error occurred
            val exception = result.error
            exception!!.printStackTrace()
        }
    }

    private fun createGroup(dataPair: Pair<ByteArrayInputStream, ByteArray>) {
        if (groupProfileData.uuid == "") return
        dialogViewModel.setDisableBackPress(true)
        val supportFragmentManager = requireActivity().supportFragmentManager
        //supportFragmentManager.beginTransaction().replace(R.id.group_chat_root, progressFragment).commit()

        val imageStream = dataPair.first
        val imageRef = FirebaseStorage.getInstance().reference.child(FragmentConfirmCreateGroup.GROUPS).child(groupProfileData.uuid).child("group_image")
        val imageUploadTask = imageRef.putStream(imageStream)
        imageUploadTask.continueWith { task ->
            if (!task.isSuccessful) task.exception?.let { it ->
                //supportFragmentManager.beginTransaction().remove(progressFragment).commit()
                throw  it
            }
            imageRef.downloadUrl.addOnSuccessListener { uri ->
                groupProfileData.groupImage = uri.toString()

                val groupRef = FirebaseDatabase.getInstance().reference.child(GROUPS).child(groupProfileData.uuid)
                groupRef.get().addOnSuccessListener { groupSnapshot ->
                    if (groupSnapshot.exists()) {
                        var groupData = groupSnapshot.getValue(GroupProfileData::class.java)!!
                        groupData.groupImage = uri.toString()
                        groupRef.setValue(groupData).addOnSuccessListener {
                            showToast("Upload success")
                            //supportFragmentManager.beginTransaction().remove(progressFragment).commit()
                        }
                    }
                }
            }.addOnFailureListener {
                //supportFragmentManager.beginTransaction().remove(progressFragment).commit()
                showToast("Upload failed!!! Retry")
                return@addOnFailureListener
            }
        }
    }

    private fun showToast(msg: String) {
        try {
            Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
        }
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.group_profile_image -> {
                if (myAuth !in groupProfileData.members) return else openImagePick()
            }
            R.id.group_profile_back -> requireActivity().onBackPressed()
            R.id.group_add_members -> {
                val groupRef = FirebaseDatabase.getInstance().reference.child(GROUPS).child(groupProfileData.uuid)
                groupRef.get().addOnSuccessListener { groupSnapshot ->
                    if (groupSnapshot.exists()) {
                        val groupInfo = groupSnapshot.getValue(GroupProfileData::class.java)!!
                        if (myAuth !in groupInfo.admins) {
                            binding.groupAddMembers.visibility = View.GONE
                        } else {
                            requireActivity().supportFragmentManager.beginTransaction().addToBackStack("add")
                                .setCustomAnimations(R.anim.enter_right_to_left, R.anim.exit_right_to_left)
                                .replace(R.id.group_chat_root, FragmentAddContactToGroup())
                                .commit()
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val USER_DETAILS = "user_details"
        const val CHAT_REFERENCE = "chat_reference"
        const val CHAT = "chat"
        const val GROUP_DISPLAY_DATA = "group_display_data"
        const val GROUPS = "groups"
        const val GROUPS_MESSAGES = "groups_messages"
        const val MY_GROUPS_MESSAGES = "my_groups_messages"
        const val TRANSFER_HISTORY = "user_coins_transfer"
        const val GROUP_IMAGES = "GroupImages"
        const val GROUP_VIDEOS = "GroupImages"
        const val groupOrChat = "groupOrChat"
    }
}

class GroupMembersAdapter : RecyclerView.Adapter<GroupMembersAdapter.ViewHOlder>() {
    lateinit var groupProfileData: GroupProfileData
    private val myAuth = FirebaseAuth.getInstance().currentUser!!.uid
    var dataset: ArrayList<UserProfile> = arrayListOf()
    lateinit var context: Context
    lateinit var activity: Context

    init {
        setHasStableIds(true)
    }

    class ViewHOlder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val rowGroupImage: ShapeableImageView = itemView.findViewById(R.id.row_group_image)
        val rowGroupName: TextView = itemView.findViewById(R.id.row_group_name)
        val rowGroupNumber: TextView = itemView.findViewById(R.id.row_group_number)
        val rowGroupMemberTag: TextView = itemView.findViewById(R.id.row_group_member_tag)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHOlder {
        context = parent.context
        val view = LayoutInflater.from(context).inflate(R.layout.row_group_members, parent, false)
        return ViewHOlder(view)
    }

    override fun onBindViewHolder(holder: ViewHOlder, position: Int) {
        val datum = dataset[position]
        Glide.with(context).load(datum.image).error(R.drawable.ic_avatar).centerCrop().into(holder.rowGroupImage)
        holder.rowGroupNumber.text = datum.phone
        holder.rowGroupName.text = datum.name
        holder.rowGroupMemberTag.text = if (datum.uid in groupProfileData.admins) "Admin" else "Member"
        holder.itemView.setOnClickListener {
            if (datum.uid == myAuth) return@setOnClickListener
            val popup = PopupMenu(context, holder.itemView)
            if (myAuth !in groupProfileData.admins) popup.inflate(R.menu.group_profile_user) else popup.inflate(R.menu.group_profile_admin_user)
            popup.gravity = Gravity.CENTER
            popup.show()
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.message -> {
                        context.startActivity(Intent(context, ChatActivity2::class.java).putExtra("data", datum.uid))
                        (activity as AppCompatActivity).finish()
                    }
                    R.id.remove_user -> {
                        val groupRef = FirebaseDatabase.getInstance().reference.child(GROUPS).child(groupProfileData.uuid)
                        groupRef.get().addOnSuccessListener { groupSnapshot ->
                            if (groupSnapshot.exists()) {
                                var groupProfile2 = groupSnapshot.getValue(GroupProfileData::class.java)!!
                                groupProfile2.members.remove(datum.uid)
                                groupProfile2.admins.remove(datum.uid)
                                if (datum.uid in groupProfile2.admins && groupProfile2.admins.size == 1 && groupProfile2.members.isNotEmpty()) {
                                    groupProfile2.members.shuffle()
                                    groupProfile2.admins.add(groupProfile2.members.first())
                                }
                                groupRef.setValue(groupProfile2).addOnSuccessListener {
                                    val timeRef = FirebaseDatabase.getInstance().reference.child("time").child(myAuth)
                                    timeRef.setValue(ServerValue.TIMESTAMP).addOnSuccessListener {
                                        timeRef.get().addOnSuccessListener { snapshot ->
                                            if (snapshot.exists()) {
                                                val rawTime = snapshot.value.toString()
                                                var chatData = ChatData(
                                                    senderuid = myAuth,
                                                    uniqueQuerableTime = rawTime,
                                                    timesent = rawTime,
                                                    groupUid = groupProfileData.uuid,
                                                    messagetype = MessageType.LEAVE_GROUP,
                                                )
                                                for (i in dataset) {
                                                    when (i.uid) {
                                                        datum.uid -> {
                                                            val mainUserRef = FirebaseDatabase.getInstance().reference
                                                                .child(MY_GROUPS_MESSAGES)
                                                                .child(datum.uid)
                                                                .child(groupProfileData.uuid)
                                                                .child(rawTime)
                                                            chatData.msg = "You were removed"
                                                            mainUserRef.setValue(chatData)
                                                        }
                                                        myAuth -> {
                                                            chatData.msg = "You removed ${i.name}"
                                                            val myUser = FirebaseDatabase.getInstance().reference
                                                                .child(MY_GROUPS_MESSAGES)
                                                                .child(myAuth)
                                                                .child(groupProfileData.uuid)
                                                                .child(rawTime)
                                                            myUser.setValue(chatData)
                                                        }
                                                        else -> {
                                                            chatData.msg = "${i.name} was removed"
                                                            val myUser = FirebaseDatabase.getInstance().reference
                                                                .child(MY_GROUPS_MESSAGES)
                                                                .child(i.uid)
                                                                .child(groupProfileData.uuid)
                                                                .child(rawTime)
                                                            myUser.setValue(chatData)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }.addOnFailureListener {
                                    Toast.makeText(context, "Failed to remove ${datum.name}", Toast.LENGTH_LONG).show()
                                }
                            } else Toast.makeText(context, "Group doesn't exist", Toast.LENGTH_LONG).show()
                        }.addOnFailureListener {
                            Toast.makeText(context, "Failed to remove ${datum.name}", Toast.LENGTH_LONG).show()
                        }
                    }
                    R.id.make_admin -> {
                        val timeRef = FirebaseDatabase.getInstance().reference.child("time").child(myAuth)
                        timeRef.setValue(ServerValue.TIMESTAMP).addOnSuccessListener {
                            timeRef.get().addOnSuccessListener { snapshot ->
                                if (snapshot.exists()) {
                                    val rawTime = snapshot.value.toString()
                                    val groupRef = FirebaseDatabase.getInstance().reference.child(GROUPS).child(groupProfileData.uuid)
                                    groupRef.get().addOnSuccessListener {
                                        if (it.exists()) {
                                            var temp = it.getValue(GroupProfileData::class.java)!!
                                            temp.admins.add(datum.uid)
                                            groupRef.setValue(temp).addOnSuccessListener {
                                                var chatData = ChatData(
                                                    senderuid = myAuth,
                                                    uniqueQuerableTime = rawTime,
                                                    timesent = rawTime,
                                                    groupUid = groupProfileData.uuid,
                                                    messagetype = MessageType.GROUP_ADMIN,
                                                )
                                                val mainUserRef = FirebaseDatabase.getInstance().reference
                                                    .child(MY_GROUPS_MESSAGES)
                                                    .child(datum.uid)
                                                    .child(groupProfileData.uuid)
                                                    .child(rawTime)
                                                chatData.msg = "You were now admin"
                                                mainUserRef.setValue(chatData)
                                                Toast.makeText(context, "${datum.name} is now an admin", Toast.LENGTH_LONG).show()
                                            }.addOnFailureListener {
                                                Toast.makeText(context, "Unable to make ${datum.name} an admin. Retry.", Toast.LENGTH_LONG).show()
                                            }
                                        } else Toast.makeText(context, "Group doesn't exist", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        }
                    }
                }
                return@setOnMenuItemClickListener true
            }
        }
    }

    override fun getItemCount() = dataset.size

    override fun getItemId(position: Int) = position.toLong()

    override fun getItemViewType(position: Int) = position

    companion object {
        const val GROUPS = "groups"
        const val GROUPS_MESSAGES = "groups_messages"
        const val MY_GROUPS_MESSAGES = "my_groups_messages"
        const val GROUP_DISPLAY_DATA = "group_display_data"
        const val TRANSFER_HISTORY = "user_coins_transfer"
        const val USER_DETAILS = "user_details"
        const val GROUP_IMAGES = "GroupImages"
        const val GROUP_VIDEOS = "GroupImages"
        const val groupOrChat = "groupOrChat"
    }
}