package com.azur.howfar.howfarchat.chat

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.ContactsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.bumptech.glide.Glide
import com.azur.howfar.R
import com.azur.howfar.databinding.FragmentContactFromChatBinding
import com.azur.howfar.viewmodel.ContactViewModel


class FragmentContactFromChat : Fragment() {
    private lateinit var binding: FragmentContactFromChatBinding
    private lateinit var pref: SharedPreferences
    private val contactViewModel by activityViewModels<ContactViewModel>()
    private var prefColor = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentContactFromChatBinding.inflate(inflater, container, false)
        pref = requireActivity().getSharedPreferences(getString(R.string.ALL_PREFERENCE), Context.MODE_PRIVATE)
        contactViewModel.userContact.observe(viewLifecycleOwner) { contact ->
            binding.userName.text = contact.name
            binding.userPhone.text = contact.mobileNumber
            binding.userBtnAdd.setOnClickListener { addContact(contact.name, contact.mobileNumber) }
        }
        contactViewModel.userProfile.observe(viewLifecycleOwner) { userProfile ->
            binding.userName.text = userProfile.name
            binding.userPhone.text = userProfile.phone
            Glide.with(requireContext()).load(userProfile.image).error(R.drawable.ic_avatar).centerCrop().into(binding.userImage)
            binding.userBtnChat.visibility = View.VISIBLE
            binding.userBtnChat.setOnClickListener {
                val intent = Intent(requireContext(), ChatActivity2::class.java)
                intent.putExtra("data", userProfile.uid)
                startActivity(intent)
            }
            binding.userBtnAdd.setOnClickListener { addContact(userProfile.name, userProfile.phone) }
        }
        return binding.root
    }

    private fun addContact(name: String, phone: String) {
        val intent = Intent(Intent.ACTION_INSERT)
        intent.type = ContactsContract.Contacts.CONTENT_TYPE
        intent.putExtra(ContactsContract.Intents.Insert.NAME, name)
        intent.putExtra(ContactsContract.Intents.Insert.PHONE, phone)
        startActivity(intent)
    }
}