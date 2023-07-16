package com.azur.howfar.livestreamming

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.azur.howfar.R
import com.azur.howfar.models.BroadcastCommentData
import com.azur.howfar.models.UserProfile

class LiveStreamCommentAdapter : RecyclerView.Adapter<LiveStreamCommentAdapter.ViewHolder>() {
    var comments: MutableList<BroadcastCommentData> = ArrayList()
    private lateinit var context: Context

    var onCommentClickListner: OnCommentClickListner? = null
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        context = parent.context
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_livestram_comment, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val comment = comments[position]
        if (comment.isJoined && comment.comment == "") {
            holder.tvJoined.text = "Joined"
            holder.tvJoined.visibility = View.VISIBLE
        } else if (!comment.isJoined && comment.comment == "") {
            holder.tvJoined.text = "Left"
            holder.tvJoined.visibility = View.VISIBLE
        } else if (comment.comment != "") {
            holder.tvJoined.visibility = View.GONE
            holder.tvComment.text = comment.comment
        }
        holder.tvUserName.text = comment.user.name
        Glide.with(context).load(comment.user.image).circleCrop().into(holder.imgUser)
    }

    override fun getItemCount() = comments.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var tvUserName: TextView = itemView.findViewById(R.id.tvUserName)
        var tvComment: TextView = itemView.findViewById(R.id.tvComment)
        var tvJoined: TextView = itemView.findViewById(R.id.tvJoined)
        var imgUser: ImageView = itemView.findViewById(R.id.imgUser)
    }
}

interface OnCommentClickListner {
    fun onClickCommet(user: UserProfile?)
}