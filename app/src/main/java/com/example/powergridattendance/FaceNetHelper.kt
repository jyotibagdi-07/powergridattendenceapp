package com.example.powergridattendance

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.sqrt

class FaceNetHelper(
    context: Context
) {

    var interpreter: Interpreter? = null
        private set

    private val inputSize = 160
    private val embeddingSize = 512

    init {
        try {
            val assetFileDescriptor = context.assets.openFd("facenet.tflite")
            val inputStream = assetFileDescriptor.createInputStream()
            val fileChannel = inputStream.channel
            val startOffset = assetFileDescriptor.startOffset
            val declaredLength = assetFileDescriptor.declaredLength

            val modelBuffer = fileChannel.map(
                FileChannel.MapMode.READ_ONLY,
                startOffset,
                declaredLength
            )

            interpreter = Interpreter(modelBuffer)
            Log.d("FACENET_INIT", "FaceNet framework initialized successfully")

        } catch (e: Exception) {
            Log.e("FACENET_INIT", "CRITICAL error provisioning FaceNet interpreter architecture", e)
        }
    }

    fun isModelLoaded(): Boolean = interpreter != null

    fun getEmbedding(bitmap: Bitmap): FloatArray {
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        val byteBuffer = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * 4)
        byteBuffer.order(ByteOrder.nativeOrder())
        byteBuffer.rewind()

        val pixels = IntArray(inputSize * inputSize)
        resizedBitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

        // Fixed single-pass iteration stream to prevent layout degradation during tensor assignment
        for (pixelValue in pixels) {
            val r = ((pixelValue shr 16) and 0xFF) / 255f
            val g = ((pixelValue shr 8) and 0xFF) / 255f
            val b = (pixelValue and 0xFF) / 255f

            byteBuffer.putFloat(r)
            byteBuffer.putFloat(g)
            byteBuffer.putFloat(b)
        }

        val embedding = Array(1) { FloatArray(embeddingSize) }
        interpreter?.run(byteBuffer, embedding)

        return embedding[0]
    }

    fun compareFaces(embedding1: FloatArray, embedding2: FloatArray): Float {
        var dot = 0f
        var norm1 = 0f
        var norm2 = 0f

        for (i in embedding1.indices) {
            dot += embedding1[i] * embedding2[i]
            norm1 += embedding1[i] * embedding1[i]
            norm2 += embedding2[i] * embedding2[i]
        }

        return (dot / (sqrt(norm1.toDouble()) * sqrt(norm2.toDouble()))).toFloat()
    }
}