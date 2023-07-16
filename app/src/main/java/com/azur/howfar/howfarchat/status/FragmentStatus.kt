package com.azur.howfar.howfarchat.status

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.azur.howfar.R
import com.azur.howfar.databinding.FragmentStatusBinding
import com.azur.howfar.howfarchat.status.StatusType.IMAGE
import com.azur.howfar.howfarchat.status.StatusType.TEXT
import com.azur.howfar.livedata.ChildEventLiveData
import com.azur.howfar.livedata.ValueEventLiveData
import com.azur.howfar.models.EventListenerType.onChildAdded
import com.azur.howfar.models.EventListenerType.onChildRemoved
import com.azur.howfar.models.EventListenerType.onDataChange
import com.azur.howfar.models.StatusUpdateData
import com.azur.howfar.models.UserProfile
import com.azur.howfar.utils.CallUtils
import com.azur.howfar.utils.TimeUtils.timeDiffFromNow
import com.azur.howfar.utils.Util
import com.azur.howfar.viewmodel.UserProfileViewmodel
import com.bumptech.glide.Glide
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.*

class FragmentStatus : Fragment(), View.OnClickListener, StatusDataTransferHelper {
    private lateinit var binding: FragmentStatusBinding
    private lateinit var pref: SharedPreferences
    private val statusAdapter = StatusAdapter()
    private val scope = CoroutineScope(Dispatchers.IO)
    private val statusAdapter1 = StatusAdapter()
    private var timeRef = FirebaseDatabase.getInstance().reference
    private var permissionsStorage = arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
    private val statusViewModel by activityViewModels<StatusViewModel>()
    private val userProfileViewmodel by activityViewModels<UserProfileViewmodel>()
    private var myStatusRef = FirebaseDatabase.getInstance().reference
    private var allyStatusRef = FirebaseDatabase.getInstance().reference
    //private val myAuth = FirebaseAuth.getInstance().currentUser!!.uid
    private val datasetMyStatus: ArrayList<StatusUpdateData> = arrayListOf()
    private var myProfile = UserProfile()
    private var contactFormattedList: ArrayList<String> = arrayListOf()
    private var dataset: ArrayList<ArrayList<StatusUpdateData>> = arrayListOf()

    val handler = Handler(Looper.getMainLooper())

