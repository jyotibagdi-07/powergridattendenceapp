package com.example.powergridattendance

import android.content.Context
import android.graphics.Bitmap
import android.util.Log

object RecognitionHelper {
    private val employeeEmbeddings = mutableMapOf<String, FloatArray>()

    fun clearCache() {
        employeeEmbeddings.clear()
    }

    fun loadEmbeddings(context: Context, faceNetHelper: FaceNetHelper) {
        // Clear cache first to ensure stale embeddings (e.g. from corrupt registration) are re-read from disk
        employeeEmbeddings.clear()
        val employees = EmployeeRepository.getAllEmployees()
        Log.d("FACENET_RECOGNITION", "loadEmbeddings: employees count=${employees.size}")
        for (employee in employees) {
            Log.d("FACENET_RECOGNITION", "loadEmbeddings: checking employee=${employee.employeeName}, id=${employee.employeeId}")
            val bitmap = BitmapUtils.loadBitmap(context, employee.imagePath)
            if (bitmap != null) {
                val embedding = faceNetHelper.getEmbedding(bitmap)
                employeeEmbeddings[employee.employeeId] = embedding
                Log.d("FACENET_RECOGNITION", "loadEmbeddings: successfully loaded embedding for ${employee.employeeName}")
            } else {
                Log.e("FACENET_RECOGNITION", "loadEmbeddings: FAILED to load bitmap from ${employee.imagePath}")
            }
        }
    }

    fun recognizeFace(faceBitmap: Bitmap, faceNetHelper: FaceNetHelper): Pair<String, Float> {
        val currentEmbedding = faceNetHelper.getEmbedding(faceBitmap)

        // Generate embedding for horizontally mirrored face to handle device-specific mirroring mismatches
        val mirrorMatrix = android.graphics.Matrix().apply { postScale(-1f, 1f) }
        val mirroredBitmap = Bitmap.createBitmap(faceBitmap, 0, 0, faceBitmap.width, faceBitmap.height, mirrorMatrix, true)
        val mirroredEmbedding = faceNetHelper.getEmbedding(mirroredBitmap)
        mirroredBitmap.recycle()

        var bestMatchName = "Unknown"
        var highestScore = 0f

        val employees = EmployeeRepository.getAllEmployees()
        Log.d("FACENET_RECOGNITION", "recognizeFace: evaluating against ${employees.size} employees, cache size=${employeeEmbeddings.size}")
        for (employee in employees) {
            val storedEmbedding = employeeEmbeddings[employee.employeeId]
            if (storedEmbedding != null) {
                val scoreNormal = faceNetHelper.compareFaces(currentEmbedding, storedEmbedding)
                val scoreMirrored = faceNetHelper.compareFaces(mirroredEmbedding, storedEmbedding)
                val score = maxOf(scoreNormal, scoreMirrored)
                Log.d("FACENET_RECOGNITION", "recognizeFace: compared with ${employee.employeeName}, score=$score (normal=$scoreNormal, mirrored=$scoreMirrored)")
                if (score > highestScore) {
                    highestScore = score
                    bestMatchName = employee.employeeName
                }
            } else {
                Log.w("FACENET_RECOGNITION", "recognizeFace: no stored embedding for employee=${employee.employeeName}")
            }
        }
        Log.d("FACENET_RECOGNITION", "recognizeFace: bestMatchName=$bestMatchName, highestScore=$highestScore")
        return Pair(bestMatchName, highestScore)
    }
}
