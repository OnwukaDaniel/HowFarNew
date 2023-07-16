package com.azur.howfar.livestreamming

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.azur.howfar.R
import com.azur.howfar.bottomsheets.UserProfileBottomSheet
import com.azur.howfar.livedata.ValueEventLiveData
import com.azur.howfar.models.EventListenerType.onDataChange
import com.azur.howfar.models.UserProfile
import com.bumptech.glide.Glide
import com.google.android.material.imageview.ShapeableImageView
import com.google.firebase.database.FirebaseDatabase
import com.google.gson.Gson

class ViewersAdapter : RecyclerView.Adapter<ViewersAdapter.ViewHolder>() {
    lateinit var context: Context
    lateinit var activity: Activity
    var dataset = arrayListOf<String>()

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val image: ShapeableImageView = itemView.findViewById(R.id.person_image)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        context = parent.context
        val view = LayoutInflater.from(context).inflate(R.layout.row_image_only, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val uid = dataset[position]
        try {
            val profileRef = FirebaseDatabase.getInstance().reference.child(USER_DETAILS).child(uid)
            ValueEventLiveData(profileRef).observe(activity as AppCompatActivity) {
                when (it.second) {
                    onDataChange -> {
                        val profile = it.first.getValue(UserProfile::class.java)!!
                        Glide.with(context).load(profile.image).into(holder.image)
                        holder.image.setOnClickListener {
                            val sheet = UserProfileBottomSheet()
                            val json = Gson().toJson(profile)
                            val bundle = Bundle()
                            bundle.putString("data", json)
                            sheet.arguments = bundle
                            sheet.show((activity as AppCompatActivity).supportFragmentManager, sheet.javaClass.simpleName)
                        }
                    }
                }
            }
        } catch (e: Exception) {
        }
    }

    override fun getItemCount() = dataset.size
    override fun getItemViewType(position: Int) = position

    companion object {
        const val USER_DETAILS = "user_details"
    }
}