package com.azur.howfar.howfarchat.groupChat

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.azur.howfar.R
import com.azur.howfar.activity.BaseActivity
import com.azur.howfar.databinding.ActivityNewGroupBinding
import com.azur.howfar.models.GroupProfileData
import com.azur.howfar.models.UserProfile
import com.azur.howfar.utils.CallUtils
import com.azur.howfar.utils.CanHubImage
import com.azur.howfar.utils.ImageCompressor
import com.azur.howfar.utils.Keyboard.hideKeyboard
import com.azur.howfar.utils.Util
import com.azur.howfar.utils.Util.formatNumber
import com.azur.howfar.utils.Util.getAllSavedContacts
import com.azur.howfar.viewmodel.ContactViewModel
import com.bumptech.glide.Glide
import com.canhub.cropper.CropImageContract
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream

class ActivityNewGroup : BaseActivity(), ContactSelectHelper, View.OnClickListener {
    private val binding by lazy { ActivityNewGroupBinding.inflate(layoutInflater) }
    private var contactsAdapter = ContactsAdapter()
    private var contacts: ArrayList<Contact> = arrayListOf()
    private lateinit var pref: SharedPreferences
    private var phoneList: ArrayList<String> = arrayListOf()
    private var selectedDataset: ArrayList<UserProfile> = arrayListOf()
    private val listOfUsers: ArrayList<UserProfile> = arrayListOf()
    private val myUsers: ArrayList<UserProfile> = arrayListOf()
    private var allUsersRef = FirebaseDatabase.getInstance().reference.child(USER_DETAILS)
    private val scope = CoroutineScope(Dispatchers.IO)
    private val contactViewModel: ContactViewModel by viewModels()
    private val myAuth = FirebaseAuth.getInstance().currentUser!!.uid
    private var imagePair: Pair<ByteArrayInputStream, ByteArray>? = null
    private lateinit var canHubImage: CanHubImage
    private lateinit var  callUtils: CallUtils

