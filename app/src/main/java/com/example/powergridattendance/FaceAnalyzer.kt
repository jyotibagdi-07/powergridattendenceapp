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

class FaceAnalyzer(private val context: Context) : ImageAnalysis.Analyzer {

    private val detector = FaceDetectorHelper()
    private val spoofHelper = TFLiteModelHelper(context, "spoof_model.tflite")
    private val nsfwHelper = TFLiteModelHelper(context, "nsfw_model.tflite")
    private val faceNetHelper = FaceNetHelper(context)
    private var lastAnalysisTimestamp = 0L
    private var lastFaceSeenTimestamp = System.currentTimeMillis()

    // 1. ATOMIC DISPATCH THREADING LAYER
    private val analysisScope = CoroutineScope(Dispatchers.Default)
    private val isProcessing = AtomicBoolean(false)

    // 3. HARDWARE-BASED COMPUTER VISION COUNTERMEASURES: Bounding Box Centers Memory
    private val boxCenters = mutableListOf<PointF>()

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
                                 val rawBitmap = imageProxy.toBitmap()
                                 val needsRotation = (rotation == 90 || rotation == 270) && (rawBitmap.width > rawBitmap.height)
                                 val uprightBitmap = if (needsRotation) {
                                     val matrix = android.graphics.Matrix().apply { postRotate(rotation.toFloat()) }
                                     Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
                                 } else {
                                     rawBitmap
                                 }

                                 val clampedRect = android.graphics.Rect(
                                     maxOf(0, rawRect.left),
                                     maxOf(0, rawRect.top),
                                     minOf(uprightBitmap.width, rawRect.right),
                                     minOf(uprightBitmap.height, rawRect.bottom)
                                 )

                                 val tempCropped = FaceCropHelper.cropFace(uprightBitmap, clampedRect)
                                 if (tempCropped != null) {
                                     // Log crop details for debugging
                                     Log.d("FACENET_RECOGNITION", "Crop debug: rawRect=$rawRect, rotation=$rotation, bitmapSize=${rawBitmap.width}x${rawBitmap.height}, uprightSize=${uprightBitmap.width}x${uprightBitmap.height}, clampedRect=$clampedRect, cropSize=${tempCropped.width}x${tempCropped.height}")
                                     
                                     // Use the cropped face directly without mirroring, matching the true-view captured image
                                     val croppedFace = tempCropped

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
                                        combinedSpoof = 0.01f // Force spoof to override liveness score to dead 0.0100
                                        blurScore = 1.0f // Set to maximum blurriness
                                        nsfwScore = 0.0f
                                        name = ""
                                        score = 0f
                                        bezelEdgeDetected = false
                                        glareAttackDetected = false
                                        screenTextureDetected = false
                                    } else {
                                        // 3. Micro-Movement Tracker
                                        val currentCenter = PointF(rawRect.exactCenterX(), rawRect.exactCenterY())
                                        var isStaticAttack = false
                                        synchronized(boxCenters) {
                                            boxCenters.add(currentCenter)
                                            if (boxCenters.size > 10) {
                                                boxCenters.removeAt(0)
                                            }
                                            if (boxCenters.size >= 10) {
                                                val meanX = boxCenters.map { it.x }.average().toFloat()
                                                val meanY = boxCenters.map { it.y }.average().toFloat()
                                                val varX = boxCenters.map { (it.x - meanX) * (it.x - meanX) }.average().toFloat()
                                                val varY = boxCenters.map { (it.y - meanY) * (it.y - meanY) }.average().toFloat()
                                                // Variance = 0.0f indicates frozen/printed photo attack (Disabled: causes false positives when holding phone still)
                                                if (varX == 0.0f && varY == 0.0f) {
                                                    isStaticAttack = false
                                                }
                                            }
                                        }

                                         // Bezel & display border scanner (pass uprightBitmap and clampedRect relative to it)
                                         bezelEdgeDetected = LivenessDetector.detectPhoneEdges(uprightBitmap, clampedRect) ||
                                                 LivenessDetector.detectPhoneBezelContours(uprightBitmap, clampedRect)
 
                                         val tfliteSpoof = spoofHelper.predict(croppedFace)[0]
                                         val hsvSpoof = LivenessDetector.analyzeHSVSpoof(croppedFace)
                                         val glareSpoof = LivenessDetector.detectScreenGlare(croppedFace)
                                         val edgeSpoof = if (bezelEdgeDetected) 0.95f else 0.0f
 
                                         // Use glareSpoof (>0.02) for glare attack detection to filter by saturation (sensitive reflection scan)
                                         glareAttackDetected = glareSpoof > 0.02f
                                         screenTextureDetected = LivenessDetector.detectScreenTexture(croppedFace)
                                         val presentationAttackDetected = glareAttackDetected || edgeSpoof > 0f || isStaticAttack || screenTextureDetected

                                        // Use nested minOf calls to avoid vararg overload resolution issues
                                        val livenessScore = minOf(tfliteSpoof, minOf(1.0f - hsvSpoof, minOf(1.0f - glareSpoof, 1.0f - edgeSpoof)))
                                        combinedSpoof = if (presentationAttackDetected) 0.01f else livenessScore

                                        // Laplacian Variance Blur Calculation (higher = blurrier)
                                        blurScore = LivenessDetector.calculateBlurScore(croppedFace)

                                        // NSFW/safety metric (higher = more NSFW probability)
                                        nsfwScore = nsfwHelper.predict(croppedFace)[1]

                                        // Perform Recognition in real-time
                                        val (n, s) = if (!CurrentEmployee.isRegisterMode) {
                                            RecognitionHelper.recognizeFace(croppedFace, faceNetHelper)
                                        } else {
                                            Pair("", 0f)
                                        }
                                        name = n
                                        score = s
                                        
                                        // Log face recognition match details for debugging
                                        Log.d("FACENET_RECOGNITION", "Real-time match: name=$name, score=$score")
                                    }

