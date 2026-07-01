package com.example.powergridattendance

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun handleVerificationSuccess(
    context: android.content.Context,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    faceNetHelper: FaceNetHelper,
    croppedFace: Bitmap,
    fullBitmap: Bitmap,
    onDone: () -> Unit,
    setIsProcessing: (Boolean) -> Unit
) {
    setIsProcessing(true)

    coroutineScope.launch {
        val spoofScore = FaceState.getAverageSpoof()
        val blurScore = FaceState.getAverageBlur()
        val nsfwScore = FaceState.getAverageNsfw()

        val fileName = if (CurrentEmployee.isRegisterMode) {
            CurrentEmployee.capturedFileName
        } else {
            "attendance.jpg"
        }

        // Save cropped face bitmap to storage
        withContext(Dispatchers.IO) {
            BitmapUtils.saveBitmap(
                context,
                croppedFace,
                fileName
            )
        }

        if (CurrentEmployee.isRegisterMode) {
            EmployeeRepository.addEmployee(
                Employee(
                    employeeId = CurrentEmployee.employeeId,
                    employeeName = CurrentEmployee.employeeName,
                    imagePath = fileName
                )
            )

            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    "Face Registered Successfully",
                    Toast.LENGTH_LONG
                ).show()

                CurrentEmployee.isRegisterMode = false

                CaptureResultState.capturedImage.value = fullBitmap
                CaptureResultState.spoofScore.value = spoofScore
                CaptureResultState.nsfwScore.value = nsfwScore
                CaptureResultState.blurScore.value = blurScore
                CaptureResultState.matchScore.value = 1f
                CaptureResultState.recognizedName.value = CurrentEmployee.employeeName
                CaptureResultState.status.value = "REGISTERED"
                CaptureResultState.showResult.value = true

                setIsProcessing(false)
                onDone()
            }
        } else {
            val attendanceEmbedding = withContext(Dispatchers.IO) {
                faceNetHelper.getEmbedding(croppedFace)
            }

            var bestScore = 0f
            var bestEmployee: Employee? = null

            withContext(Dispatchers.IO) {
                for (employee in EmployeeRepository.getAllEmployees()) {
                    val registeredBitmap = BitmapUtils.loadBitmap(
                        context,
                        employee.imagePath
                    )

                    if (registeredBitmap != null) {
                        val registeredEmbedding = faceNetHelper.getEmbedding(registeredBitmap)
                        val similarity = faceNetHelper.compareFaces(
                            registeredEmbedding,
                            attendanceEmbedding
                        )

                        if (similarity > bestScore) {
                            bestScore = similarity
                            bestEmployee = employee
                        }
                    }
                }
            }

            val timestamp = SimpleDateFormat(
                "dd-MM-yyyy HH:mm:ss",
                Locale.getDefault()
            ).format(Date())

            val matchedEmployee = bestEmployee
            if (matchedEmployee != null && bestScore > 0.60f) {
                AttendanceRepository.addRecord(
                    AttendanceRecord(
                        employeeName = matchedEmployee.employeeName,
                        imagePath = fileName,
                        spoofScore = spoofScore,
                        blurScore = blurScore,
                        nsfwScore = nsfwScore,
                        matchScore = bestScore,
                        status = "SUCCESS",
                        reason = "Attendance Marked",
                        timestamp = timestamp
                    )
                )

                withContext(Dispatchers.Main) {
                    RecognitionState.recognizedName.value = matchedEmployee.employeeName
                    RecognitionState.attendanceMarked.value = true

                    Toast.makeText(
                        context,
                        "Attendance Marked: ${matchedEmployee.employeeName}",
                        Toast.LENGTH_LONG
                    ).show()

                    CaptureResultState.capturedImage.value = fullBitmap
                    CaptureResultState.spoofScore.value = spoofScore
                    CaptureResultState.nsfwScore.value = nsfwScore
                    CaptureResultState.blurScore.value = blurScore
                    CaptureResultState.matchScore.value = bestScore
                    CaptureResultState.recognizedName.value = matchedEmployee.employeeName
                    CaptureResultState.status.value = "SUCCESS"
                    CaptureResultState.showResult.value = true

                    setIsProcessing(false)
                    onDone()
                }
            } else {
                AttendanceRepository.addRecord(
                    AttendanceRecord(
                        employeeName = "Unknown",
                        imagePath = fileName,
                        spoofScore = spoofScore,
                        blurScore = blurScore,
                        nsfwScore = nsfwScore,
                        matchScore = bestScore,
                        status = "FAILED",
                        reason = "Face Not Matched",
                        timestamp = timestamp
                    )
                )

                withContext(Dispatchers.Main) {
                    RecognitionState.recognizedName.value = ""
                    RecognitionState.attendanceMarked.value = false

                    Toast.makeText(
                        context,
                        "Face Not Matching Score: $bestScore",
                        Toast.LENGTH_LONG
                    ).show()

                    CaptureResultState.capturedImage.value = fullBitmap
                    CaptureResultState.spoofScore.value = spoofScore
                    CaptureResultState.nsfwScore.value = nsfwScore
                    CaptureResultState.blurScore.value = blurScore
                    CaptureResultState.matchScore.value = bestScore
                    CaptureResultState.recognizedName.value = "Unknown"
                    CaptureResultState.status.value = "FAILED"
                    CaptureResultState.showResult.value = true

                    setIsProcessing(false)
                    onDone()
                }
            }
        }
    }
}

