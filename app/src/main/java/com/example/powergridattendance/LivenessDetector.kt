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
            if (leftOpen < 0.35f && rightOpen < 0.35f) { // Accurate blink threshold
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
        return (1.0f - (variance / 250.0).coerceIn(0.0, 1.0).toFloat())
    }

    fun detectScreenGlare(bitmap: Bitmap): Float {
        val width = bitmap.width
        val height = bitmap.height
        val startX = (width * 0.2).toInt()
        val startY = (height * 0.2).toInt()
        val endX = (width * 0.8).toInt()
        val endY = (height * 0.8).toInt()
        val targetWidth = endX - startX
        val targetHeight = endY - startY
        if (targetWidth <= 0 || targetHeight <= 0) return 0f

        val pixels = IntArray(targetWidth * targetHeight)
        bitmap.getPixels(pixels, 0, targetWidth, startX, startY, targetWidth, targetHeight)

        var glarePixels = 0
        for (color in pixels) {
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF
            
            val v_pixel = maxOf(r, g, b)
            val minColor = minOf(r, g, b)
            val s_pixel = if (v_pixel == 0) 0f else 255f * (v_pixel - minColor) / v_pixel

            if (v_pixel > (0.92f * 255) && s_pixel < (0.05f * 255)) {
                glarePixels++
            }
        }
        return glarePixels.toFloat() / (targetWidth * targetHeight)
    }

    fun analyzeHSVSpoof(bitmap: Bitmap): Float {
        val width = bitmap.width
        val height = bitmap.height
        val startX = (width * 0.2).toInt()
        val startY = (height * 0.2).toInt()
        val endX = (width * 0.8).toInt()
        val endY = (height * 0.8).toInt()
        val targetWidth = endX - startX
        val targetHeight = endY - startY
        if (targetWidth <= 0 || targetHeight <= 0) return 0.1f

        val pixels = IntArray(targetWidth * targetHeight)
        bitmap.getPixels(pixels, 0, targetWidth, startX, startY, targetWidth, targetHeight)

        var totalSaturation = 0f
        for (color in pixels) {
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF

            val v_pixel = maxOf(r, g, b)
            val minColor = minOf(r, g, b)
            val s_pixel = if (v_pixel == 0) 0f else 255f * (v_pixel - minColor) / v_pixel

            totalSaturation += s_pixel
        }
        val avgSaturation = totalSaturation / (targetWidth * targetHeight)
        return if (avgSaturation > (0.82f * 255)) 0.7f else 0.1f
    }

    fun detectPhoneEdges(fullBitmap: Bitmap, faceRect: Rect): Boolean {
        val shrinkWidth = (faceRect.width() * 0.15f).toInt()
        val shrinkHeight = (faceRect.height() * 0.15f).toInt()

        val left = maxOf(0, faceRect.left + shrinkWidth)
        val top = maxOf(0, faceRect.top + shrinkHeight)
        val right = minOf(fullBitmap.width, faceRect.right - shrinkWidth)
        val bottom = minOf(fullBitmap.height, faceRect.bottom - shrinkHeight)

        val cropW = right - left
        val cropH = bottom - top
        if (cropW <= 10 || cropH <= 10) return false

        try {
            val cropped = Bitmap.createBitmap(fullBitmap, left, top, cropW, cropH)
            val width = cropped.width
            val height = cropped.height

            val pixels = IntArray(width * height)
            cropped.getPixels(pixels, 0, width, 0, 0, width, height)

            val gray = FloatArray(width * height)
            for (i in pixels.indices) {
                val pix = pixels[i]
                val r = (pix shr 16) and 0xFF
                val g = (pix shr 8) and 0xFF
                val b = pix and 0xFF
                gray[i] = 0.299f * r + 0.587f * g + 0.114f * b
            }

            val gradX = FloatArray(width * height)
            val gradY = FloatArray(width * height)

            for (y in 1 until height - 1) {
                for (x in 1 until width - 1) {
                    val idx = y * width + x
                    val gx = (gray[idx + 1 - width] - gray[idx - 1 - width]) +
                             2f * (gray[idx + 1] - gray[idx - 1]) +
                             (gray[idx + 1 + width] - gray[idx - 1 + width])

                    val gy = (gray[idx - 1 + width] - gray[idx - 1 - width]) +
                             2f * (gray[idx + width] - gray[idx - width]) +
                             (gray[idx + 1 + width] - gray[idx + 1 - width])

                    gradX[idx] = Math.abs(gx)
                    gradY[idx] = Math.abs(gy)
                }
            }

            val colSumX = FloatArray(width)
            for (x in 1 until width - 1) {
                var sum = 0f
                for (y in 1 until height - 1) {
                    sum += gradX[y * width + x]
                }
                colSumX[x] = sum
            }

            val rowSumY = FloatArray(height)
            for (y in 1 until height - 1) {
                var sum = 0f
                for (x in 1 until width - 1) {
                    sum += gradY[y * width + x]
                }
                rowSumY[y] = sum
            }

            var verticalLinesCount = 0
            var lastVerticalLineX = -100
            val colThreshold = colSumX.average().toFloat() * 2.5f
            for (x in 2 until width - 2) {
                if (colSumX[x] > maxOf(colThreshold, 30f * height) &&
                    colSumX[x] > colSumX[x - 1] && colSumX[x] > colSumX[x + 1] &&
                    colSumX[x] > colSumX[x - 2] && colSumX[x] > colSumX[x + 2]) {
                    
                    if (x - lastVerticalLineX > 15) {
                        verticalLinesCount++
                        lastVerticalLineX = x
                    }
                }
            }

            var horizontalLinesCount = 0
            var lastHorizontalLineY = -100
            val rowThreshold = rowSumY.average().toFloat() * 2.5f
            for (y in 2 until height - 2) {
                if (rowSumY[y] > maxOf(rowThreshold, 30f * width) &&
                    rowSumY[y] > rowSumY[y - 1] && rowSumY[y] > rowSumY[y + 1] &&
                    rowSumY[y] > rowSumY[y - 2] && rowSumY[y] > rowSumY[y + 2]) {
                    
                    if (y - lastHorizontalLineY > 15) {
                        horizontalLinesCount++
                        lastHorizontalLineY = y
                    }
                }
            }

            // Require parallel lines (left & right phone borders OR top & bottom phone borders)
            val detected = verticalLinesCount >= 2 || horizontalLinesCount >= 2
            if (detected) {
                Log.d("EDGE_DETECTION", "Phone edge detected! VerticalCount=$verticalLinesCount, HorizontalCount=$horizontalLinesCount")
            }
            return detected
        } catch (e: Exception) {
            Log.e("LivenessDetector", "Error in phone edge detection", e)
            return false
        }
    }

    fun detectPhoneBezelContours(fullBitmap: Bitmap, faceRect: Rect): Boolean {
        val shrinkWidth = (faceRect.width() * 0.15f).toInt()
        val shrinkHeight = (faceRect.height() * 0.15f).toInt()

        val left = maxOf(0, faceRect.left + shrinkWidth)
        val top = maxOf(0, faceRect.top + shrinkHeight)
        val right = minOf(fullBitmap.width, faceRect.right - shrinkWidth)
        val bottom = minOf(fullBitmap.height, faceRect.bottom - shrinkHeight)

        val cropW = right - left
        val cropH = bottom - top
        if (cropW <= 10 || cropH <= 10) return false

        try {
            val cropped = Bitmap.createBitmap(fullBitmap, left, top, cropW, cropH)
            val width = cropped.width
            val height = cropped.height

            val pixels = IntArray(width * height)
            cropped.getPixels(pixels, 0, width, 0, 0, width, height)

            val gray = FloatArray(width * height)
            for (i in pixels.indices) {
                val pix = pixels[i]
                val r = (pix shr 16) and 0xFF
                val g = (pix shr 8) and 0xFF
                val b = pix and 0xFF
                gray[i] = 0.299f * r + 0.587f * g + 0.114f * b
            }

            val edgeMap = BooleanArray(width * height)
            val threshold = 80f // Sobel edge threshold

            for (y in 1 until height - 1) {
                for (x in 1 until width - 1) {
                    val idx = y * width + x
                    val gx = (gray[idx + 1 - width] - gray[idx - 1 - width]) +
                             2f * (gray[idx + 1] - gray[idx - 1]) +
                             (gray[idx + 1 + width] - gray[idx - 1 + width])

                    val gy = (gray[idx - 1 + width] - gray[idx - 1 - width]) +
                             2f * (gray[idx + width] - gray[idx - width]) +
                             (gray[idx + 1 + width] - gray[idx + 1 - width])

                    val mag = kotlin.math.sqrt(gx * gx + gy * gy)
                    edgeMap[idx] = mag > threshold
                }
            }

            val minLineLenV = (height * 0.35f).toInt() // 35% of ROI height
            val minLineLenH = (width * 0.35f).toInt()  // 35% of ROI width

            var verticalLines = 0
            var lastVerticalX = -100
            for (x in 2 until width - 2) {
                var maxLen = 0
                var currentLen = 0
                for (y in 2 until height - 2) {
                    val isEdge = edgeMap[y * width + x] || edgeMap[y * width + x - 1] || edgeMap[y * width + x + 1]
                    if (isEdge) {
                        currentLen++
                    } else {
                        maxLen = maxOf(maxLen, currentLen)
                        currentLen = 0
                    }
                }
                maxLen = maxOf(maxLen, currentLen)
                if (maxLen > minLineLenV) {
                    if (x - lastVerticalX > 15) {
                        verticalLines++
                        lastVerticalX = x
                    }
                }
            }

            var horizontalLines = 0
            var lastHorizontalY = -100
            for (y in 2 until height - 2) {
                var maxLen = 0
                var currentLen = 0
                for (x in 2 until width - 2) {
                    val isEdge = edgeMap[y * width + x] || edgeMap[y * width - width + x] || edgeMap[y * width + width + x]
                    if (isEdge) {
                        currentLen++
                    } else {
                        maxLen = maxOf(maxLen, currentLen)
                        currentLen = 0
                    }
                }
                maxLen = maxOf(maxLen, currentLen)
                if (maxLen > minLineLenH) {
                    if (y - lastHorizontalY > 15) {
                        horizontalLines++
                        lastHorizontalY = y
                    }
                }
            }

            // Require parallel bezel contours (at least two lines on opposite margins)
            val detected = verticalLines >= 2 || horizontalLines >= 2
            if (detected) {
                Log.d("EDGE_DETECTION", "Phone bezel contour detected! VerticalCount=$verticalLines, HorizontalCount=$horizontalLines")
            }
            return detected
        } catch (e: Exception) {
            Log.e("LivenessDetector", "Error in bezel contours detection", e)
            return false
        }
    }

    fun reset() {
        blinkDetected = false
    }
}
