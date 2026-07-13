package com.example.powergridattendance

object CurrentAttendanceSession {
    var employeeId: String? = null
    var verificationTimestamp: Long = 0L
    var isSessionVerified: Boolean = false

    fun clearSession() {
        employeeId = null
        verificationTimestamp = 0L
        isSessionVerified = false
    }
}
