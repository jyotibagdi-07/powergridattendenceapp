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
    context: Context,
    private val modelName: String
) {

    private var interpreter: Interpreter? = null
    private var inputWidth = 224
    private var inputHeight = 224
    private var isNCHW = false // Auto-detected structural configuration flag

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

            val inputShape = interpreter!!.getInputTensor(0).shape()
            Log.d("MODEL_SHAPE", "$modelName -> ${inputShape.joinToString()}")

            // Evaluate if channel depth token (3) sits at index 1 (NCHW) or index 3 (NHWC)
            if (inputShape[1] == 3) {
                isNCHW = true
                inputHeight = inputShape[2]
                inputWidth = inputShape[3]
            } else {
                isNCHW = false
                inputHeight = inputShape[1]
                inputWidth = inputShape[2]
            }

            Log.d("MODEL_INIT_SUCCESS", "$modelName configuration: isNCHW=$isNCHW, Dimensions=[$inputHeight x $inputWidth]")

        } catch (e: Exception) {
            Log.e("MODEL_LOAD", "FAILED to provision interpreter for $modelName", e)
        }
    }

    fun predict(bitmap: Bitmap): Float {
        if (interpreter == null) {
            return -1f
        }

        // 1. Rescale bitmap layer cleanly to fit current model dimensions
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, inputWidth, inputHeight, true)
        val pixels = IntArray(inputWidth * inputHeight)
        resizedBitmap.getPixels(pixels, 0, inputWidth, 0, 0, inputWidth, inputHeight)

        // 2. Allocate native memory (1 batch * 3 channels * height * width * 4 bytes per Float32)
        val inputBuffer = ByteBuffer.allocateDirect(1 * 3 * inputWidth * inputHeight * 4)
        inputBuffer.order(ByteOrder.nativeOrder())
        inputBuffer.rewind()

        // 3. Transform and stream pixel metrics matching discovered memory map signature
        if (isNCHW) {
            // Channel-First Structure: Stream full Red plane, then full Green plane, then full Blue plane
            val totalPixels = pixels.size
            val rArr = FloatArray(totalPixels)
            val gArr = FloatArray(totalPixels)
            val bArr = FloatArray(totalPixels)

            for (i in pixels.indices) {
                val pixel = pixels[i]
                val r = ((pixel shr 16) and 0xFF) / 255f
                val g = ((pixel shr 8) and 0xFF) / 255f
                val b = (pixel and 0xFF) / 255f

                if (modelName == "spoof_model.tflite") {
                    // Apply PyTorch ImageNet normalization: mean=[0.485, 0.456, 0.406], std=[0.229, 0.224, 0.225]
                    rArr[i] = (r - 0.485f) / 0.229f
                    gArr[i] = (g - 0.456f) / 0.224f
                    bArr[i] = (b - 0.406f) / 0.225f
                } else {
                    rArr[i] = r
                    gArr[i] = g
                    bArr[i] = b
                }
            }

            for (v in rArr) inputBuffer.putFloat(v)
            for (v in gArr) inputBuffer.putFloat(v)
            for (v in bArr) inputBuffer.putFloat(v)

        } else {
            // Channel-Last Structure: Stream color tokens sequentially inline [R1, G1, B1, R2, G2, B2...]
            for (pixel in pixels) {
                val r = ((pixel shr 16) and 0xFF) / 255f
                val g = ((pixel shr 8) and 0xFF) / 255f
                val b = (pixel and 0xFF) / 255f

                if (modelName == "spoof_model.tflite") {
                    inputBuffer.putFloat((r - 0.485f) / 0.229f)
                    inputBuffer.putFloat((g - 0.456f) / 0.224f)
                    inputBuffer.putFloat((b - 0.406f) / 0.225f)
                } else {
                    inputBuffer.putFloat(r)
                    inputBuffer.putFloat(g)
                    inputBuffer.putFloat(b)
                }
            }
        }

        // 4. Resolve output properties
        val outputShape = interpreter!!.getOutputTensor(0).shape()
        val outputSize = outputShape.last()
        val output = Array(1) { FloatArray(outputSize) }

        inputBuffer.rewind()
        interpreter!!.run(inputBuffer, output)

        // 5. Evaluate and route logit tensors
        if (outputSize == 2) {
            val class0 = output[0][0]
            val class1 = output[0][1]

            Log.d("RAW_OUTPUT_LOGITS", "$modelName raw components: class0=$class0, class1=$class1")

            val exp0 = exp(class0.toDouble())
            val exp1 = exp(class1.toDouble())
            val sum = exp0 + exp1
            val prob1 = (exp1 / sum).toFloat() // Softmax confidence vector for class 1

            Log.d("MODEL_SCORE_EVAL", "$modelName computed score=$prob1")
            return prob1
        }

        val score = output[0][0]
        Log.d("MODEL_SCORE_EVAL", "$modelName computed score=$score")
        return score
    }
}