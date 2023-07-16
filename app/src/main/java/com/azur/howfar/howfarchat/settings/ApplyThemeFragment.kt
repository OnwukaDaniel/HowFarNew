package com.azur.howfar.howfarchat.settings

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.azur.howfar.R
import com.azur.howfar.databinding.FragmentApplyThemeBinding

class ApplyThemeFragment : Fragment(), View.OnClickListener {
    private lateinit var binding: FragmentApplyThemeBinding
    private lateinit var pref: SharedPreferences

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentApplyThemeBinding.inflate(inflater, container, false)
        pref = requireContext().getSharedPreferences(getString(R.string.ALL_PREFERENCE), Context.MODE_PRIVATE)
        binding.themeBack.setOnClickListener { requireActivity().onBackPressed() }
        //binding.themeKellygreen.setOnClickListener(this)
        binding.themeSky.setOnClickListener(this)
        binding.themeSsColorAccent.setOnClickListener(this)
        binding.themePink.setOnClickListener(this)
        binding.themeAppDefault.setOnClickListener(this)
        binding.themeGrey.setOnClickListener(this)
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        //binding.doneKellygreen.visibility = View.GONE
        binding.doneSky.visibility = View.GONE
        binding.doneSs.visibility = View.GONE
        binding.donePink.visibility = View.GONE
        binding.doneGrey.visibility = View.GONE
        when (pref.getInt(getString(R.string.THEME_SHARED_PREFERENCE), R.style.Theme_HowFar)) {
            //R.style.Theme_HowFar_KellyGreen -> binding.doneKellygreen.visibility = View.VISIBLE
            R.style.Theme_HowFar_Sky -> binding.doneSky.visibility = View.VISIBLE
            R.style.Theme_HowFar_SS -> binding.doneSs.visibility = View.VISIBLE
            R.style.Theme_HowFar_Pink -> binding.donePink.visibility = View.VISIBLE
            R.style.Theme_HowFar_Grey -> binding.doneGrey.visibility = View.VISIBLE
        }
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            //R.id.theme_kellygreen -> pref.edit().putInt(getString(R.string.THEME_SHARED_PREFERENCE), R.style.Theme_HowFar_KellyGreen).apply()
            R.id.theme_sky -> pref.edit().putInt(getString(R.string.THEME_SHARED_PREFERENCE), R.style.Theme_HowFar_Sky).apply()
            R.id.theme_ss_colorAccent -> pref.edit().putInt(getString(R.string.THEME_SHARED_PREFERENCE), R.style.Theme_HowFar_SS).apply()
            R.id.theme_pink -> pref.edit().putInt(getString(R.string.THEME_SHARED_PREFERENCE), R.style.Theme_HowFar_Pink).apply()
            R.id.theme_app_default -> pref.edit().putInt(getString(R.string.THEME_SHARED_PREFERENCE), R.style.Theme_HowFar).apply()
            R.id.theme_grey -> pref.edit().putInt(getString(R.string.THEME_SHARED_PREFERENCE), R.style.Theme_HowFar_Grey).apply()
        }
        requireActivity().recreate()
    }
}