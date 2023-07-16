package com.azur.howfar.chat

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.azur.howfar.R
import com.azur.howfar.databinding.FragmentMessageBinding
import com.azur.howfar.howfarchat.chat.FragmentDisplayImage
import com.azur.howfar.models.ChatData
import com.azur.howfar.models.ParticipantTempData
import com.azur.howfar.models.UserProfile
import com.azur.howfar.utils.Util
import com.azur.howfar.viewmodel.ChatDataViewModel
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class MessageFragment : Fragment() {
    lateinit var binding: FragmentMessageBinding
    private val user = FirebaseAuth.getInstance().currentUser
    private var dataset: ArrayList<ChatData> = arrayListOf()
    private var chatUserAdapter = ChatUserAdapter()
    private val chatDataViewModel by activityViewModels<ChatDataViewModel>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentMessageBinding.inflate(inflater, container, false)
        initView()
        return binding.root
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun initView() {
        chatUserAdapter.activity = requireActivity()
        chatUserAdapter.dataset = dataset
        binding.rvMessage.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
        binding.rvMessage.adapter = chatUserAdapter
        chatDataViewModel.chatData.observe(viewLifecycleOwner) {
            dataset.clear()
            dataset.addAll(it)
            chatUserAdapter.notifyDataSetChanged()
        }
    }

    companion object {
        const val GUEST_USERS_CHAT = "GUEST_USERS_CHAT"
    }
}

class ChatUserAdapter : RecyclerView.Adapter<ChatUserAdapter.ChatUserViewHolder>() {
    private var context: Context? = null
    lateinit var activity: Activity
    var dataset: MutableList<ChatData> = ArrayList()
    private val user = FirebaseAuth.getInstance().currentUser
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatUserViewHolder {
        context = parent.context
        return ChatUserViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_chatusers, parent, false))
    }

    override fun onBindViewHolder(holder: ChatUserViewHolder, position: Int) {
        holder.setData(position)
    }

    override fun getItemCount() = dataset.size

    inner class ChatUserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private var imguser: ImageView = itemView.findViewById(R.id.imguser)
        private var tvusername: TextView = itemView.findViewById(R.id.tvusername)
        private var tvlastchet: TextView = itemView.findViewById(R.id.tvlastchet)
        private var tvtime: TextView = itemView.findViewById(R.id.tvtime)
        private var tvcountry: TextView = itemView.findViewById(R.id.tvcountry)
        fun setData(position: Int) {
            val chatUser = dataset[position]
            try {
                Glide.with(itemView).load(getOtherParticipant(chatUser).tempImage).circleCrop().into(imguser)
            } catch (e: Exception) {
            }
            tvusername.text = getOtherParticipant(chatUser).tempName
            when {
                chatUser.imageData.storageLink != "" -> tvlastchet.text = chatUser.imageData.displayMessage
                else -> tvlastchet.text = chatUser.msg
            }
            tvtime.text = Util.formatSmartDateTime(chatUser.uniqueQuerableTime)
            itemView.setOnClickListener {
                context!!.startActivity(Intent(context, GuestChatActivity::class.java).putExtra("userId", getOtherParticipant(chatUser).uid))
            }
            FirebaseDatabase.getInstance().reference.child(USER_DETAILS).child(getOtherParticipant(chatUser).uid).get().addOnSuccessListener {
                if (it.exists()) {
                    val userProfile = it.getValue(UserProfile::class.java)!!
                    try {
                        Glide.with(itemView).load(userProfile.image).circleCrop().into(imguser)
                    } catch (e: Exception) {
                    }
                    tvusername.text = userProfile.name
                    tvcountry.text = userProfile.countryCode.uppercase()
                    imguser.setOnClickListener {
                        clickImage(userProfile.image)
                    }
                }
            }
            imguser.setOnClickListener {
                clickImage(getOtherParticipant(chatUser).tempImage)
            }
        }

        private fun getOtherParticipant(chatUser: ChatData): ParticipantTempData {
            for (i in chatUser.participantsTempData) if (i.uid != user!!.uid) return i
            return ParticipantTempData()
        }

        private fun clickImage(image: String) {
            val fragment = FragmentDisplayImage()
            val bundle = Bundle()
            bundle.putString("image", image)
            fragment.arguments = bundle
            (activity as AppCompatActivity).supportFragmentManager.beginTransaction().addToBackStack("image")
                .replace(R.id.guest_messages_root, fragment).commit()
        }
    }

    companion object {
        const val USER_DETAILS = "user_details"
    }
}