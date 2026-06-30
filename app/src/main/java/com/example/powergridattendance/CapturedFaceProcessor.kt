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

            onSuccess = { rect ->

                val croppedFace =
                    FaceCropHelper.cropFace(
                        bitmap,
                        rect
                    )

                if (croppedFace != null) {

                    onSuccess(croppedFace)

                } else {

                    Toast.makeText(
                        context,
                        "Face Crop Failed",
                        Toast.LENGTH_SHORT
                    ).show()

                    onFailure()
                }
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