package com.example.powergridattendance

import android.annotation.SuppressLint
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner

@SuppressLint("UnsafeOptInUsageError")
@Composable
fun CameraPreview() {

    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        factory = { ctx ->

            Log.d("FACE_TEST", "CameraPreview Created")

            val previewView = PreviewView(ctx)

            val cameraProviderFuture =
                ProcessCameraProvider.getInstance(ctx)

            cameraProviderFuture.addListener({

                val cameraProvider =
                    cameraProviderFuture.get()

                val preview =
                    Preview.Builder().build()

                preview.setSurfaceProvider(
                    previewView.surfaceProvider
                )

                val cameraSelector =
                    CameraSelector.DEFAULT_FRONT_CAMERA

                // Face Detection
                val imageAnalysis =
                    ImageAnalysis.Builder()
                        .setBackpressureStrategy(
                            ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                        )
                        .build()


                imageAnalysis.setAnalyzer(
                    ContextCompat.getMainExecutor(ctx),
                    FaceAnalyzer()
                )

                // Photo Capture
                val imageCapture =
                    ImageCapture.Builder()
                        .build()
                CameraState.imageCapture = imageCapture

                try {

                    cameraProvider.unbindAll()

                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis,
                        imageCapture
                    )

                    Log.d(
                        "FACE_TEST",
                        "Camera Bound Successfully"
                    )

                } catch (e: Exception) {

                    Log.e(
                        "FACE_TEST",
                        "Camera Binding Failed",
                        e
                    )
                }

            }, ContextCompat.getMainExecutor(ctx))

            previewView
        }
    )
}