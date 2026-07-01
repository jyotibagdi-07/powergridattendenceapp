package com.example.powergridattendance

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

class FaceAnalyzer(private val context: Context) : ImageAnalysis.Analyzer {

    private val detector = FaceDetectorHelper()
    private val spoofHelper = TFLiteModelHelper(context, "spoof_model.tflite")
    private val nsfwHelper = TFLiteModelHelper(context, "nsfw_model.tflite")
    private var lastAnalysisTimestamp = 0L
    private var lastFaceSeenTimestamp = 0L
    private val coroutineScope = CoroutineScope(Dispatchers.Default)
    private val isAnalyzing = AtomicBoolean(false)

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image

        if (mediaImage != null) {
            val rotation = imageProxy.imageInfo.rotationDegrees
            val image = InputImage.fromMediaImage(mediaImage, rotation)

            detector.detectFace(image) {
                if (FaceState.faceDetected.value) {
                    lastFaceSeenTimestamp = System.currentTimeMillis()
                    val currentTimestamp = System.currentTimeMillis()
                    
                    // Throttling to once per 500ms and using AtomicBoolean backpressure flag
                    if (currentTimestamp - lastAnalysisTimestamp >= 500) {
                        if (isAnalyzing.compareAndSet(false, true)) {
                            lastAnalysisTimestamp = currentTimestamp
                            try {
                                val width = imageProxy.width
                                val height = imageProxy.height
                                val rotatedBitmap = imageProxy.toBitmap()

                                // Close imageProxy immediately so the camera preview doesn't block
                                imageProxy.close()

                                coroutineScope.launch {
                                    try {
                                        val rawRect = FaceState.faceRect.value
                                        if (rawRect != null) {
                                            val rotatedRect = detector.rotateRect(
                                                rawRect,
                                                width,
                                                height,
                                                rotation
                                            )
                                            val croppedFace = FaceCropHelper.cropFace(rotatedBitmap, rotatedRect)
                                            if (croppedFace != null) {
                                                // Combined Spoof Detection Logic
                                                val tfliteSpoof = spoofHelper.predict(croppedFace)
                                                val hsvSpoof = LivenessDetector.analyzeHSVSpoof(croppedFace)
                                                val glareSpoof = LivenessDetector.detectScreenGlare(croppedFace)
                                                
                                                val combinedSpoof = if (glareSpoof > 0.5f) 0.95f else (tfliteSpoof + hsvSpoof) / 2f

                                                // Laplacian Variance Blur Detection
                                                val blurScore = LivenessDetector.calculateBlurScore(croppedFace)

                                                // NSFW detection
                                                val nsfwScore = nsfwHelper.predict(rotatedBitmap)

                                                withContext(Dispatchers.Main) {
                                                    FaceState.addSpoofScore(combinedSpoof)
                                                    FaceState.addBlurScore(blurScore)
                                                    FaceState.addNsfwScore(nsfwScore)
                                                    
                                                    // Relaxed verification requirements for better reliability:
                                                    // 1. Spoof score must be low-ish (< 0.6)
                                                    // 2. Image must be somewhat sharp (blur > 8)
                                                    // 3. Image must be safe (nsfw < 0.5)
                                                    // 4. A clear blink must have been detected
                                                    val isLive = (combinedSpoof < 0.6f && 
                                                                 blurScore > 8f && 
                                                                 nsfwScore < 0.5f &&
                                                                 LivenessDetector.isBlinkDetected())
                                                    
                                                    FaceState.isLiveVerified.value = isLive
                                                    
                                                    if (isLive) {
                                                        Log.d("LIVENESS_SUCCESS", "Face Verified as LIVE")
                                                    }
                                                }
                                            } else {
                                                withContext(Dispatchers.Main) {
                                                    FaceState.clearHistory()
                                                }
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.e("SPOOF_LIVE", "Error predicting scores", e)
                                        withContext(Dispatchers.Main) {
                                            FaceState.clearHistory()
                                        }
                                    } finally {
                                        isAnalyzing.set(false)
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("SPOOF_LIVE", "Error converting ImageProxy to Bitmap", e)
                                isAnalyzing.set(false)
                                imageProxy.close()
                            }
                        } else {
                            // Drop frame: analysis currently in progress
                            imageProxy.close()
                        }
                    } else {
                        // Throttled frame
                        imageProxy.close()
                    }
                } else {
                    // No face detected, reset live scores after a 2-second grace period to prevent flickering on quick drops/blinks
                    val now = System.currentTimeMillis()
                    if (now - lastFaceSeenTimestamp > 2000) {
                        FaceState.clearHistory()
                        LivenessDetector.reset()
                    }
                    imageProxy.close()
                }
            }
        } else {
            imageProxy.close()
        }
    }
}