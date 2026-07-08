package com.example.powergridattendance

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.util.Log
import com.google.mlkit.vision.face.Face

object LivenessDetector {

    var blinkDetected = false
        private set

    private var eyesOpenBefore = false
    private var eyesClosed = false

    fun addFrame(face: Face) {
        val leftOpen = face.leftEyeOpenProbability ?: -1.0f
        val rightOpen = face.rightEyeOpenProbability ?: -1.0f
        
        Log.d("LIVENESS_DEBUG", "Eyes Open Prob -> Left: $leftOpen, Right: $rightOpen")

        // ML Kit returns -1 if classification is not initialized or failed
        if (leftOpen != -1.0f && rightOpen != -1.0f) {
            val avgOpen = (leftOpen + rightOpen) / 2.0f
            if (!eyesOpenBefore) {
                if (avgOpen > 0.60f) {
                    eyesOpenBefore = true
                    Log.d("LIVENESS_DEBUG", "BLINK SEQUENCE: Eyes Open Detected")
                }
            } else if (!eyesClosed) {
                // Register eyes closed if either eye drops below 0.35f
                if (leftOpen < 0.35f || rightOpen < 0.35f) {
                    eyesClosed = true
                    Log.d("LIVENESS_DEBUG", "BLINK SEQUENCE: Eyes Closed Detected")
                }
            } else {
                if (avgOpen > 0.60f) {
                    blinkDetected = true
                    Log.d("LIVENESS_DEBUG", "BLINK SEQUENCE COMPLETE: Eyes Opened Again!")
                }
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

            // Differentiate cool screen light (blue/white) from warm skin highlights (red/yellow)
            val isScreenColor = (r - b) < 15

            if (v_pixel > (0.92f * 255) && s_pixel < (0.05f * 255) && isScreenColor) {
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
        var totalValue = 0f
        for (color in pixels) {
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF

            val v_pixel = maxOf(r, g, b)
            val minColor = minOf(r, g, b)
            val s_pixel = if (v_pixel == 0) 0f else 255f * (v_pixel - minColor) / v_pixel

            totalSaturation += s_pixel
            totalValue += v_pixel
        }
        val avgSaturation = totalSaturation / (targetWidth * targetHeight)
        val avgValue = totalValue / (targetWidth * targetHeight)

        // Detect high-brightness glowing screens (abnormally bright & desaturated/washed out face crop)
        val isHighBrightnessScreen = (avgValue > 215f && avgSaturation < 70f)

        return if (avgSaturation > (0.82f * 255) || isHighBrightnessScreen) 0.95f else 0.1f
    }

    fun detectPhoneEdges(fullBitmap: Bitmap, faceRect: Rect): Boolean {
        val expandWidth = (faceRect.width() * 0.20f).toInt()
        val expandHeight = (faceRect.height() * 0.20f).toInt()

        val left = maxOf(0, faceRect.left - expandWidth)
        val top = maxOf(0, faceRect.top - expandHeight)
        val right = minOf(fullBitmap.width, faceRect.right + expandWidth)
        val bottom = minOf(fullBitmap.height, faceRect.bottom + expandHeight)

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

            var marginColsSum = 0f
            var marginColsCount = 0
            for (x in 1 until width - 1) {
                if (x >= expandWidth && x <= width - expandWidth) continue
                marginColsSum += colSumX[x]
                marginColsCount++
            }
            val colThreshold = if (marginColsCount > 0) (marginColsSum / marginColsCount) * 2.2f else 30f * height

            var verticalLinesCount = 0
            var lastVerticalLineX = -100
            for (x in 2 until width - 2) {
                // SKIP the face area (only check left and right margins)
                if (x >= expandWidth && x <= width - expandWidth) continue

                if (colSumX[x] > maxOf(colThreshold, 45f * height) &&
                    colSumX[x] > colSumX[x - 1] && colSumX[x] > colSumX[x + 1] &&
                    colSumX[x] > colSumX[x - 2] && colSumX[x] > colSumX[x + 2]) {
                    
                    if (x - lastVerticalLineX > 15) {
                        verticalLinesCount++
                        lastVerticalLineX = x
                    }
                }
            }

            var marginRowsSum = 0f
            var marginRowsCount = 0
            for (y in 1 until height - 1) {
                if (y >= expandHeight && y <= height - expandHeight) continue
                marginRowsSum += rowSumY[y]
                marginRowsCount++
            }
            val rowThreshold = if (marginRowsCount > 0) (marginRowsSum / marginRowsCount) * 2.2f else 30f * width

            var horizontalLinesCount = 0
            var lastHorizontalLineY = -100
            for (y in 2 until height - 2) {
                // SKIP the face area (only check top and bottom margins)
                if (y >= expandHeight && y <= height - expandHeight) continue

                if (rowSumY[y] > maxOf(rowThreshold, 45f * width) &&
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
        val expandWidth = (faceRect.width() * 0.20f).toInt()
        val expandHeight = (faceRect.height() * 0.20f).toInt()

        val left = maxOf(0, faceRect.left - expandWidth)
        val top = maxOf(0, faceRect.top - expandHeight)
        val right = minOf(fullBitmap.width, faceRect.right + expandWidth)
        val bottom = minOf(fullBitmap.height, faceRect.bottom + expandHeight)

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
            val threshold = 50f // Sobel edge threshold

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
                // SKIP the face area (only check left and right margins)
                if (x >= expandWidth && x <= width - expandWidth) continue

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
                // SKIP the face area (only check top and bottom margins)
                if (y >= expandHeight && y <= height - expandHeight) continue

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

    fun detectScreenTexture(bitmap: Bitmap): Boolean {
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
        // Abnormally high high-frequency variance indicates screen subpixels/moiré grid patterns
        val detected = variance > 1600.0
        if (detected) {
            Log.d("TEXTURE_DETECTION", "Screen texture / moire detected! Variance=$variance")
        }
        return detected
    }

    fun reset() {
        blinkDetected = false
        eyesOpenBefore = false
        eyesClosed = false
    }
}
