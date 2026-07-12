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

    fun predict(bitmap: Bitmap): FloatArray {
        synchronized(lock) {
            val tflite = interpreter ?: return floatArrayOf(0.0f, 0.0f)

            // 1. Rescale bitmap layer cleanly to fit current model dimensions
            val resizedBitmap = if (bitmap.width == inputWidth && bitmap.height == inputHeight) {
                bitmap
            } else {
                Bitmap.createScaledBitmap(bitmap, inputWidth, inputHeight, true)
            }

            // Use pre-allocated pixelsBuffer array instead of fresh allocations
            val intValues = pixelsBuffer
            resizedBitmap.getPixels(intValues, 0, inputWidth, 0, 0, inputWidth, inputHeight)

            if (resizedBitmap != bitmap) {
                resizedBitmap.recycle()
            }

            // 2. Rewind pre-allocated native input buffer
            inputBuffer.rewind()

            val isSpoofModel = modelName.contains("spoof", ignoreCase = true)
            val isBlurModel = modelName.contains("blur", ignoreCase = true)
            val useNCHW = isNCHW || isSpoofModel
            val useSigmoid = (outputSize == 1) || isBlurModel

            // 3. Replace the internal execution segment with the normalized path
            if (useNCHW) {
                // Planar format [1, 3, 224, 224]
                if (isSpoofModel) {
                    // ImageNet normalization: (x / 255.0 - mean) / std
                    for (i in 0 until intValues.size) {
                        val pixel = intValues[i]
                        val r = (((pixel shr 16) and 0xFF) / 255.0f - 0.485f) / 0.229f
                        inputBuffer.putFloat(r)
                    }
                    for (i in 0 until intValues.size) {
                        val pixel = intValues[i]
                        val g = (((pixel shr 8) and 0xFF) / 255.0f - 0.456f) / 0.224f
                        inputBuffer.putFloat(g)
                    }
                    for (i in 0 until intValues.size) {
                        val pixel = intValues[i]
                        val b = (((pixel and 0xFF)) / 255.0f - 0.406f) / 0.225f
                        inputBuffer.putFloat(b)
                    }
                } else {
                    for (i in 0 until intValues.size) {
                        val pixel = intValues[i]
                        val r = ((pixel shr 16) and 0xFF) / 255.0f
                        inputBuffer.putFloat(r)
                    }
                    for (i in 0 until intValues.size) {
                        val pixel = intValues[i]
                        val g = ((pixel shr 8) and 0xFF) / 255.0f
                        inputBuffer.putFloat(g)
                    }
                    for (i in 0 until intValues.size) {
                        val pixel = intValues[i]
                        val b = (pixel and 0xFF) / 255.0f
                        inputBuffer.putFloat(b)
                    }
                }
            } else {
                // Interleaved standard format [1, 224, 224, 3]
                for (i in 0 until intValues.size) {
                    val pixel = intValues[i]
                    inputBuffer.putFloat(((pixel shr 16) and 0xFF) / 255.0f)
                    inputBuffer.putFloat(((pixel shr 8) and 0xFF) / 255.0f)
                    inputBuffer.putFloat((pixel and 0xFF) / 255.0f)
                }
            }

            // 4. Run the evaluation matrix dynamically allocated based on outputSize
            inputBuffer.rewind()
            val rawOutputs = Array(1) { FloatArray(outputSize) }
            tflite.run(inputBuffer, rawOutputs)

            Log.d("TFLiteHelper", "Model $modelName raw outputs: ${rawOutputs[0].joinToString()}")

            // 5. Normalization based on output size (Sigmoid for single binary node, Softmax for 2 classes)
            if (useSigmoid) {
                val logit = rawOutputs[0][0]
                val sigmoidVal = (1.0 / (1.0 + Math.exp(-logit.toDouble()))).toFloat()
                return floatArrayOf(sigmoidVal)
            } else {
                // DEFENSIVE EXPLICIT SOFTMAX OVERLAY
                val logit0 = rawOutputs[0][0]
                val logit1 = rawOutputs[0][1]
                val maxLogit = maxOf(logit0, logit1)

                val exp0 = Math.exp((logit0 - maxLogit).toDouble()).toFloat()
                val exp1 = Math.exp((logit1 - maxLogit).toDouble()).toFloat()
                val sumExp = exp0 + exp1

                val finalizedProbabilities = floatArrayOf(exp0 / sumExp, exp1 / sumExp)
                return finalizedProbabilities
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