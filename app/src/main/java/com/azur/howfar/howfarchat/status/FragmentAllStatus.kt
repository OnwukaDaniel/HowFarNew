package com.azur.howfar.howfarchat.status

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_DRAGGING
import androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_IDLE
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.azur.howfar.databinding.FragmentAllStatusBinding
import com.azur.howfar.models.StatusUpdateData
import com.google.gson.Gson

class FragmentAllStatus : Fragment(), RecyclerViewScrollHelper {
    private lateinit var binding: FragmentAllStatusBinding
    private val statusViewModel by activityViewModels<StatusViewModel>()
    private lateinit var layoutManager: LinearLayoutManager
    private var datasetLarge: ArrayList<ArrayList<StatusUpdateData>> = arrayListOf()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentAllStatusBinding.inflate(inflater, container, false)
        layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)

        val fragmentList: ArrayList<Fragment> = arrayListOf()
        val json = requireArguments().getString("datum")
        val pos = requireArguments().getInt("pos")
        val list = Gson().fromJson(json, ArrayList::class.java)
        for ((index, x) in list.withIndex()) {
            val listStatus = Gson().fromJson(Gson().toJson(x), ArrayList::class.java)
            val dataset: ArrayList<StatusUpdateData> = arrayListOf()
            for (i in listStatus) {
                val statusUpdateData = Gson().fromJson(Gson().toJson(i), StatusUpdateData::class.java)
                dataset.add(statusUpdateData)
            }
            val jsonDatum = Gson().toJson(dataset)
            val fragment = FragmentViewStatus()
            val bundle = Bundle()
            bundle.putString("datum", jsonDatum)
            if (index == list.size - 1) bundle.putBoolean("assist", false) else bundle.putBoolean("assist", true)
            fragment.arguments = bundle
            fragmentList.add(fragment)
            datasetLarge.add(dataset)
        }

        val parentViewStatusTabAdapter = ParentViewStatusTabAdapter(requireActivity())
        parentViewStatusTabAdapter.dataset = fragmentList
        binding.allStatusViewPager.adapter = parentViewStatusTabAdapter
        binding.allStatusViewPager.currentItem = pos

        var currentPage = 0
        val onPageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                super.onPageScrolled(position, positionOffset, positionOffsetPixels)
                currentPage = position
            }

            override fun onPageScrollStateChanged(state: Int) {
                super.onPageScrollStateChanged(state)
                if (binding.allStatusViewPager.scrollState == SCROLL_STATE_IDLE && binding.allStatusViewPager.scrollState != SCROLL_STATE_DRAGGING) {
                    statusViewModel.setSegmentController(false to currentPage)
                }
                if (binding.allStatusViewPager.scrollState != SCROLL_STATE_IDLE && binding.allStatusViewPager.scrollState == SCROLL_STATE_DRAGGING) {
                    statusViewModel.setSegmentController(true to currentPage)
                }
            }
        }
        binding.allStatusViewPager.registerOnPageChangeCallback(onPageChangeCallback)
        return binding.root
    }

    class ParentViewStatusTabAdapter(fa: FragmentActivity) : FragmentStateAdapter(fa) {
        lateinit var dataset: ArrayList<Fragment>
        override fun getItemCount(): Int = dataset.size
        override fun createFragment(position: Int): Fragment {
            return dataset[position]
        }
    }

    override fun scrollToPosition(position: Int) {
    }
}

interface RecyclerViewScrollHelper {
    fun scrollToPosition(position: Int)
}