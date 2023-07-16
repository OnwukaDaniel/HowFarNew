package com.azur.howfar.photo

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.azur.howfar.R
import com.azur.howfar.databinding.ActivityEditPhotoBinding
import com.bumptech.glide.Glide
import com.google.android.material.snackbar.Snackbar
import ja.burhanrashid52.photoeditor.PhotoEditor
import ja.burhanrashid52.photoeditor.PhotoFilter
import ja.burhanrashid52.photoeditor.shape.ShapeBuilder
import java.io.File


class EditPhotoFragment : AppCompatActivity(), View.OnClickListener {
    private val binding by lazy { ActivityEditPhotoBinding.inflate(layoutInflater) }
    var imagePath = ""
    private lateinit var mPhotoEditor: PhotoEditor
    private lateinit var mShapeBuilder: ShapeBuilder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        imagePath = intent.getStringExtra("image")!!
        Glide.with(this).load(File(imagePath)).centerCrop().into(binding.photoEditorView.source)

        mPhotoEditor = PhotoEditor.Builder(this, binding.photoEditorView)
            .setPinchTextScalable(true)
            .setClipSourceImage(true)
            .build()
        setFilters()
        setUndoRedo()
        binding.editSave.setOnClickListener(this)
        binding.editDone.setOnClickListener(this)
        binding.editClose.setOnClickListener(this)
        binding.editText.setOnClickListener(this)
        binding.editPen.setOnClickListener(this)
        binding.editEmoji.setOnClickListener(this)
        binding.editFilter.setOnClickListener(this)
    }

    private fun setEditText() {
        binding.editColor.visibility = View.VISIBLE
        binding.editEditText.visibility = View.VISIBLE
        val list = arrayListOf(
            binding.color1, binding.color2, binding.color3, binding.color4, binding.color5, binding.color6, binding.color7, binding
                .color8, binding.color9, binding.color10, binding.color11, binding.color12, binding.color13
        )
        val colorList = arrayListOf(
            "#FFFFFF", "#B6A3A3", "#C51162", "#AA00FF", "#6200EA",
            "#00C853", "#915555", "#AEEA00", "#FFAB00", "#FF6D00", "#DD2C00", "#1A237E", "#000000"
        )
        var selectedTextColor = "#FFFFFF"

        for ((index, i) in list.withIndex()) i.setOnClickListener { selectedTextColor = colorList[index] }

        binding.editEditText.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                mPhotoEditor.addText(binding.editEditText.text.trim().toString(), Color.parseColor(selectedTextColor))
                binding.editEditText.text.clear()
                binding.editEditText.visibility = View.GONE
            }
            return@setOnEditorActionListener false
        }
    }

    private fun setUndoRedo() {
        binding.editUndo.setOnClickListener { mPhotoEditor.undo() }
        binding.editRedo.setOnClickListener { mPhotoEditor.redo() }
    }

    private fun setBrushColors() {
        binding.editColor.visibility = View.VISIBLE
        binding.editPenList.visibility = View.VISIBLE
        mPhotoEditor.setBrushDrawingMode(true)
        mShapeBuilder = ShapeBuilder()
            .withShapeOpacity(50)
        val colorList = arrayListOf(
            "#FFFFFF", "#B6A3A3", "#C51162", "#AA00FF", "#6200EA",
            "#00C853", "#915555", "#AEEA00", "#FFAB00", "#FF6D00", "#DD2C00", "#1A237E", "#000000"
        )
        val list = arrayListOf(
            binding.color1, binding.color2, binding.color3, binding.color4, binding.color5, binding.color6, binding.color7, binding
                .color8, binding.color9, binding.color10, binding.color11, binding.color12, binding.color13
        )
        val penList = arrayListOf(binding.editPen1, binding.editPen2, binding.editPen3, binding.editPen4)
        for ((index, i) in list.withIndex()) {
            i.setOnClickListener {
                mShapeBuilder = mShapeBuilder.withShapeColor(Color.parseColor(colorList[index]))
                mPhotoEditor.setShape(mShapeBuilder)
                mPhotoEditor.setBrushDrawingMode(true)
            }
        }

        for ((index, i) in penList.withIndex()) {
            i.setOnClickListener {
                mShapeBuilder = mShapeBuilder.withShapeSize((index + 1) * 10F)
                mPhotoEditor.setShape(mShapeBuilder)
            }
        }
    }

    override fun onBackPressed() {
        if (binding.editToolbar.visibility == View.VISIBLE) {
            hideTools()
            hideToolBar()
            showTools()
        } else super.onBackPressed()
    }

    private fun setFilters() {
        val photoEditor1 = PhotoEditor.Builder(this, binding.filter1).build()
        //Glide.with(this).load(File(imagePath)).into(binding.filter1.source)
        photoEditor1.setFilterEffect(PhotoFilter.NONE)
        binding.filter1.setOnClickListener { mPhotoEditor.setFilterEffect(PhotoFilter.NONE) }

        val photoEditor2 = PhotoEditor.Builder(this, binding.filter2).build()
        //Glide.with(this).load(File(imagePath)).into(binding.filter2.source)
        photoEditor2.setFilterEffect(PhotoFilter.VIGNETTE)
        binding.filter2.setOnClickListener { mPhotoEditor.setFilterEffect(PhotoFilter.VIGNETTE) }

        val photoEditor3 = PhotoEditor.Builder(this, binding.filter3).build()
        //Glide.with(this).load(File(imagePath)).into(binding.filter3.source)
        photoEditor3.setFilterEffect(PhotoFilter.TEMPERATURE)
        binding.filter3.setOnClickListener { mPhotoEditor.setFilterEffect(PhotoFilter.TEMPERATURE) }

        val photoEditor4 = PhotoEditor.Builder(this, binding.filter4).build()
        //Glide.with(this).load(File(imagePath)).into(binding.filter4.source)
        photoEditor4.setFilterEffect(PhotoFilter.SHARPEN)
        binding.filter4.setOnClickListener { mPhotoEditor.setFilterEffect(PhotoFilter.SHARPEN) }

        val photoEditor5 = PhotoEditor.Builder(this, binding.filter5).build()
        //Glide.with(this).load(File(imagePath)).into(binding.filter5.source)
        photoEditor5.setFilterEffect(PhotoFilter.SEPIA)
        binding.filter5.setOnClickListener { mPhotoEditor.setFilterEffect(PhotoFilter.SEPIA) }

        val photoEditor6 = PhotoEditor.Builder(this, binding.filter6).build()
        //Glide.with(this).load(File(imagePath)).into(binding.filter6.source)
        photoEditor6.setFilterEffect(PhotoFilter.GRAY_SCALE)
        binding.filter6.setOnClickListener { mPhotoEditor.setFilterEffect(PhotoFilter.GRAY_SCALE) }

        val photoEditor7 = PhotoEditor.Builder(this, binding.filter7).build()
        //Glide.with(this).load(File(imagePath)).into(binding.filter7.source)
        photoEditor7.setFilterEffect(PhotoFilter.LOMISH)
        binding.filter7.setOnClickListener { mPhotoEditor.setFilterEffect(PhotoFilter.LOMISH) }
    }

    private fun hideTools() {
        binding.editOptions.visibility = View.GONE
        binding.editText.visibility = View.GONE
        binding.editPen.visibility = View.GONE
        binding.editEmoji.visibility = View.GONE
        binding.editFilter.visibility = View.GONE
        binding.filters.visibility = View.GONE
        binding.editSave.visibility = View.GONE
        mPhotoEditor.setBrushDrawingMode(false)
    }

    private fun showTools() {
        binding.editClose.visibility = View.VISIBLE
        binding.editText.visibility = View.VISIBLE
        binding.editPen.visibility = View.VISIBLE
        binding.editEmoji.visibility = View.VISIBLE
        binding.editFilter.visibility = View.VISIBLE
        binding.editOptions.visibility = View.VISIBLE
        binding.editSave.visibility = View.VISIBLE
        mPhotoEditor.setBrushDrawingMode(false)
    }

    private fun hideToolBar() {
        binding.editToolbar.visibility = View.GONE
        binding.editColor.visibility = View.GONE
        binding.editPenList.visibility = View.GONE
        binding.editEditText.visibility = View.GONE
    }

    private fun showToolBar() {
        binding.editToolbar.visibility = View.VISIBLE
    }

    @SuppressLint("MissingPermission")
    override fun onClick(v: View?) {
        hideTools()
        when (v?.id) {
            R.id.edit_save -> {
                val file = File(this.filesDir.path + "/GuestImagesEdited/")
                val oldFiles = File(this.filesDir.path + "/GuestImages/")
                if (!file.exists()) file.mkdir()
                val path = File(file, "${System.currentTimeMillis()}.jpeg")
                mPhotoEditor.saveAsFile(path.absolutePath, object : PhotoEditor.OnSaveListener {
                    override fun onFailure(exception: Exception) {
                        Snackbar.make(binding.root, "Save Failed", Snackbar.LENGTH_SHORT).show()
                        oldFiles.delete()
                    }

                    override fun onSuccess(imagePath: String) {
                        oldFiles.delete()
                        val intent = Intent()
                        intent.putExtra("guest image result", path.absolutePath)
                        setResult(RESULT_OK, intent)
                        finish()
                        Toast.makeText(applicationContext, "Save Successful", Toast.LENGTH_SHORT).show()
                    }
                })
            }
            R.id.edit_done -> {
                hideToolBar()
                showTools()
            }
            R.id.edit_close -> {
                hideToolBar()
                showTools()
            }
            R.id.edit_text -> {
                showToolBar()
                setEditText()
            }
            R.id.edit_pen -> {
                showToolBar()
                setBrushColors()
            }
            R.id.edit_emoji -> {
                showToolBar()
            }
            R.id.edit_filter -> {
                showToolBar()
                binding.filters.visibility = View.VISIBLE
            }
        }
    }

    companion object {
        const val USER_DETAILS = "user_details"
    }
}