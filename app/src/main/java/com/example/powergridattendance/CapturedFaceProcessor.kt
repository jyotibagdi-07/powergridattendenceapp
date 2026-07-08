package com.example.powergridattendance

import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast

object CapturedFaceProcessor {

    fun processCapturedFace(
        context: Context,
        bitmap: Bitmap,
        onSuccess: (Bitmap) -> Unit,
        onFailure: () -> Unit
    ) {

        MLKitPhotoHelper.detectFaceInBitmap(
            bitmap = bitmap,

            onSuccess = { croppedFace ->
                onSuccess(croppedFace)
            },

            onFailure = {

                Toast.makeText(
                    context,
                    "No Face Found In Captured Image",
                    Toast.LENGTH_SHORT
                ).show()

                onFailure()
            }
        )
    }
}