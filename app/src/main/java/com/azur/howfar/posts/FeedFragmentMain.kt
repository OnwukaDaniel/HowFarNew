package com.azur.howfar.posts

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.OnTabSelectedListener
import com.google.android.material.tabs.TabLayoutMediator
import com.azur.howfar.R
import com.azur.howfar.databinding.FragmentFeedMainBinding

class FeedFragmentMain : Fragment() {
    private lateinit var binding: FragmentFeedMainBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentFeedMainBinding.inflate(inflater, container, false)
        initTabLayout()
        return binding.root
    }

    private fun initTabLayout() {
        binding.viewPager.adapter = FeedViewPagerAdapter(requireActivity())
        binding.tablayout1.setBackgroundColor(Color.TRANSPARENT)
        binding.tablayout1.addOnTabSelectedListener(object : OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val v = tab.customView
                if (v != null) {
                    val tv = v.findViewById<View>(R.id.tvTab) as TextView
                    tv.setTextColor(ContextCompat.getColor(activity!!, R.color.white))
                    val indicator = v.findViewById(R.id.indicator) as View
                    indicator.visibility = View.VISIBLE
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {
                val v = tab.customView
                if (v != null) {
                    val tv = v.findViewById<View>(R.id.tvTab) as TextView
                    tv.setTextColor(ContextCompat.getColor(activity!!, R.color.graylight))
                    val indicator = v.findViewById(R.id.indicator) as View
                    indicator.visibility = View.INVISIBLE
                }
            }

            override fun onTabReselected(tab: TabLayout.Tab) {
            }
        })
        val tabsText = arrayOf("Latest", "Popular", "Following")
        TabLayoutMediator(binding.tablayout1, binding.viewPager) { tabs, position -> tabs.text = tabsText[position] }.attach()
    }

    private fun settab(contry: Array<String>) {
        binding.tablayout1.tabGravity = TabLayout.GRAVITY_FILL
        binding.tablayout1.removeAllTabs()
        for (i in contry.indices) {
            binding.tablayout1.addTab(binding.tablayout1.newTab().setCustomView(createCustomView(i, contry[i])))
        }
    }

    private fun createCustomView(i: Int, s: String): View {
        val v = LayoutInflater.from(requireActivity()).inflate(R.layout.custom_tabhorizontol2, null)
        val tv = v.findViewById<View>(R.id.tvTab) as TextView
        tv.text = s
        tv.setTextColor(ContextCompat.getColor(requireActivity(), R.color.white))
        val indicator = v.findViewById(R.id.indicator) as View
        if (i == 0) indicator.visibility = View.VISIBLE else indicator.visibility = View.GONE
        return v
    }
}