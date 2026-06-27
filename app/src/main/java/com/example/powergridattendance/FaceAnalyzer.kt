package com.example.powergridattendance

import android.annotation.SuppressLint
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage

class FaceAnalyzer : ImageAnalysis.Analyzer {

    private val detector =
        FaceDetectorHelper()

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(
        imageProxy: ImageProxy
    ) {

        val mediaImage = imageProxy.image

        if (mediaImage != null) {

            val image =
                InputImage.fromMediaImage(
                    mediaImage,
                    imageProxy.imageInfo.rotationDegrees
                )

            detector.detectFace(image) {

                imageProxy.close()
            }

        } else {

            imageProxy.close()
        }
    }
}