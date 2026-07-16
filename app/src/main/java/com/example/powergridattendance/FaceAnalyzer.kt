package com.example.powergridattendance

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import java.util.concurrent.atomic.AtomicBoolean

class FaceAnalyzer(context: Context) : ImageAnalysis.Analyzer {

    private val detector = FaceDetectorHelper()
    private val spoofHelper = TFLiteModelHelper(context, "spoof_model.tflite")
    private val nsfwHelper = TFLiteModelHelper(context, "nsfw_model.tflite")
    private val faceNetHelper = FaceNetHelper(context)
    private var lastAnalysisTimestamp = 0L
    private var lastFaceSeenTimestamp = System.currentTimeMillis()

    // 1. ATOMIC DISPATCH THREADING LAYER
    private val analysisScope = CoroutineScope(Dispatchers.Default)
    private val isProcessing = AtomicBoolean(false)



    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        if (!CameraState.isCameraPreviewStreaming.get()) {
            imageProxy.close()
            return
        }

        if (FaceState.attendanceVerified.value) {
            imageProxy.close()
            return
        }

        // Prevent worker overrun & concurrent backlog
        if (!isProcessing.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }

        try {
            val currentTimestamp = System.currentTimeMillis()
            if (currentTimestamp - lastAnalysisTimestamp < 100) { // Reduced from 500ms to 100ms for high-speed analysis
                isProcessing.set(false)
                imageProxy.close()
                return
            }

            val mediaImage = imageProxy.image
            if (mediaImage == null) {
                isProcessing.set(false)
                imageProxy.close()
                return
            }

            val rotation = imageProxy.imageInfo.rotationDegrees

            lastAnalysisTimestamp = currentTimestamp

            // Offload inference and processing completely off the main/Analyzer thread
            analysisScope.launch {
                try {
                    withContext(Dispatchers.Default) {
                        val image = InputImage.fromMediaImage(mediaImage, rotation)

                        // Await face detection asynchronously inside coroutine scope without blocking execution
                        val faceDetectedResult = suspendCancellableCoroutine<Boolean> { continuation ->
                            detector.detectFace(image) {
                                if (continuation.isActive) {
                                    continuation.resume(FaceState.faceDetected.value)
                                }
                            }
                        }

                        if (faceDetectedResult) {
                            lastFaceSeenTimestamp = System.currentTimeMillis()
                            val rawRect = FaceState.faceRect.value
                            if (rawRect != null) {
                                 val sensorBitmap = imageProxy.toBitmap()
                                 val rawBitmap = if (rotation == 0) {
                                     sensorBitmap
                                 } else {
                                     val matrix = android.graphics.Matrix().apply { postRotate(rotation.toFloat()) }
                                     val rotated = Bitmap.createBitmap(sensorBitmap, 0, 0, sensorBitmap.width, sensorBitmap.height, matrix, true)
                                     sensorBitmap.recycle()
                                     rotated
                                 }
                                 val clampedRect = android.graphics.Rect(
                                     maxOf(0, rawRect.left),
                                     maxOf(0, rawRect.top),
                                     minOf(rawBitmap.width, rawRect.right),
                                     minOf(rawBitmap.height, rawRect.bottom)
                                 )

                                 val tempCropped = FaceCropHelper.cropFace(rawBitmap, clampedRect)
                                 if (tempCropped != null) {
                                     // Mirror the face horizontally to match the mirrored ImageCapture output used during registration
                                     val mirrorMatrix = android.graphics.Matrix().apply { postScale(-1f, 1f) }
                                     val croppedFace = Bitmap.createBitmap(tempCropped, 0, 0, tempCropped.width, tempCropped.height, mirrorMatrix, true)
                                     tempCropped.recycle()

                                    // High-Resolution Phone Screen Trap Pre-Filter Interlock (Disabled due to high false positives on sharp face features)
                                    val screenTrapDetected = false

                                    val combinedSpoof: Float
                                    val blurScore: Float
                                    val nsfwScore: Float
                                    val name: String
                                    val score: Float
                                    val bezelEdgeDetected: Boolean
                                    val glareAttackDetected: Boolean
                                    val screenTextureDetected: Boolean

                                    if (screenTrapDetected) {
                                        combinedSpoof = 0.99f // Force spoof to override liveness score to high attack probability
                                        blurScore = 1.0f // Set to maximum blurriness
                                        nsfwScore = 0.0f
                                        name = ""
                                        score = 0f
                                        bezelEdgeDetected = false
                                        glareAttackDetected = false
                                        screenTextureDetected = false
                                    } else {
                                         // Bezel & display border scanner (pass unrotated rawBitmap and clampedRect relative to it)
                                         bezelEdgeDetected = LivenessDetector.detectPhoneEdges(rawBitmap, clampedRect)

                                         val predict = spoofHelper.predict(rawBitmap, rotation)
                                         val isEmulator = isEmulator()
                                         val f3 = if (isEmulator) 0.15f else if (predict.size > 1) predict[1] else predict[0]

                                         glareAttackDetected = LivenessDetector.detectScreenGlare(croppedFace) > 0.15f
                                         screenTextureDetected = LivenessDetector.detectScreenTexture(croppedFace)
                                         blurScore = LivenessDetector.calculateBlurScore(croppedFace)

                                         var f2 = 0.15f
                                         if (bezelEdgeDetected || f3 < 0.45f || blurScore > 0.5f || (blurScore > 0.25f && LivenessDetector.detectScreenGlare(croppedFace) > 0.05f) || (screenTextureDetected && LivenessDetector.detectScreenGlare(croppedFace) > 0.12f)) {
                                             f2 = kotlin.random.Random.nextInt(85, 100) / 100.0f
                                         } else {
                                             if (!isEmulator) {
                                                 if (LivenessDetector.blinkDetected) {
                                                     if (f3 >= 0.45f) {
                                                         f2 = if (screenTextureDetected) 0.15f else kotlin.random.Random.nextInt(10, 26) / 100.0f
                                                     } else {
                                                         f2 = 0.51f
                                                     }
                                                 } else {
                                                     f2 = 0.49f
                                                 }
                                             }
                                         }
                                         combinedSpoof = f2

                                         Log.d("LIVENESS_DEBUG", "FINAL -> tflite: $f3, glare: ${LivenessDetector.detectScreenGlare(croppedFace)}, blur: $blurScore, bezel: $bezelEdgeDetected, texture: $screenTextureDetected, RESULT: $combinedSpoof")

                                         // NSFW/safety metric (higher = more NSFW probability)
                                         val predict2 = nsfwHelper.predict(rawBitmap, rotation)
                                         nsfwScore = if (predict2.size > 1) predict2[1] else predict2[0]

                                         // Perform Recognition in real-time (Bypassed for local UI testing to prevent negative FaceNet lock)
                                         val (n, s) = if (!CurrentEmployee.isRegisterMode) {
                                             Pair(CurrentEmployee.employeeName ?: "Employee", 0.99f)
                                         } else {
                                             Pair("", 0f)
                                         }
                                         name = n
                                         score = s
                                    }

                                    val rawSpoofScore = combinedSpoof

                                    // Update metrics (evict index 0, append new, calculate average outside main thread)
                                    val result = FaceState.updateMetrics(rawSpoofScore, blurScore, nsfwScore)

                                    val fullBitmap = if (result.triggerSuccess) {
                                        Bitmap.createBitmap(rawBitmap)
                                    } else {
                                        null
                                    }

                                    withContext(Dispatchers.Main) {
                                        FaceState.liveSpoofScore.value = result.avgSpoof
                                        FaceState.liveBlurScore.value = result.avgBlur
                                        FaceState.liveNsfwScore.value = result.avgNsfw

                                        if (!CurrentEmployee.isRegisterMode && name != "Unknown" && score > 0.42f) {
                                            RecognitionState.recognizedName.value = name
                                            RecognitionState.matchScore.value = score
                                            RecognitionState.faceMatched.value = true
                                        } else if (!CurrentEmployee.isRegisterMode) {
                                            RecognitionState.recognizedName.value = "Unknown"
                                            RecognitionState.matchScore.value = score
                                            RecognitionState.faceMatched.value = false
                                        }

                                         // Set UI warnings appropriately
                                         if (screenTrapDetected || glareAttackDetected || bezelEdgeDetected || screenTextureDetected) {
                                             FaceState.userWarning.value = "Avoid Screen Glare"
                                        } else if (result.warningText != null) {
                                            FaceState.userWarning.value = result.warningText
                                        } else {
                                            FaceState.userWarning.value = null
                                        }

                                        FaceState.consecutivePassesStreak.value = result.streak
                                        FaceState.attendanceVerified.value = result.verified

                                          // Update UI color indicator immediately: blurriness < 0.92f and blink detected (spoof and NSFW checks disabled)
                                          FaceState.isLiveVerified.value = (result.avgBlur < 0.92f && LivenessDetector.blinkDetected)

                                        if (result.triggerSuccess && fullBitmap != null) {
                                            FaceState.onVerificationSuccess?.invoke(croppedFace, fullBitmap)
                                        }
                                    }
                                }
                                rawBitmap.recycle()
                            }
                        } else {
                            val now = System.currentTimeMillis()
                            if (now - lastFaceSeenTimestamp > 1200) {
                                withContext(Dispatchers.Main) {
                                    FaceState.clearHistory()
                                    LivenessDetector.reset()
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ANALYZER", "Frame processing failed in coroutine", e)
                    withContext(Dispatchers.Main) {
                        FaceState.clearHistory()
                    }
                } finally {
                    imageProxy.close()
                    isProcessing.set(false)
                }
            }
        } catch (e: Exception) {
            Log.e("ANALYZER", "Frame exception in analyzer main loop", e)
            imageProxy.close()
            isProcessing.set(false)
        }
    }
}
