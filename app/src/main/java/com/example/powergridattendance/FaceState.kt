package com.example.powergridattendance

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

    private val spoofHistory = mutableListOf<Float>()
    private val blurHistory = mutableListOf<Float>()
    private val nsfwSafetyHistory = mutableListOf<Float>()

    private var consecutivePassesStreakInternal = 0
    private var attendanceVerifiedInternal = false

    suspend fun updateMetrics(spoof: Float, blur: Float, nsfw: Float): MetricUpdateResult = withContext(Dispatchers.IO) {
        synchronized(this@FaceState) {
            // Evict the oldest historical score element (FIFO approach) if size limit is reached
            if (spoofHistory.size >= 10) {
                spoofHistory.removeAt(0)
            }
            // Append the newly computed frame score to the tail of the list
            spoofHistory.add(spoof)

            if (blurHistory.size >= 10) {
                blurHistory.removeAt(0)
            }
            blurHistory.add(blur)

            if (nsfwSafetyHistory.size >= 10) {
                nsfwSafetyHistory.removeAt(0)
            }
            nsfwSafetyHistory.add(nsfw)

            // Calculate the true rolling average across the active historical buffer elements
            val avgSpoof = if (spoofHistory.isNotEmpty()) spoofHistory.average().toFloat() else 0.0f
            val avgBlur = if (blurHistory.isNotEmpty()) blurHistory.average().toFloat() else 0.0f
            val avgNsfw = if (nsfwSafetyHistory.isNotEmpty()) nsfwSafetyHistory.average().toFloat() else 0.0f

            // Corrected Thresholds:
            // - avgBlur must be strictly LESS than 0.92f (clarityPass)
            // - avgNsfw must be strictly LESS than 0.70f (attack/NSFW probability)
            // - avgSpoof must be strictly LESS than 0.60f (since spoofScore represents Spoof/Attack probability, relaxed to 0.60f for emulators/webcams)
            val blurPass = avgBlur < 0.92f 
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
            return@synchronized MetricUpdateResult(
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

    suspend fun addSpoofScore(score: Float) = withContext(Dispatchers.IO) {
        synchronized(this@FaceState) {
            if (spoofHistory.size >= 10) {
                spoofHistory.removeAt(0)
            }
            spoofHistory.add(score)
            val calculatedAvg = if (spoofHistory.isNotEmpty()) spoofHistory.average().toFloat() else 0.0f
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                liveSpoofScore.value = calculatedAvg
            }
        }
    }

    suspend fun addBlurScore(score: Float) = withContext(Dispatchers.IO) {
        synchronized(this@FaceState) {
            if (blurHistory.size >= 10) {
                blurHistory.removeAt(0)
            }
            blurHistory.add(score)
            val calculatedAvg = if (blurHistory.isNotEmpty()) blurHistory.average().toFloat() else 0.0f
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                liveBlurScore.value = calculatedAvg
            }
        }
    }

    suspend fun addNsfwScore(score: Float) = withContext(Dispatchers.IO) {
        synchronized(this@FaceState) {
            if (nsfwSafetyHistory.size >= 10) {
                nsfwSafetyHistory.removeAt(0)
            }
            nsfwSafetyHistory.add(score)
            val calculatedAvg = if (nsfwSafetyHistory.isNotEmpty()) nsfwSafetyHistory.average().toFloat() else 0.0f
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                liveNsfwScore.value = calculatedAvg
            }
        }
    }

    fun getAverageSpoof(): Float {
        synchronized(this) {
            return if (spoofHistory.isNotEmpty()) spoofHistory.average().toFloat() else 0.0f
        }
    }

    fun getAverageBlur(): Float {
        synchronized(this) {
            return if (blurHistory.isNotEmpty()) blurHistory.average().toFloat() else 0.0f
        }
    }

    fun getAverageNsfw(): Float {
        synchronized(this) {
            return if (nsfwSafetyHistory.isNotEmpty()) nsfwSafetyHistory.average().toFloat() else 0.0f
        }
    }

    fun clearHistory() {
        synchronized(this) {
            spoofHistory.clear()
            blurHistory.clear()
            nsfwSafetyHistory.clear()

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