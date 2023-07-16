package com.azur.howfar.user.vip

import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.azur.howfar.R
import com.azur.howfar.activity.BaseActivity
import com.azur.howfar.adapter.DotAdapter
import com.azur.howfar.databinding.ActivityVipPlanBinding

class VipPlanActivity : BaseActivity() {
    private val binding by lazy { ActivityVipPlanBinding.inflate(layoutInflater) }
    private var vipImagesAdapter = VipImagesAdapter()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        val vipPlanAdapter = VipPlanAdapter()
        binding.rvPlan.adapter = vipPlanAdapter
        setVIpSlider()
    }

    private fun setVIpSlider() {
        binding.rvBanner.adapter = vipImagesAdapter
        val dotAdapter = DotAdapter(vipImagesAdapter.itemCount, R.color.pink)
        binding.rvDots.adapter = dotAdapter
        binding.rvBanner.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val myLayoutManager = binding.rvBanner.layoutManager as LinearLayoutManager?
                val scrollPosition = myLayoutManager!!.findFirstVisibleItemPosition()
                dotAdapter.changeDot(scrollPosition)
            }
        })
    }
}