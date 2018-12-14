package com.ysn.fotos

import android.Manifest
import android.app.Activity
import android.app.ProgressDialog
import android.content.DialogInterface
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.widget.Toast
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_main.*
import okhttp3.*
import org.json.JSONObject
import java.io.File
import java.io.IOException

class MainActivity : AppCompatActivity(), View.OnClickListener {

    private val TAG = javaClass.simpleName
    lateinit var picturePath: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(arrayOf(Manifest.permission.INTERNET, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE), 100)
        }
        button_choose_photo.setOnClickListener(this)
        button_upload_photo.setOnClickListener(this)
    }

    override fun onClick(view: View?) {
        when (view?.id) {
            R.id.button_choose_photo -> {
                val intentGallery = Intent()
                intentGallery.type = "image/*"
                intentGallery.action = Intent.ACTION_GET_CONTENT
                val intentChooser = Intent.createChooser(intentGallery, "Choose Photo")
                startActivityForResult(intentChooser, 101)
            }
            R.id.button_upload_photo -> {
                if (picturePath.isEmpty()) {
                    Toast.makeText(this, "Invalid image", Toast.LENGTH_LONG)
                        .show()
                    return
                }
                val exifInterface = ExifInterface(picturePath)
                val dataPhoto = exifInterface.getAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION)
                if (dataPhoto != null) {
                    val jsonObjectDataPhoto = JSONObject(dataPhoto)
                    if (jsonObjectDataPhoto.has("upload")) {
                        val alertDialogInfo = AlertDialog.Builder(this)
                            .setMessage("Photo cannot be uploaded because already on the server")
                            .setNegativeButton("Dismiss") { dialog: DialogInterface?, _: Int ->
                                dialog?.dismiss()
                            }
                            .create()
                        alertDialogInfo.show()
                        return
                    }
                }

                val progressDialog = ProgressDialog(this)
                progressDialog.setMessage("Uploading...")
                progressDialog.setCancelable(false)
                progressDialog.show()
                Observable
                    .create<String> {
                        val okHttpClient = OkHttpClient.Builder()
                            .build()
                        val requestBodyBuilder = MultipartBody.Builder()
                            .setType(MultipartBody.FORM)
                            .addFormDataPart(
                                "image",
                                "photo",
                                RequestBody.create(MediaType.parse("image/jpeg"), File(picturePath))
                            )
                        val request = Request.Builder()
                            .url("https://api.imgur.com/3/image")
                            .addHeader("Authorization", "Client-ID 2b9608076de1a3f")
                            .post(requestBodyBuilder.build())
                            .build()
                        okHttpClient.newCall(request)
                            .enqueue(object : Callback {
                                override fun onFailure(call: Call, e: IOException) {
                                    e.printStackTrace()
                                    Toast.makeText(this@MainActivity, "Upload photo failed", Toast.LENGTH_LONG)
                                        .show()
                                }

                                override fun onResponse(call: Call, response: Response) {
                                    val jsonObjectResponse = JSONObject(response.body()?.string())
                                    val link = jsonObjectResponse.getJSONObject("data")
                                        .getString("link")
                                    it.onNext(link)
                                    it.onComplete()
                                }
                            })
                    }
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                        {
                            progressDialog.dismiss()
                            Log.d(TAG, "link: $it")
                            image_view.setImageBitmap(null)
                            picturePath = ""
                            Toast.makeText(this@MainActivity, "Upload photo success", Toast.LENGTH_LONG)
                                .show()
                            val intentShare = Intent(Intent.ACTION_SEND)
                            intentShare.type = "text/plain"
                            intentShare.putExtra(Intent.EXTRA_TEXT, "link image: $it")
                            startActivity(intentShare)
                        },
                        {
                            progressDialog.dismiss()
                            it.printStackTrace()
                            Toast.makeText(this, it.message, Toast.LENGTH_LONG)
                                .show()
                        },
                        {
                            /* nothing to do in here */
                        }
                    )
            }
            else -> {
                /* nothing to do in here */
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                100 -> {
                    /* nothing to do in here */
                }
                101 -> {
                    val uriSelectedImage = data?.data
                    val filePathColumn = arrayOf(MediaStore.Images.Media.DATA)
                    val cursor = contentResolver.query(uriSelectedImage!!, filePathColumn, null, null, null)
                    if (cursor == null || cursor.count < 1) {
                        return
                    }

                    cursor.moveToFirst()
                    val columnIndex = cursor.getColumnIndex(filePathColumn[0])
                    if (columnIndex < 0) {
                        Toast.makeText(this@MainActivity, "Invalid image", Toast.LENGTH_LONG)
                            .show()
                        return
                    }

                    picturePath = cursor.getString(columnIndex)
                    if (picturePath == null) {
                        Toast.makeText(this@MainActivity, "Picture path not found", Toast.LENGTH_LONG)
                            .show()
                        return
                    }
                    cursor.close()

                    Log.d(TAG, "picturePath: $picturePath")
                    val exifInterface = ExifInterface(picturePath)
                    val jsonObjectData = JSONObject()
                    jsonObjectData.put("upload", true)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        exifInterface.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, jsonObjectData.toString())
                        exifInterface.saveAttributes()
                    }
                    val bitmap = BitmapFactory.decodeFile(picturePath)
                    image_view.setImageBitmap(bitmap)
                }
                else -> {
                    /* nothing to do in here */
                }
            }
        }
    }
}
