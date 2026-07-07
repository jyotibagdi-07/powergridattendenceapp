package com.example.powergridattendance

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.compose.runtime.mutableStateOf

data class MetricUpdateResult(
    val avgSpoof: Float,
    val avgBlur: Float,
    val avgNsfw: Float,
    val warningText: String?,
    val streak: Int,
    val verified: Boolean,
    val triggerSuccess: Boolean
)

object FaceState {

    val faceDetected =
        mutableStateOf(false)

    val faceRect =
        mutableStateOf<Rect?>(null)

    val liveSpoofScore =
        mutableStateOf<Float?>(0.0f)

    val liveBlurScore =
        mutableStateOf<Float?>(0.0f)

    val liveNsfwScore =
        mutableStateOf<Float?>(0.0f)

    val isLiveVerified =
        mutableStateOf(false)

    val isFullFaceVisible =
        mutableStateOf(false)

    val micromovementScore =
        mutableStateOf(0f)

    val consecutivePassesStreak =
        mutableStateOf(0)

    val attendanceVerified =
        mutableStateOf(false)

    val userWarning =
        mutableStateOf<String?>(null)

    var onVerificationSuccess: ((croppedFace: Bitmap, fullBitmap: Bitmap) -> Unit)? = null
    var onSoftAlert: ((String) -> Unit)? = null

    private val spoofHistory = MutableList(10) { 0.0f }
    private val blurHistory = MutableList(10) { 0.0f }
    private val nsfwSafetyHistory = MutableList(10) { 0.0f }

    private var consecutivePassesStreakInternal = 0
    private var attendanceVerifiedInternal = false

    fun updateMetrics(spoof: Float, blur: Float, nsfw: Float): MetricUpdateResult {
        synchronized(this) {
            // Evict the oldest historical score element (index 0 / FIFO approach)
            if (spoofHistory.isNotEmpty()) {
                spoofHistory.removeAt(0)
            }
            // Append the newly computed frame score to the tail of the array
            spoofHistory.add(spoof)

            if (blurHistory.isNotEmpty()) {
                blurHistory.removeAt(0)
            }
            blurHistory.add(blur)

            if (nsfwSafetyHistory.isNotEmpty()) {
                nsfwSafetyHistory.removeAt(0)
            }
            nsfwSafetyHistory.add(nsfw)

            // Calculate the true rolling average across the updated 10-frame buffer array
            val avgSpoof = spoofHistory.average().toFloat()
            val avgBlur = blurHistory.average().toFloat()
            val avgNsfw = nsfwSafetyHistory.average().toFloat()

            // Ultra-Relaxed Thresholds for Speed
            val blurPass = avgBlur < 0.80f 
            val nsfwPass = avgNsfw < 0.70f 
            val spoofPass = avgSpoof < 0.60f 

            var warningText: String? = null
            var triggerSuccess = false

            if (!blurPass || !nsfwPass) {
                warningText = if (!blurPass) "Image Blurry" else "Adjust Lighting"
            } else if (spoofPass) {
                if (!LivenessDetector.blinkDetected) {
                    warningText = "Blink to Verify Liveness"
                } else {
                    // Liveness verified but don't auto-trigger, let user click capture
                    attendanceVerifiedInternal = true
                    triggerSuccess = false
                    warningText = "Liveness Verified! Click Capture"
                }
            } else {
                consecutivePassesStreakInternal = 0
                warningText = "Liveness Failed"
            }
            return MetricUpdateResult(
                avgSpoof = avgSpoof,
                avgBlur = avgBlur,
                avgNsfw = avgNsfw,
                warningText = warningText,
                streak = consecutivePassesStreakInternal,
                verified = attendanceVerifiedInternal,
                triggerSuccess = triggerSuccess
            )
        }
    }

    fun addSpoofScore(score: Float) {
        synchronized(this) {
            if (spoofHistory.isNotEmpty()) {
                spoofHistory.removeAt(0)
            }
            spoofHistory.add(score)
            liveSpoofScore.value = spoofHistory.average().toFloat()
        }
    }

    fun addBlurScore(score: Float) {
        synchronized(this) {
            if (blurHistory.isNotEmpty()) {
                blurHistory.removeAt(0)
            }
            blurHistory.add(score)
            liveBlurScore.value = blurHistory.average().toFloat()
        }
    }

    fun addNsfwScore(score: Float) {
        synchronized(this) {
            if (nsfwSafetyHistory.isNotEmpty()) {
                nsfwSafetyHistory.removeAt(0)
            }
            nsfwSafetyHistory.add(score)
            liveNsfwScore.value = nsfwSafetyHistory.average().toFloat()
        }
    }

    fun getAverageSpoof(): Float {
        synchronized(this) {
            return spoofHistory.average().toFloat()
        }
    }

    fun getAverageBlur(): Float {
        synchronized(this) {
            return blurHistory.average().toFloat()
        }
    }

    fun getAverageNsfw(): Float {
        synchronized(this) {
            return nsfwSafetyHistory.average().toFloat()
        }
    }

    fun clearHistory() {
        synchronized(this) {
            spoofHistory.clear()
            repeat(10) { spoofHistory.add(0.0f) }
            blurHistory.clear()
            repeat(10) { blurHistory.add(0.0f) }
            nsfwSafetyHistory.clear()
            repeat(10) { nsfwSafetyHistory.add(0.0f) }

            consecutivePassesStreakInternal = 0
            attendanceVerifiedInternal = false
            LivenessDetector.reset()
        }
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            liveSpoofScore.value = 0.0f
            liveBlurScore.value = 0.0f
            liveNsfwScore.value = 0.0f
            isLiveVerified.value = false
            isFullFaceVisible.value = false
            consecutivePassesStreak.value = 0
            attendanceVerified.value = false
            userWarning.value = null
        }
    }
}