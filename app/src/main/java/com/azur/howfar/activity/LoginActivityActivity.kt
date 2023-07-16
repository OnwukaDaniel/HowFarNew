package com.azur.howfar.activity

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.azur.howfar.R
import com.azur.howfar.databinding.ActivityLoginActivityBinding
import com.azur.howfar.databinding.TextFragmentBinding
import com.azur.howfar.model.*
import com.azur.howfar.utils.CanHubImage
import com.google.gson.Gson
import com.synnapps.carouselview.ImageListener

class LoginActivityActivity : AppCompatActivity() {
    private val binding by lazy { ActivityLoginActivityBinding.inflate(layoutInflater) }
    private lateinit var viewPagerAdapter: ViewPagerAdapter
    private val images: ArrayList<Int> = arrayListOf(
        R.drawable.person_image2,
        R.drawable.person_image5,
        R.drawable.background_image_girl,
        R.drawable.known,
        R.drawable.major,
        R.drawable.major2,
        R.drawable.image1,
        R.drawable.image2,
        R.drawable.image3,
    )
    private var textFragments = arrayListOf<Fragment>()
    private var handler = Handler(Looper.getMainLooper())
    private lateinit var canHubImage: CanHubImage
    private val runnable = object : Runnable {
        override fun run() {
            handler.postDelayed(this, 4000)
            val pos = if (binding.textViewPager.currentItem < textFragments.size) binding.textViewPager.currentItem + 1 else 1
            binding.textViewPager.setCurrentItem(pos, true)
        }
    }
    private val texts = arrayListOf(
        "Find new \nfriends nearby" to "With millions of users all over the world, we gives you the ability to connect with people no matter " +
                "where you are.",
        "Connect with\nfriends nearby" to "with millions of users all over the world, we give you the ability to connect with people no matter where you " +
                "are.",
        "Entertainment\nat its peak" to "with millions of users all over the world, we give you the ability to connect with people no matter where you " +
                "are.",
        "Enjoy Comfort \nin  luxury ride" to "with millions of users all over the world, we give you the ability to connect with people no matter where you " +
                "are.",
    )

    init {
        images.shuffle()
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        canHubImage = CanHubImage(this)

        val imageListener = ImageListener { position, imageView -> imageView.setImageResource(images[position]) }
        binding.carouselView.setImageListener(imageListener)
        binding.carouselView.pageCount = images.size
        viewPagerAdapter = ViewPagerAdapter(this)
        for (pair in texts) {
            val textFrag = TextFragment()
            val bundle = Bundle()
            val json = Gson().toJson(pair)
            bundle.putString("json", json)
            textFrag.arguments = bundle
            textFragments.add(textFrag)
        }
        viewPagerAdapter.textFragments = textFragments
        binding.textViewPager.adapter = viewPagerAdapter
        handler.postDelayed(runnable, 1000)
    }

    inner class ViewPagerAdapter(fa: FragmentActivity) : FragmentStateAdapter(fa) {
        var textFragments: ArrayList<Fragment> = arrayListOf()
        override fun getItemCount() = textFragments.size
        override fun createFragment(position: Int) = textFragments[position]
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onResume() {
        super.onResume()
        window.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
    }

    override fun onDestroy() {
        super.onDestroy()
        window.clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        handler.removeCallbacks(runnable)
    }

    @SuppressLint("MissingInflatedId")
    fun onClickSignUp(view: View?) {
        val intent = Intent(this, SignUpActivity::class.java)
        startActivity(intent)
    }

    fun onClickLoginPhone(view: View?) {
        supportFragmentManager.beginTransaction().addToBackStack("phone")
            .setCustomAnimations(R.anim.enter_bottom_to_top, R.anim.fade_out)
            .replace(R.id.login_root, FragmentLoginType()).commit()
    }
}

class TextFragment : Fragment() {
    private lateinit var binding: TextFragmentBinding
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = TextFragmentBinding.inflate(inflater, container, false)
        val json = requireArguments().getString("json")
        val pair = Gson().fromJson(json, Pair::class.java)
        binding.textView1.text = pair.first.toString()
        binding.textView2.text = pair.second.toString()
        return binding.root
    }
}