@Composable
fun CameraScreen(
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isProcessing by remember { mutableStateOf(false) }

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

    DisposableEffect(Unit) {
        FaceState.onVerificationSuccess = { cropped, full ->
            handleVerificationSuccess(
                context = context,
                coroutineScope = coroutineScope,
                faceNetHelper = faceNetHelper,
                croppedFace = cropped,
                fullBitmap = full,
                onDone = onDone,
                setIsProcessing = { isProcessing = it }
            )
        }
        onDispose {
            FaceState.onVerificationSuccess = null
            FaceState.clearHistory()
        }
    }

    val faceDetected = FaceState.faceDetected.value
    val liveSpoof = FaceState.liveSpoofScore.value
    val liveBlur = FaceState.liveBlurScore.value
    val liveNsfw = FaceState.liveNsfwScore.value

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        CameraPreview()

        // Viewfinder Border
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(
                    width = 320.dp,
                    height = 420.dp
                )
                .border(
                    width = 4.dp,
                    color = when {
                        FaceState.attendanceVerified.value -> Color.Green
                        FaceState.isLiveVerified.value -> Color.Green
                        FaceState.faceDetected.value -> Color.Yellow
                        else -> Color.Red
                    }
                )
        )

        // Dynamic Liveness Status Banner Overlaying Viewfinder
        Card(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 40.dp)
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White.copy(alpha = 0.9f)
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when {
                    FaceState.attendanceVerified.value -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "✅ Attendance Verified",
                                color = Color(0xFF2E7D32),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Spoof: ${if (liveSpoof != null) "${(liveSpoof * 100).toInt()}%" else "Calculating..."} | " +
                                    "Blur: ${if (liveBlur != null) "${(liveBlur * 100).toInt()}%" else "Calculating..."} | " +
                                    "NSFW: ${if (liveNsfw != null) "${(liveNsfw * 100).toInt()}%" else "Calculating..."}",
                            color = Color.DarkGray,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp
                        )
                    }
                    FaceState.userWarning.value != null -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "⚠️ Warning: ${FaceState.userWarning.value}",
                                color = Color.Red,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Spoof: ${if (liveSpoof != null) "${(liveSpoof * 100).toInt()}%" else "Calculating..."} | " +
                                    "Blur: ${if (liveBlur != null) "${(liveBlur * 100).toInt()}%" else "Calculating..."} | " +
                                    "NSFW: ${if (liveNsfw != null) "${(liveNsfw * 100).toInt()}%" else "Calculating..."}",
                            color = Color.DarkGray,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp
                        )
                    }
                    FaceState.isLiveVerified.value -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "✅ Live Verified (Liveness Check Passed)",
                                color = Color(0xFF2E7D32),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Spoof: ${if (liveSpoof != null) "${(liveSpoof * 100).toInt()}%" else "Calculating..."} | " +
                                    "Blur: ${if (liveBlur != null) "${(liveBlur * 100).toInt()}%" else "Calculating..."} | " +
                                    "NSFW: ${if (liveNsfw != null) "${(liveNsfw * 100).toInt()}%" else "Calculating..."}",
                            color = Color.DarkGray,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp
                        )
                    }
                    !faceDetected -> {
                        Text(
                            text = "❌ No Face Detected",
                            color = Color.Red,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                    !FaceState.isFullFaceVisible.value -> {
                        Text(
                            text = "⚠️ Face Partially Visible (Align Face)",
                            color = Color.Red,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                    else -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "🔒 Liveness Pending (Hold Still)",
                                color = Color.Red,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Spoof: ${if (liveSpoof != null) "${(liveSpoof * 100).toInt()}%" else "Calculating..."} | " +
                                    "Blur: ${if (liveBlur != null) "${(liveBlur * 100).toInt()}%" else "Calculating..."} | " +
                                    "NSFW: ${if (liveNsfw != null) "${(liveNsfw * 100).toInt()}%" else "Calculating..."}",
                            color = Color.DarkGray,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        if (RecognitionState.recognizedName.value.isNotEmpty() && !isProcessing) {
            Text(
                text = "👤 Recognized: ${RecognitionState.recognizedName.value}",
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                color = Color.White,
                fontWeight = FontWeight.Bold
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

                if (!FaceState.isLiveVerified.value && !FaceState.attendanceVerified.value) {
                    Toast.makeText(
                        context,
                        "Liveness not verified. Please align face and wait.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@Button
                }

                isProcessing = true

                val fileName =
                    if (CurrentEmployee.isRegisterMode)
                        CurrentEmployee.capturedFileName
                    else
                        "attendance.jpg"

                val tempFileName = "temp_capture.jpg"

                ImageCaptureHelper.captureImage(
                    context = context,
                    imageCapture = CameraState.imageCapture,
                    fileName = tempFileName,

                    onSaved = { capturedBitmap ->
                        val cleanupTempFile = {
                            try {
                                val file = java.io.File(context.filesDir, tempFileName)
                                if (file.exists()) {
                                    file.delete()
                                }
                            } catch (e: Exception) {
                                Log.e("CLEANUP", "Error deleting temporary file", e)
                            }
                            Unit
                        }

                        coroutineScope.launch outerLaunch@{
                            val blurScore = FaceState.getAverageBlur()
                            val nsfwScore = FaceState.getAverageNsfw()
                            Log.d("TEST_METRICS", "Blur: $blurScore, NSFW: $nsfwScore")

                            // Block if safety average is <= 0.50f
                            if (nsfwScore < 0.50f) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                        context,
                                        "Unsafe image detected. Capture blocked.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    isProcessing = false
                                }
                                cleanupTempFile()
                            } else {
                                withContext(Dispatchers.Main) {
                                    CapturedFaceProcessor.processCapturedFace(
                                        context = context,
                                        bitmap = capturedBitmap,

                                        onSuccess = { croppedFace ->
                                            coroutineScope.launch innerLaunch@{
                                                var spoofScore = FaceState.getAverageSpoof()
                                                Log.d("TEST_SPOOF", "Averaged Spoof score = $spoofScore, Live verified = ${FaceState.isLiveVerified.value}")

                                                if (FaceState.isLiveVerified.value || FaceState.attendanceVerified.value) {
                                                    spoofScore = 0.0f // Bypass: set to minimum spoof probability (0.0f)
                                                    Log.d("TEST_SPOOF", "Bypassed spoof check as face liveness is verified. Overridden spoof score to $spoofScore")
                                                }

                                                // Block if spoof average is >= 0.40f (meaning spoof is detected)
                                                if (spoofScore >= 0.40f) {
                                                    withContext(Dispatchers.Main) {
                                                        Toast.makeText(
                                                            context,
                                                            "Anti-spoofing validation failed. Capture rejected.",
                                                            Toast.LENGTH_LONG
                                                        ).show()
                                                        isProcessing = false
                                                    }
                                                    cleanupTempFile()
                                                } else {
                                                    withContext(Dispatchers.IO) {
                                                        BitmapUtils.saveBitmap(
                                                            context,
                                                            croppedFace,
                                                            fileName
                                                        )
                                                    }
                                                    cleanupTempFile()

                                                    if (CurrentEmployee.isRegisterMode) {
                                                        EmployeeRepository.addEmployee(
                                                            Employee(
                                                                employeeId = CurrentEmployee.employeeId,
                                                                employeeName = CurrentEmployee.employeeName,
                                                                imagePath = fileName
                                                            )
                                                        )

                                                        withContext(Dispatchers.Main) {
                                                            Toast.makeText(
                                                                context,
                                                                "Face Registered Successfully",
                                                                Toast.LENGTH_LONG
                                                            ).show()

                                                            CurrentEmployee.isRegisterMode = false

                                                            CaptureResultState.capturedImage.value = capturedBitmap
                                                            CaptureResultState.spoofScore.value = spoofScore
                                                            CaptureResultState.nsfwScore.value = nsfwScore
                                                            CaptureResultState.blurScore.value = blurScore
                                                            CaptureResultState.matchScore.value = 1f
                                                            CaptureResultState.recognizedName.value = CurrentEmployee.employeeName
                                                            CaptureResultState.status.value = "REGISTERED"
                                                            CaptureResultState.showResult.value = true

                                                            isProcessing = false
                                                            onDone()
                                                        }
                                                    } else {
                                                        val attendanceEmbedding = withContext(Dispatchers.IO) {
                                                            faceNetHelper.getEmbedding(croppedFace)
                                                        }

                                                        var bestScore = 0f
                                                        var bestEmployee: Employee? = null

                                                        withContext(Dispatchers.IO) {
                                                            for (employee in EmployeeRepository.getAllEmployees()) {
                                                                val registeredBitmap = BitmapUtils.loadBitmap(
                                                                    context,
                                                                    employee.imagePath
                                                                )

                                                                if (registeredBitmap != null) {
                                                                    val registeredEmbedding = faceNetHelper.getEmbedding(registeredBitmap)
                                                                    val similarity = faceNetHelper.compareFaces(
                                                                        registeredEmbedding,
                                                                        attendanceEmbedding
                                                                    )

                                                                    if (similarity > bestScore) {
                                                                        bestScore = similarity
                                                                        bestEmployee = employee
                                                                    }
                                                                }
                                                            }
                                                        }

                                                        val timestamp = SimpleDateFormat(
                                                            "dd-MM-yyyy HH:mm:ss",
                                                            Locale.getDefault()
                                                        ).format(Date())

                                                        val matchedEmployee = bestEmployee
                                                        if (matchedEmployee != null && bestScore > 0.60f) {
                                                            AttendanceRepository.addRecord(
                                                                AttendanceRecord(
                                                                    employeeName = matchedEmployee.employeeName,
                                                                    imagePath = fileName,
                                                                    spoofScore = spoofScore,
                                                                    blurScore = blurScore,
                                                                    nsfwScore = nsfwScore,
                                                                    matchScore = bestScore,
                                                                    status = "SUCCESS",
                                                                    reason = "Attendance Marked",
                                                                    timestamp = timestamp
                                                                )
                                                            )

                                                            withContext(Dispatchers.Main) {
                                                                RecognitionState.recognizedName.value = matchedEmployee.employeeName
                                                                RecognitionState.attendanceMarked.value = true

                                                                Toast.makeText(
                                                                    context,
                                                                    "Attendance Marked: ${matchedEmployee.employeeName}",
                                                                    Toast.LENGTH_LONG
                                                                ).show()

                                                                CaptureResultState.capturedImage.value = capturedBitmap
                                                                CaptureResultState.spoofScore.value = spoofScore
                                                                CaptureResultState.nsfwScore.value = nsfwScore
                                                                CaptureResultState.blurScore.value = blurScore
                                                                CaptureResultState.matchScore.value = bestScore
                                                                CaptureResultState.recognizedName.value = matchedEmployee.employeeName
                                                                CaptureResultState.status.value = "SUCCESS"
                                                                CaptureResultState.showResult.value = true

                                                                isProcessing = false
                                                                onDone()
                                                            }
                                                        } else {
                                                            AttendanceRepository.addRecord(
                                                                AttendanceRecord(
                                                                    employeeName = "Unknown",
                                                                    imagePath = fileName,
                                                                    spoofScore = spoofScore,
                                                                    blurScore = blurScore,
                                                                    nsfwScore = nsfwScore,
                                                                    matchScore = bestScore,
                                                                    status = "FAILED",
                                                                    reason = "Face Not Matched",
                                                                    timestamp = timestamp
                                                                )
                                                            )

                                                            withContext(Dispatchers.Main) {
                                                                RecognitionState.recognizedName.value = ""
                                                                RecognitionState.attendanceMarked.value = false

                                                                Toast.makeText(
                                                                    context,
                                                                    "Face Not Matching Score: $bestScore",
                                                                    Toast.LENGTH_LONG
                                                                ).show()

                                                                CaptureResultState.capturedImage.value = capturedBitmap
                                                                CaptureResultState.spoofScore.value = spoofScore
                                                                CaptureResultState.nsfwScore.value = nsfwScore
                                                                CaptureResultState.blurScore.value = blurScore
                                                                CaptureResultState.matchScore.value = bestScore
                                                                CaptureResultState.recognizedName.value = "Unknown"
                                                                CaptureResultState.status.value = "FAILED"
                                                                CaptureResultState.showResult.value = true

                                                                isProcessing = false
                                                                onDone()
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        },

                                        onFailure = {
                                            Toast.makeText(
                                                context,
                                                "Face Processing Failed",
                                                Toast.LENGTH_LONG
                                            ).show()
                                            isProcessing = false
                                            cleanupTempFile()
                                        }
                                    )
                                }
                            }
                        }
                    }
                )
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp),
            enabled = !isProcessing
        ) {
            Text("Capture Face")
        }

        // Dark progress overlay covering viewport when isProcessing is active
        if (isProcessing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .clickable(enabled = false) {}, // Intercept touch events
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Processing Face Metrics...",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}