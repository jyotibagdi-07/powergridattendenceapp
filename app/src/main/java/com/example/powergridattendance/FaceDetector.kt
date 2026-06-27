package com.example.powergridattendance

import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions

class FaceDetectorHelper {

    private val options =
        FaceDetectorOptions.Builder()
            .setPerformanceMode(
                FaceDetectorOptions.PERFORMANCE_MODE_FAST
            )
            .build()

    private val detector =
        FaceDetection.getClient(options)

    fun detectFace(
        image: InputImage,
        onComplete: () -> Unit
    ) {

        detector.process(image)

            .addOnSuccessListener { faces ->

                if (faces.isNotEmpty()) {

                    FaceState.faceDetected.value = true

                    FaceState.faceRect.value =
                        faces[0].boundingBox

                    Log.d(
                        "FACE_TEST",
                        "FACE DETECTED"
                    )

                    Log.d(
                        "FACE_BOX",
                        "Rect: ${faces[0].boundingBox}"
                    )

                } else {

                    FaceState.faceDetected.value = false
                    FaceState.faceRect.value = null

                    Log.d(
                        "FACE_TEST",
                        "NO FACE"
                    )
                }

                onComplete()
            }

            .addOnFailureListener { e ->

                Log.e(
                    "FACE_TEST",
                    "DETECTION FAILED",
                    e
                )

                FaceState.faceDetected.value = false
                FaceState.faceRect.value = null

                onComplete()
            }
    }
}