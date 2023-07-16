package com.azur.howfar.posts

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.Toast
import androidx.activity.viewModels
import androidx.databinding.DataBindingUtil
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.azur.howfar.R
import com.azur.howfar.activity.BaseActivity
import com.azur.howfar.databinding.ActivityUploadPostBinding
import com.azur.howfar.databinding.BottomSheetPrivacyBinding
import com.azur.howfar.models.Moment
import com.azur.howfar.models.MomentPrivacy
import com.azur.howfar.utils.CanHubImage
import com.azur.howfar.utils.Keyboard.hideKeyboard
import com.azur.howfar.viewmodel.UploadPostSpecialViewModel
import com.azur.howfar.workManger.MomentWorkManager
import com.bumptech.glide.Glide
import com.canhub.cropper.CropImageContract
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import java.io.ByteArrayInputStream

class UploadPostActivity : BaseActivity(), View.OnClickListener, CompoundButton.OnCheckedChangeListener {
    private val binding by lazy { ActivityUploadPostBinding.inflate(layoutInflater) }
    private var privacy = Privacy.PUBLIC
    private var selectedImage: Uri? = null
    private val workManager = WorkManager.getInstance(this)
    private var imageStream: Pair<ByteArrayInputStream, ByteArray>? = null
    private var imageUri = ""
    private var user = FirebaseAuth.getInstance().currentUser
    private lateinit var pref: SharedPreferences
    private var PRIVACY = MomentPrivacy.PUBLIC
    private var dataset: ArrayList<String> = arrayListOf()
    private var ALLOW_COMMENT = true
    private var EDITED = false
    private var hashTag: List<String> = listOf()
    private var permissionsStorage = arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
    private var editedMoment = Moment()
    private lateinit var canHubImage: CanHubImage
    private val uploadPostSpecialViewModel by viewModels<UploadPostSpecialViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        canHubImage = CanHubImage(this, 400, 550)
        getAndSetEditData()
        binding.allowComment.setOnCheckedChangeListener(this)
        pref = getSharedPreferences(getString(R.string.ALL_PREFERENCE), Context.MODE_PRIVATE)
        setPrivacy(privacy)
        initListener()
    }

    private fun getAndSetEditData() {
        if (intent.hasExtra("post data")) {
            binding.postHeading.text = "Edit post"
            EDITED = true
            hideAllViews()
            val json = intent.getStringExtra("post data")
            editedMoment = Gson().fromJson(json, Moment::class.java)
            dataset = editedMoment.images
            setImagesPartition()
            imageUri = editedMoment.image
            PRIVACY = editedMoment.privacy
            //Glide.with(this).load(imageUri).centerCrop().into(binding.imageview)
            binding.imageview.adjustViewBounds = true
            //binding.btnDelete.visibility = View.VISIBLE
            binding.caption.setText(editedMoment.caption)
            var hashs = ""
            for (i in editedMoment.hashTags) hashs += " #$i"
            binding.tvHashtag.text = hashs
            hashTag = hashs.trim().replace(" ", "").replace("#", "").lowercase().split(",")
        }
    }

    override fun onBackPressed() {
        goBack()
    }

    private fun initListener() {
        binding.lytPrivacy.setOnClickListener { v: View? ->
            val bottomSheetDialog = BottomSheetDialog(this, R.style.customStyle)
            val sheetPrivacyBinding: BottomSheetPrivacyBinding = DataBindingUtil.inflate(LayoutInflater.from(this), R.layout.bottom_sheet_privacy, null, false)
            bottomSheetDialog.setContentView(sheetPrivacyBinding.root)
            bottomSheetDialog.show()
            sheetPrivacyBinding.tvPublic.setOnClickListener { v1: View? ->
                setPrivacy(Privacy.PUBLIC)
                bottomSheetDialog.dismiss()
            }
            sheetPrivacyBinding.tvOnlyFollowr.setOnClickListener { v1: View? ->
                setPrivacy(Privacy.FOLLOWRS)
                bottomSheetDialog.dismiss()
            }
            sheetPrivacyBinding.tvOnlyMe.setOnClickListener { v1: View? ->
                setPrivacy(Privacy.PRIVATE)
                bottomSheetDialog.dismiss()
            }
        }
        binding.addImages.setOnClickListener(this)
        binding.imagesRoot.setOnClickListener(this)
        binding.allowComment.setOnCheckedChangeListener(this)
        binding.back.setOnClickListener(this)
        binding.btnDelete.setOnClickListener(this)
        binding.lytHashtag.setOnClickListener(this)
    }

    private val cropImage = registerForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) {
            selectedImage = result.uriContent!!
            //result.getUriFilePath(this)!! // optional usage
            binding.imageview.adjustViewBounds = true
            binding.btnDelete.visibility = View.VISIBLE
            dataset.add(selectedImage.toString())
            setImagesPartition()
        } else {
            val exception = result.error
            exception!!.printStackTrace()
        }
    }

    private fun setImagesPartition() {
        when (dataset.size) {
            0 -> hideAllViews()
            1 -> {
                hideAllViews()
                Glide.with(this).load(dataset[0]).into(binding.imageview)
            }
            2 -> {
                hideAllViews()
                showSecondImageRoot()
                Glide.with(this).load(dataset[0]).into(binding.imageview)
                Glide.with(this).load(dataset[0]).into(binding.doubleImage1)
                Glide.with(this).load(dataset[1]).into(binding.doubleImage2)
            }
            3 -> {
                hideAllViews()
                showThirdImageRoot()
                Glide.with(this).load(dataset[0]).into(binding.imageview)
                Glide.with(this).load(dataset[0]).into(binding.tripleImage1)
                Glide.with(this).load(dataset[1]).into(binding.tripleImage2)
                Glide.with(this).load(dataset[2]).into(binding.tripleImage3)
            }
            else -> {
                showThirdImageRoot()
                Glide.with(this).load(dataset[0]).into(binding.imageview)
                Glide.with(this).load(dataset[0]).into(binding.tripleImage1)
                Glide.with(this).load(dataset[1]).into(binding.tripleImage2)
                Glide.with(this).load(dataset[2]).into(binding.tripleImage3)
                binding.tripleImageMore.visibility = View.VISIBLE
            }
        }
    }

    private fun setPrivacy(privacy: Privacy) {
        this.privacy = privacy
        when (privacy) {
            Privacy.PRIVATE -> {
                binding.tvPrivacy.text = "Only Me"
                PRIVACY = MomentPrivacy.ME
            }
            Privacy.FOLLOWRS -> {
                binding.tvPrivacy.text = "My Followers"
                PRIVACY = MomentPrivacy.FOLLOWERS_ONLY
            }
            else -> {
                binding.tvPrivacy.text = "Public"
                PRIVACY = MomentPrivacy.PUBLIC
            }
        }
    }

    fun onClickPost(view: View?) {
        val caption = binding.caption.text.toString().trim()
        when {
            user == null -> {
                Toast.makeText(this, "You are not signed in!!!", Toast.LENGTH_LONG).show()
                return
            }
            imageStream == null && caption.isEmpty() -> {
                Toast.makeText(this, "Add an image or caption to post", Toast.LENGTH_LONG).show()
                return
            }
        }
        val images = arrayListOf<String>()
        for (i in dataset) images.add(i)
        val moment = Moment(
            caption = caption,
            allowedComment = ALLOW_COMMENT,
            hashTags = hashTag,
            images = images,
            privacy = PRIVACY,
            creatorUid = user!!.uid,
            timePosted = if (editedMoment.timePosted == "") "" else editedMoment.timePosted,
            image = imageUri
        )
        workManagerUpload(moment)
        Toast.makeText(this, "Uploading...", Toast.LENGTH_SHORT).show()
        super.onBackPressed()
    }

    private fun workManagerUpload(moment: Moment) {
        val json = Gson().toJson(moment)
        pref.edit().putString(getString(R.string.moment_data), json).apply()
        val workRequest = OneTimeWorkRequestBuilder<MomentWorkManager>().addTag("moment upload")
            .setInputData(workDataOf("edited" to EDITED))
            .build()
        workManager.enqueue(workRequest)
    }

    internal enum class Privacy {
        PUBLIC, FOLLOWRS, PRIVATE
    }

    private fun hideAllViews() {
        binding.rootDoubleImage.visibility = View.GONE
        binding.rootTripleImage.visibility = View.GONE
        binding.tripleImageMore.visibility = View.GONE
    }

    private fun showSecondImageRoot() {
        binding.rootDoubleImage.visibility = View.VISIBLE
    }

    private fun showThirdImageRoot() {
        binding.rootTripleImage.visibility = View.VISIBLE
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.add_images -> canHubImage.openCanHub(cropImage, "Moment")
            R.id.images_root -> {
                if (dataset.size >= 1) {
                    val frag = UploadPostFragment()
                    val bundle = Bundle()
                    bundle.putBoolean("shareAble", false)
                    frag.arguments = bundle
                    uploadPostSpecialViewModel.setImagesData(dataset)
                    supportFragmentManager.beginTransaction().addToBackStack("images").replace(R.id.upload_post_root, frag).commit()
                }
            }
            R.id.btnDelete -> {
                binding.imageview.setImageDrawable(null)
                selectedImage = null
                imageUri = ""
                selectedImage = Uri.EMPTY
                binding.btnDelete.visibility = View.GONE
            }
            R.id.lytHashtag -> try {
                val alert = AlertDialog.Builder(this)
                val view = LayoutInflater.from(this).inflate(R.layout.hash_tag_dialog, null)
                alert.setView(view)
                alert.setCancelable(true)
                val hashDialog = alert.create()

                val close: Button = view.findViewById(R.id.close)
                val caption: EditText = view.findViewById(R.id.caption)
                val addCaption: Button = view.findViewById(R.id.add_caption)
                close.setOnClickListener { hashDialog.dismiss() }
                caption.setText(binding.tvHashtag.text.toString().replace(" ", ""))
                addCaption.setOnClickListener {
                    hashDialog.dismiss()
                    if (caption.text.isEmpty()) return@setOnClickListener
                    val hashes = caption.text.toString().trim().replace(" ", "").replace("#", "").lowercase().split(",")
                    hashTag = hashes
                    var hashText = ""
                    for (i in hashes) hashText += "#${i.replace(" ", "")} "
                    binding.tvHashtag.text = hashText
                }
                alert.setOnDismissListener { hideKeyboard() }
                hashDialog.show()
            } catch (e: Exception) {
            }
            R.id.back -> goBack()
        }
    }

    private fun goBack() {
        if (supportFragmentManager.backStackEntryCount >= 1) {
            super.onBackPressed()
            return
        }
        if (imageUri != "" || binding.caption.text.toString().trim() != "") try {
            val alert = AlertDialog.Builder(this)
            alert.setTitle("Unsaved changes")
            alert.setMessage("You have unsaved changes. Do you want to discard?")
            alert.setPositiveButton("Discard") { dialog, _ -> dialog.dismiss(); super.onBackPressed() }
            alert.setNegativeButton("keep editing") { dialog, _ -> dialog.dismiss() }
            alert.setCancelable(true)
            alert.create().show()
        } catch (e: Exception) {
        } else super.onBackPressed()
    }

    override fun onCheckedChanged(buttonView: CompoundButton?, isChecked: Boolean) {
        if (buttonView == binding.allowComment) {
            ALLOW_COMMENT = isChecked
        }
    }
}