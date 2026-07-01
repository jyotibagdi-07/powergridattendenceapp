package com.example.powergridattendance

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import com.google.mlkit.vision.face.Face
import kotlin.math.abs

object LivenessDetector {

    private var frameCount = 0
    private var blinkDetected = false
    private var eyesClosedAt = 0L
    private var lastEyeStatus = true // true for open

    fun addFrame(face: Face) {
        frameCount++
        
        val leftOpen = face.leftEyeOpenProbability ?: 1.0f
        val rightOpen = face.rightEyeOpenProbability ?: 1.0f
        val avgOpen = (leftOpen + rightOpen) / 2.0f
        
        // Log eye probabilities for debugging
        if (frameCount % 10 == 0) {
            android.util.Log.d("LIVENESS_DEBUG", "Avg Open Prob: $avgOpen (L: $leftOpen, R: $rightOpen)")
        }

        // Sensitive thresholds for blink
        val currentlyClosed = avgOpen < 0.4f
        val currentlyOpen = avgOpen > 0.6f
        
        if (lastEyeStatus && currentlyClosed) {
            // Eyes just closed
            eyesClosedAt = System.currentTimeMillis()
            lastEyeStatus = false
            android.util.Log.d("LIVENESS_DEBUG", "Eyes CLOSED detected")
        } else if (!lastEyeStatus && currentlyOpen) {
            // Eyes just opened after being closed
            val closedDuration = System.currentTimeMillis() - eyesClosedAt
            android.util.Log.d("LIVENESS_DEBUG", "Eyes OPENED detected. Duration: $closedDuration ms")
            
            // A realistic blink is usually between 50ms and 1000ms
            if (closedDuration in 50..1000) {
                blinkDetected = true
                android.util.Log.d("LIVENESS_DEBUG", "BLINK SUCCESSFUL")
            }
            lastEyeStatus = true
        }
    }

    fun isBlinkDetected(): Boolean = blinkDetected

    /**
     * Estimates blur using a simplified Laplacian variance (Edge intensity)
     * Higher value means sharper image.
     */
    fun calculateBlurScore(bitmap: Bitmap): Float {
        val width = bitmap.width
        val height = bitmap.height
        if (width < 3 || height < 3) return 0f

        var totalVariance = 0f
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // Laplacian kernel: [[0, 1, 0], [1, -4, 1], [0, 1, 0]]
        // We calculate the sum of absolute differences for speed
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val center = Color.red(pixels[y * width + x])
                val left = Color.red(pixels[y * width + (x - 1)])
                val right = Color.red(pixels[y * width + (x + 1)])
                val up = Color.red(pixels[(y - 1) * width + x])
                val down = Color.red(pixels[(y + 1) * width + x])
                
                val laplacian = abs(4 * center - left - right - up - down)
                totalVariance += laplacian
            }
        }

        // Normalize by area and scale for easier thresholding
        return (totalVariance / (width * height)) * 10f
    }

    /**
     * Checks for high intensity spots that might indicate screen glare.
     */
    fun detectScreenGlare(bitmap: Bitmap): Float {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        var glarePixels = 0
        for (pixel in pixels) {
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)
            // Screen glare often results in pure white (255, 255, 255)
            if (r > 240 && g > 240 && b > 240) {
                glarePixels++
            }
        }

        val glareRatio = glarePixels.toFloat() / (width * height)
        // If more than 5% of face is pure white glare, it's likely a screen
        return if (glareRatio > 0.05f) 0.8f else 0.0f
    }

    /**
     * Simplified HSV Spoof Detection.
     * Screens often have unnatural color saturation or limited gamut.
     */
    fun analyzeHSVSpoof(bitmap: Bitmap): Float {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val hsv = FloatArray(3)
        var highSaturationCount = 0
        
        for (pixel in pixels) {
            Color.colorToHSV(pixel, hsv)
            // Screens often show very high saturation or odd hue shifts for skin
            if (hsv[1] > 0.9f) { 
                highSaturationCount++
            }
        }

        val ratio = highSaturationCount.toFloat() / (width * height)
        return if (ratio > 0.1f) 0.5f else 0.1f
    }

    fun detectPhoneEdges(bitmap: Bitmap, faceRect: Rect): Boolean {
        // Advanced edge detection would go here. 
        // Returning false as default to avoid false positives.
        return false
    }

    fun detectPhoneBezelContours(bitmap: Bitmap, faceRect: Rect): Boolean {
        return false
    }

    fun reset() {
        frameCount = 0
        blinkDetected = false
        lastEyeStatus = true
        eyesClosedAt = 0L
        android.util.Log.d("LIVENESS_DEBUG", "Liveness Detector RESET")
    }
}
