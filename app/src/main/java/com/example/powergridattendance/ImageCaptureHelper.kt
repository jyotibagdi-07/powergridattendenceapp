package com.example.powergridattendance

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.widget.Toast
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import java.io.File

object ImageCaptureHelper {

    fun captureImage(
        context: Context,
        imageCapture: ImageCapture?,
        fileName: String,
        onSaved: ((Bitmap) -> Unit)? = null
    ) {

        if (imageCapture == null) {

            Toast.makeText(
                context,
                "Camera Not Ready",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val photoFile = File(
            context.filesDir,
            fileName
        )

        val outputOptions =
            ImageCapture.OutputFileOptions.Builder(
                photoFile
            ).build()

        imageCapture.takePicture(
            outputOptions,
            androidx.core.content.ContextCompat.getMainExecutor(context),

            object : ImageCapture.OnImageSavedCallback {

                override fun onImageSaved(
                    outputFileResults: ImageCapture.OutputFileResults
                ) {

                    Log.d(
                        "STEP1",
                        "Saved path = ${photoFile.absolutePath}"
                    )

                    val bitmap =
                        BitmapFactory.decodeFile(
                            photoFile.absolutePath
                        )

                    if (bitmap == null) {

                        Log.e(
                            "STEP1",
                            "Bitmap decode FAILED"
                        )

                        Toast.makeText(
                            context,
                            "Bitmap Decode Failed",
                            Toast.LENGTH_LONG
                        ).show()

                        return
                    }

                    Log.d(
                        "STEP1",
                        "Bitmap size = ${bitmap.width} x ${bitmap.height}"
                    )

                    Toast.makeText(
                        context,
                        "Image Saved: $fileName",
                        Toast.LENGTH_LONG
                    ).show()

                    onSaved?.invoke(bitmap)
                }

                override fun onError(
                    exception: ImageCaptureException
                ) {

                    Log.e(
                        "STEP1",
                        "Capture Failed",
                        exception
                    )

                    Toast.makeText(
                        context,
                        "Save Failed",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        )
    }
}