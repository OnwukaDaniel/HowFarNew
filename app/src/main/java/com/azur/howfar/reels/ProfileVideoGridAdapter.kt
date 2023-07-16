package com.azur.howfar.reels

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.azur.howfar.R
import com.azur.howfar.models.VideoPost
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.google.gson.Gson

class ProfileVideoGridAdapter : RecyclerView.Adapter<ProfileVideoGridAdapter.FeedViewHolder>() {
    private lateinit var context: Context
    lateinit var activity: Activity
    var reels: MutableList<VideoPost> = ArrayList()

    init {
        setHasStableIds(true)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FeedViewHolder {
        context = parent.context
        return FeedViewHolder(LayoutInflater.from(context).inflate(R.layout.item_vid_profile_list, parent, false))
    }

    override fun onBindViewHolder(holder: FeedViewHolder, position: Int) {
        holder.setData(position)
    }

    override fun getItemCount() = reels.size

    inner class FeedViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imgThumb: ImageView = itemView.findViewById(R.id.img_thumb)
        fun setData(position: Int) {
            val requestOptions = RequestOptions()
            val reel = reels[position]
            try {
                Glide.with(context).setDefaultRequestOptions(requestOptions).load(reel.videoUrl).into(imgThumb)
            } catch (e: Exception) {
            }
            imgThumb.setOnClickListener {
                val jsonArray = Gson().toJson(reels)
                context.startActivity(Intent(context, VideoListActivity::class.java).apply {
                    putExtra("personal videos", jsonArray)
                    putExtra("current", position)
                }
                )
            }
        }
    }
}