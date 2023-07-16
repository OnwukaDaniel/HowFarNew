package com.azur.howfar.user

import android.content.Context
import com.azur.howfar.user.FollowersUsersAdapter.FollowersUserViewHolder
import android.view.ViewGroup
import android.view.LayoutInflater
import com.azur.howfar.R
import com.bumptech.glide.Glide
import android.content.Intent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.imageview.ShapeableImageView
import com.azur.howfar.user.guestuser.GuestActivity
import com.azur.howfar.models.UserProfile
import com.azur.howfar.retrofit.Const
import java.util.ArrayList

class FollowersUsersAdapter : RecyclerView.Adapter<FollowersUserViewHolder>() {
    private var context: Context? = null
    var list: MutableList<UserProfile> = ArrayList()
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FollowersUserViewHolder {
        context = parent.context
        return FollowersUserViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_followrs, parent, false))
    }

    override fun onBindViewHolder(holder: FollowersUserViewHolder, position: Int) {
        holder.setData(position)
    }

    override fun getItemCount() =list.size

    inner class FollowersUserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imguser: ShapeableImageView = itemView.findViewById(R.id.imguser)
        private val lytname: LinearLayout = itemView.findViewById(R.id.lytname)
        private val tvusername: TextView = itemView.findViewById(R.id.tvusername)
        private val tvcountry: TextView = itemView.findViewById(R.id.tvcountry)
        private val tvBio: TextView = itemView.findViewById(R.id.tvBio)
        fun setData(position: Int) {
            val user = list[position]
            try{ Glide.with(itemView).load(user.image).circleCrop().into(imguser) }catch (e: Exception){}
            tvusername.text = user.name
            tvBio.text = user.bio
            tvcountry.text = user.countryCode
            itemView.setOnClickListener { v: View? ->
                context!!.startActivity(
                    Intent(context, GuestActivity::class.java).putExtra(
                        Const.USER_STR,
                        user.uid
                    )
                )
            }
        }
    }
}