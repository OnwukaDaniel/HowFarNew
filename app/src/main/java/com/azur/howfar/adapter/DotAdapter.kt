package com.azur.howfar.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.azur.howfar.R
import com.azur.howfar.adapter.DotAdapter.DotViewHolder
import com.azur.howfar.databinding.ItemDotsBinding

class DotAdapter(private val slides: Int, private val color: Int) : RecyclerView.Adapter<DotViewHolder>() {
    private var context: Context? = null
    private var selectedPos = 0
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DotViewHolder {
        context = parent.context
        return DotViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_dots, parent, false))
    }

    override fun onBindViewHolder(holder: DotViewHolder, position: Int) {
        if (selectedPos == position) {
            holder.binding.dot.backgroundTintList = ContextCompat.getColorStateList(context!!, color)
        } else {
            holder.binding.dot.backgroundTintList = ContextCompat.getColorStateList(context!!, R.color.white)
        }
    }

    override fun getItemCount(): Int {
        return slides
    }

    @SuppressLint("NotifyDataSetChanged")
    fun changeDot(scrollPosition: Int) {
        selectedPos = scrollPosition
        notifyDataSetChanged()
    }

    inner class DotViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var binding: ItemDotsBinding = ItemDotsBinding.bind(itemView)
    }
}