package com.azur.howfar.posts

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.azur.howfar.databinding.FragmentFeedListBinding
import com.azur.howfar.livedata.ChildEventLiveData
import com.azur.howfar.models.EventListenerType.onChildAdded
import com.azur.howfar.models.EventListenerType.onChildChanged
import com.azur.howfar.models.EventListenerType.onChildRemoved
import com.azur.howfar.models.Moment
import com.azur.howfar.models.UserProfile
import com.azur.howfar.user.guestuser.GuestActivity
import com.azur.howfar.viewmodel.UserProfileViewmodel
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class FeedFollowingFragment : Fragment(), OnPostClickListener {
    lateinit var binding: FragmentFeedListBinding
    private var feedAdapter = NormalFeedAdapter()
    private var posts: ArrayList<Moment> = arrayListOf()
    private var user = FirebaseAuth.getInstance().currentUser
    private val listOfFollowers: ArrayList<String> = arrayListOf()
    private val listOfFollowing: ArrayList<String> = arrayListOf()
    private var myProfile = UserProfile()
    private val userProfileViewModel by activityViewModels<UserProfileViewmodel>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentFeedListBinding.inflate(inflater, container, false)
        initView()
        initListener()
        userProfileViewModel.userProfile.observe(viewLifecycleOwner) {
            myProfile = it
            feedAdapter.myProfile = myProfile
        }
        return binding.root
    }

    private fun initListener() {
        val followers = FirebaseDatabase.getInstance().reference.child(GuestActivity.FOLLOWERS).child(user!!.uid)
        val following = FirebaseDatabase.getInstance().reference.child(GuestActivity.FOLLOWING).child(user!!.uid)
        followers.keepSynced(false)
        following.keepSynced(false)

        followers.get().addOnSuccessListener { follower ->
            if (follower.exists()) {
                for (i in follower.children) if (i.value.toString() !in listOfFollowers) listOfFollowers.add(i.value.toString())
            }
            following.get().addOnSuccessListener { followingShot ->
                if (followingShot.exists()) {
                    for (i in followingShot.children) if (i.value.toString() !in listOfFollowing) listOfFollowing.add(i.value.toString())
                }
                val ref = FirebaseDatabase.getInstance().reference.child(MOMENT_DATA)
                ref.keepSynced(false)
                ChildEventLiveData(ref).observe(viewLifecycleOwner) {
                    when (it.second) {
                        onChildAdded -> {
                            val post = it.first.getValue(Moment::class.java)!!
                            if ((post !in posts) && (post.creatorUid in listOfFollowers || post.creatorUid in listOfFollowing)) {
                                posts.add(post)
                                feedAdapter.notifyItemInserted(posts.size)
                                binding.feedProgress.visibility = View.GONE
                            }
                        }
                        onChildChanged -> {
                            val post = it.first.getValue(Moment::class.java)!!
                            for (i in posts) {
                                if (i.timePosted == post.timePosted) {
                                    changePost(i, post)
                                    return@observe
                                }
                            }
                        }
                        onChildRemoved -> {
                            val post = it.first.getValue(Moment::class.java)!!
                            for (i in posts) {
                                if (i.timePosted == post.timePosted && i.creatorUid == post.creatorUid) {
                                    val pos = posts.indexOf(i)
                                    posts.removeAt(pos)
                                    feedAdapter.notifyItemRemoved(pos)
                                    return@observe
                                }
                            }
                        }
                    }
                }
            }.addOnFailureListener {}
        }.addOnFailureListener {}
    }

    private fun changePost(i: Moment, update: Moment) {
        val pos = posts.indexOf(i)
        posts[pos] = update
        feedAdapter.notifyItemChanged(pos)
    }

    private fun initView() {
        feedAdapter.activity = requireActivity()
        feedAdapter.viewLifecycleOwner = viewLifecycleOwner
        feedAdapter.dataset = posts
        binding.rvFeed.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
        binding.rvFeed.adapter = feedAdapter
        feedAdapter.onPostClickListener = this
    }

    override fun onLikeClick(post: Moment) {
    }

    override fun onCommentClick(post: Moment) {
        openFragment(CommentFragment(post))
    }

    private fun openFragment(bottomSheetDialogFragment: BottomSheetDialogFragment) {
        bottomSheetDialogFragment.show(childFragmentManager, bottomSheetDialogFragment.javaClass.simpleName)
    }

    override fun onShareClick(post: Moment) {
    }

    companion object {
        const val MOMENT_DATA = "MOMENT_DATA"
    }
}