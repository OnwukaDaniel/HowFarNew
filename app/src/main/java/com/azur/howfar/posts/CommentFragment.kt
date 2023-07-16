package com.azur.howfar.posts

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.azur.howfar.R
import com.azur.howfar.databinding.FragmentCommentBinding
import com.azur.howfar.livedata.ValueEventLiveData
import com.azur.howfar.models.EventListenerType.onDataChange
import com.azur.howfar.models.Moment
import com.azur.howfar.models.MomentComment
import com.azur.howfar.models.MomentDetails
import com.azur.howfar.models.UserProfile
import com.azur.howfar.utils.Util
import com.azur.howfar.viewmodel.UserProfileViewmodel
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.imageview.ShapeableImageView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue

class CommentFragment : BottomSheetDialogFragment {
    lateinit var binding: FragmentCommentBinding
    var commentAdapter = CommentAdapter()
    private var comments: ArrayList<MomentDetails> = arrayListOf()
    private var commentsKey: ArrayList<String> = arrayListOf()
    private var user = FirebaseAuth.getInstance().currentUser
    var post: Moment? = null
    private var myProfile = UserProfile()
    private var cmt = MomentDetails()
    private var timeRef = FirebaseDatabase.getInstance().reference
    private var recentCommentTime = ""
    private val userProfileViewModel by activityViewModels<UserProfileViewmodel>()

    constructor() {
    }

    constructor(post: Moment) {
        //this.comments = momentDetails
        this.post = post
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentCommentBinding.inflate(inflater, container, false)
        initMain()
        userProfileViewModel.userProfile.observe(viewLifecycleOwner) {
            myProfile = it
        }
        return binding.root
    }

    private fun initMain() {
        commentAdapter.comments = comments
        commentAdapter.post = post!!
        binding.rvComments.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
        binding.rvComments.adapter = commentAdapter
        binding.tvCommentCount.text = commentAdapter.itemCount.toString()
        val commentsRef = FirebaseDatabase.getInstance().reference
            .child(MOMENT_DETAILS)
            .child(post!!.creatorUid)
            .child(post!!.timePosted)
        ValueEventLiveData(commentsRef).observe(viewLifecycleOwner) {
            when (it.second) {
                onDataChange -> {
                    for (x in it.first.children) {
                        val comment = x.getValue(MomentDetails::class.java)!!
                        if (recentCommentTime == comment.localTime && comment.localTime != "") {
                            val pos = comments.indexOf(cmt)
                            comments[pos] = comment
                            commentAdapter.notifyItemChanged(pos)
                            binding.tvCommentCount.text = comments.size.toString()
                        } else if (comment.comment.profileUid + comment.time !in commentsKey && comment.comment.profileUid != "") {
                            comments.add(comment)
                            commentsKey.add(comment.comment.profileUid + comment.time)
                            commentAdapter.notifyItemInserted(comments.size)
                            binding.tvCommentCount.text = comments.size.toString()
                        }
                    }
                }
            }
        }

        binding.btnsend.setOnClickListener {
            val comment = binding.etComment.text.toString()
            if (comment.isNotEmpty()) {
                recentCommentTime = System.currentTimeMillis().toString()
                cmt = MomentDetails(
                    timeMomentPosted = post!!.timePosted,
                    localTime = recentCommentTime,
                    comment = MomentComment(profileUid = myProfile.uid, profileName = myProfile.name, profileComment = comment, profilePhoto = myProfile.image)
                )
                comments.add(cmt)
                commentAdapter.notifyItemInserted(comments.size)
                binding.rvComments.scrollToPosition(commentAdapter.itemCount - 1)
                binding.etComment.setText("")

                timeRef = FirebaseDatabase.getInstance().reference.child("time").child(user!!.uid)
                timeRef.setValue(ServerValue.TIMESTAMP).addOnSuccessListener {
                    timeRef.get().addOnSuccessListener { timeSnapshot ->
                        if (timeSnapshot.exists()) {
                            val timeSent = timeSnapshot.value.toString()
                            cmt.time = timeSent
                            val postActivityRef = FirebaseDatabase.getInstance().reference
                                .child(MOMENT_DETAILS)
                                .child(post!!.creatorUid)
                                .child(post!!.timePosted)
                                .child(timeSent)
                            postActivityRef.setValue(cmt).addOnSuccessListener{
                                Util.sendNotification(message = "New Comment", body = "You have a new comment on your post", receiverUid = post!!.creatorUid,
                                    view = "post comment")
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val TRANSFER_HISTORY = "user_coins_transfer"
        const val MOMENT_DETAILS = "MOMENT_DETAILS"
    }
}

class CommentAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    lateinit var post: Moment
    var comments: ArrayList<MomentDetails> = arrayListOf()
    private lateinit var context: Context
    private var user = FirebaseAuth.getInstance().currentUser
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        context = parent.context
        return CommentViewHOlder(LayoutInflater.from(context).inflate(R.layout.item_comment, parent, false))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        (holder as CommentViewHOlder).setData(position)
    }

    override fun getItemCount() = comments.size

    inner class CommentViewHOlder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgUser: ShapeableImageView = itemView.findViewById(R.id.imgUser)
        private val tvComment: TextView = itemView.findViewById(R.id.tvComment)
        private val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        val tvUserName: TextView = itemView.findViewById(R.id.tvUserName)
        private val tvCreator: TextView = itemView.findViewById(R.id.tvCreator)

        fun setData(position: Int) {
            val comment = comments[position]
            if (comment.comment.profileUid != "") {
                tvCreator.visibility = if (comment.comment.profileUid == post.creatorUid) View.VISIBLE else View.GONE
                Glide.with(context).load(comment.comment.profilePhoto).circleCrop().into(imgUser)
                tvComment.text = comment.comment.profileComment
                tvDate.text = Util.formatSmartDateTime(comment.time)
                tvUserName.text = comment.comment.profileName
            }
        }
    }
}