package com.azur.howfar.posts

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class FeedViewPagerAdapter(fm: FragmentActivity?) : FragmentStateAdapter(fm!!) {
    val list: ArrayList<Fragment> = arrayListOf(FeedLatestFragment(), FeedPopularFragment(), FeedFollowingFragment())

    override fun getItemCount() = list.size

    override fun createFragment(position: Int) = list[position]
}