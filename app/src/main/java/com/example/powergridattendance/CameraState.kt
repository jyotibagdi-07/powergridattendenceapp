package com.example.powergridattendance

import androidx.camera.core.ImageCapture
import java.util.concurrent.atomic.AtomicBoolean

object CameraState {
    var imageCapture: ImageCapture? = null
    val isCameraPreviewStreaming = AtomicBoolean(false)
}