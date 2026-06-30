package com.example.powergridattendance

import android.graphics.Rect
import androidx.compose.runtime.mutableStateOf

object FaceState {

    val faceDetected =
        mutableStateOf(false)

    val faceRect =
        mutableStateOf<Rect?>(null)

    val liveSpoofScore =
        mutableStateOf<Float?>(null)

    val liveBlurScore =
        mutableStateOf<Float?>(null)

    val liveNsfwScore =
        mutableStateOf<Float?>(null)

    val isLiveVerified =
        mutableStateOf(false)

    val isFullFaceVisible =
        mutableStateOf(false)

    val micromovementScore =
        mutableStateOf(0f)

    private val spoofHistory = mutableListOf<Float>()
    private val blurHistory = mutableListOf<Float>()
    private val nsfwHistory = mutableListOf<Float>()

    fun addSpoofScore(score: Float) {
        synchronized(spoofHistory) {
            spoofHistory.add(score)
            if (spoofHistory.size > 10) {
                spoofHistory.removeAt(0)
            }
            liveSpoofScore.value = spoofHistory.average().toFloat()
        }
    }

    fun addBlurScore(score: Float) {
        synchronized(blurHistory) {
            blurHistory.add(score)
            if (blurHistory.size > 10) {
                blurHistory.removeAt(0)
            }
            liveBlurScore.value = blurHistory.average().toFloat()
        }
    }

    fun addNsfwScore(score: Float) {
        synchronized(nsfwHistory) {
            nsfwHistory.add(score)
            if (nsfwHistory.size > 10) {
                nsfwHistory.removeAt(0)
            }
            liveNsfwScore.value = nsfwHistory.average().toFloat()
        }
    }

    fun getAverageSpoof(): Float {
        synchronized(spoofHistory) {
            return if (spoofHistory.isEmpty()) 0.5f else spoofHistory.average().toFloat()
        }
    }

    fun getAverageBlur(): Float {
        synchronized(blurHistory) {
            return if (blurHistory.isEmpty()) 0.0f else blurHistory.average().toFloat()
        }
    }

    fun getAverageNsfw(): Float {
        synchronized(nsfwHistory) {
            return if (nsfwHistory.isEmpty()) 0.0f else nsfwHistory.average().toFloat()
        }
    }

    fun clearHistory() {
        synchronized(spoofHistory) {
            spoofHistory.clear()
            liveSpoofScore.value = null
        }
        synchronized(blurHistory) {
            blurHistory.clear()
            liveBlurScore.value = null
        }
        synchronized(nsfwHistory) {
            nsfwHistory.clear()
            liveNsfwScore.value = null
        }
        isFullFaceVisible.value = false
    }
}