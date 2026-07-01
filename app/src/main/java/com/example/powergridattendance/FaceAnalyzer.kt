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
    private var lastAnalysisTimestamp = 0L
    private var lastFaceSeenTimestamp = 0L

    // 1. ATOMIC DISPATCH THREADING LAYER
    private val analysisScope = CoroutineScope(Dispatchers.Default)
    private val isProcessing = AtomicBoolean(false)

    // 3. HARDWARE-BASED COMPUTER VISION COUNTERMEASURES: Bounding Box Centers Memory
    private val boxCenters = mutableListOf<PointF>()

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
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
            if (currentTimestamp - lastAnalysisTimestamp < 500) {
                isProcessing.set(false)
                return
            }

            val mediaImage = imageProxy.image
            if (mediaImage == null) {
                isProcessing.set(false)
                return
            }

            val rotation = imageProxy.imageInfo.rotationDegrees
            val width = imageProxy.width
            val height = imageProxy.height
            val rotatedBitmap = imageProxy.toBitmap()

            lastAnalysisTimestamp = currentTimestamp

            // Offload inference and processing completely off the main/Analyzer thread
            analysisScope.launch {
                try {
                    withContext(Dispatchers.Default) {
                        val image = InputImage.fromBitmap(rotatedBitmap, rotation)
                        
                        // Await face detection asynchronously inside coroutine scope without blocking execution
                        val faceDetected = suspendCancellableCoroutine<Boolean> { continuation ->
                            detector.detectFace(image) {
                                if (continuation.isActive) {
                                    continuation.resume(FaceState.faceDetected.value)
                                }
                            }
                        }

<<<<<<< HEAD
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
=======
                        if (faceDetected) {
                            lastFaceSeenTimestamp = System.currentTimeMillis()
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
                                            // Variance = 0.0f indicates frozen/printed photo attack
                                            if (varX == 0.0f && varY == 0.0f) {
                                                isStaticAttack = true
>>>>>>> 2e29c9052ceab75004fd741efded0bcd1eaae963
                                            }
                                        }
                                    }

                                    // 3. Algorithmic Anti-Glare Scanner (HSV color space thresholding)
                                    val glareRatio = calculateGlareRatio(croppedFace)
                                    val glareAttackDetected = glareRatio > 0.08f

                                    // Bezel & display border scanner
                                    val bezelEdgeDetected = LivenessDetector.detectPhoneEdges(rotatedBitmap, rotatedRect) ||
                                                             LivenessDetector.detectPhoneBezelContours(rotatedBitmap, rotatedRect)

                                    val tfliteSpoof = spoofHelper.predict(croppedFace)
                                    val hsvSpoof = LivenessDetector.analyzeHSVSpoof(croppedFace)
                                    val glareSpoof = LivenessDetector.detectScreenGlare(croppedFace)
                                    val edgeSpoof = if (bezelEdgeDetected) 0.95f else 0.0f

                                    val presentationAttackDetected = glareSpoof > 0f || edgeSpoof > 0f || glareAttackDetected || isStaticAttack
                                    if (presentationAttackDetected) {
                                        withContext(Dispatchers.Main) {
                                            LivenessDetector.reset()
                                        }
                                    }

                                    // Override combined spoof score to 0.99f if any presentation/glare/bezel/static attack is detected
                                    val combinedSpoof = if (presentationAttackDetected) 0.99f else maxOf(0.6f * tfliteSpoof + 0.4f * hsvSpoof, glareSpoof, edgeSpoof)
                                    val rawSpoofScore = combinedSpoof

                                    // Laplacian Variance Blur Calculation (Clarity metric where higher = sharper)
                                    val blurScore = LivenessDetector.calculateBlurScore(croppedFace)
                                    val clarityScore = 1.0f - blurScore

                                    // NSFW/safety metric where higher = safer (Logits class 0 Safe probability is maximized)
                                    val nsfwScore = nsfwHelper.predict(rotatedBitmap)
                                    val safetyScore = 1.0f - nsfwScore

                                    // Update metrics (evict index 0, append new, calculate average outside main thread)
                                    val result = FaceState.updateMetrics(rawSpoofScore, clarityScore, safetyScore)

                                    withContext(Dispatchers.Main) {
                                        FaceState.liveSpoofScore.value = result.avgSpoof
                                        FaceState.liveBlurScore.value = result.avgBlur
                                        FaceState.liveNsfwScore.value = result.avgNsfw
                                        
                                        // Set UI warnings appropriately
                                        if (glareAttackDetected || bezelEdgeDetected) {
                                            FaceState.userWarning.value = "Avoid Screen Glare"
                                        } else if (result.warningText != null) {
                                            FaceState.userWarning.value = result.warningText
                                        } else {
                                            FaceState.userWarning.value = null
                                        }

                                        FaceState.consecutivePassesStreak.value = result.streak
                                        FaceState.attendanceVerified.value = result.verified
                                        
                                        // Update UI color indicator immediately
                                        FaceState.isLiveVerified.value = (result.avgSpoof < 0.40f && result.avgBlur > 0.40f && result.avgNsfw > 0.50f)

                                        if (result.triggerSuccess) {
                                            FaceState.onVerificationSuccess?.invoke(croppedFace, rotatedBitmap)
                                        }
                                    }
                                } else {
                                    withContext(Dispatchers.Main) {
                                        FaceState.clearHistory()
                                    }
                                }
                            }
                        } else {
                            val now = System.currentTimeMillis()
                            if (now - lastFaceSeenTimestamp > 2000) {
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
                    isProcessing.set(false)
                }
            }
        } catch (e: Exception) {
            Log.e("ANALYZER", "Frame exception in analyzer main loop", e)
            isProcessing.set(false)
        } finally {
            imageProxy.close()
        }
    }

    // HSV glare ratio calculation: Value (V) exceeds 230 (on [0, 255] scale)
    private fun calculateGlareRatio(croppedFace: Bitmap): Float {
        val width = croppedFace.width
        val height = croppedFace.height
        val pixels = IntArray(width * height)
        croppedFace.getPixels(pixels, 0, width, 0, 0, width, height)

        val hsv = FloatArray(3)
        var glarePixels = 0

        for (color in pixels) {
            android.graphics.Color.colorToHSV(color, hsv)
            val v = hsv[2] * 255f
            if (v > 230f) {
                glarePixels++
            }
        }

        return glarePixels.toFloat() / pixels.size
    }
}