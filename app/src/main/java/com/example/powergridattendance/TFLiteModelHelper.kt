package com.example.powergridattendance

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class TFLiteModelHelper(
    private val context: Context,
    private val modelName: String
) {

    private var interpreter: Interpreter? = null
    private var inputWidth = 224
    private var inputHeight = 224
    private val numChannels = 3
    private var isNCHW = false // Dynamic layout interlock flag
    private var outputSize = 2

    // Pre-allocated buffers for zero-allocation prediction loop
    private lateinit var pixelsBuffer: IntArray
    private lateinit var inputBuffer: ByteBuffer

    private val lock = Any()

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

            // Inspect the model structure to dynamically detect NCHW vs NHWC layout
            val inputShape = interpreter?.getInputTensor(0)?.shape()
            val inputDataType = interpreter?.getInputTensor(0)?.dataType()
            val inputCount = interpreter?.inputTensorCount
            val outputCount = interpreter?.outputTensorCount
            if (inputShape != null && inputShape.size == 4) {
                // If index 1 is 3 (Channels), it's NCHW [1, 3, 224, 224]
                isNCHW = inputShape[1] == numChannels
                
                // Read input dimensions dynamically
                if (isNCHW) {
                    inputHeight = inputShape[2]
                    inputWidth = inputShape[3]
                } else {
                    inputHeight = inputShape[1]
                    inputWidth = inputShape[2]
                }
                
                Log.d("TFLiteHelper", "Model $modelName structural check -> isNCHW layout: $isNCHW, dimensions: $inputWidth x $inputHeight, dataType: $inputDataType, inputs: $inputCount, outputs: $outputCount")
            }

            // Pre-allocate pixel and ByteBuffers
            val totalPixels = inputWidth * inputHeight
            pixelsBuffer = IntArray(totalPixels)
            inputBuffer = ByteBuffer.allocateDirect(1 * numChannels * totalPixels * 4).apply {
                order(ByteOrder.nativeOrder())
            }

            // Inspect output shape to dynamically resolve outputSize
            val outputShape = interpreter?.getOutputTensor(0)?.shape()
            if (outputShape != null) {
                outputSize = outputShape.last()
            }

            Log.d("TFLiteHelper", "Model $modelName initialized successfully with outputSize: $outputSize")

        } catch (e: Exception) {
            Log.e("TFLiteHelper", "Error initializing interpreter for $modelName", e)
        }
    }

    fun predict(bitmap: Bitmap, rotationDegrees: Int = 0): FloatArray {
        synchronized(lock) {
            val tflite = interpreter ?: return floatArrayOf(0.0f, 0.0f)

            val resizedBitmap = if (bitmap.width == inputWidth && bitmap.height == inputHeight && rotationDegrees == 0) {
                bitmap
            } else {
                val scaled = Bitmap.createScaledBitmap(bitmap, inputWidth, inputHeight, true)
                if (rotationDegrees != 0) {
                    val matrix = android.graphics.Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                    val rotated = Bitmap.createBitmap(scaled, 0, 0, scaled.width, scaled.height, matrix, true)
                    scaled.recycle()
                    rotated
                } else {
                    scaled
                }
            }

            val intValues = pixelsBuffer
            resizedBitmap.getPixels(intValues, 0, inputWidth, 0, 0, inputWidth, inputHeight)

            if (resizedBitmap != bitmap) {
                resizedBitmap.recycle()
            }

            inputBuffer.rewind()

            val isSpoofModel = modelName.contains("spoof", ignoreCase = true)
            val isBlurModel = modelName.contains("blur", ignoreCase = true)

            // Dynamic layout flag check from input shape
            val inputShape = tflite.getInputTensor(0)?.shape()
            val useNCHW = if (inputShape != null && inputShape.size == 4) {
                inputShape[1] == numChannels
            } else {
                false
            }

            // In the working APK, the layout selection was overridden:
            // if z (which is isSpoofModel) is true, it runs interleaved.
            // if z is false, it runs planar.
            val layoutIsPlanar = if (isSpoofModel) {
                false // runs interleaved
            } else {
                useNCHW // otherwise checks input layout
            }

            if (!layoutIsPlanar) {
                // Interleaved NHWC format [1, 224, 224, 3]
                for (pixel in intValues) {
                    val r = ((pixel shr 16) and 0xFF) / 255.0f
                    val g = ((pixel shr 8) and 0xFF) / 255.0f
                    val b = (pixel and 0xFF) / 255.0f
                    inputBuffer.putFloat(r)
                    inputBuffer.putFloat(g)
                    inputBuffer.putFloat(b)
                }
            } else {
                // Planar NCHW format [1, 3, 224, 224]
                for (pixel in intValues) {
                    inputBuffer.putFloat(((pixel shr 16) and 0xFF) / 255.0f)
                }
                for (pixel in intValues) {
                    inputBuffer.putFloat(((pixel shr 8) and 0xFF) / 255.0f)
                }
                for (pixel in intValues) {
                    inputBuffer.putFloat((pixel and 0xFF) / 255.0f)
                }
            }

            inputBuffer.rewind()
            val rawOutputs = Array(1) { FloatArray(outputSize) }
            tflite.run(inputBuffer, rawOutputs)

            val useSigmoid = (outputSize == 1) || isBlurModel
            if (useSigmoid) {
                val logit = rawOutputs[0][0]
                if (logit >= 0.0f && logit <= 1.0f) {
                    return floatArrayOf(logit)
                }
                val sigmoidVal = (1.0 / (1.0 + Math.exp(-logit.toDouble()))).toFloat()
                return floatArrayOf(sigmoidVal)
            } else {
                val logit0 = rawOutputs[0][0]
                val logit1 = rawOutputs[0][1]
                if (Math.abs((logit0 + logit1) - 1.0f) < 0.02f && logit0 >= 0.0f && logit1 >= 0.0f) {
                    return rawOutputs[0]
                }
                val maxLogit = maxOf(logit0, logit1)
                val exp0 = Math.exp((logit0 - maxLogit).toDouble()).toFloat()
                val exp1 = Math.exp((logit1 - maxLogit).toDouble()).toFloat()
                val sumExp = exp0 + exp1
                return floatArrayOf(exp0 / sumExp, exp1 / sumExp)
            }
        }
    }

    fun close() {
        synchronized(lock) {
            interpreter?.close()
            interpreter = null
        }
    }
}