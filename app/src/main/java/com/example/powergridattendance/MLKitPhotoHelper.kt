package com.example.powergridattendance

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions

object MLKitPhotoHelper {

    private val detector =
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(
                    FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE
                )
                .setMinFaceSize(0.10f)
                .build()
        )

    fun detectFaceInBitmap(
        bitmap: Bitmap,
        onSuccess: (Bitmap) -> Unit,
        onFailure: () -> Unit
    ) {

        val rotations =
            listOf(0, 90, 180, 270)

        tryRotation(
            bitmap,
            rotations,
            0,
            onSuccess,
            onFailure
        )
    }

    private fun tryRotation(
        bitmap: Bitmap,
        rotations: List<Int>,
        index: Int,
        onSuccess: (Bitmap) -> Unit,
        onFailure: () -> Unit
    ) {

        if (index >= rotations.size) {
            onFailure()
            return
        }

        val rotation = rotations[index]

        val image =
            InputImage.fromBitmap(
                bitmap,
                rotation
            )

        detector.process(image)
            .addOnSuccessListener { faces ->

                if (faces.isNotEmpty()) {

                    Log.d(
                        "MLKIT_ROTATION",
                        "Face found at rotation = $rotation"
                    )

                    val uprightBitmap = rotateBitmap(bitmap, rotation)
                    val cropped = FaceCropHelper.cropFace(uprightBitmap, faces[0].boundingBox)
                    
                    if (uprightBitmap != bitmap) {
                        uprightBitmap.recycle()
                    }

                    if (cropped != null) {
                        onSuccess(cropped)
                    } else {
                        onFailure()
                    }

                } else {

                    tryRotation(
                        bitmap,
                        rotations,
                        index + 1,
                        onSuccess,
                        onFailure
                    )
                }
            }
            .addOnFailureListener {

                tryRotation(
                    bitmap,
                    rotations,
                    index + 1,
                    onSuccess,
                    onFailure
                )
            }
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        val matrix = android.graphics.Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}