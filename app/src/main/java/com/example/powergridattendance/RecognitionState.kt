package com.example.powergridattendance

import androidx.compose.runtime.mutableStateOf

object RecognitionState {

    val recognizedName =
        mutableStateOf("")

    val attendanceMarked =
        mutableStateOf(false)

    val matchScore =
        mutableStateOf(0f)

    val faceMatched =
        mutableStateOf(false)
}