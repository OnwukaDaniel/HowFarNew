package com.azur.howfar.howfarwallet

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.azur.howfar.R
import com.azur.howfar.databinding.FragmentBankListBinding
import com.azur.howfar.viewmodel.VFDAccreditedBanksVieModel
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.SocketTimeoutException

class FragmentBankList : Fragment(), View.OnClickListener {
    private lateinit var binding: FragmentBankListBinding
    private val scope = CoroutineScope(Dispatchers.IO)
    private val adapter = BanksAdapter()
    private var token = ""
    private var allUsers: ArrayList<VFDBanksList> = arrayListOf()
    private var dataset: ArrayList<VFDBanksList> = arrayListOf()
    private val vFDTransferToVieModel by activityViewModels<VFDAccreditedBanksVieModel>()

    inner class InputTextWatcher : TextWatcher {
        override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) = Unit
        override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) = Unit
        override fun afterTextChanged(p0: Editable?) = searchUser(p0.toString().trim().lowercase())
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun searchUser(input: String) {
        clearAdapter()
        if (input != "") for (i in allUsers) {
            if (input in i.bankName.lowercase() || input in i.bankName) dataset.add(i)
            adapter.notifyDataSetChanged()
        } else {
            dataset.clear()
            adapter.notifyDataSetChanged()
            for (i in allUsers) dataset.add(i)
        }
        adapter.dataset = dataset
        binding.banksRv.adapter = adapter
        adapter.notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun clearAdapter() {
        dataset.clear()
        adapter.notifyDataSetChanged()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentBankListBinding.inflate(inflater, container, false)
        token = requireArguments().getString("token")!!
        binding.searchBank.setOnClickListener(this)
        binding.searchBankEt.addTextChangedListener(InputTextWatcher())
        fetchBanks()
        adapter.activity = requireActivity()
        adapter.dataset = dataset
        binding.banksRv.adapter = adapter
        binding.banksRv.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
        adapter.vFDTransferToVieModel = vFDTransferToVieModel
        return binding.root
    }

    override fun onResume() {
        hideSearchBank()
        super.onResume()
    }

    private fun hideLoading() {
        binding.banksLoading.visibility = View.GONE
    }

    private fun hideSearchBank() {
        binding.searchBankEt.visibility = View.GONE
        binding.selectBankText.visibility = View.VISIBLE
    }

    private fun showSearchBank() {
        binding.searchBankEt.visibility = View.VISIBLE
        binding.selectBankText.visibility = View.GONE
        binding.searchBankEt.requestFocus()
        binding.searchBankEt.postDelayed({
            val imm: InputMethodManager = requireActivity().getSystemService(AppCompatActivity.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(binding.searchBankEt, InputMethodManager.HIDE_IMPLICIT_ONLY)
        }, 1000)
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun fetchBanks() {
        binding.banksLoading.visibility = View.VISIBLE
        try {
            val header = "Authorization"
            val key = "Bearer: $token"
            scope.launch {
                val url = "https://howfarserver.online/v1/bank/list"
                val client = OkHttpClient()
                val request = Request.Builder().url(url).addHeader(header, key).build()
                val response = client.newCall(request).execute()
                val jsonResponse = response.body?.string()
                if (response.code == 200) {
                    if (activity != null && isAdded) requireActivity().runOnUiThread {
                        hideLoading()
                        val banks = Gson().fromJson(jsonResponse, VFDBanks::class.java)
                        dataset.clear()
                        allUsers.clear()
                        dataset.addAll(banks.data)
                        allUsers.addAll(banks.data)
                        adapter.notifyDataSetChanged()
                    }
                } else {
                    if (activity != null && isAdded) requireActivity().runOnUiThread { hideLoading() }
                }
            }
        } catch (e: SocketTimeoutException) {
            if (activity != null && isAdded) requireActivity().runOnUiThread {
                hideLoading()
                showMsg("Time out")
            }
        }
    }

    private fun showMsg(msg: String = "Date not set") {
        Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG).show()
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.search_bank -> {
                when (binding.searchBankEt.visibility) {
                    View.GONE -> showSearchBank()
                    View.VISIBLE -> {
                    }
                }
            }
        }
    }
}

class BanksAdapter : RecyclerView.Adapter<BanksAdapter.ViewHolder>() {
    var dataset: ArrayList<VFDBanksList> = arrayListOf()
    lateinit var context: Context
    lateinit var activity: Activity
    lateinit var vFDTransferToVieModel: VFDAccreditedBanksVieModel

    init {
        setHasStableIds(true)
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val name: TextView = itemView.findViewById(R.id.bank_name)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        context = parent.context
        val view = LayoutInflater.from(context).inflate(R.layout.row_bank_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val datum = dataset[position]
        holder.name.text = datum.bankName
        if (holder.absoluteAdapterPosition - 1 >= 0 && holder.absoluteAdapterPosition + 1 < dataset.size) {
            //previousAndNext(holder)
        }

        holder.itemView.setOnClickListener {
            vFDTransferToVieModel.setBank(datum)
            (activity as AppCompatActivity).onBackPressed()
        }
    }

    override fun getItemCount() = dataset.size

    override fun getItemId(position: Int) = position.toLong()
}

data class VFDBanks(
    var status: String = "",
    var message: String = "",
    var data: ArrayList<VFDBanksList> = arrayListOf(),
)

data class VFDBanksList(
    var bankName: String = "",
    var bankCode: String = ""
)

data class VFDAccreditedBanks(
    var name: String = "",
    var code: String = ""
)

data class VFDTransferBank(
    var accountNumber: String = "",
    var bankCode: String = ""
)