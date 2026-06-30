package com.example.powergridattendance

import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark

class FaceDetectorHelper {

    fun rotateRect(rect: android.graphics.Rect, imageWidth: Int, imageHeight: Int, rotationDegrees: Int): android.graphics.Rect {
        return when (rotationDegrees) {
            90 -> android.graphics.Rect(
                imageHeight - rect.bottom,
                rect.left,
                imageHeight - rect.top,
                rect.right
            )
            180 -> android.graphics.Rect(
                imageWidth - rect.right,
                imageHeight - rect.bottom,
                imageWidth - rect.left,
                imageHeight - rect.top
            )
            270 -> android.graphics.Rect(
                rect.top,
                imageWidth - rect.right,
                rect.bottom,
                imageWidth - rect.left
            )
            else -> rect
        }
    }

    private val options =
        FaceDetectorOptions.Builder()
            .setPerformanceMode(
                FaceDetectorOptions.PERFORMANCE_MODE_FAST
            )
            .setLandmarkMode(
                FaceDetectorOptions.LANDMARK_MODE_ALL
            )
            .setClassificationMode(
                FaceDetectorOptions.CLASSIFICATION_MODE_ALL
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
                    val face = faces[0]
                    val box = face.boundingBox
                    val imageWidth = image.width
                    val imageHeight = image.height

                    val hasLeftEye = face.getLandmark(FaceLandmark.LEFT_EYE) != null
                    val hasRightEye = face.getLandmark(FaceLandmark.RIGHT_EYE) != null
                    val hasNose = face.getLandmark(FaceLandmark.NOSE_BASE) != null
                    val hasMouthLeft = face.getLandmark(FaceLandmark.MOUTH_LEFT) != null
                    val hasMouthRight = face.getLandmark(FaceLandmark.MOUTH_RIGHT) != null

                    val isWithinBounds = box.left >= 0 && box.top >= 0 &&
                                         box.right <= imageWidth && box.bottom <= imageHeight

                    FaceState.faceDetected.value = true
                    FaceState.faceRect.value = box
                    FaceState.isFullFaceVisible.value = hasLeftEye && hasRightEye && hasNose && hasMouthLeft && hasMouthRight && isWithinBounds

                    LivenessDetector.addFrame(face)

                    Log.d(
                        "FACE_TEST",
                        "FACE DETECTED"
                    )

                    Log.d(
                        "FACE_BOX",
                        "Rect: ${face.boundingBox}"
                    )

                } else {

                    FaceState.faceDetected.value = false
                    FaceState.faceRect.value = null
                    FaceState.isFullFaceVisible.value = false

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
                FaceState.isFullFaceVisible.value = false

                onComplete()
            }
    }
}