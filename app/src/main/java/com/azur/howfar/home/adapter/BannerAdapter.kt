package com.azur.howfar.home.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.azur.howfar.R

class BannerAdapter : RecyclerView.Adapter<BannerAdapter.ViewHolder>() {
    private lateinit var context: Context
    var dataset = arrayListOf(R.drawable.banner, R.drawable.banner2, R.drawable.banner,
        R.drawable.banner2, R.drawable.banner, R.drawable.banner2, R.drawable.banner)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        context = parent.context
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_banner, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val datum = dataset[position]
        holder.imageview.setImageDrawable(ContextCompat.getDrawable(context, datum))
    }

    override fun getItemCount() = dataset.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var imageview: ImageView = itemView.findViewById(R.id.imageview)
    }
}