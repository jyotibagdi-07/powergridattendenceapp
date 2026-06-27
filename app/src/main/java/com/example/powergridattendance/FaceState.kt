package com.example.powergridattendance

import android.graphics.Rect
import androidx.compose.runtime.mutableStateOf

object FaceState {

    val faceDetected =
        mutableStateOf(false)

    val faceRect =
        mutableStateOf<Rect?>(null)
}