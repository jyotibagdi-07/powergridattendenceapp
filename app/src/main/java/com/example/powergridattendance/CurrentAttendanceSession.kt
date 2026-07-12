package com.example.powergridattendance

object CurrentAttendanceSession {

    fun clearSession() {
        RecognitionState.recognizedName.value = ""
        RecognitionState.attendanceMarked.value = false
        RecognitionState.matchScore.value = 0f
        RecognitionState.faceMatched.value = false

        CaptureResultState.capturedImage.value = null
        CaptureResultState.spoofScore.value = 0f
        CaptureResultState.blurScore.value = 0f
        CaptureResultState.nsfwScore.value = 0f
        CaptureResultState.matchScore.value = 0f
        CaptureResultState.recognizedName.value = ""
        CaptureResultState.status.value = ""
        CaptureResultState.showResult.value = false

        AttendanceState.attendanceMode = false
    }
}
