package com.azur.howfar.user.wallet

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.azur.howfar.R
import com.azur.howfar.models.CoinPlan
import com.azur.howfar.user.wallet.CoinPurchaseAdapter.CoinViewHolder

class CoinPurchaseAdapter : RecyclerView.Adapter<CoinViewHolder>() {
    var context: Context? = null
    lateinit var onCoinPlanClickListener: OnCoinPlanClickListener
    var coinList: MutableList<CoinPlan> = ArrayList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CoinViewHolder {
        context = parent.context
        return CoinViewHolder(LayoutInflater.from(context).inflate(R.layout.item_purchase_coin, parent, false))
    }

    override fun onBindViewHolder(holder: CoinViewHolder, position: Int) {
        holder.setData(position)
    }

    override fun getItemCount() = coinList.size

    interface OnCoinPlanClickListener {
        fun onPlanClick(coinPlan: CoinPlan)
    }

    inner class CoinViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvCoin: TextView = itemView.findViewById(R.id.tvCoin)
        private val tvLabel: TextView = itemView.findViewById(R.id.tvLabel)
        private val tvAmount: TextView = itemView.findViewById(R.id.tvAmount)
        fun setData(position: Int) {
            val coinPlan = coinList[position]
            tvCoin.text = coinPlan.coin.toString()
            tvLabel.visibility = if (coinPlan.label.isEmpty()) View.GONE else View.VISIBLE
            tvLabel.text = coinPlan.label
            tvAmount.text = "# " + coinPlan.amount.toString()
            itemView.setOnClickListener { onCoinPlanClickListener.onPlanClick(coinPlan) }
        }
    }
}