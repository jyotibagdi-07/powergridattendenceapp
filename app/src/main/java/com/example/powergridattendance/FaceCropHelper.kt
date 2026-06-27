package com.example.powergridattendance

import android.graphics.Bitmap
import android.graphics.Rect

object FaceCropHelper {

    fun cropFace(
        bitmap: Bitmap,
        rect: Rect?
    ): Bitmap? {

        if (rect == null) {
            return null
        }

        val left =
            rect.left.coerceAtLeast(0)

        val top =
            rect.top.coerceAtLeast(0)

        val right =
            rect.right.coerceAtMost(
                bitmap.width
            )

        val bottom =
            rect.bottom.coerceAtMost(
                bitmap.height
            )

        val width = right - left
        val height = bottom - top

        if (width <= 0 || height <= 0) {
            return null
        }

        return Bitmap.createBitmap(
            bitmap,
            left,
            top,
            width,
            height
        )
    }
}