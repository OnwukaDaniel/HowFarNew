package com.azur.howfar.livestreamming

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.azur.howfar.R
import com.azur.howfar.adapter.DotAdapter
import com.azur.howfar.databinding.FragmentLiveBinding
import com.azur.howfar.home.adapter.BannerAdapter
import com.azur.howfar.match.RandomMatchActivity
import com.azur.howfar.reels.VideoListActivity
import com.google.android.material.tabs.TabLayoutMediator

class LiveFragmentMain : Fragment() {
    private lateinit var binding: FragmentLiveBinding
    private lateinit var tabAdapter: TabAdapter
    var bannerAdapter = BannerAdapter()
    private val liveListFragment = LiveListFragment()
    private val livePopularFragment = LivePopularFragment()
    val handler = Handler(Looper.getMainLooper())
    val runnable: Runnable = object : Runnable {
        var pos = 0
        var flag = true
        override fun run() {
            if (pos == bannerAdapter.itemCount - 1) flag = false else if (pos == 0) flag = true
            if (flag) pos++ else pos--
            binding.rvBanner.smoothScrollToPosition(pos)
            handler.postDelayed(this, 2000)
        }
    }

    inner class TabAdapter(fa: FragmentActivity) : FragmentStateAdapter(fa) {
        lateinit var dataset: ArrayList<Fragment>
        override fun getItemCount(): Int = dataset.size
        override fun createFragment(position: Int): Fragment {
            return dataset[position]
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentLiveBinding.inflate(inflater, container, false)
        initView()
        setupLogicAutoSlider()
        binding.tvVideo.setOnClickListener { startActivity(Intent(requireContext(), VideoListActivity::class.java)) }
        binding.tvOnetoOne.setOnClickListener { startActivity(Intent(requireContext(), RandomMatchActivity::class.java)) }
        return binding.root
    }

    private fun setupLogicAutoSlider() {
        val dotAdapter = DotAdapter(bannerAdapter.itemCount, R.color.pink)
        binding.rvBanner.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvBanner.adapter = bannerAdapter
        binding.rvDots.adapter = dotAdapter
        binding.rvBanner.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val myLayoutManager = binding.rvBanner.layoutManager as LinearLayoutManager?
                val scrollPosition = myLayoutManager!!.findFirstVisibleItemPosition()
                dotAdapter.changeDot(scrollPosition)
            }
        })
        handler.postDelayed(runnable, 2000)
    }

    override fun onDestroy() {
        handler.removeCallbacks(runnable)
        super.onDestroy()
    }

    private fun initView() {
        tabAdapter = TabAdapter(requireActivity())
        binding.tablayout1.setBackgroundColor(Color.TRANSPARENT)

        val tabsText = arrayListOf("All", "Popular", "Following")
        tabAdapter.dataset = arrayListOf(liveListFragment, livePopularFragment, LiveListFollowingFragment())
        binding.viewPager.adapter = tabAdapter
        TabLayoutMediator(binding.tablayout1, binding.viewPager) { tabs, position -> tabs.text = tabsText[position] }.attach()
    }

    private fun setTab(line: View, text: TextView) {
        line.background = ColorDrawable(Color.parseColor("#BA0C53"))
        text.setTextColor(Color.WHITE)
    }

    private fun resetTabs() {
        binding.line1.background = ColorDrawable(Color.parseColor("#00000000"))
        binding.line2.background = ColorDrawable(Color.parseColor("#00000000"))
        binding.line3.background = ColorDrawable(Color.parseColor("#00000000"))
        binding.tvLive.setTextColor(Color.parseColor("#A7A7B3"))
        binding.videoLine2.setTextColor(Color.parseColor("#A7A7B3"))
        binding.videoLine3.setTextColor(Color.parseColor("#A7A7B3"))
    }
}