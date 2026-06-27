package com.example.powergridattendance

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.exp

class TFLiteModelHelper(
    private val context: Context,
    private val modelName: String
) {

    private var interpreter: Interpreter? = null
    private var inputWidth = 224
    private var inputHeight = 224

    init {
        try {

            val afd = context.assets.openFd(modelName)
            val inputStream = afd.createInputStream()
            val fileChannel = inputStream.channel

            val modelBuffer = fileChannel.map(
                FileChannel.MapMode.READ_ONLY,
                afd.startOffset,
                afd.declaredLength
            )

            interpreter = Interpreter(modelBuffer)

            val inputShape =
                interpreter!!.getInputTensor(0).shape()

            Log.d(
                "MODEL_SHAPE",
                "$modelName -> ${inputShape.joinToString()}"
            )

            inputHeight = inputShape[2]
            inputWidth = inputShape[3]

        } catch (e: Exception) {
            Log.e(
                "MODEL_LOAD",
                "FAILED $modelName",
                e
            )
        }
    }

    fun predict(bitmap: Bitmap): Float {

        if (interpreter == null) {
            return -1f
        }

        val resizedBitmap =
            Bitmap.createScaledBitmap(
                bitmap,
                inputWidth,
                inputHeight,
                true
            )

        val pixels =
            IntArray(inputWidth * inputHeight)

        resizedBitmap.getPixels(
            pixels,
            0,
            inputWidth,
            0,
            0,
            inputWidth,
            inputHeight
        )

        val inputBuffer =
            ByteBuffer.allocateDirect(
                1 * 3 * inputWidth * inputHeight * 4
            )

        inputBuffer.order(ByteOrder.nativeOrder())

        val redChannel =
            FloatArray(inputWidth * inputHeight)

        val greenChannel =
            FloatArray(inputWidth * inputHeight)

        val blueChannel =
            FloatArray(inputWidth * inputHeight)

        for (i in pixels.indices) {

            val pixel = pixels[i]

            val r =
                ((pixel shr 16) and 0xFF) / 255f

            val g =
                ((pixel shr 8) and 0xFF) / 255f

            val b =
                (pixel and 0xFF) / 255f

            if (modelName == "spoof_model.tflite") {

                redChannel[i] =
                    (r - 0.485f) / 0.229f

                greenChannel[i] =
                    (g - 0.456f) / 0.224f

                blueChannel[i] =
                    (b - 0.406f) / 0.225f

            } else {

                redChannel[i] = r
                greenChannel[i] = g
                blueChannel[i] = b
            }
        }

        for (value in redChannel) {
            inputBuffer.putFloat(value)
        }

        for (value in greenChannel) {
            inputBuffer.putFloat(value)
        }

        for (value in blueChannel) {
            inputBuffer.putFloat(value)
        }

        val outputShape =
            interpreter!!.getOutputTensor(0).shape()

        Log.d(
            "OUTPUT_SHAPE",
            "$modelName -> ${outputShape.joinToString()}"
        )

        val outputSize = outputShape.last()

        val output =
            Array(1) { FloatArray(outputSize) }

        inputBuffer.rewind()

        interpreter!!.run(
            inputBuffer,
            output
        )

        if (outputSize == 2) {

            val class0 = output[0][0]
            val class1 = output[0][1]

            Log.d(
                "RAW_OUTPUT",
                "$modelName class0=$class0 class1=$class1"
            )

            val exp0 = exp(class0.toDouble())
            val exp1 = exp(class1.toDouble())

            val sum = exp0 + exp1

            val prob1 =
                (exp1 / sum).toFloat()

            Log.d(
                "MODEL_SCORE",
                "$modelName score=$prob1"
            )

            return prob1
        }

        val score = output[0][0]

        Log.d(
            "MODEL_SCORE",
            "$modelName score=$score"
        )

        return score
    }
}