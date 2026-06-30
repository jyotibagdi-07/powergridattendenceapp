package com.example.powergridattendance

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CameraScreen(
    onDone: () -> Unit
) {
    val context = LocalContext.current

    val faceNetHelper = remember {
        FaceNetHelper(context)
    }

    val spoofHelper = remember {
        TFLiteModelHelper(
            context,
            "spoof_model.tflite"
        )
    }

    val nsfwHelper = remember {
        TFLiteModelHelper(
            context,
            "nsfw_model.tflite"
        )
    }
    val blurHelper = remember {
        TFLiteModelHelper(
            context,
            "blur_model.tflite"
        )
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {

        CameraPreview()

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(
                    width = 320.dp,
                    height = 420.dp
                )
                .border(
                    width = 4.dp,
                    color =
                        if (FaceState.faceDetected.value)
                            Color.Green
                        else
                            Color.Red
                )
        )

        if (FaceState.faceDetected.value) {
            Text(
                text = "✅ Face Detected",
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 50.dp)
            )
        } else {
            Text(
                text = "❌ No Face Detected",
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 50.dp)
            )
        }

        if (RecognitionState.recognizedName.value.isNotEmpty()) {
            Text(
                text = "👤 Recognized: ${RecognitionState.recognizedName.value}",
                modifier = Modifier.align(Alignment.Center)
            )
        }

        Button(
            onClick = {

                if (!FaceState.faceDetected.value) {
                    Toast.makeText(
                        context,
                        "No Face Detected",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@Button
                }

                val fileName =
                    if (CurrentEmployee.isRegisterMode)
                        CurrentEmployee.capturedFileName
                    else
                        "attendance.jpg"

                ImageCaptureHelper.captureImage(
                    context = context,
                    imageCapture = CameraState.imageCapture,
                    fileName = fileName,

                    onSaved = { capturedBitmap ->

                        CapturedFaceProcessor.processCapturedFace(
                            context = context,
                            bitmap = capturedBitmap,
                            outputFileName = fileName,

                            onSuccess = { croppedFace ->

                                val spoofScore =
                                    spoofHelper.predict(croppedFace)

                                Log.d(
                                    "TEST_SPOOF",
                                    "Spoof score = $spoofScore"
                                )
                                val blurScore =
                                    blurHelper.predict(croppedFace)

                                Log.d(
                                    "TEST_BLUR",
                                    "Blur score = $blurScore"
                                )

                                val nsfwScore =
                                    nsfwHelper.predict(capturedBitmap)

                                Log.d(
                                    "TEST_NSFW",
                                    "NSFW score = $nsfwScore"
                                )

                                if (nsfwScore > 0.3f) {
                                    Toast.makeText(
                                        context,
                                        "Unsafe image detected. Capture blocked.",
                                        Toast.LENGTH_LONG
                                    ).show()

                                    return@processCapturedFace
                                }

                                if (CurrentEmployee.isRegisterMode) {

                                    EmployeeRepository.addEmployee(
                                        Employee(
                                            employeeId = CurrentEmployee.employeeId,
                                            employeeName = CurrentEmployee.employeeName,
                                            imagePath = fileName
                                        )
                                    )

                                    Toast.makeText(
                                        context,
                                        "Face Registered Successfully",
                                        Toast.LENGTH_LONG
                                    ).show()

                                    CurrentEmployee.isRegisterMode = false

                                    CaptureResultState.capturedImage.value =
                                        capturedBitmap

                                    CaptureResultState.spoofScore.value =
                                        spoofScore

                                    CaptureResultState.nsfwScore.value =
                                        nsfwScore

                                    CaptureResultState.blurScore.value = blurScore

                                    CaptureResultState.matchScore.value =
                                        1f

                                    CaptureResultState.recognizedName.value =
                                        CurrentEmployee.employeeName

                                    CaptureResultState.status.value =
                                        "REGISTERED"

                                    CaptureResultState.showResult.value =
                                        true

                                    onDone()

                                } else {

                                    val attendanceEmbedding =
                                        faceNetHelper.getEmbedding(croppedFace)

                                    var bestScore = 0f
                                    var bestEmployee: Employee? = null

                                    for (employee in EmployeeRepository.getAllEmployees()) {

                                        val registeredBitmap =
                                            BitmapUtils.loadBitmap(
                                                context,
                                                employee.imagePath
                                            )

                                        if (registeredBitmap != null) {

                                            val registeredEmbedding =
                                                faceNetHelper.getEmbedding(
                                                    registeredBitmap
                                                )

                                            val similarity =
                                                faceNetHelper.compareFaces(
                                                    registeredEmbedding,
                                                    attendanceEmbedding
                                                )

                                            if (similarity > bestScore) {
                                                bestScore = similarity
                                                bestEmployee = employee
                                            }
                                        }
                                    }

                                    if (
                                        bestEmployee != null &&
                                        bestScore > 0.60f
                                    ) {

                                        RecognitionState.recognizedName.value =
                                            bestEmployee.employeeName

                                        RecognitionState.attendanceMarked.value =
                                            true

                                        val timestamp =
                                            SimpleDateFormat(
                                                "dd-MM-yyyy HH:mm:ss",
                                                Locale.getDefault()
                                            ).format(Date())

                                        AttendanceRepository.addRecord(
                                            AttendanceRecord(
                                                employeeName = bestEmployee.employeeName,
                                                imagePath = fileName,
                                                spoofScore = spoofScore,
                                                blurScore = null,
                                                nsfwScore = nsfwScore,
                                                matchScore = bestScore,
                                                status = "SUCCESS",
                                                reason = "Attendance Marked",
                                                timestamp = timestamp
                                            )
                                        )

                                        Toast.makeText(
                                            context,
                                            "Attendance Marked: ${bestEmployee.employeeName}",
                                            Toast.LENGTH_LONG
                                        ).show()

                                        CaptureResultState.capturedImage.value =
                                            capturedBitmap

                                        CaptureResultState.spoofScore.value =
                                            spoofScore

                                        CaptureResultState.nsfwScore.value =
                                            nsfwScore

                                        CaptureResultState.blurScore.value =
                                            0f

                                        CaptureResultState.matchScore.value =
                                            bestScore

                                        CaptureResultState.recognizedName.value =
                                            bestEmployee.employeeName

                                        CaptureResultState.status.value =
                                            "SUCCESS"

                                        CaptureResultState.showResult.value =
                                            true

                                        onDone()

                                    } else {

                                        RecognitionState.recognizedName.value = ""
                                        RecognitionState.attendanceMarked.value =
                                            false

                                        val timestamp =
                                            SimpleDateFormat(
                                                "dd-MM-yyyy HH:mm:ss",
                                                Locale.getDefault()
                                            ).format(Date())

                                        AttendanceRepository.addRecord(
                                            AttendanceRecord(
                                                employeeName = "Unknown",
                                                imagePath = fileName,
                                                spoofScore = spoofScore,
                                                blurScore = null,
                                                nsfwScore = nsfwScore,
                                                matchScore = bestScore,
                                                status = "FAILED",
                                                reason = "Face Not Matched",
                                                timestamp = timestamp
                                            )
                                        )

                                        Toast.makeText(
                                            context,
                                            "Face Not Matching Score: $bestScore",
                                            Toast.LENGTH_LONG
                                        ).show()
                                        CaptureResultState.capturedImage.value =
                                            capturedBitmap

                                        CaptureResultState.spoofScore.value =
                                            spoofScore

                                        CaptureResultState.nsfwScore.value =
                                            nsfwScore

                                        CaptureResultState.blurScore.value =
                                            0f

                                        CaptureResultState.matchScore.value =
                                            bestScore

                                        CaptureResultState.recognizedName.value =
                                            "Unknown"

                                        CaptureResultState.status.value =
                                            "FAILED"

                                        CaptureResultState.showResult.value =
                                            true

                                        onDone()
                                    }
                                }
                            },

                            onFailure = {
                                Toast.makeText(
                                    context,
                                    "Face Processing Failed",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        )
                    }
                )
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp)
        ) {
            Text("Capture Face")
        }
    }
}