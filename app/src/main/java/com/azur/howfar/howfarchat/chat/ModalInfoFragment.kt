package com.azur.howfar.howfarchat.chat

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.imageview.ShapeableImageView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.azur.howfar.R
import com.azur.howfar.databinding.ChatInfoDialogFragmentBinding
import com.azur.howfar.models.ChatData
import com.azur.howfar.models.UserProfile
import com.azur.howfar.utils.TimeUtils
import com.azur.howfar.utils.Util
import com.azur.howfar.viewmodel.SingleChatInfoViewModel

class ModalInfoFragment : BottomSheetDialogFragment() {
    private lateinit var binding: ChatInfoDialogFragmentBinding
    private val singleChatInfoViewModel by activityViewModels<SingleChatInfoViewModel>()
    private val participantsAdapter = ParticipantsAdapter()
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = ChatInfoDialogFragmentBinding.inflate(inflater, container, false)
        singleChatInfoViewModel.chatData.observe(viewLifecycleOwner) {
            participantsAdapter.chatData = it!!
            participantsAdapter.dataset = it.participants
            binding.rvParticipants.adapter = participantsAdapter
            binding.rvParticipants.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
        }
        return binding.root
    }
}

class ParticipantsAdapter : RecyclerView.Adapter<ParticipantsAdapter.ViewHolder>() {
    var dataset: ArrayList<String> = arrayListOf()
    private lateinit var context: Context
    var chatData = ChatData()
    private val myAuth = FirebaseAuth.getInstance().currentUser!!.uid

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val infoImage: ShapeableImageView = itemView.findViewById(R.id.info_image)
        val infoName: TextView = itemView.findViewById(R.id.info_name)
        val infoTime: TextView = itemView.findViewById(R.id.info_time)
        val infoTick: ImageView = itemView.findViewById(R.id.info_tick)

    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        context = parent.context
        val view = LayoutInflater.from(parent.context).inflate(R.layout.row_chat_info, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val datum = dataset[position]
        FirebaseDatabase.getInstance().reference.child(USER_DETAILS).child(datum).get().addOnSuccessListener {
            if (it.exists()) {
                val userData = it.getValue(UserProfile::class.java)!!
                Glide.with(context).load(userData.image).error(R.drawable.ic_avatar).centerCrop().into(holder.infoImage)
                when {
                    datum != myAuth -> {
                        holder.infoName.text = userData.name
                        if (chatData.sent) holder.infoTick.setImageResource(R.drawable.ic_sent_msg)
                        if (chatData.delivered) holder.infoTick.setImageResource(R.drawable.ic_delivered)
                        if (chatData.read) holder.infoTick.setImageResource(R.drawable.ic_read)
                        val time = TimeUtils.UTCToLocal(chatData.timeseen)
                        holder.infoTime.text = Util.formatTime(time)
                    }
                    else -> {
                        holder.infoName.text = "You"
                        holder.infoTick.visibility = View.GONE
                        val time = TimeUtils.UTCToLocal(chatData.uniqueQuerableTime)
                        holder.infoTime.text = Util.formatTime(time)
                    }
                }
            }
        }
    }

    override fun getItemCount() = dataset.size

    companion object {
        const val USER_DETAILS = "user_details"
    }
}