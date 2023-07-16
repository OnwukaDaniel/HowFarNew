package com.azur.howfar.user.wallet

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.FirebaseDatabase
import com.azur.howfar.R
import com.azur.howfar.databinding.ActivityHistoryBinding
import com.azur.howfar.livedata.ValueEventLiveData
import com.azur.howfar.models.*
import com.azur.howfar.utils.Util.formatSmartDateTime
import com.azur.howfar.viewmodel.TransactionHistoryViewModel

class HistoryFragment : Fragment() {
    private lateinit var binding: ActivityHistoryBinding
    private val coinHistoryAdapter = CoinHistoryAdapter()
    private var userDetailsList: ArrayList<UserAuthName> = arrayListOf()
    private var transactionData: ArrayList<TransactionDisplayData> = arrayListOf()
    private val transactionHistoryViewModel: TransactionHistoryViewModel by activityViewModels()
    private var income = 0
    private var outcome = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = ActivityHistoryBinding.inflate(inflater, container, false)
        coinHistoryAdapter.viewLiveCycleOwner = viewLifecycleOwner
        coinHistoryAdapter.dataset = transactionData
        binding.rvHistory.adapter = coinHistoryAdapter
        initDatePiker()
        transactionHistoryViewModel.transactionDisplayData.observe(viewLifecycleOwner) { transactionList ->
            for (i in transactionList) if (i !in transactionData) {
                transactionData.add(i)
                transactionData.sortWith(compareByDescending { it.datetime })
                coinHistoryAdapter.notifyItemInserted(transactionData.size)
                if (i.transactionType == TransactionType.SENT) outcome += 1
                if (i.transactionType == TransactionType.RECEIVED ||
                    i.transactionType == TransactionType.BOUGHT ||
                    i.transactionType == TransactionType.APP_GIFT ||
                    i.transactionType == TransactionType.EARNED
                ) income += 1
                binding.tvIncome.text = income.toString()
                binding.tvOutcome.text = outcome.toString()
            }
        }
        return binding.root
    }

    private fun initDatePiker() {
        binding.lytDatePicker.lytDatePicker.visibility = View.GONE
        binding.lytDateDimonds.setOnClickListener { v: View? -> binding.lytDatePicker.lytDatePicker.visibility = View.VISIBLE }
    }
}

class CoinHistoryAdapter : RecyclerView.Adapter<CoinHistoryAdapter.CoinHistoryViewHolder>() {
    var context: Context? = null
    lateinit var viewLiveCycleOwner: LifecycleOwner
    var dataset: ArrayList<TransactionDisplayData> = arrayListOf()

    init {
        setHasStableIds(true)
    }

    inner class CoinHistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var displayInfo: TextView = itemView.findViewById(R.id.displayInfo)
        var recipientName: TextView = itemView.findViewById(R.id.recipientName)
        var datetime: TextView = itemView.findViewById(R.id.datetime)
        var quantity: TextView = itemView.findViewById(R.id.quantity)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CoinHistoryViewHolder {
        context = parent.context
        return CoinHistoryViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_coin_history, parent, false))
    }

    override fun onBindViewHolder(holder: CoinHistoryViewHolder, position: Int) {
        val datum = dataset[position]
        var transactionMsg = ""
        var quantity = ""

        val ref = FirebaseDatabase.getInstance().reference.child(USER_DETAILS).child(datum.recipientUid)
        val profileLiveData = ValueEventLiveData(ref)
        if (holder.recipientName.text == "") profileLiveData.observe(viewLiveCycleOwner) {
            when (it.second) {
                EventListenerType.onDataChange -> holder.recipientName.text = it.first.getValue(UserProfile::class.java)!!.name
            }
        }

        when (datum.transactionType) {
            TransactionType.SENT -> transactionMsg = "${datum.item} SENT to "
            TransactionType.RECEIVED -> transactionMsg = "${datum.item} RECEIVED from "
            TransactionType.APP_GIFT -> transactionMsg = "APP GIFT to "
            TransactionType.BOUGHT -> transactionMsg = "${datum.item} BOUGHT "
            TransactionType.EARNED -> transactionMsg = "${datum.item} EARNED from "
        }

        when (datum.transactionType) {
            TransactionType.SENT -> quantity = "-${datum.quantity}"
            TransactionType.RECEIVED -> quantity = "+${datum.quantity}"
            TransactionType.APP_GIFT -> quantity = "+${datum.quantity}"
            TransactionType.BOUGHT -> quantity = "+${datum.quantity}"
            TransactionType.EARNED -> quantity = "+${datum.quantity}"
        }
        holder.displayInfo.text = transactionMsg
        holder.datetime.text = formatSmartDateTime(datum.datetime)
        holder.quantity.text = quantity
    }

    override fun getItemCount() = dataset.size

    override fun getItemId(position: Int) = position.toLong()

    override fun getItemViewType(position: Int): Int {
        return position
    }

    companion object {
        val TRANSFER_HISTORY = "user_coins_transfer"
        val USER_DETAILS = "user_details"
    }
}
