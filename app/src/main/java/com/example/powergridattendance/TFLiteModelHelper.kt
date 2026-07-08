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

    // Pre-allocated buffers for zero-allocation prediction loop
    private lateinit var pixelsBuffer: IntArray
    private lateinit var inputBuffer: ByteBuffer
    private var outputSize = 2
    private lateinit var outputArray: Array<FloatArray>

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

            // Pre-allocate pixel and ByteBuffers
            val totalPixels = inputWidth * inputHeight
            pixelsBuffer = IntArray(totalPixels)
            inputBuffer = ByteBuffer.allocateDirect(1 * 3 * totalPixels * 4).apply {
                order(ByteOrder.nativeOrder())
            }

            val outputShape = interpreter!!.getOutputTensor(0).shape()
            outputSize = outputShape.last()
            outputArray = Array(1) { FloatArray(outputSize) }

        } catch (e: Exception) {
            Log.e("MODEL_LOAD", "FAILED to provision interpreter for $modelName", e)
        }
    }

    fun predict(bitmap: Bitmap): Float {
        if (interpreter == null) {
            return -1f
        }

        // 1. Rescale bitmap layer cleanly to fit current model dimensions (unavoidable allocation)
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, inputWidth, inputHeight, true)
        
        // Use pre-allocated pixelsBuffer array instead of fresh allocations
        resizedBitmap.getPixels(pixelsBuffer, 0, inputWidth, 0, 0, inputWidth, inputHeight)

        // 2. Rewind pre-allocated native input buffer
        inputBuffer.rewind()

        // 3. Transform and stream pixel metrics directly into the native input buffer (Zero allocation loop)
        val totalPixels = inputWidth * inputHeight
        
        var totalLuminance = 0f
        for (i in 0 until totalPixels) {
            val pixel = pixelsBuffer[i]
            val r = ((pixel shr 16) and 0xFF) / 255f
            val g = ((pixel shr 8) and 0xFF) / 255f
            val b = (pixel and 0xFF) / 255f
            totalLuminance += 0.299f * r + 0.587f * g + 0.114f * b
        }
        val avgLuminance = totalLuminance / totalPixels
        val scale = 1.0f

        if (isNCHW) {
            // Channel-First Structure: Stream Red plane, then Green plane, then Blue plane sequentially using relative writes
            for (i in 0 until totalPixels) {
                val pixel = pixelsBuffer[i]
                val r = (((pixel shr 16) and 0xFF) / 255f) * scale
                val nr = if (modelName == "spoof_model.tflite") {
                    (r - 0.485f) / 0.229f
                } else {
                    r
                }
                inputBuffer.putFloat(nr)
            }
            for (i in 0 until totalPixels) {
                val pixel = pixelsBuffer[i]
                val g = (((pixel shr 8) and 0xFF) / 255f) * scale
                val ng = if (modelName == "spoof_model.tflite") {
                    (g - 0.456f) / 0.224f
                } else {
                    g
                }
                inputBuffer.putFloat(ng)
            }
            for (i in 0 until totalPixels) {
                val pixel = pixelsBuffer[i]
                val b = ((pixel and 0xFF) / 255f) * scale
                val nb = if (modelName == "spoof_model.tflite") {
                    (b - 0.406f) / 0.225f
                } else {
                    b
                }
                inputBuffer.putFloat(nb)
            }
        } else {
            // Channel-Last Structure: Stream color tokens sequentially [R1, G1, B1...]
            for (i in 0 until totalPixels) {
                val pixel = pixelsBuffer[i]
                val r = (((pixel shr 16) and 0xFF) / 255f) * scale
                val g = (((pixel shr 8) and 0xFF) / 255f) * scale
                val b = ((pixel and 0xFF) / 255f) * scale

                if (modelName == "spoof_model.tflite") {
                    val nr = (r - 0.485f) / 0.229f
                    val ng = (g - 0.456f) / 0.224f
                    val nb = (b - 0.406f) / 0.225f
                    inputBuffer.putFloat(nr)
                    inputBuffer.putFloat(ng)
                    inputBuffer.putFloat(nb)
                } else {
                    inputBuffer.putFloat(r)
                    inputBuffer.putFloat(g)
                    inputBuffer.putFloat(b)
                }
            }
        }

        // 4. Run inference using pre-allocated outputArray
        inputBuffer.rewind()
        interpreter!!.run(inputBuffer, outputArray)

        // 5. Evaluate and route logit tensors
        if (outputSize == 2) {
            val class0 = outputArray[0][0]
            val class1 = outputArray[0][1]

            Log.d("RAW_OUTPUT_LOGITS", "$modelName raw components: class0=$class0, class1=$class1")

            val exp0 = exp(class0.toDouble())
            val exp1 = exp(class1.toDouble())
            val sum = exp0 + exp1
            
            return if (modelName == "spoof_model.tflite") {
                val prob0 = (exp0 / sum).toFloat() // Softmax probability for Class 0 (Safe/Real Human)
                Log.d("MODEL_SCORE_EVAL", "$modelName computed passing score=$prob0")
                prob0
            } else {
                val prob1 = (exp1 / sum).toFloat() // Softmax probability for Class 1 (Unsafe/NSFW/Attack)
                Log.d("MODEL_SCORE_EVAL", "$modelName computed attack score=$prob1")
                prob1
            }
        }

        val score = outputArray[0][0]
        Log.d("MODEL_SCORE_EVAL", "$modelName computed score=$score")
        return score
    }
}