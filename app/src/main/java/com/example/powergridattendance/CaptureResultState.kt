package com.example.powergridattendance

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf

object CaptureResultState {

    val capturedImage =
        mutableStateOf<Bitmap?>(null)

    val spoofScore =
        mutableStateOf(0f)

    val blurScore =
        mutableStateOf(0f)

    val nsfwScore =
        mutableStateOf(0f)

    val matchScore =
        mutableStateOf(0f)

    val recognizedName =
        mutableStateOf("")

    val status =
        mutableStateOf("")

    val showResult =
        mutableStateOf(false)
}