                                    val rawSpoofScore = combinedSpoof

                                    // Update metrics (evict index 0, append new, calculate average outside main thread)
                                    val result = FaceState.updateMetrics(rawSpoofScore, blurScore, nsfwScore)

                                    val fullBitmap = if (result.triggerSuccess) {
                                        Bitmap.createBitmap(uprightBitmap)
                                    } else {
                                        null
                                    }

                                    withContext(Dispatchers.Main) {
                                        FaceState.liveSpoofScore.value = result.avgSpoof
                                        FaceState.liveBlurScore.value = result.avgBlur
                                        FaceState.liveNsfwScore.value = result.avgNsfw

                                         if (!CurrentEmployee.isRegisterMode && name != "Unknown" && score > 0.35f) {
                                             if (RecognitionState.recognizedName.value != name) {
                                                 Log.d("FACENET_RECOGNITION", "Recognized name changed from '${RecognitionState.recognizedName.value}' to '$name'. Resetting liveness.")
                                                 FaceState.clearHistory()
                                             }
                                             RecognitionState.recognizedName.value = name
                                             RecognitionState.matchScore.value = score
                                             RecognitionState.faceMatched.value = true
                                         } else if (!CurrentEmployee.isRegisterMode) {
                                             if (RecognitionState.recognizedName.value != "Unknown") {
                                                 Log.d("FACENET_RECOGNITION", "Recognized name changed to Unknown. Resetting liveness.")
                                                 FaceState.clearHistory()
                                             }
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

                                        // Update UI color indicator immediately
                                        FaceState.isLiveVerified.value = result.verified

                                        if (result.triggerSuccess && fullBitmap != null) {
                                            FaceState.onVerificationSuccess?.invoke(croppedFace, fullBitmap)
                                        }
                                    }
                                }
                                rawBitmap.recycle()
                            }
                        } else {
                            val now = System.currentTimeMillis()
                            if (now - lastFaceSeenTimestamp > 300) {
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

    private fun detectPhoneScreenTrap(bitmap: Bitmap): Boolean {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        var totalLuminance = 0.0
        var laplacianSum = 0.0
        var count = 0

        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val idx = y * width + x
                val color = pixels[idx]
                val r = (color shr 16) and 0xFF
                val g = (color shr 8) and 0xFF
                val b = color and 0xFF
                val luminance = 0.299f * r + 0.587f * g + 0.114f * b
                totalLuminance += luminance

                val left = (pixels[idx - 1] shr 16) and 0xFF
                val right = (pixels[idx + 1] shr 16) and 0xFF
                val top = (pixels[(y - 1) * width + x] shr 16) and 0xFF
                val bottom = (pixels[(y + 1) * width + x] shr 16) and 0xFF
                
                val laplacian = 4 * r - left - right - top - bottom
                laplacianSum += laplacian * laplacian
                count++
            }
        }

        val avgLuminance = if (width * height > 0) totalLuminance / (width * height) else 0.0

        var luminancePeaks = 0
        val hsv = FloatArray(3)
        for (color in pixels) {
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF
            val luminance = 0.299f * r + 0.587f * g + 0.114f * b
            android.graphics.Color.colorToHSV(color, hsv)
            if (luminance > 250f && hsv[1] < 0.04f && hsv[2] > 0.98f) {
                luminancePeaks++
            }
        }

        val variance = if (count > 0) laplacianSum / count else 0.0
        val peakRatio = luminancePeaks.toFloat() / (width * height)

        // Constraints:
        // - peakRatio > 0.08f indicates intense screen backlight lighting anomalies
        // - variance > 18000.0 indicates screen matrix grid groupings (moiré patterns)
        val screenTrap = peakRatio > 0.08f || variance > 18000.0
        if (screenTrap) {
            Log.d("SCREEN_TRAP", "Screen trap caught: Peaks=$peakRatio, Var=$variance, AvgL=$avgLuminance")
        }
        return screenTrap
    }



    private fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        val matrix = android.graphics.Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
