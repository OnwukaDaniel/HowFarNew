package com.azur.howfar.howfarchat.settings

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.azur.howfar.R
import com.azur.howfar.activity.ContactUsActivity
import com.azur.howfar.activity.LoginActivityActivity
import com.azur.howfar.databinding.FragmentSettingsBinding
import com.azur.howfar.howfarwallet.ActivityFingerPrint
import com.azur.howfar.howfarwallet.ActivityWallet
import com.azur.howfar.models.FingerprintRoute
import com.azur.howfar.models.FingerprintRoute.HOW_FAR_PAY
import com.azur.howfar.user.EditProfileActivity
import com.azur.howfar.user.ProfileFragment
import com.azur.howfar.viewmodel.UserProfileViewmodel
import com.bumptech.glide.Glide
import com.google.android.material.color.MaterialColors
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class SettingsFragment : Fragment(), View.OnClickListener {
    private lateinit var binding: FragmentSettingsBinding
    private lateinit var pref: SharedPreferences
    private val userProfileViewModel by activityViewModels<UserProfileViewmodel>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentSettingsBinding.inflate(inflater, container, false)
        pref = requireContext().getSharedPreferences(getString(R.string.ALL_PREFERENCE), Context.MODE_PRIVATE)
        binding.settingsBack.setOnClickListener(this)
        binding.settingsUserEdit.setOnClickListener(this)
        binding.settingsMyAccount.setOnClickListener(this)
        binding.settingsChat.setOnClickListener(this)
        binding.settingsAccent.setOnClickListener(this)
        binding.settingsLogOut.setOnClickListener(this)
        binding.settingsPayment.setOnClickListener(this)
        binding.privacyPolicy.setOnClickListener(this)
        binding.help.setOnClickListener(this)
        binding.settingsNotificationToggleCard.setOnClickListener(this)
        binding.settingsThemeToggleCard.setOnClickListener(this)

        userProfileViewModel.userProfile.observe(viewLifecycleOwner) {
            binding.settingsUserName.text = it.name
            binding.settingsUserBio.text = it.bio
            if (isAdded && activity != null)
                Glide.with(requireActivity()).load(it.image).error(R.drawable.ic_avatar).centerCrop().into(binding.settingsUserImage)
        }
        binding.settingsNotificationToggle.setOnCheckedChangeListener { buttonView, isChecked ->
            pref.edit().putBoolean(getString(R.string.show_notification), isChecked).apply()
        }
        binding.settingsThemeToggle.setOnCheckedChangeListener { buttonView, isChecked ->
            AppCompatDelegate.setDefaultNightMode(if (isChecked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO)
        }
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        val showNotification = pref.getBoolean(getString(R.string.show_notification), true)
        binding.settingsNotificationToggle.isChecked = showNotification

        var color = MaterialColors.getColor(requireContext(), android.R.attr.windowBackground, Color.BLACK)
        requireActivity().window.statusBarColor = color
        when (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
            Configuration.UI_MODE_NIGHT_YES -> binding.settingsThemeToggle.isChecked = true
            Configuration.UI_MODE_NIGHT_NO -> binding.settingsThemeToggle.isChecked = false
            Configuration.UI_MODE_NIGHT_UNDEFINED -> binding.settingsThemeToggle.isChecked = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        requireActivity().window.statusBarColor = MaterialColors.getColor(requireContext(), android.R.attr.colorPrimary, Color.BLACK)
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.settings_back -> requireActivity().onBackPressed()
            R.id.settings_user_edit -> startActivity(Intent(activity, EditProfileActivity::class.java))
            R.id.settings_chat -> requireActivity().onBackPressed()
            R.id.settings_notification_toggle_card -> {
                pref.edit().putBoolean(getString(R.string.show_notification), binding.settingsNotificationToggle.isChecked).apply()
            }
            R.id.settings_my_account -> requireActivity().supportFragmentManager.beginTransaction().addToBackStack("profile")
                .replace(R.id.chat_landing_root, ProfileFragment()).commit()
            R.id.settings_theme_toggle_card -> {
                AppCompatDelegate.setDefaultNightMode(if (binding.settingsThemeToggle.isChecked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO)
            }
            R.id.help->{
                startActivity(Intent(requireContext(), ContactUsActivity::class.java))
                requireActivity().overridePendingTransition(R.anim.enter_left_to_right, R.anim.exit_left_to_right)
            }
            R.id.settings_accent -> {
                requireActivity().supportFragmentManager.beginTransaction().addToBackStack("settings")
                    .setCustomAnimations(R.anim.enter_right_to_left, R.anim.exit_right_to_left, R.anim.enter_left_to_right, R.anim.exit_left_to_right)
                    .replace(R.id.chat_landing_root, ApplyThemeFragment()).commit()
            }
            R.id.settings_payment -> {
                startActivity(Intent(requireContext(), ActivityFingerPrint::class.java).putExtra("data", HOW_FAR_PAY))
                requireActivity().overridePendingTransition(R.anim.enter_right_to_left, R.anim.exit_right_to_left)
            }
            R.id.privacy_policy->{
                val uri = Uri.parse("http://www.howfar.online")
                val intent = Intent(Intent.ACTION_VIEW, uri)
                startActivity(intent)
            }
            R.id.settings_log_out -> {
                pref.edit().clear().apply()
                Firebase.auth.signOut()
                val intent = Intent(requireContext(), LoginActivityActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                requireActivity().overridePendingTransition(R.anim.enter_left_to_right, R.anim.exit_left_to_right)
            }
        }
    }
}