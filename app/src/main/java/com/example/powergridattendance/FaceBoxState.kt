package com.example.powergridattendance

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf

object FaceBoxState {

    val x = mutableFloatStateOf(0f)
    val y = mutableFloatStateOf(0f)
    val width = mutableFloatStateOf(0f)
    val height = mutableFloatStateOf(0f)

    val visible = mutableStateOf(false)
}