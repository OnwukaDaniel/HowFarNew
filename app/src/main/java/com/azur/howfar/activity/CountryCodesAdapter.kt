package com.azur.howfar.activity

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.azur.howfar.R
import com.azur.howfar.models.Countries

class CountryCodesAdapter: RecyclerView.Adapter<CountryCodesAdapter.ViewHolder>() {
    lateinit var context: Context
    var dataset: List<Countries> = arrayListOf()
    lateinit var clickHelper: ClickHelper

    class ViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {
        val countryCode: TextView = itemView.findViewById(R.id.country_code)
        val countryName: TextView = itemView.findViewById(R.id.country_name)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        context = parent.context
        val view = LayoutInflater.from(context).inflate(R.layout.country_codes_row, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val datum = dataset[position]
        holder.countryName.text = datum.name
        holder.countryCode.text = datum.dial_code
        holder.itemView.setOnClickListener {
            clickHelper.onClickedHelp(datum)
        }
    }

    override fun getItemCount() = dataset.size
}

interface ClickHelper{
    fun onClickedHelp(datum: Countries)
}