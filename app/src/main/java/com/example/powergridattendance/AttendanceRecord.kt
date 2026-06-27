package com.example.powergridattendance

data class AttendanceRecord(
    val employeeName: String,
    val imagePath: String,
    val spoofScore: Float?,
    val blurScore: Float?,
    val nsfwScore: Float?,
    val matchScore: Float?,
    val status: String,
    val reason: String,
    val timestamp: String
)