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

            val blurPass = avgBlur < 0.15f
            val nsfwPass = avgNsfw < 0.7f
            val spoofPass = avgSpoof < 0.5f

            var warningText: String? = null
            var triggerSuccess = false

            if (blurPass && nsfwPass) {
                if (spoofPass) {
                    if (LivenessDetector.blinkDetected) {
                        consecutivePassesStreakInternal++
                        if (consecutivePassesStreakInternal >= 8) {
                            attendanceVerifiedInternal = true
                            warningText = "Liveness Verified! Click Capture"
                        } else {
                            warningText = "Verifying Liveness... Keep Still"
                        }
                    } else {
                        consecutivePassesStreakInternal = 0
                        warningText = "Blink to Verify Liveness"
                    }
                } else {
                    consecutivePassesStreakInternal = 0
                    warningText = if (avgSpoof >= 0.5f) "Avoid Screen Glare" else "Liveness Failed"
                }
            } else {
                consecutivePassesStreakInternal = 0
                warningText = if (!blurPass && avgSpoof > 0.7f) {
                    "Avoid Screen Glare"
                } else if (!blurPass) {
                    "Image Blurry"
                } else {
                    "Adjust Lighting"
                }
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