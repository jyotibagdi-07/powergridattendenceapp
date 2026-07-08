package com.example.powergridattendance

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object AttendanceRepository {

    private val records = mutableListOf<AttendanceRecord>()
    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        try {
            val file = File(context.filesDir, "attendance_records.json")
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            Log.e("AttendanceRepository", "Error clearing attendance records on init", e)
        }
        records.clear()
        isInitialized = true
    }

    private fun loadRecords(context: Context) {
        try {
            val file = File(context.filesDir, "attendance_records.json")
            if (file.exists()) {
                val jsonString = file.readText()
                val jsonArray = JSONArray(jsonString)
                records.clear()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    records.add(
                        AttendanceRecord(
                            employeeName = obj.getString("employeeName"),
                            imagePath = obj.getString("imagePath"),
                            spoofScore = if (obj.isNull("spoofScore")) null else obj.getDouble("spoofScore").toFloat(),
                            blurScore = if (obj.isNull("blurScore")) null else obj.getDouble("blurScore").toFloat(),
                            nsfwScore = if (obj.isNull("nsfwScore")) null else obj.getDouble("nsfwScore").toFloat(),
                            matchScore = if (obj.isNull("matchScore")) null else obj.getDouble("matchScore").toFloat(),
                            status = obj.getString("status"),
                            reason = obj.getString("reason"),
                            timestamp = obj.getString("timestamp")
                        )
                    )
                }
                Log.d("AttendanceRepository", "Loaded ${records.size} records from disk")
            }
        } catch (e: Exception) {
            Log.e("AttendanceRepository", "Error loading records", e)
        }
    }

    private fun saveRecords(context: Context) {
        try {
            val jsonArray = JSONArray()
            for (record in records) {
                val obj = JSONObject().apply {
                    put("employeeName", record.employeeName)
                    put("imagePath", record.imagePath)
                    put("spoofScore", record.spoofScore?.toDouble() ?: JSONObject.NULL)
                    put("blurScore", record.blurScore?.toDouble() ?: JSONObject.NULL)
                    put("nsfwScore", record.nsfwScore?.toDouble() ?: JSONObject.NULL)
                    put("matchScore", record.matchScore?.toDouble() ?: JSONObject.NULL)
                    put("status", record.status)
                    put("reason", record.reason)
                    put("timestamp", record.timestamp)
                }
                jsonArray.put(obj)
            }
            val file = File(context.filesDir, "attendance_records.json")
            file.writeText(jsonArray.toString())
            Log.d("AttendanceRepository", "Saved ${records.size} records to disk")
        } catch (e: Exception) {
            Log.e("AttendanceRepository", "Error saving records", e)
        }
    }

    fun addRecord(context: Context, record: AttendanceRecord) {
        records.add(record)
        saveRecords(context)
    }

    fun getAllRecords(): List<AttendanceRecord> {
        return records
    }

    fun clearAllRecords(context: Context) {
        try {
            val file = File(context.filesDir, "attendance_records.json")
            if (file.exists()) {
                file.delete()
            }
            context.filesDir.listFiles()?.forEach { f ->
                if (f.name.startsWith("attendance_") || f.name == "attendance.jpg") {
                    f.delete()
                }
            }
        } catch (e: Exception) {
            Log.e("AttendanceRepository", "Error clearing attendance records", e)
        }
        records.clear()
    }
}