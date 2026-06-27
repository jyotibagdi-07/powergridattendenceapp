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
        onSuccess: (android.graphics.Rect) -> Unit,
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
        onSuccess: (android.graphics.Rect) -> Unit,
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

                    onSuccess(
                        faces[0].boundingBox
                    )

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
}