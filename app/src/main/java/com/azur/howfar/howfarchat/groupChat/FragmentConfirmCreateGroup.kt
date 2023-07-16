package com.azur.howfar.howfarchat.groupChat

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.imageview.ShapeableImageView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.storage.FirebaseStorage
import com.azur.howfar.R
import com.azur.howfar.databinding.FragmentConfirmCreateGroupBinding
import com.azur.howfar.dilog.ProgressFragment
import com.azur.howfar.models.ChatData
import com.azur.howfar.models.GroupProfileData
import com.azur.howfar.models.MessageType
import com.azur.howfar.models.MessageType.ADDED_TO_GROUP
import com.azur.howfar.models.MessageType.CREATED_GROUP
import com.azur.howfar.models.UserProfile
import com.azur.howfar.viewmodel.ContactViewModel
import com.google.android.material.snackbar.Snackbar
import java.io.ByteArrayInputStream
import java.util.*

class FragmentConfirmCreateGroup : Fragment() {
    private lateinit var binding: FragmentConfirmCreateGroupBinding
    private var selectedDataset: ArrayList<UserProfile> = arrayListOf()
    private val contactViewModel: ContactViewModel by activityViewModels()
    private var groupProfileData = GroupProfileData()
    private val confirmContactsAdapter = ConfirmContactsAdapter()
    private var timeRef = FirebaseDatabase.getInstance().reference
    private val myAuth = FirebaseAuth.getInstance().currentUser!!.uid
    private var groupRef = FirebaseDatabase.getInstance().reference
    private var dataPair: Pair<ByteArrayInputStream, ByteArray>? = null
    private val uuid = UUID.randomUUID().toString()
    private val progressFragment = ProgressFragment()
    private lateinit var supportFragmentManager: FragmentManager

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentConfirmCreateGroupBinding.inflate(inflater, container, false)
        timeRef = FirebaseDatabase.getInstance().reference.child("time").child(myAuth)
        groupRef = groupRef.child(GROUPS).child(uuid)
        contactViewModel.userprofileList.observe(viewLifecycleOwner) {
            binding.confirmGroupContactsCount.text = it.size.toString()
            selectedDataset = it
            confirmContactsAdapter.dataset = it
            binding.confirmGroupRv.adapter = confirmContactsAdapter
            binding.confirmGroupRv.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
        }
        contactViewModel.imagePair.observe(viewLifecycleOwner) {
            dataPair = it
            Glide.with(requireContext()).load(it.second).centerCrop().into(binding.confirmGroupImage)
        }
        contactViewModel.groupProfile.observe(viewLifecycleOwner) {
            groupProfileData = it
            groupProfileData.uuid = uuid
            binding.confirmGroupName.text = it.groupName
        }
        binding.confirmGroupCancel.setOnClickListener {
            requireActivity().supportFragmentManager.beginTransaction().remove(this).commit()
        }
        binding.confirmGroupCreate.setOnClickListener {
            //if (dataPair == null) return@setOnClickListener
            createGroup()
        }
        return binding.root
    }

    private fun createGroup() {
        supportFragmentManager = requireActivity().supportFragmentManager
        supportFragmentManager.beginTransaction().replace(R.id.confirm_group_root, progressFragment).commit()

        if (dataPair == null) createGroupFinal(groupProfileData)
        else {
            val imageStream = dataPair!!.first
            val imageRef = FirebaseStorage.getInstance().reference.child(GROUPS).child(groupProfileData.uuid).child("group_image")
            val imageUploadTask = imageRef.putStream(imageStream)
            imageUploadTask.continueWith { task ->
                if (!task.isSuccessful) task.exception?.let { it ->
                    supportFragmentManager.beginTransaction().remove(progressFragment).commit()
                    throw  it
                }
                imageRef.downloadUrl.addOnSuccessListener { uri ->
                    groupProfileData.groupImage = uri.toString()
                    createGroupFinal(groupProfileData)
                }.addOnFailureListener {
                    supportFragmentManager.beginTransaction().remove(progressFragment).commit()
                    Toast.makeText(requireContext(), "Upload failed!!! Retry", Toast.LENGTH_LONG).show()
                    return@addOnFailureListener
                }
            }
        }
    }

    private fun createGroupFinal(groupProfileData: GroupProfileData) {
        var groupProfile = groupProfileData
        timeRef.setValue(ServerValue.TIMESTAMP).addOnSuccessListener {
            timeRef.get().addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    val rawTime = snapshot.value.toString()
                    var timeAdd = rawTime.toLong()
                    groupProfile.timeCreated = rawTime
                    groupProfile.admins = arrayListOf(myAuth)

                    var chatData = ChatData(
                        senderuid = myAuth,
                        uniqueQuerableTime = rawTime,
                        timesent = rawTime,
                        msg = "You were added to ${groupProfile.groupName}",
                        groupUid = groupProfile.uuid,
                        messagetype = CREATED_GROUP
                    )

                    groupRef.setValue(groupProfile).addOnSuccessListener {
                        val myRef = FirebaseDatabase.getInstance().reference.child(MY_GROUPS_MESSAGES).child(myAuth).child(groupProfile.uuid).child(rawTime)
                        chatData.msg = "You created ${groupProfile.groupName}"
                        myRef.setValue(chatData)

                        for ((index, auths) in selectedDataset.withIndex()) {
                            timeAdd += 1
                            chatData.messagetype = ADDED_TO_GROUP
                            chatData.uniqueQuerableTime = timeAdd.toString()
                            chatData.timesent = timeAdd.toString()

                            val myRef2 = FirebaseDatabase.getInstance().reference.child(MY_GROUPS_MESSAGES)
                                .child(myAuth)
                                .child(groupProfile.uuid)
                                .child(timeAdd.toString())
                            chatData.msg = "You added ${auths.name}"
                            myRef2.setValue(chatData)

                            val ref = FirebaseDatabase.getInstance().reference.child(MY_GROUPS_MESSAGES).child(auths.uid).child(groupProfile.uuid)
                                .child(timeAdd.toString())
                            chatData.msg = "You were added"
                            chatData.uniqueQuerableTime = timeAdd.toString()
                            chatData.timesent = timeAdd.toString()

                            ref.setValue(chatData).addOnSuccessListener {
                                if (index == selectedDataset.size - 1) {
                                    supportFragmentManager.beginTransaction().remove(progressFragment).commit()
                                    val intent = Intent(requireContext(), GroupChatActivity::class.java)
                                    intent.putExtra("data", chatData.groupUid)
                                    requireActivity().startActivity(intent)
                                    requireActivity().finish()
                                }
                            }.addOnFailureListener {
                                Snackbar.make(binding.root, "Unable to add $auths", Snackbar.LENGTH_LONG).show()
                                if (index == selectedDataset.size - 1) supportFragmentManager.beginTransaction().remove(progressFragment).commit()
                            }
                        }
                    }.addOnFailureListener {
                        Snackbar.make(binding.root, "Unable to create Group. Please retry", Snackbar.LENGTH_LONG).show()
                        supportFragmentManager.beginTransaction().remove(progressFragment).commit()
                    }
                }
            }
        }
    }

    companion object {
        const val USER_DETAILS = "user_details"
        const val CHAT_REFERENCE = "chat_reference"
        const val CHAT = "chat"
        const val GROUPS = "groups"
        const val MY_GROUPS_MESSAGES = "my_groups_messages"
    }
}

class ConfirmContactsAdapter : RecyclerView.Adapter<ConfirmContactsAdapter.ViewHolder>() {

    var dataset: ArrayList<UserProfile> = arrayListOf()
    lateinit var context: Context

    init {
        setHasStableIds(true)
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val image: ShapeableImageView = itemView.findViewById(R.id.contact_card_image)
        val name: TextView = itemView.findViewById(R.id.contact_card_name)
        val number: TextView = itemView.findViewById(R.id.contact_card_phone)
        val card: CardView = itemView.findViewById(R.id.row_contact_card)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        context = parent.context
        val view = LayoutInflater.from(context).inflate(R.layout.row_contact_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val datum = dataset[position]
        //holder.image.setImageURI(Uri.parse(datum.uri))
        holder.name.text = datum.name
        holder.number.text = datum.phone
    }

    override fun getItemCount() = dataset.size

    override fun getItemId(position: Int) = position.toLong()
}