    @SuppressLint("NotifyDataSetChanged")
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { permissions ->
        when (permissions) {
            true -> {
                runBlocking {
                    scope.launch {
                        val pair = getAllSavedContacts(this@ActivityNewGroup)
                        contacts = pair.first
                        phoneList = pair.second
                        println("Output ******************************** $contacts")
                        allUsersRef.get().addOnSuccessListener {
                            if (it.exists()) {
                                hideProgressBar()
                                for (i in it.children) {
                                    val user = i.getValue(UserProfile::class.java)!!
                                    if (user.uid == myAuth) continue
                                    if (user !in listOfUsers) listOfUsers.add(user)
                                    if (formatNumber(user.phone) in phoneList) {
                                        myUsers.add(user)
                                        runOnUiThread { contactsAdapter.notifyItemInserted(myUsers.size) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            else -> {
                callUtils = CallUtils(this, this)
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_DENIED) {
                    val snack = Snackbar.make(binding.root, "", Snackbar.LENGTH_INDEFINITE)
                    snack.setText("HowFar needs permission to find registered users in your contact")
                    snack.setAction("GRANT PERMISSION") { callUtils.openAppSettings() }
                    snack.show()
                }
            }
        }
    }

    private fun askPermission() {
        if (!Util.permissionsAvailable(arrayOf(Manifest.permission.READ_CONTACTS), this))
            permissionLauncher.launch(Manifest.permission.READ_CONTACTS) else permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pref = getSharedPreferences(getString(R.string.ALL_PREFERENCE), Context.MODE_PRIVATE)
        setContentView(binding.root)
        canHubImage = CanHubImage(this)
        binding.displayImage.setOnClickListener(this)
        binding.newGroupFab.setOnClickListener(this)
        contactsAdapter.dataset = myUsers
        contactsAdapter.selectedDataset = selectedDataset
        contactsAdapter.contactSelectHelper = this
        binding.contactRv.adapter = contactsAdapter
        binding.contactRv.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)

        askPermission()
    }

    private fun hideProgressBar() {
        binding.contactProgress.visibility = View.GONE
        binding.contactRv.visibility = View.VISIBLE
    }

    override fun onSelect(selectedDataset: ArrayList<UserProfile>) {
        this.selectedDataset = selectedDataset
    }

    override fun onDeselect(selectedDataset: ArrayList<UserProfile>) {
        this.selectedDataset = selectedDataset
    }

    private val cropImage = registerForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) {
            val uriContent = result.uriContent!!
            //val uriFilePath = result.getUriFilePath(this) // optional usage
            try {
                imagePair = ImageCompressor.compressImage(uriContent, this, null)
                Glide.with(this).load(imagePair!!.second).centerCrop().into(binding.displayImage)
            } catch (e: Exception) {
                println("Exception *********************************************************** ${e.printStackTrace()}")
            }
        } else {
            val exception = result.error
            exception!!.printStackTrace()
        }
    }

    companion object {
        const val USER_DETAILS = "user_details"
    }

    override fun onClick(p0: View?) {
        when (p0?.id) {
            R.id.displayImage -> canHubImage.openCanHub(cropImage)
            R.id.new_group_fab -> {
                hideKeyboard()
                val input = binding.newGroupName.text.toString().trim()
                if (input.isEmpty()) {
                    Snackbar.make(binding.root, "Group name must be set", Snackbar.LENGTH_LONG).show()
                    return
                }
                if (selectedDataset.isEmpty()) {
                    Snackbar.make(binding.root, "At least one contact must be selected", Snackbar.LENGTH_LONG).show()
                    return
                }
                val members: ArrayList<String> = arrayListOf()
                for (i in selectedDataset) members.add(i.uid)
                members.add(myAuth)
                val groupProfileData = GroupProfileData(groupName = input, members = members, creatorProfileLink = myAuth)
                contactViewModel.setUserProfiles(selectedDataset)
                contactViewModel.setGroupData(groupProfileData)
                if (imagePair != null) contactViewModel.setImagePair(imagePair!!)
                supportFragmentManager.beginTransaction().addToBackStack("confirm")
                    .setCustomAnimations(R.anim.enter_right_to_left, R.anim.exit_right_to_left)
                    .replace(R.id.new_group_root, FragmentConfirmCreateGroup()).commit()
            }
        }
    }
}

class ContactsAdapter : RecyclerView.Adapter<ContactsAdapter.ViewHolder>() {

    var dataset: ArrayList<UserProfile> = arrayListOf()
    var selectedDataset: ArrayList<UserProfile> = arrayListOf()
    lateinit var context: Context
    lateinit var contactSelectHelper: ContactSelectHelper

    init {
        setHasStableIds(true)
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val image: ShapeableImageView = itemView.findViewById(R.id.contact_card_image)
        val name: TextView = itemView.findViewById(R.id.contact_card_name)
        val number: TextView = itemView.findViewById(R.id.contact_card_phone)
        val card: CardView = itemView.findViewById(R.id.row_contact_card)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        context = parent.context
        val view = LayoutInflater.from(context).inflate(R.layout.row_contact_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val datum = dataset[position]
        //holder.image.setImageURI(Uri.parse(datum.uri)) 08137666699
        holder.name.text = datum.name
        holder.number.text = datum.phone
        if (datum in selectedDataset) {
            holder.card.setCardBackgroundColor(Color.parseColor("#4961FF"))
            holder.image.setImageResource(R.drawable.ic_check_circle)
        } else {
            holder.card.setCardBackgroundColor(Color.TRANSPARENT)
            holder.image.setImageResource(R.drawable.ic_avatar)
        }

        holder.card.setOnClickListener {
            val selectedDatum = dataset[holder.bindingAdapterPosition]
            if (selectedDatum in selectedDataset) {
                holder.card.setCardBackgroundColor(Color.TRANSPARENT)
                selectedDataset.remove(dataset[holder.bindingAdapterPosition])
                holder.image.setImageResource(R.drawable.ic_avatar)
                contactSelectHelper.onDeselect(selectedDataset)
            } else {
                holder.card.setCardBackgroundColor(Color.parseColor("#4961FF"))
                selectedDataset.add(dataset[holder.bindingAdapterPosition])
                holder.image.setImageResource(R.drawable.ic_check_circle)
                contactSelectHelper.onSelect(selectedDataset)
            }
        }
    }

    override fun getItemCount() = dataset.size

    override fun getItemId(position: Int) = position.toLong()
}

interface ContactSelectHelper {
    fun onSelect(selectedDataset: ArrayList<UserProfile>)
    fun onDeselect(selectedDataset: ArrayList<UserProfile>)
}

data class Contact(
    var id: String = "",
    var name: String = "",
    var person: String = "",
    var uri: String = "",
    var mobileNumber: String = "",
)