package com.example.powergridattendance

import android.content.Context
import android.graphics.Bitmap
import android.util.Log

object RecognitionHelper {
    private val employeeEmbeddings = mutableMapOf<String, FloatArray>()

    fun loadEmbeddings(context: Context, faceNetHelper: FaceNetHelper) {
        val employees = EmployeeRepository.getAllEmployees()
        for (employee in employees) {
            if (!employeeEmbeddings.containsKey(employee.employeeId)) {
                val bitmap = BitmapUtils.loadBitmap(context, employee.imagePath)
                if (bitmap != null) {
                    val embedding = faceNetHelper.getEmbedding(bitmap)
                    employeeEmbeddings[employee.employeeId] = embedding
                }
            }
        }
    }

    fun recognizeFace(faceBitmap: Bitmap, faceNetHelper: FaceNetHelper): Pair<String, Float> {
        val currentEmbedding = faceNetHelper.getEmbedding(faceBitmap)
        var bestMatchName = "Unknown"
        var highestScore = 0f

        val employees = EmployeeRepository.getAllEmployees()
        for (employee in employees) {
            val storedEmbedding = employeeEmbeddings[employee.employeeId]
            if (storedEmbedding != null) {
                val score = faceNetHelper.compareFaces(currentEmbedding, storedEmbedding)
                if (score > highestScore) {
                    highestScore = score
                    bestMatchName = employee.employeeName
                }
            }
        }
        return Pair(bestMatchName, highestScore)
    }
}
