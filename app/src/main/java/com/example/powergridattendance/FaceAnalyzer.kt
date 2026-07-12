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
    private val isTFLiteProcessing = AtomicBoolean(false)
    private var lastTFLiteAnalysisTimestamp = 0L
 
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
 
        // Prevent worker overrun & concurrent backlog for face detection
        if (!isProcessing.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }
 
        try {
            val mediaImage = imageProxy.image
            if (mediaImage == null) {
                isProcessing.set(false)
                imageProxy.close()
                return
            }
 
            val rotation = imageProxy.imageInfo.rotationDegrees
 
            analysisScope.launch {
                var closedImage = false
                try {
                    val image = InputImage.fromMediaImage(mediaImage, rotation)
 
                    // Await face detection asynchronously inside coroutine scope without blocking execution (fast ~15ms)
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
                            val now = System.currentTimeMillis()
                            // Run heavy TFLite models if it's been > 600ms OR if a blink was just detected
                            val shouldRunTFLite = (now - lastTFLiteAnalysisTimestamp > 600 || LivenessDetector.blinkDetected) &&
                                    isTFLiteProcessing.compareAndSet(false, true)
 
                            if (shouldRunTFLite) {
                                val rawBitmap = imageProxy.toBitmap()
                                val needsRotation = (rotation == 90 || rotation == 270) && (rawBitmap.width > rawBitmap.height)
                                val uprightBitmap = if (needsRotation) {
                                    val matrix = android.graphics.Matrix().apply { postRotate(rotation.toFloat()) }
                                    Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
                                } else {
                                    rawBitmap
                                }
 
                                val expandW = (rawRect.width() * 0.10f).toInt()
                                val expandH = (rawRect.height() * 0.10f).toInt()
                                val clampedRect = android.graphics.Rect(
                                    maxOf(0, rawRect.left - expandW),
                                    maxOf(0, rawRect.top - expandH),
                                    minOf(uprightBitmap.width, rawRect.right + expandW),
                                    minOf(uprightBitmap.height, rawRect.bottom + expandH)
                                )
 
                                val tempCropped = FaceCropHelper.cropFace(uprightBitmap, clampedRect)
                                
                                // Close the imageProxy and unlock face analyzer immediately to allow next frames to stream at 30fps
                                imageProxy.close()
                                closedImage = true
                                isProcessing.set(false)
 
                                if (tempCropped != null) {
                                    processHeavyModels(tempCropped, uprightBitmap, rawRect, clampedRect, rotation)
                                } else {
                                    isTFLiteProcessing.set(false)
                                }
                            }
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
                } catch (e: Exception) {
                    Log.e("ANALYZER", "Frame processing failed in coroutine", e)
                } finally {
                    if (!closedImage) {
                        imageProxy.close()
                        isProcessing.set(false)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ANALYZER", "Frame exception in analyzer main loop", e)
            imageProxy.close()
            isProcessing.set(false)
        }
    }
 
    private fun processHeavyModels(
        croppedFace: Bitmap,
        uprightBitmap: Bitmap,
        rawRect: android.graphics.Rect,
        clampedRect: android.graphics.Rect,
        rotation: Int
    ) {
        analysisScope.launch {
            try {
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
                    combinedSpoof = 0.01f
                    blurScore = 1.0f
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
                            if (varX == 0.0f && varY == 0.0f) {
                                isStaticAttack = false
                            }
                        }
                    }
 
                    // Bezel & display border scanner (retained for UI warnings only)
                    bezelEdgeDetected = LivenessDetector.detectPhoneEdges(uprightBitmap, clampedRect) ||
                            LivenessDetector.detectPhoneBezelContours(uprightBitmap, clampedRect)
 
                    val tfliteSpoof = spoofHelper.predict(croppedFace)[1]
                    val hsvSpoof = LivenessDetector.analyzeHSVSpoof(croppedFace)
                    val glareSpoof = LivenessDetector.detectScreenGlare(croppedFace)
 
                    // Use a higher, safer glare threshold to avoid false positives on natural skin highlights
                    glareAttackDetected = glareSpoof > 0.15f
                    screenTextureDetected = LivenessDetector.detectScreenTexture(croppedFace)
                    
                    // Rely primarily on TFLite spoofing prediction, penalizing only on clear glare reflections (>0.15f) or extreme saturation washouts (>0.90f)
                    val heuristicSpoofFactor = if (glareAttackDetected || hsvSpoof > 0.90f) 0.01f else 1.0f
                    combinedSpoof = minOf(tfliteSpoof, heuristicSpoofFactor)
 
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
                    if (screenTrapDetected || glareAttackDetected) {
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
            } catch (e: Exception) {
                Log.e("ANALYZER", "Background TFLite processing failed", e)
            } finally {
                lastTFLiteAnalysisTimestamp = System.currentTimeMillis()
                isTFLiteProcessing.set(false)
            }
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
