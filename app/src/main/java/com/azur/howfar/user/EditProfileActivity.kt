package com.azur.howfar.user

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import androidx.core.app.ActivityCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.bumptech.glide.Glide
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageView
import com.canhub.cropper.options
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.gson.Gson
import com.azur.howfar.workManger.ProfilePicsWorkManager
import com.azur.howfar.R
import com.azur.howfar.activity.BaseActivity
import com.azur.howfar.databinding.ActivityEditProfileBinding
import com.azur.howfar.dilog.ProgressFragment
import com.azur.howfar.models.UserProfile
import com.azur.howfar.utils.ImageCompressor
import com.azur.howfar.utils.Util
import java.io.ByteArrayInputStream

class EditProfileActivity : BaseActivity() {
    private val binding by lazy { ActivityEditProfileBinding.inflate(layoutInflater) }
    private val uid = FirebaseAuth.getInstance().currentUser!!.uid
    private val progressFragment = ProgressFragment()
    private lateinit var pref: SharedPreferences
    private var ref = FirebaseDatabase.getInstance().reference
    private val workManager = WorkManager.getInstance(this)
    private var profileData = UserProfile()

    private var permissionsStorage = arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pref = getSharedPreferences(getString(R.string.ALL_PREFERENCE), Context.MODE_PRIVATE)
        setContentView(binding.root)
        ref = ref.child(USER_DETAILS).child(uid)
        ref.get().addOnSuccessListener {
            if (it.exists()){
                profileData = it.getValue(UserProfile::class.java)!!
                try {
                    Glide.with(this).load(profileData.image).circleCrop().into(binding.imgUser)
                } catch (e: Exception) {
                }
                binding.etName.setText(profileData.name)
                binding.etEmail.setText(profileData.email)
                binding.etBio.setText(profileData.bio)
            }
        }
        binding.imgUser.setOnClickListener { openImagePick() }
        binding.tvSubmit.setOnClickListener {
            val name = binding.etName.text.trim().toString()
            val bio = binding.etBio.text.trim().toString()
            val email = binding.etEmail.text.trim().toString()
            if (name == "") {
                showSnackBar(binding.root, "Name can't be empty")
                return@setOnClickListener
            }
            if (profileData.phone == "") {
                showSnackBar(binding.root, "Please wait...\nFetching data")
                return@setOnClickListener
            }
            profileData.name = name
            profileData.bio = bio
            profileData.email = email
            supportFragmentManager.beginTransaction().replace(R.id.edit_root, progressFragment).commit()
            workManagerUpload()
        }
    }

    private fun workManagerUpload() {
        val json = Gson().toJson(profileData)
        pref.edit().putString("profileData", json).apply()
        val workRequest = OneTimeWorkRequestBuilder<ProfilePicsWorkManager>().addTag("profile").build()
        workManager.enqueue(workRequest)
        finish()
    }

    private fun openImagePick() {
        if (Util.permissionsAvailable(permissionsStorage, this)) {
            cropImage.launch(
                options {
                    this.setActivityTitle("Profile image")
                    this.setAllowFlipping(true)
                    this.setAllowRotation(true)
                    this.setAutoZoomEnabled(true)
                    this.setBackgroundColor(Color.BLACK)
                    this.setImageSource(includeGallery = true, includeCamera = true)
                    setGuidelines(CropImageView.Guidelines.ON)
                }
            )
        } else {
            ActivityCompat.requestPermissions(this, permissionsStorage, 36)
        }
    }

    private val cropImage = registerForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) {
            val dataResult = result.uriContent!!
            val uriFilePath = result.getUriFilePath(this)!!// optional usage
            try {
                profileData.image = uriFilePath
                val pair: Pair<ByteArrayInputStream, ByteArray> = ImageCompressor.compressImage(dataResult, this, null)
                Glide.with(this).load(pair.second).into(binding.imgUser)
            } catch (e: Exception) {
                println("Exception *********************************************************** ${e.printStackTrace()}")
            }
        } else {
            val exception = result.error
            exception!!.printStackTrace()
        }
    }

    companion object {
        val PROFILE_IMAGE = "user_profile_image"
        const val CALL_REFERENCE = "call_reference"
        const val TRANSFER_HISTORY = "user_coins_transfer"
        const val USER_DETAILS = "user_details"
    }
}