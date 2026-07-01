package com.example.powergridattendance

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.util.Log
import com.google.mlkit.vision.face.Face

object LivenessDetector {

    var blinkDetected = false
        private set

    fun addFrame(face: Face) {
        val leftOpen = face.leftEyeOpenProbability ?: -1.0f
        val rightOpen = face.rightEyeOpenProbability ?: -1.0f
        
        Log.d("LIVENESS_DEBUG", "Eyes Open Prob -> Left: $leftOpen, Right: $rightOpen")

        // ML Kit returns -1 if classification is not initialized or failed
        if (leftOpen != -1.0f && rightOpen != -1.0f) {
            if (leftOpen < 0.6f && rightOpen < 0.6f) { // Very high sensitivity
                blinkDetected = true
                Log.d("LIVENESS_DEBUG", "BLINK DETECTED!")
            }
        }
    }

    fun calculateBlurScore(bitmap: Bitmap): Float {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        var laplacianSum = 0.0
        var count = 0

        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val center = Color.red(pixels[y * width + x])
                val left = Color.red(pixels[y * width + (x - 1)])
                val right = Color.red(pixels[y * width + (x + 1)])
                val top = Color.red(pixels[(y - 1) * width + x])
                val bottom = Color.red(pixels[(y + 1) * width + x])

                val laplacian = (4 * center - left - right - top - bottom).toDouble()
                laplacianSum += laplacian * laplacian
                count++
            }
        }

        val variance = if (count > 0) laplacianSum / count else 0.0
        // Variance is high for sharp images, low for blurry images.
        // FaceAnalyzer uses clarityScore = 1.0 - blurScore.
        // So blurScore should be high for blurry images.
        return (1.0f - (variance / 1000.0).coerceIn(0.0, 1.0).toFloat())
    }

    fun detectScreenGlare(bitmap: Bitmap): Float {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        var glarePixels = 0
        val hsv = FloatArray(3)
        for (color in pixels) {
            Color.colorToHSV(color, hsv)
            if (hsv[2] > 0.9f && hsv[1] < 0.1f) {
                glarePixels++
            }
        }
        return glarePixels.toFloat() / (width * height)
    }

    fun analyzeHSVSpoof(bitmap: Bitmap): Float {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        var totalSaturation = 0f
        val hsv = FloatArray(3)
        for (color in pixels) {
            Color.colorToHSV(color, hsv)
            totalSaturation += hsv[1]
        }
        val avgSaturation = totalSaturation / (width * height)
        return if (avgSaturation > 0.65f) 0.7f else 0.1f
    }

    fun detectPhoneEdges(bitmap: Bitmap, rect: Rect): Boolean {
        return false
    }

    fun detectPhoneBezelContours(bitmap: Bitmap, rect: Rect): Boolean {
        return false
    }

    fun reset() {
        blinkDetected = false
    }
}
