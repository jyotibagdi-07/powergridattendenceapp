package com.example.powergridattendance

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import android.graphics.Rect
import android.util.Log
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceLandmark
import kotlin.math.max
import kotlin.math.sqrt

data class FaceFrameData(
    val timestamp: Long,
    val boundingBox: Rect,
    val leftEyeOpenProb: Float?,
    val rightEyeOpenProb: Float?,
    val smileProb: Float?,
    val eulerX: Float,
    val eulerY: Float,
    val eulerZ: Float,
    val leftEye: PointF?,
    val rightEye: PointF?,
    val noseBase: PointF?,
    val mouthLeft: PointF?,
    val mouthRight: PointF?
)

object LivenessDetector {
    private const val TAG = "LivenessDetector"
    private const val MAX_HISTORY_SIZE = 10
    private val history = mutableListOf<FaceFrameData>()

    fun reset() {
        synchronized(history) {
            history.clear()
            FaceState.isLiveVerified.value = false
            FaceState.micromovementScore.value = 0f
            Log.d(TAG, "History and liveness verification reset")
        }
    }

    fun addFrame(face: Face) {
        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position
        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position
        val noseBase = face.getLandmark(FaceLandmark.NOSE_BASE)?.position
        val mouthLeft = face.getLandmark(FaceLandmark.MOUTH_LEFT)?.position
        val mouthRight = face.getLandmark(FaceLandmark.MOUTH_RIGHT)?.position

        val frame = FaceFrameData(
            timestamp = System.currentTimeMillis(),
            boundingBox = face.boundingBox,
            leftEyeOpenProb = face.leftEyeOpenProbability,
            rightEyeOpenProb = face.rightEyeOpenProbability,
            smileProb = face.smilingProbability,
            eulerX = face.headEulerAngleX,
            eulerY = face.headEulerAngleY,
            eulerZ = face.headEulerAngleZ,
            leftEye = leftEye,
            rightEye = rightEye,
            noseBase = noseBase,
            mouthLeft = mouthLeft,
            mouthRight = mouthRight
        )

        synchronized(history) {
            history.add(frame)
            if (history.size > MAX_HISTORY_SIZE) {
                history.removeAt(0)
            }
            analyzeLiveness()
        }
    }

    private fun analyzeLiveness() {
        val size = history.size
        if (size < MAX_HISTORY_SIZE) {
            Log.d("LivenessDetector", "Buffering frames for temporal verification (current history size: $size/10)")
            return
        }

        // 1. Eye Blink Detection
        val leftEyes = history.mapNotNull { it.leftEyeOpenProb }
        val rightEyes = history.mapNotNull { it.rightEyeOpenProb }
        
        var blinkDetected = false
        if (leftEyes.size >= MAX_HISTORY_SIZE) {
            val maxLeft = leftEyes.maxOrNull() ?: 0f
            val minLeft = leftEyes.minOrNull() ?: 1f
            if (maxLeft - minLeft > 0.35f && minLeft < 0.25f) {
                blinkDetected = true
            }
        }
        if (rightEyes.size >= MAX_HISTORY_SIZE) {
            val maxRight = rightEyes.maxOrNull() ?: 0f
            val minRight = rightEyes.minOrNull() ?: 1f
            if (maxRight - minRight > 0.35f && minRight < 0.25f) {
                blinkDetected = true
            }
        }

        // 2. Compute micromovement score for diagnostic reference
        val relDist1List = mutableListOf<Float>()
        val relDist2List = mutableListOf<Float>()
        val relDist3List = mutableListOf<Float>()
        val relDist4List = mutableListOf<Float>()
        val relDist5List = mutableListOf<Float>()
        val relDist6List = mutableListOf<Float>()

        for (frame in history) {
            val le = frame.leftEye
            val re = frame.rightEye
            val nose = frame.noseBase
            val ml = frame.mouthLeft
            val mr = frame.mouthRight

            val eyeDist = dist(le, re)
            if (eyeDist != null && eyeDist > 1.0f) {
                val eyeMid = PointF(((le?.x ?: 0f) + (re?.x ?: 0f)) / 2f, ((le?.y ?: 0f) + (re?.y ?: 0f)) / 2f)

                dist(nose, eyeMid)?.div(eyeDist)?.let { relDist1List.add(it) }
                dist(ml, eyeMid)?.div(eyeDist)?.let { relDist2List.add(it) }
                dist(mr, eyeMid)?.div(eyeDist)?.let { relDist3List.add(it) }
                dist(ml, nose)?.div(eyeDist)?.let { relDist4List.add(it) }
                dist(mr, nose)?.div(eyeDist)?.let { relDist5List.add(it) }
                dist(ml, mr)?.div(eyeDist)?.let { relDist6List.add(it) }
            }
        }

        val dev1 = stdDev(relDist1List)
        val dev2 = stdDev(relDist2List)
        val dev3 = stdDev(relDist3List)
        val dev4 = stdDev(relDist4List)
        val dev5 = stdDev(relDist5List)
        val dev6 = stdDev(relDist6List)

        val micromovementScore = dev1 + dev2 + dev3 + dev4 + dev5 + dev6
        FaceState.micromovementScore.value = micromovementScore

        // Only blinking determines isLive!
        val isLive = blinkDetected

        Log.d(TAG, "Liveness Analysis: size=$size, blink=$blinkDetected, microScore=$micromovementScore -> isLive=$isLive")

        if (isLive) {
            FaceState.isLiveVerified.value = true
        }
    }

