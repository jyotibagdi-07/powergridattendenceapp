package com.example.powergridattendance

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream

object BitmapUtils {

    fun loadBitmap(
        context: Context,
        fileName: String
    ): Bitmap? {

        val file = File(
            context.filesDir,
            fileName
        )

        if (!file.exists()) {
            return null
        }

        return BitmapFactory.decodeFile(
            file.absolutePath
        )
    }

    fun saveBitmap(
        context: Context,
        bitmap: Bitmap,
        fileName: String
    ) {

        val file = File(
            context.filesDir,
            fileName
        )

        val outputStream =
            FileOutputStream(file)

        bitmap.compress(
            Bitmap.CompressFormat.JPEG,
            100,
            outputStream
        )

        outputStream.flush()
        outputStream.close()
    }
}