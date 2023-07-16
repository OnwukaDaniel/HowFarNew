package com.azur.howfar.videos

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.bumptech.glide.Glide
import com.azur.howfar.R
import com.azur.howfar.databinding.ActivityChooseVideoTypeBinding
import com.azur.howfar.utils.Util

class ChooseVideoTypeActivity : AppCompatActivity(), View.OnClickListener {
    private val binding by lazy { ActivityChooseVideoTypeBinding.inflate(layoutInflater) }
    private var permissionsStorage = arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)

    private val pickVideoLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { dataResult ->
        try {
            if (dataResult.data!!.data == null) return@registerForActivityResult
            if (dataResult.resultCode == RESULT_OK) {
                val videoUri = dataResult.data!!.data!!
                //val lastPathSegment = videoUri.lastPathSegment!!
                //val name = lastPathSegment.substring(lastPathSegment.lastIndexOf("/") + 1)
                val fragment = FragmentFetchVideoParams()
                val bundle = Bundle()
                bundle.putString("data", videoUri.toString())
                fragment.arguments = bundle
                supportFragmentManager.beginTransaction().addToBackStack("video edit").replace(R.id.choose_video_root, fragment).commit()
            } else {
            }
        } catch (e: Exception) {
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Glide.with(this).load(R.drawable.video_gif1).into(binding.videoGif)
        setContentView(binding.root)
        binding.chooseVideo.setOnClickListener(this)
    }

    private fun backLogic() {
        try {
            val alert = AlertDialog.Builder(this)
            alert.setTitle("Exit")
            alert.setMessage("Do you want to discard this video?")
            alert.setNegativeButton("Continue") { dialog, _ ->
                dialog.dismiss()
            }
            alert.setPositiveButton("Discard") { dialog, _ ->
                dialog.dismiss()
                super.onBackPressed()
            }
            alert.create().show()
        } catch (e: Exception) {
            super.onBackPressed()
        }
    }

    override fun onBackPressed() {
        if (supportFragmentManager.backStackEntryCount == 1) backLogic()
        else super.onBackPressed()
    }

    private fun openVideoPicker() {
        if (Util.permissionsAvailable(permissionsStorage, this)) {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            pickVideoLauncher.launch(intent)
        } else {
            ActivityCompat.requestPermissions(this, permissionsStorage, 41)
        }
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.choose_video -> openVideoPicker()
            R.id.create_video -> {}
        }
    }
}