    private fun dist(p1: PointF?, p2: PointF?): Float? {
        if (p1 == null || p2 == null) return null
        val dx = p1.x - p2.x
        val dy = p1.y - p2.y
        return sqrt(dx * dx + dy * dy)
    }

    private fun stdDev(values: List<Float>): Float {
        if (values.size < 2) return 0f
        val mean = values.average().toFloat()
        val variance = values.map { (it - mean) * (it - mean) }.average().toFloat()
        return sqrt(variance)
    }

    // Mathematical Laplacian Variance Blur calculation
    fun calculateBlurScore(bitmap: Bitmap): Float {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val gray = FloatArray(width * height)
        for (i in pixels.indices) {
            val pix = pixels[i]
            val r = (pix shr 16) and 0xFF
            val g = (pix shr 8) and 0xFF
            val b = pix and 0xFF
            gray[i] = 0.299f * r + 0.587f * g + 0.114f * b
        }

        val laplacian = FloatArray(width * height)
        var sum = 0f
        var count = 0

        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val idx = y * width + x
                val valCenter = gray[idx]
                val valLeft = gray[idx - 1]
                val valRight = gray[idx + 1]
                val valUp = gray[idx - width]
                val valDown = gray[idx + width]

                val lap = valLeft + valRight + valUp + valDown - 4f * valCenter
                laplacian[idx] = lap
                sum += lap
                count++
            }
        }

        if (count == 0) return 1f

        val mean = sum / count
        var varianceSum = 0f
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val idx = y * width + x
                val diff = laplacian[idx] - mean
                varianceSum += diff * diff
            }
        }

        val variance = varianceSum / count
        // Higher variance = sharper, clear. Lower variance = blurry.
        // We normalize to a score between 0.0 (clear) and 1.0 (very blurry)
        // A threshold of 300 is common for clear images.
        return max(0f, 1.0f - (variance / 300f))
    }

    // HSV Specular Reflection / Specular Highlight Analysis for Screen Spoofing
    fun analyzeHSVSpoof(bitmap: Bitmap): Float {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val hsv = FloatArray(3)
        var sumS = 0f
        var sumV = 0f
        val sValues = FloatArray(pixels.size)
        val vValues = FloatArray(pixels.size)

        for (i in pixels.indices) {
            val color = pixels[i]
            Color.colorToHSV(color, hsv)
            sValues[i] = hsv[1] // Saturation
            vValues[i] = hsv[2] // Value (Brightness)
            sumS += hsv[1]
            sumV += hsv[2]
        }

        val meanS = sumS / pixels.size
        val meanV = sumV / pixels.size

        var varS = 0f
        var varV = 0f
        for (i in pixels.indices) {
            val diffS = sValues[i] - meanS
            val diffV = vValues[i] - meanV
            varS += diffS * diffS
            varV += diffV * diffV
        }
        val stdDevS = sqrt(varS / pixels.size)
        val stdDevV = sqrt(varV / pixels.size)

        var glareCount = 0
        for (v in vValues) {
            if (v > 0.95f) {
                glareCount++
            }
        }
        val glareRatio = glareCount.toFloat() / pixels.size

        var hsvSpoofScore = 0f
        if (stdDevV > 0.25f || glareRatio > 0.15f) {
            // Specular reflections/glares indicating display screen
            hsvSpoofScore = 0.7f
        } else if (stdDevS < 0.08f) {
            // Flat, washed out colors typical of phone displays
            hsvSpoofScore = 0.5f
        }

        return hsvSpoofScore
    }

    // Specular highlight screen glare detector
    fun detectScreenGlare(croppedFace: Bitmap): Float {
        val width = croppedFace.width
        val height = croppedFace.height
        val pixels = IntArray(width * height)
        croppedFace.getPixels(pixels, 0, width, 0, 0, width, height)

        val hsv = FloatArray(3)
        var glarePixels = 0

        for (color in pixels) {
            Color.colorToHSV(color, hsv)
            val s = hsv[1]
            val v = hsv[2]
            // Specular glare: V > 0.99f (virtually pure white) and S < 0.03f
            if (v > 0.99f && s < 0.03f) {
                glarePixels++
            }
        }

        val glareRatio = glarePixels.toFloat() / pixels.size
        return if (glareRatio > 0.03f) 0.85f else 0.0f
    }

    // Straight phone bezel / screen display boundary detector
    fun detectPhoneEdges(fullBitmap: Bitmap, faceRect: Rect): Boolean {
        // Expand the face bounding box to include the surrounding area
        val expandWidth = (faceRect.width() * 0.4f).toInt()
        val expandHeight = (faceRect.height() * 0.4f).toInt()

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

            var verticalLineDetected = false
            val colThreshold = colSumX.average().toFloat() * 2.5f
            for (x in 2 until width - 2) {
                if (colSumX[x] > colThreshold &&
                    colSumX[x] > colSumX[x - 1] && colSumX[x] > colSumX[x + 1] &&
                    colSumX[x] > colSumX[x - 2] && colSumX[x] > colSumX[x + 2]) {
                    verticalLineDetected = true
                    break
                }
            }

            var horizontalLineDetected = false
            val rowThreshold = rowSumY.average().toFloat() * 2.5f
            for (y in 2 until height - 2) {
                if (rowSumY[y] > rowThreshold &&
                    rowSumY[y] > rowSumY[y - 1] && rowSumY[y] > rowSumY[y + 1] &&
                    rowSumY[y] > rowSumY[y - 2] && rowSumY[y] > rowSumY[y + 2]) {
                    horizontalLineDetected = true
                    break
                }
            }

            return verticalLineDetected && horizontalLineDetected
        } catch (e: Exception) {
            Log.e("LivenessDetector", "Error in phone edge detection", e)
            return false
        }
    }

    // Straight line phone bezel contour tracking (classic edge alignment)
    fun detectPhoneBezelContours(fullBitmap: Bitmap, faceRect: Rect): Boolean {
        val expandWidth = (faceRect.width() * 0.4f).toInt()
        val expandHeight = (faceRect.height() * 0.4f).toInt()

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

                    val mag = sqrt(gx * gx + gy * gy)
                    edgeMap[idx] = mag > threshold
                }
            }

            val minLineLenV = (height * 0.35f).toInt() // 35% of ROI height
            val minLineLenH = (width * 0.35f).toInt()  // 35% of ROI width

            var verticalLines = 0
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
                    verticalLines++
                }
            }

            var horizontalLines = 0
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
                    horizontalLines++
                }
            }

            return verticalLines >= 2 && horizontalLines >= 2
        } catch (e: Exception) {
            Log.e("LivenessDetector", "Error in bezel contours detection", e)
            return false
        }
    }
}
