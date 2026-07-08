package com.example.powergridattendance

import android.content.Context
import android.graphics.Bitmap
import android.util.Log

object RecognitionHelper {
    private val employeeEmbeddings = mutableMapOf<String, FloatArray>()

    fun loadEmbeddings(context: Context, faceNetHelper: FaceNetHelper) {
        val employees = EmployeeRepository.getAllEmployees()
        Log.d("FACENET_RECOGNITION", "loadEmbeddings: employees count=${employees.size}")
        for (employee in employees) {
            Log.d("FACENET_RECOGNITION", "loadEmbeddings: checking employee=${employee.employeeName}, id=${employee.employeeId}")
            if (!employeeEmbeddings.containsKey(employee.employeeId)) {
                val bitmap = BitmapUtils.loadBitmap(context, employee.imagePath)
                if (bitmap != null) {
                    val embedding = faceNetHelper.getEmbedding(bitmap)
                    employeeEmbeddings[employee.employeeId] = embedding
                    Log.d("FACENET_RECOGNITION", "loadEmbeddings: successfully loaded embedding for ${employee.employeeName}")
                } else {
                    Log.e("FACENET_RECOGNITION", "loadEmbeddings: FAILED to load bitmap from ${employee.imagePath}")
                }
            } else {
                Log.d("FACENET_RECOGNITION", "loadEmbeddings: embedding already present in cache for ${employee.employeeName}")
            }
        }
    }

    fun recognizeFace(faceBitmap: Bitmap, faceNetHelper: FaceNetHelper): Pair<String, Float> {
        val currentEmbedding = faceNetHelper.getEmbedding(faceBitmap)
        var bestMatchName = "Unknown"
        var highestScore = 0f

        val employees = EmployeeRepository.getAllEmployees()
        Log.d("FACENET_RECOGNITION", "recognizeFace: evaluating against ${employees.size} employees, cache size=${employeeEmbeddings.size}")
        for (employee in employees) {
            val storedEmbedding = employeeEmbeddings[employee.employeeId]
            if (storedEmbedding != null) {
                val score = faceNetHelper.compareFaces(currentEmbedding, storedEmbedding)
                Log.d("FACENET_RECOGNITION", "recognizeFace: compared with ${employee.employeeName}, score=$score")
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
