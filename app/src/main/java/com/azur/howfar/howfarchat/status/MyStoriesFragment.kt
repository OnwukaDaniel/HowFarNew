package com.azur.howfar.howfarchat.status

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.azur.howfar.R
import com.azur.howfar.databinding.FragmentMyStoriesBinding
import com.azur.howfar.models.StatusUpdateData
import com.azur.howfar.utils.TimeUtils
import com.azur.howfar.utils.Util
import com.bumptech.glide.Glide
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson

class MyStoriesFragment : Fragment(), StatusDataTransferHelper, View.OnClickListener {
    private lateinit var binding: FragmentMyStoriesBinding
    private val datasetMyStatus = arrayListOf<StatusUpdateData>()
    private val myStatusAdapter = MyStatusAdapter()
    private lateinit var pref: SharedPreferences
    private var selectedData = arrayListOf<StatusUpdateData>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentMyStoriesBinding.inflate(inflater, container, false)
        pref = requireContext().getSharedPreferences(getString(R.string.ALL_PREFERENCE), Context.MODE_PRIVATE)
        val json = requireArguments().getString("stories")
        val list = Gson().fromJson(json, ArrayList::class.java)
        for (i in list) datasetMyStatus.add(Gson().fromJson(Gson().toJson(i), StatusUpdateData::class.java))
        datasetMyStatus.sortWith(compareByDescending { it.serverTime })
        binding.deleteStatus.setOnClickListener(this)
        myStatusAdapter.dataset = datasetMyStatus
        myStatusAdapter.statusDataTransferHelper = this
        myStatusAdapter.activity = requireActivity()
        binding.myStatusRv.adapter = myStatusAdapter
        binding.myStatusRv.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
        return binding.root
    }

    override fun sendDatum(position: Int, dataset: ArrayList<ArrayList<StatusUpdateData>>, selected: ArrayList<StatusUpdateData>) {
        binding.deleteStatus.visibility = if (selected.isEmpty()) View.GONE else View.VISIBLE
        selectedData = selected
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.delete_status -> {
                if (selectedData.isEmpty()) return
                val workManager = WorkManager.getInstance(requireContext())
                val statusDataJson = Gson().toJson(selectedData)
                pref.edit().putString("status_time_sent ${selectedData.first().timeSent}", statusDataJson).apply()
                val workData = workDataOf("time_sent" to selectedData.first().timeSent)
                val workRequest = OneTimeWorkRequestBuilder<DeleteStatusWorker>().setInputData(workData).build()
                workManager.enqueue(workRequest)
                for (i in selectedData) {
                    datasetMyStatus.remove(i)
                    myStatusAdapter.notifyItemRemoved(datasetMyStatus.indexOf(i))
                }
                if (activity != null && isAdded) requireActivity().onBackPressed()
                Snackbar.make(binding.root, "Deleting story ...", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    companion object {
        const val OTHER_STATUS = "other_status"
        const val STATUS_UPDATE = "status_update"
        const val IMAGE_REFERENCE = "IMAGE_REFERENCE"
        const val MY_STATUS = "my_status"
        const val TRANSFER_HISTORY = "user_coins_transfer"
        const val USER_DETAILS = "user_details"
    }
}

class MyStatusAdapter : RecyclerView.Adapter<MyStatusAdapter.ViewHolder>() {
    lateinit var statusDataTransferHelper: StatusDataTransferHelper
    var dataset: ArrayList<StatusUpdateData> = arrayListOf()
    var selectedList: ArrayList<StatusUpdateData> = arrayListOf()
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
        val myStatusMore: ShapeableImageView = itemView.findViewById(R.id.my_status_more)
        val textStatus: TextView = itemView.findViewById(R.id.status_display_text)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        context = parent.context
        val view = LayoutInflater.from(context).inflate(R.layout.status_card_my, parent, false)
        return ViewHolder(view)
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val datum = dataset[position]
        holder.name.text = "My status"
        if (datum in selectedList) holder.itemView.setBackgroundColor(Color.DKGRAY) else holder.itemView.setBackgroundColor(Color.TRANSPARENT)
        holder.itemView.setOnClickListener {
            if (selectedList.isNotEmpty()) {
                if (datum !in selectedList) selectedList.add(datum) else selectedList.remove(datum)
                statusDataTransferHelper.sendDatum(position, selected = selectedList)
                notifyDataSetChanged()
                return@setOnClickListener
            }
            val fragment = FragmentViewStatus()
            val bundle = Bundle()
            val json = Gson().toJson(dataset)
            bundle.putString("datum", json)
            bundle.putBoolean("assist", false)
            fragment.arguments = bundle
            (activity as AppCompatActivity).supportFragmentManager.beginTransaction().addToBackStack("single")
                .replace(R.id.my_status_root, fragment)
                .commit()
        }
        holder.itemView.setOnLongClickListener {
            if (datum !in selectedList) selectedList.add(datum) else selectedList.remove(datum)
            statusDataTransferHelper.sendDatum(position, selected = selectedList)
            notifyDataSetChanged()
            return@setOnLongClickListener true
        }
        holder.myStatusMore.setOnClickListener{
            val popup = PopupMenu(context, it)
            popup.inflate(R.menu.my_story)
            popup.show()
            popup.setOnMenuItemClickListener { menuItem->
                when(menuItem.itemId){
                    R.id.select->{
                        if (datum !in selectedList) selectedList.add(datum) else selectedList.remove(datum)
                        statusDataTransferHelper.sendDatum(position, selected = selectedList)
                        notifyDataSetChanged()
                    }
                }
                return@setOnMenuItemClickListener true
            }
        }
        setLastStatus(datum, holder)
    }

    private fun setLastStatus(datum: StatusUpdateData, holder: ViewHolder) {
        when (datum.statusType) {
            StatusType.IMAGE -> {
                holder.textStatus.visibility = View.GONE
                holder.textStatusBackground.visibility = View.GONE
                if (!activity.isDestroyed) {
                    try {
                        Glide.with(context).load(datum.storageLink).centerCrop().into(holder.image)
                    } catch (e: Exception) {
                    }
                }
            }
            StatusType.TEXT -> {
                holder.textStatus.visibility = View.VISIBLE
                holder.textStatusBackground.visibility = View.VISIBLE
                val color = datum.captionBackgroundColor
                holder.textStatusBackground.background = ColorDrawable(Color.parseColor(color))
                holder.textStatus.text = datum.caption
            }
        }
        val diff = TimeUtils.timeDiffFromNow(datum.serverTime)
        holder.time.text = Util.statusTime(diff, datum.serverTime.toLong())
    }

    override fun getItemCount() = dataset.size
    override fun getItemViewType(position: Int) = position
}