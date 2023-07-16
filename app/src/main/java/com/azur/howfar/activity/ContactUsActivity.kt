package com.azur.howfar.activity

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.azur.howfar.R
import com.azur.howfar.databinding.ActivityContactUsBinding
import com.azur.howfar.models.ChatData
import com.azur.howfar.models.ImageData
import com.azur.howfar.utils.CanHubImage
import com.azur.howfar.utils.ImageCompressor
import com.azur.howfar.workManger.SupportWorkManager
import com.bumptech.glide.Glide
import com.canhub.cropper.CropImageContract
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import java.io.ByteArrayInputStream
import java.util.*

class ContactUsActivity : AppCompatActivity(), SupportDeleteHelper {
    private val binding by lazy { ActivityContactUsBinding.inflate(layoutInflater) }
    private var user = FirebaseAuth.getInstance().currentUser
    private var dataset = arrayListOf<ByteArray>()
    private lateinit var pref: SharedPreferences
    private var imageString = ""
    private var contactImagesAdapter = ContactImagesAdapter()
    private val workManager = WorkManager.getInstance(this)
    private lateinit var canHubImage: CanHubImage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        canHubImage = CanHubImage(this)
        pref = getSharedPreferences(getString(R.string.ALL_PREFERENCE), Context.MODE_PRIVATE)
        contactImagesAdapter.dataset = dataset
        contactImagesAdapter.supportDeleteHelper = this
        binding.rvImages.adapter = contactImagesAdapter
        binding.addImagesCard.setOnClickListener { canHubImage.openCanHub(cropImage, "Moment") }
        binding.send.setOnClickListener {
            val subject = binding.subject.text.trim().toString()
            val content = binding.message.text.trim().toString()
            if (subject == "") {
                Snackbar.make(binding.root, "Empty subject", Snackbar.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if (content == "") {
                Snackbar.make(binding.root, "Empty Message", Snackbar.LENGTH_LONG).show()
                return@setOnClickListener
            }
            binding.subject.text.clear()
            binding.message.text.clear()
            Toast.makeText(this, "Sending...", Toast.LENGTH_LONG).show()
            val data = ChatData(
                senderuid = user!!.uid, displaytitle = subject, msg = content, imageData = ImageData(storageLink = imageString),
                timesent = Calendar.getInstance().timeInMillis.toString(),
            )
            val workRequest = OneTimeWorkRequestBuilder<SupportWorkManager>().addTag("support upload")
                .setInputData(workDataOf("data" to Gson().toJson(data)))
                .build()
            workManager.enqueue(workRequest)
            finish()
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private val cropImage = registerForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) {
            val selectedImage = result.uriContent!!
            //result.getUriFilePath(this)!! // optional usage
            val pair: Pair<ByteArrayInputStream, ByteArray> = ImageCompressor.compressImage(selectedImage, this, null)
            imageString = selectedImage.toString()
            dataset.clear()
            contactImagesAdapter.notifyDataSetChanged()
            dataset.add(pair.second)
            contactImagesAdapter.notifyItemInserted(dataset.size)
        } else {
            val exception = result.error
            exception!!.printStackTrace()
        }
    }

    override fun delete(position: Int) {
        dataset.removeAt(position)
    }
}

interface SupportDeleteHelper {
    fun delete(position: Int)
}

class ContactImagesAdapter : RecyclerView.Adapter<ContactImagesAdapter.ViewHolder>() {
    var dataset = arrayListOf<ByteArray>()
    lateinit var supportDeleteHelper: SupportDeleteHelper
    lateinit var context: Context

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val image: ImageView = itemView.findViewById(R.id.contact_image)
        val delete: ImageView = itemView.findViewById(R.id.delete_image)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        context = parent.context
        val view = LayoutInflater.from(context).inflate(R.layout.contact_support_images, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val datum = dataset[position]
        Glide.with(context).load(dataset[position]).centerCrop().into(holder.image)

        holder.delete.setOnClickListener {
            supportDeleteHelper.delete(dataset.indexOf(datum))
            dataset.remove(datum)
            notifyItemRemoved(dataset.indexOf(datum))
        }
    }

    override fun getItemCount() = dataset.size
}