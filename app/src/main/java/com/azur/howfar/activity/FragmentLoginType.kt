package com.azur.howfar.activity

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.azur.howfar.R
import com.azur.howfar.databinding.FragmentLoginTypeBinding

class FragmentLoginType : Fragment() {
    private lateinit var binding: FragmentLoginTypeBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentLoginTypeBinding.inflate(inflater, container, false)
        val bundle = Bundle()
        binding.emailLogin.setOnClickListener {
            val fragment = FragmentSignInEmail()
            bundle.putString("data", "email")
            fragment.arguments = bundle
            requireActivity().supportFragmentManager
                .beginTransaction()
                .addToBackStack("email")
                .setCustomAnimations(R.anim.enter_bottom_to_top, R.anim.fade_out)
                .replace(R.id.login_type_root, fragment).commit()
        }
        binding.phoneLogin.setOnClickListener {
            val fragment = FragmentSignInPhone()
            bundle.putString("data", "phone")
            fragment.arguments = bundle
            requireActivity().supportFragmentManager
                .beginTransaction()
                .addToBackStack("phone")
                .setCustomAnimations(R.anim.enter_bottom_to_top, R.anim.fade_out)
                .replace(R.id.login_type_root, fragment).commit()
        }
        return binding.root
    }
}