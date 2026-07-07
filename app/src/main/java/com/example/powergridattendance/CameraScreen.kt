package com.example.powergridattendance

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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
                context,
                Employee(
                    employeeId = CurrentEmployee.employeeId,
                    employeeName = CurrentEmployee.employeeName,
                    imagePath = fileName
                )
            )
            RecognitionHelper.loadEmbeddings(context, faceNetHelper)

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
            // Perform Recognition
            val (matchedNameResult, matchScoreResult) = RecognitionHelper.recognizeFace(croppedFace, faceNetHelper)

            withContext(Dispatchers.Main) {
                RecognitionState.recognizedName.value = matchedNameResult
                RecognitionState.matchScore.value = matchScoreResult
                RecognitionState.faceMatched.value = matchedNameResult != "Unknown" && matchScoreResult > 0.60f
            }

            // Anti-spoofing validation check before marking attendance
            if (spoofScore >= 0.40f) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Anti-spoofing validation failed. Attendance rejected.",
                        Toast.LENGTH_LONG
                    ).show()
                    
                    CaptureResultState.capturedImage.value = fullBitmap
                    CaptureResultState.spoofScore.value = spoofScore
                    CaptureResultState.nsfwScore.value = nsfwScore
                    CaptureResultState.blurScore.value = blurScore
                    CaptureResultState.matchScore.value = matchScoreResult
                    CaptureResultState.recognizedName.value = "Spoof Detected"
                    CaptureResultState.status.value = "SPOOF"
                    CaptureResultState.showResult.value = true
                    
                    setIsProcessing(false)
                    onDone()
                }
                return@launch
            }

            val timestamp = SimpleDateFormat(
                "dd-MM-yyyy HH:mm:ss",
                Locale.getDefault()
            ).format(Date())

            val matchedName = matchedNameResult
            val matchScore = matchScoreResult

            if (matchedName != "Unknown" && matchScore > 0.60f) {
                AttendanceRepository.addRecord(
                    context,
                    AttendanceRecord(
                        employeeName = matchedName,
                        imagePath = fileName,
                        spoofScore = spoofScore,
                        blurScore = blurScore,
                        nsfwScore = nsfwScore,
                        matchScore = matchScore,
                        status = "SUCCESS",
                        reason = "Attendance Marked",
                        timestamp = timestamp
                    )
                )

                withContext(Dispatchers.Main) {
                    RecognitionState.attendanceMarked.value = true
                    RecognitionState.faceMatched.value = true

                    Toast.makeText(
                        context,
                        "Attendance Marked: $matchedName",
                        Toast.LENGTH_LONG
                    ).show()

                    CaptureResultState.capturedImage.value = fullBitmap
                    CaptureResultState.spoofScore.value = spoofScore
                    CaptureResultState.nsfwScore.value = nsfwScore
                    CaptureResultState.blurScore.value = blurScore
                    CaptureResultState.matchScore.value = matchScore
                    CaptureResultState.recognizedName.value = matchedName
                    CaptureResultState.status.value = "SUCCESS"
                    CaptureResultState.showResult.value = true

                    setIsProcessing(false)
                    onDone()
                }
            } else {
                AttendanceRepository.addRecord(
                    context,
                    AttendanceRecord(
                        employeeName = "Unknown",
                        imagePath = fileName,
                        spoofScore = spoofScore,
                        blurScore = blurScore,
                        nsfwScore = nsfwScore,
                        matchScore = matchScore,
                        status = "FAILED",
                        reason = "Face Not Matched",
                        timestamp = timestamp
                    )
                )

                withContext(Dispatchers.Main) {
                    RecognitionState.recognizedName.value = "Unknown"
                    RecognitionState.attendanceMarked.value = false
                    RecognitionState.faceMatched.value = false

                    Toast.makeText(
                        context,
                        "Face Not Matching Score: $matchScore",
                        Toast.LENGTH_LONG
                    ).show()

                    CaptureResultState.capturedImage.value = fullBitmap
                    CaptureResultState.spoofScore.value = spoofScore
                    CaptureResultState.nsfwScore.value = nsfwScore
                    CaptureResultState.blurScore.value = blurScore
                    CaptureResultState.matchScore.value = matchScore
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
    onDone: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isProcessing by remember { mutableStateOf(false) }

    val faceNetHelper = remember {
        FaceNetHelper(context)
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

        Button(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 40.dp, start = 16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Black.copy(alpha = 0.5f),
                contentColor = Color.White
            )
        ) {
            Text("< Back")
        }

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
                        FaceState.attendanceVerified.value && RecognitionState.faceMatched.value -> Color.Green
                        FaceState.attendanceVerified.value && !RecognitionState.faceMatched.value -> Color.Magenta
                        FaceState.isLiveVerified.value -> Color.Cyan
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
                if (CurrentEmployee.isRegisterMode) {
                    Text(
                        text = if (faceDetected) "✅ Face Detected - Ready to Register" else "❌ No Face Detected",
                        color = if (faceDetected) Color(0xFF2E7D32) else Color.Red,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                } else {
                    when {
                        FaceState.attendanceVerified.value -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val text = if (RecognitionState.faceMatched.value) {
                                    "✅ Verified: ${RecognitionState.recognizedName.value}"
                                } else if (RecognitionState.recognizedName.value == "Unknown") {
                                    "❌ Unknown Person"
                                } else {
                                    "🔄 Recognizing..."
                                }
                                Text(
                                    text = text,
                                    color = if (RecognitionState.faceMatched.value) Color(0xFF2E7D32) else Color.Red,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Spoof: ${((FaceState.liveSpoofScore.value ?: 0f) * 100).toInt()}% | " +
                                        "Blur: ${((FaceState.liveBlurScore.value ?: 0f) * 100).toInt()}% | " +
                                        "NSFW: ${((FaceState.liveNsfwScore.value ?: 0f) * 100).toInt()}% | " +
                                        "Match: ${(RecognitionState.matchScore.value * 100).toInt()}%",
                                color = Color.DarkGray,
                                fontWeight = FontWeight.Medium,
                                fontSize = 11.sp
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
                                    text = "🔒 Liveness Pending (Align Face)",
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
        }

        if (RecognitionState.recognizedName.value.isNotEmpty() && RecognitionState.recognizedName.value != "Unknown" && !isProcessing && !FaceState.attendanceVerified.value) {
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
                    Toast.makeText(context, "No Face Detected", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                
                if (!CurrentEmployee.isRegisterMode && !FaceState.isLiveVerified.value && !FaceState.attendanceVerified.value) {
                    Toast.makeText(
                        context,
                        "Liveness not verified. Please align face and wait.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@Button
                }

                isProcessing = true
                Log.d("CAPTURE_DEBUG", "Starting capture. RegisterMode: ${CurrentEmployee.isRegisterMode}")

                val fileName =
                    if (CurrentEmployee.isRegisterMode)
                        CurrentEmployee.capturedFileName
                    else
                        "attendance.jpg"

                val tempFileName = "temp_capture_${System.currentTimeMillis()}.jpg"
                Log.d("CAPTURE_DEBUG", "Filename: $fileName, Temp: $tempFileName")

                ImageCaptureHelper.captureImage(
                    context = context,
                    imageCapture = CameraState.imageCapture,
                    fileName = tempFileName,

                    onSaved = { capturedBitmap ->
                        Log.d("CAPTURE_DEBUG", "Image captured successfully")
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
                            Log.d("CAPTURE_DEBUG", "Metrics: Blur=$blurScore, NSFW=$nsfwScore")

                            // Block if NSFW average is > 0.50f
                            if (nsfwScore > 0.50f) {
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
                                    Log.d("CAPTURE_DEBUG", "Processing captured face...")
                                    CapturedFaceProcessor.processCapturedFace(
                                        context = context,
                                        bitmap = capturedBitmap,

                                        onSuccess = { croppedFace ->
                                            Log.d("CAPTURE_DEBUG", "Face processed and cropped")
                                            coroutineScope.launch innerLaunch@{
                                                var spoofScore = FaceState.getAverageSpoof()
                                                Log.d("CAPTURE_DEBUG", "Spoof: $spoofScore, LiveVerified: ${FaceState.isLiveVerified.value}")



                                                // Block if spoof average is >= 0.40f (meaning spoof is detected)
                                                if (!CurrentEmployee.isRegisterMode && spoofScore >= 0.40f) {
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
                                                    Log.d("CAPTURE_DEBUG", "Saving bitmap to $fileName")
                                                    withContext(Dispatchers.IO) {
                                                        BitmapUtils.saveBitmap(
                                                            context,
                                                            croppedFace,
                                                            fileName
                                                        )
                                                    }
                                                    cleanupTempFile()

                                                    if (CurrentEmployee.isRegisterMode) {
                                                        Log.d("CAPTURE_DEBUG", "Registering employee: ${CurrentEmployee.employeeName}")
                                                        EmployeeRepository.addEmployee(
                                                            context,
                                                            Employee(
                                                                employeeId = CurrentEmployee.employeeId,
                                                                employeeName = CurrentEmployee.employeeName,
                                                                imagePath = fileName
                                                            )
                                                        )
                                                        RecognitionHelper.loadEmbeddings(context, faceNetHelper)

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
                                                        val matchedName = RecognitionState.recognizedName.value
                                                        val matchScore = RecognitionState.matchScore.value
                                                        
                                                        val timestamp = SimpleDateFormat(
                                                            "dd-MM-yyyy HH:mm:ss",
                                                            Locale.getDefault()
                                                        ).format(Date())

                                                        if (matchedName.isNotEmpty() && matchedName != "Unknown" && matchScore > 0.60f) {
                                                            AttendanceRepository.addRecord(
                                                                context,
                                                                AttendanceRecord(
                                                                    employeeName = matchedName,
                                                                    imagePath = fileName,
                                                                    spoofScore = spoofScore,
                                                                    blurScore = blurScore,
                                                                    nsfwScore = nsfwScore,
                                                                    matchScore = matchScore,
                                                                    status = "SUCCESS",
                                                                    reason = "Attendance Marked",
                                                                    timestamp = timestamp
                                                                )
                                                            )

                                                            withContext(Dispatchers.Main) {
                                                                RecognitionState.attendanceMarked.value = true
                                                                RecognitionState.faceMatched.value = true

                                                                Toast.makeText(
                                                                    context,
                                                                    "Attendance Marked: $matchedName",
                                                                    Toast.LENGTH_LONG
                                                                ).show()

                                                                CaptureResultState.capturedImage.value = capturedBitmap
                                                                CaptureResultState.spoofScore.value = spoofScore
                                                                CaptureResultState.nsfwScore.value = nsfwScore
                                                                CaptureResultState.blurScore.value = blurScore
                                                                CaptureResultState.matchScore.value = matchScore
                                                                CaptureResultState.recognizedName.value = matchedName
                                                                CaptureResultState.status.value = "SUCCESS"
                                                                CaptureResultState.showResult.value = true

                                                                isProcessing = false
                                                                onDone()
                                                            }
                                                        } else {
                                                            AttendanceRepository.addRecord(
                                                                context,
                                                                AttendanceRecord(
                                                                    employeeName = "Unknown",
                                                                    imagePath = fileName,
                                                                    spoofScore = spoofScore,
                                                                    blurScore = blurScore,
                                                                    nsfwScore = nsfwScore,
                                                                    matchScore = matchScore,
                                                                    status = "FAILED",
                                                                    reason = "Face Not Matched",
                                                                    timestamp = timestamp
                                                                )
                                                            )

                                                            withContext(Dispatchers.Main) {
                                                                RecognitionState.recognizedName.value = "Unknown"
                                                                RecognitionState.attendanceMarked.value = false
                                                                RecognitionState.faceMatched.value = false

                                                                Toast.makeText(
                                                                    context,
                                                                    "Face Not Matching Score: $matchScore",
                                                                    Toast.LENGTH_LONG
                                                                ).show()

                                                                CaptureResultState.capturedImage.value = capturedBitmap
                                                                CaptureResultState.spoofScore.value = spoofScore
                                                                CaptureResultState.nsfwScore.value = nsfwScore
                                                                CaptureResultState.blurScore.value = blurScore
                                                                CaptureResultState.matchScore.value = matchScore
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
