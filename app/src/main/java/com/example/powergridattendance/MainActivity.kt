package com.example.powergridattendance

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val faceNetHelper = FaceNetHelper(this)

        if (faceNetHelper.isModelLoaded()) {
            Log.d(
                "FACENET",
                "READY FOR EMBEDDING"
            )
        }

        setContent {

            var currentScreen by remember {
                mutableStateOf("dashboard")
            }

            MaterialTheme {

                when (currentScreen) {

                    "dashboard" -> {
                        PowerGridDashboard(
                            onRegisterClick = {
                                CurrentEmployee.isRegisterMode = true
                                AttendanceState.attendanceMode = false
                                currentScreen = "register"
                            },

                            onAttendanceClick = {
                                CurrentEmployee.isRegisterMode = false
                                AttendanceState.attendanceMode = true
                                currentScreen = "camera"
                            },

                            onHistoryClick = {
                                currentScreen = "history"
                            },

                            onEmployeeDetailsClick = {
                                currentScreen = "employeeDetails"
                            }
                        )
                    }

                    "register" -> {
                        RegisterFaceScreen(
                            onCaptureClick = {
                                currentScreen = "camera"
                            }
                        )
                    }

                    "camera" -> {
                        CameraScreen(
                            onDone = {
                                currentScreen = "result"
                            }
                        )
                    }

                    "result" -> {
                        ResultScreen(
                            onBack = {
                                currentScreen = "dashboard"
                            }
                        )
                    }

                    "history" -> {
                        AttendanceHistoryScreen(
                            onBack = {
                                currentScreen = "dashboard"
                            }
                        )
                    }

                    "employeeDetails" -> {
                        EmployeeDetailsScreen(
                            onBack = {
                                currentScreen = "dashboard"
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PowerGridDashboard(
    onRegisterClick: () -> Unit,
    onAttendanceClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onEmployeeDetailsClick: () -> Unit
) {

    Surface(
        modifier = Modifier.fillMaxSize()
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),

            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Spacer(modifier = Modifier.height(30.dp))

            Text(
                text = "POWER GRID",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Employee Attendance System",
                fontSize = 18.sp
            )

            Spacer(modifier = Modifier.height(40.dp))

            Button(
                onClick = onRegisterClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),

                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Register Face")
            }

            Spacer(modifier = Modifier.height(16.dp))

            DashboardButton(
                title = "Mark Attendance",
                onClick = onAttendanceClick
            )

            Spacer(modifier = Modifier.height(16.dp))

            DashboardButton(
                title = "Attendance History",
                onClick = onHistoryClick
            )

            Spacer(modifier = Modifier.height(16.dp))

            DashboardButton(
                title = "Employee Details",
                onClick = onEmployeeDetailsClick
            )

            Spacer(modifier = Modifier.height(16.dp))

            DashboardButton(
                title = "Settings",
                onClick = {}
            )
        }
    }
}

@Composable
fun DashboardButton(
    title: String,
    onClick: () -> Unit
) {

    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp),

        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = title,
            fontSize = 18.sp
        )
    }
}