    val runnable = object : Runnable {
        override fun run() {
            setMyCurrentLastDisplay()
            handler.postDelayed(this, 1000)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun recurseTimeCalculator(statusUpdateData: StatusUpdateData, root: String = OTHER_STATUS): Boolean {
        val diff = timeDiffFromNow(statusUpdateData.serverTime)
        var refS = FirebaseDatabase.getInstance().reference

        //refS = when (root) {
        //    OTHER_STATUS -> refS.child(STATUS_UPDATE).child(root).child(myAuth).child(statusUpdateData.senderUid).child(statusUpdateData.serverTime)
        //    else -> refS.child(STATUS_UPDATE).child(root).child(myAuth).child(statusUpdateData.serverTime)
        //}

        if (diff > 86400) {
            val imageRef = FirebaseStorage.getInstance().reference.child(CreateStatusWorker.IMAGE_REFERENCE).child("statuses")
                .child(statusUpdateData.senderUid)
                .child(statusUpdateData.timeSent)
            imageRef.delete()
            refS.removeValue()
            datasetMyStatus.remove(statusUpdateData)
            statusAdapter.notifyDataSetChanged()
            return true
        }
        return false
    }

    @SuppressLint("NotifyDataSetChanged")
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { permissions ->
        if (permissions) {
            runBlocking {
                scope.launch {
                    contactFormattedList = Util.getAllSavedContacts(requireContext()).second
                    if (activity != null) requireActivity().runOnUiThread {
                        val allStatusLiveData = ValueEventLiveData(allyStatusRef)
                        allStatusLiveData.observe(viewLifecycleOwner) { all_snap ->
                            when (all_snap.second) {
                                onDataChange -> {
                                    var all: ArrayList<ArrayList<StatusUpdateData>> = arrayListOf()
                                    for (d in all_snap.first.children) {
                                        var tempStatus: ArrayList<StatusUpdateData> = arrayListOf()
                                        for (status in d.children) {
                                            val data = status.getValue(StatusUpdateData::class.java)!!
                                            if (!recurseTimeCalculator(data)) {
                                                if (Util.formatNumber(data.senderPhone) !in contactFormattedList && !data.isAdmin) continue
                                                tempStatus.add(data)
                                            }
                                        }
                                        tempStatus.sortedWith(compareByDescending { it.serverTime })
                                        if (tempStatus.isNotEmpty()) all.add(tempStatus)
                                    }
                                    all.sortWith(compareByDescending { allData -> allData.last().serverTime })
                                    dataset.clear()
                                    dataset.addAll(all)
                                    statusAdapter.notifyDataSetChanged()
                                    statusAdapter1.notifyDataSetChanged()
                                    when(dataset.isEmpty()){
                                        true->{
                                            binding.channelEmptyRoot.visibility = View.VISIBLE
                                            binding.statusRv1.visibility = View.GONE
                                        }
                                        else ->{
                                            binding.channelEmptyRoot.visibility = View.GONE
                                            binding.statusRv1.visibility = View.VISIBLE
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            val callUtils = CallUtils(viewLifecycleOwner, requireActivity())
            if (ContextCompat.checkSelfPermission(requireActivity(), Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_DENIED) {
                val snack = Snackbar.make(binding.root, "", Snackbar.LENGTH_INDEFINITE)
                snack.setText("HowFar needs permission to find registered users in your contact")
                snack.setAction("GRANT PERMISSION") { callUtils.openAppSettings() }
                snack.show()
            }
        }
    }

    val timer = Timer().schedule(object : TimerTask() {
        override fun run() {
        }

        override fun cancel(): Boolean {
            return super.cancel()
        }

        override fun scheduledExecutionTime(): Long {
            return super.scheduledExecutionTime()
        }
    }, 1000)

    @SuppressLint("NotifyDataSetChanged")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        pref = requireActivity().getSharedPreferences(getString(R.string.ALL_PREFERENCE), Context.MODE_PRIVATE)
        binding = FragmentStatusBinding.inflate(inflater, container, false)
        //timeRef = timeRef.child("time").child(myAuth)
        //myStatusRef = myStatusRef.child(STATUS_UPDATE).child(MY_STATUS).child(myAuth)
        //allyStatusRef = allyStatusRef.child(STATUS_UPDATE).child(OTHER_STATUS).child(myAuth)
        permissionLauncher.launch(Manifest.permission.READ_CONTACTS)

        binding.statusFabText.setOnClickListener(this)
        binding.statusMyStatus.setOnClickListener(this)

        userProfileViewmodel.userProfile.observe(viewLifecycleOwner) { myProfile = it }
        statusAdapter.dataset = dataset
        statusAdapter.activity = requireActivity()
        statusAdapter.livecycleOwner = this
        statusAdapter.statusDataTransferHelper = this
        binding.statusRv.adapter = statusAdapter
        binding.statusRv.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)

        statusAdapter1.dataset = dataset
        statusAdapter1.activity = requireActivity()
        statusAdapter1.livecycleOwner = this
        statusAdapter1.showText = false
        statusAdapter1.statusDataTransferHelper = this
        binding.statusRv1.adapter = statusAdapter1
        binding.statusRv1.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        handler.postDelayed(runnable, 1000)
        val childEventLiveData = ChildEventLiveData(myStatusRef)
        childEventLiveData.observe(viewLifecycleOwner) {
            when (it.second) {
                onChildAdded -> {
                    val status = it.first.getValue(StatusUpdateData::class.java)!!
                    if (!recurseTimeCalculator(status, MY_STATUS)) {
                        if (status !in datasetMyStatus) datasetMyStatus.add(status)
                        handler.postDelayed(runnable, 1000)
                        setMyCurrentLastDisplay()
                    }
                }
                onChildRemoved -> {
                    val removed = it.first.getValue(StatusUpdateData::class.java)
                    handler.postDelayed(runnable, 1000)
                    datasetMyStatus.remove(removed)
                }
            }
        }
        return binding.root
    }

    private fun showMyStatusImage() {
        binding.myStatusDisplayText.visibility = View.GONE
        binding.myStatusDisplay.visibility = View.VISIBLE
    }

    private fun showMyStatusText() {
        binding.myStatusDisplayText.visibility = View.VISIBLE
        binding.myStatusDisplay.visibility = View.GONE
    }

    private fun setMyCurrentLastDisplay() = try {
        binding.myCircularStatusView.setPortionsCount(datasetMyStatus.size)
        if (datasetMyStatus.isNotEmpty()) {
            when (datasetMyStatus.last().statusType) {
                IMAGE -> {
                    showMyStatusImage()
                    Glide.with(this).load(datasetMyStatus.last().storageLink).centerCrop().into(binding.myStatusDisplay)
                }
                TEXT -> {
                    showMyStatusText()
                    binding.myStatusDisplayText.text = datasetMyStatus.last().caption
                }
            }
            val diff = timeDiffFromNow(datasetMyStatus.last().serverTime)
            binding.myStatusTime.text = Util.statusTime(diff, datasetMyStatus.last().serverTime.toLong())
            binding.myCircularStatusView.setPortionsCount(datasetMyStatus.size)
            binding.statusMore.visibility = View.VISIBLE
            binding.statusMore.setOnClickListener {
                val json = Gson().toJson(datasetMyStatus)
                val bundle = Bundle()
                bundle.putString("stories", json)
                val fragment = MyStoriesFragment()
                fragment.arguments = bundle
                requireActivity().supportFragmentManager.beginTransaction().addToBackStack("stories")
                    .setCustomAnimations(R.anim.enter_right_to_left, R.anim.exit_right_to_left, R.anim.enter_left_to_right, R.anim.exit_left_to_right)
                    .replace(R.id.chat_landing_root, fragment).commit()
            }
        } else {
            binding.statusMore.visibility = View.GONE
            binding.myStatusDisplay.visibility = View.VISIBLE
            Glide.with(this).load(myProfile.image).error(R.drawable.ic_avatar).centerCrop().into(binding.myStatusDisplay)
            binding.myStatusTime.text = "Tap to add story update"
        }
    } catch (e: Exception) {
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.status_fab_text -> {
                val intent = Intent(requireContext(), ActivityCreateStatus::class.java)
                intent.putExtra("type", TEXT)
                startActivity(intent)
            }
            R.id.status_my_status -> {
                if (datasetMyStatus.isNotEmpty()) {
                    val jsonDatum = Gson().toJson(datasetMyStatus)
                    val fragment = FragmentViewStatus()
                    val bundle = Bundle()
                    bundle.putString("datum", jsonDatum)
                    bundle.putBoolean("assist", false)
                    fragment.arguments = bundle
                    (activity as AppCompatActivity).supportFragmentManager.beginTransaction().addToBackStack("view")
                        .replace(R.id.chat_landing_root, fragment).commit()
                    return
                } else if (datasetMyStatus.isEmpty()) {
                    val intent = Intent(requireContext(), ActivityCreateStatus::class.java)
                    intent.putExtra("type", IMAGE)
                    startActivity(intent)
                }
            }
        }
    }

    override fun sendDatum(position: Int, dataset: ArrayList<ArrayList<StatusUpdateData>>, selected: ArrayList<StatusUpdateData>) {
        statusViewModel.setDatum(position to dataset)
    }

    companion object {
        const val OTHER_STATUS = "other_status"
        const val STATUS_UPDATE = "status_update"
        const val MY_STATUS = "my_status"
        const val TRANSFER_HISTORY = "user_coins_transfer"
        const val USER_DETAILS = "user_details"
    }
}

class StatusAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    lateinit var statusDataTransferHelper: StatusDataTransferHelper
    lateinit var livecycleOwner: LifecycleOwner
    var showText = true
    var dataset: ArrayList<ArrayList<StatusUpdateData>> = arrayListOf()
    lateinit var context: Context
    lateinit var activity: Activity

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int) = position.toLong()

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val image: ShapeableImageView = itemView.findViewById(R.id.status_display)
        val portions: com.devlomi.circularstatusview.CircularStatusView = itemView.findViewById(R.id.circular_status_view)
        val name: TextView = itemView.findViewById(R.id.status_user_name)
        val time: TextView = itemView.findViewById(R.id.my_status_time)
        val textStatusBackground: ShapeableImageView = itemView.findViewById(R.id.status_display_text_background)
        val textStatus: TextView = itemView.findViewById(R.id.status_display_text)
        val blueTick: ImageView = itemView.findViewById(R.id.blue_tick)
    }

    class ImageOnlyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val image: ShapeableImageView = itemView.findViewById(R.id.status_display)
        val portions: com.devlomi.circularstatusview.CircularStatusView = itemView.findViewById(R.id.circular_status_view)
        val textStatusBackground: ShapeableImageView = itemView.findViewById(R.id.status_display_text_background)
        val textStatus: TextView = itemView.findViewById(R.id.status_display_text)
        val blueTick: ImageView = itemView.findViewById(R.id.blue_tick)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        context = parent.context
        val view = LayoutInflater.from(context).inflate(R.layout.status_card, parent, false)
        val viewImageOnly = LayoutInflater.from(context).inflate(R.layout.status_card1, parent, false)
        return if (showText) ViewHolder(view) else ImageOnlyViewHolder(viewImageOnly)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val datum = dataset[position]

        if (showText) {
            holder as ViewHolder
            setLastStatus(datum, holder)
            if (!datum.first().isAdmin) {
                val senderRef = FirebaseDatabase.getInstance().reference.child("user_details").child(datum.first().senderUid)
                val liveData = ValueEventLiveData(senderRef)
                liveData.observe(livecycleOwner) {
                    when (it.second) {
                        onDataChange -> {
                            val user = it.first.getValue(UserProfile::class.java)!!
                            holder.name.text = user.name
                        }
                    }
                }
            }
        } else {
            holder as ImageOnlyViewHolder
            setImageOnlyLastStatus(datum, holder)
        }
        holder.itemView.setOnClickListener {
            val fragment = FragmentAllStatus()
            val bundle = Bundle()
            val json = Gson().toJson(dataset)
            val pos = holder.bindingAdapterPosition
            bundle.putString("datum", json)
            bundle.putInt("pos", pos)
            fragment.arguments = bundle
            (activity as AppCompatActivity).supportFragmentManager.beginTransaction().addToBackStack("view")
                .replace(R.id.chat_landing_root, fragment)
                .commit()
        }
    }

    private fun setLastStatus(datum: ArrayList<StatusUpdateData>, holder: ViewHolder) {
        when (datum.first().isAdmin) {
            true -> holder.blueTick.visibility = View.VISIBLE
            false -> holder.blueTick.visibility = View.GONE
        }
        when (datum.last().isAdmin) {
            true -> holder.name.text = "HowFar"
            else ->{
            }
        }
        when (datum.last().statusType) {
            IMAGE -> {
                holder.textStatus.visibility = View.GONE
                holder.textStatusBackground.visibility = View.GONE
                if (!activity.isDestroyed) {
                    try {
                        Glide.with(context).load(datum.last().storageLink).centerCrop().into(holder.image)
                    } catch (e: Exception) {
                    }
                }
            }
            TEXT -> {
                holder.textStatus.visibility = View.VISIBLE
                holder.textStatusBackground.visibility = View.VISIBLE
                val color = datum.last().captionBackgroundColor
                holder.textStatusBackground.background = ColorDrawable(Color.parseColor(color))
                holder.textStatus.text = datum.last().caption
            }
        }
        val instance = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        instance.timeInMillis = datum.last().serverTime.toLong()
        val timePostedInMillis = instance.timeInMillis / 1000
        val nowInMill = Calendar.getInstance().timeInMillis / 1000
        val diff = nowInMill - timePostedInMillis
        if (diff < 86400) { // LESS THAN A DAY
            holder.portions.setPortionsCount(datum.size)
            holder.time.text = Util.statusTime(diff, datum.last().serverTime.toLong())
        }
    }

    private fun setImageOnlyLastStatus(datum: ArrayList<StatusUpdateData>, holder: ImageOnlyViewHolder) {
        when (datum.first().isAdmin) {
            true -> holder.blueTick.visibility = View.VISIBLE
            false -> holder.blueTick.visibility = View.GONE
        }
        when (datum.last().statusType) {
            IMAGE -> {
                holder.textStatus.visibility = View.GONE
                holder.textStatusBackground.visibility = View.GONE
                if (!activity.isDestroyed) {
                    try {
                        Glide.with(context).load(datum.last().storageLink).centerCrop().into(holder.image)
                    } catch (e: Exception) {
                    }
                }
            }
            TEXT -> {
                holder.textStatus.visibility = View.VISIBLE
                holder.textStatusBackground.visibility = View.VISIBLE
                val color = datum.last().captionBackgroundColor
                holder.textStatusBackground.background = ColorDrawable(Color.parseColor(color))
                holder.textStatus.text = datum.last().caption
            }
        }
        val instance = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        instance.timeInMillis = datum.last().serverTime.toLong()
        val timePostedInMillis = instance.timeInMillis / 1000
        val nowInMill = Calendar.getInstance().timeInMillis / 1000
        val diff = nowInMill - timePostedInMillis
        if (diff < 86400) { // LESS THAN A DAY
            holder.portions.setPortionsCount(datum.size)
        }
    }

    override fun getItemCount() = dataset.size

    override fun getItemViewType(position: Int) = position
}

class StatusViewModel : ViewModel() {
    var datum = MutableLiveData<Pair<Int, ArrayList<ArrayList<StatusUpdateData>>>>()
    var playCurrentStatus = MutableLiveData<Boolean>()
    fun setDatum(input: Pair<Int, ArrayList<ArrayList<StatusUpdateData>>>) {
        datum.value = input
    }

    var segmentController = MutableLiveData<Pair<Boolean, Int>>()
    fun setSegmentController(input: Pair<Boolean, Int>) {
        segmentController.value = input
    }

    fun setPlayCurrentStatus(input: Boolean) {
        playCurrentStatus.value = input
    }
}

interface StatusDataTransferHelper {
    fun sendDatum(position: Int, dataset: ArrayList<ArrayList<StatusUpdateData>> = arrayListOf(), selected: ArrayList<StatusUpdateData> = arrayListOf())
}