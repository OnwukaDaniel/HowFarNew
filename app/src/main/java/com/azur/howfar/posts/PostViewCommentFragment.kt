package com.azur.howfar.posts

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.azur.howfar.R
import com.azur.howfar.databinding.FragmentPostViewCommentBinding
import com.azur.howfar.models.Moment
import com.azur.howfar.models.MomentComment
import com.azur.howfar.models.MomentDetails
import com.azur.howfar.models.UserProfile
import com.azur.howfar.utils.Util
import com.azur.howfar.workManger.MomentWorkManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.gson.Gson

class PostViewCommentFragment : Fragment(), View.OnClickListener {
    private lateinit var binding: FragmentPostViewCommentBinding
    private var user = FirebaseAuth.getInstance().currentUser
    private var timeRef = FirebaseDatabase.getInstance().reference
    private var moment = Moment()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentPostViewCommentBinding.inflate(inflater, container, false)
        moment = Gson().fromJson(requireArguments().getString("data"), Moment::class.java)!!
        binding.btnSend.setOnClickListener(this)
        timeRef = FirebaseDatabase.getInstance().reference.child("time").child(user!!.uid)
        return binding.root
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.btn_send -> {
                val input = binding.editTextComment.text.trim().toString()
                if (input == "") return
                Toast.makeText(requireContext(), "Reply sending...", Toast.LENGTH_LONG).show()
                binding.editTextComment.setText("")
                val ref = FirebaseDatabase.getInstance().reference.child(MomentWorkManager.USER_DETAILS).child(user!!.uid)
                ref.get().addOnSuccessListener { userProfile ->
                    if (userProfile.exists()) {
                        val myProfile = userProfile.getValue(UserProfile::class.java)!!
                        var cmt = MomentDetails(
                            timeMomentPosted = moment.timePosted,
                            localTime = System.currentTimeMillis().toString(),
                            comment = MomentComment(
                                profileUid = myProfile.uid,
                                profileName = myProfile.name,
                                profileComment = input,
                                profilePhoto = myProfile.image
                            )
                        )
                        timeRef.setValue(ServerValue.TIMESTAMP).addOnSuccessListener {
                            timeRef.get().addOnSuccessListener { timeSnapshot ->
                                if (timeSnapshot.exists()) {
                                    val timeSent = timeSnapshot.value.toString()
                                    cmt.time = timeSent
                                    val postActivityRef = FirebaseDatabase.getInstance().reference
                                        .child(CommentFragment.MOMENT_DETAILS)
                                        .child(moment.creatorUid)
                                        .child(moment.timePosted)
                                        .child(timeSent)
                                    postActivityRef.setValue(cmt).addOnSuccessListener {
                                        Util.sendNotification(
                                            message = "New Comment",
                                            body = "You have a new comment on your post",
                                            receiverUid = moment.creatorUid,
                                            view = "post comment"
                                        )
                                    }
                                    Toast.makeText(requireContext(), "Reply sent.", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}