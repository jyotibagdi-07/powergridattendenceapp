package com.example.powergridattendance

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.d("PERMISSION", "Camera permission successfully granted by launcher")
        } else {
            Log.e("PERMISSION", "Camera permission denied by user")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request CAMERA permission at startup if not already granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        EmployeeRepository.init(this)
        AttendanceRepository.init(this)

        val faceNetHelper = FaceNetHelper(this)

        if (faceNetHelper.isModelLoaded()) {
            Log.d(
                "FACENET",
                "READY FOR EMBEDDING"
            )
            RecognitionHelper.loadEmbeddings(this, faceNetHelper)
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
                            },

                            onSettingsClick = {
                                currentScreen = "settings"
                            }
                        )
                    }

                    "register" -> {
                        RegisterFaceScreen(
                            onCaptureClick = {
                                currentScreen = "camera"
                            },
                            onBack = {
                                currentScreen = "dashboard"
                            }
                        )
                    }

                    "camera" -> {
                        CameraScreen(
                            onDone = {
                                currentScreen = "dashboard"
                            },
                            onBack = {
                                currentScreen = "dashboard"
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
                                CurrentAttendanceSession.clearSession()
                                FaceState.clearHistory()
                                currentScreen = "dashboard"
                            }
                        )
                    }

                    "settings" -> {
                        SettingsScreen(
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
    onEmployeeDetailsClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    val firstEmployee = remember {
        val list = EmployeeRepository.getAllEmployees()
        if (list.isNotEmpty()) list.first() else null
    }

    val welcomeName = firstEmployee?.employeeName ?: "Rajesh"

    val profileBitmap = remember(firstEmployee) {
        firstEmployee?.let {
            BitmapUtils.loadBitmap(context, it.imagePath)
        }
    }

    // Dynamic timestamp formatting
    val currentDayDate = remember {
        SimpleDateFormat("EEEE, MMM dd, yyyy", Locale.getDefault()).format(Date())
    }
    val currentTime = remember {
        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF4F6F9))
    ) {
        // 1. Dark Blue Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0F3572))
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = null,
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(
                        text = "POWERGRID",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        lineHeight = 14.sp
                    )
                    Text(
                        text = "ATTENDANCE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF90CAF9),
                        lineHeight = 10.sp
                    )
                }
            }

            Text(
                text = "HOME",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "Notifications",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    // Notification badge
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .background(Color.Red, CircleShape)
                            .align(Alignment.TopEnd)
                            .offset(x = 4.dp, y = (-2).dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "2",
                            color = Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                if (profileBitmap != null) {
                    Image(
                        bitmap = profileBitmap.asImageBitmap(),
                        contentDescription = "Profile",
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = "Profile",
                        tint = Color.White,
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                    )
                }
            }
        }

        // Scrollable content area
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 2. Greeting Section
            Text(
                text = "Good Morning, $welcomeName!",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E293B)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$currentDayDate | $currentTime",
                fontSize = 13.sp,
                color = Color(0xFF64748B)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 3. 2x2 Grid of Rounded Cards
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.weight(1f)) {
                    GridCard(
                        title = "REGISTER FACE",
                        description = "Enroll face for quick biometric login.",
                        backgroundColor = Color(0xFF2E7D32),
                        icon = Icons.Default.Face,
                        onClick = onRegisterClick
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Box(modifier = Modifier.weight(1f)) {
                    GridCard(
                        title = "MARK ATTENDANCE",
                        description = "Clock in/out for the day.",
                        backgroundColor = Color(0xFF1976D2),
                        icon = Icons.Default.Check,
                        onClick = onAttendanceClick
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.weight(1f)) {
                    GridCard(
                        title = "ATTENDANCE HISTORY",
                        description = "View past records & monthly summary.",
                        backgroundColor = Color(0xFFE65100),
                        icon = Icons.Default.DateRange,
                        onClick = onHistoryClick
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Box(modifier = Modifier.weight(1f)) {
                    GridCard(
                        title = "EMPLOYEE DETAILS",
                        description = "Profile, Shift, Info & Records.",
                        backgroundColor = Color(0xFF6A1B9A),
                        icon = Icons.Default.Person,
                        onClick = onEmployeeDetailsClick
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            AttendanceOverviewCard()

            Spacer(modifier = Modifier.height(20.dp))

            // 5. Settings Card Link
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSettingsClick() },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = Color(0xFF475569),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Settings",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color(0xFF1E293B)
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "Open Settings",
                        tint = Color(0xFF94A3B8)
                    )
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@Composable
fun GridCard(
    title: String,
    description: String,
    backgroundColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )

            Column {
                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 10.sp,
                    lineHeight = 12.sp
                )
            }
        }
    }
}

// Attendance and Leave Overview Card Backend Connected Implementation
@Composable
fun AttendanceOverviewCard() {
    val context = LocalContext.current
    val records = remember { AttendanceRepository.getAllRecords() }

    val calendar = remember { java.util.Calendar.getInstance() }
    val today = remember { calendar.get(java.util.Calendar.DAY_OF_MONTH) }
    val curMonth = remember { calendar.get(java.util.Calendar.MONTH) } // 0-indexed (July is 6)
    val curYear = remember { calendar.get(java.util.Calendar.YEAR) }

    // Status: 0=Weekend (Grey), 1=Present (Green), 2=Absent/Leave (Red), 3=Future (Plain)
    val dayStatuses = remember(records) {
        val statuses = IntArray(32)
        for (d in 1..31) {
            val checkCal = java.util.Calendar.getInstance()
            checkCal.set(2026, java.util.Calendar.JULY, d)
            val dayOfWeek = checkCal.get(java.util.Calendar.DAY_OF_WEEK)
            val isWeekend = dayOfWeek == java.util.Calendar.SATURDAY || dayOfWeek == java.util.Calendar.SUNDAY

            if (isWeekend) {
                statuses[d] = 0
            } else if (d > today && curMonth == java.util.Calendar.JULY && curYear == 2026) {
                statuses[d] = 3
            } else {
                // Past or today weekday - check successful attendance
                val hasSuccess = records.any { record ->
                    record.status == "SUCCESS" &&
                    try {
                        val datePart = record.timestamp.split(" ")[0]
                        val parts = datePart.split("-")
                        parts[0].toInt() == d && parts[1].toInt() == 7 && parts[2].toInt() == 2026
                    } catch (e: Exception) {
                        false
                    }
                }
                if (hasSuccess) {
                    statuses[d] = 1
                } else {
                    statuses[d] = 2
                }
            }
        }
        statuses
    }

    val presentCount = remember(dayStatuses) { dayStatuses.count { it == 1 } }
    val leaveCount = remember(dayStatuses) { dayStatuses.count { it == 2 } }
    val remainingCount = remember(presentCount, leaveCount) { 31 - presentCount - leaveCount }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "MY ATTENDANCE & LEAVE OVERVIEW",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color(0xFF1E293B)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Donut Chart (Left)
                Box(
                    modifier = Modifier.size(70.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWidth = 16f
                        val total = 31f
                        val sweepPresent = (presentCount.toFloat() / total) * 360f
                        val sweepLeave = (leaveCount.toFloat() / total) * 360f
                        val sweepRemaining = (remainingCount.toFloat() / total) * 360f

                        // Present (Green)
                        drawArc(
                            color = Color(0xFF2E7D32),
                            startAngle = -90f,
                            sweepAngle = sweepPresent,
                            useCenter = false,
                            style = Stroke(strokeWidth)
                        )
                        // Leave (Red)
                        drawArc(
                            color = Color(0xFFD32F2F),
                            startAngle = -90f + sweepPresent,
                            sweepAngle = sweepLeave,
                            useCenter = false,
                            style = Stroke(strokeWidth)
                        )
                        // Remaining (Grey)
                        drawArc(
                            color = Color(0xFFB0BEC5),
                            startAngle = -90f + sweepPresent + sweepLeave,
                            sweepAngle = sweepRemaining,
                            useCenter = false,
                            style = Stroke(strokeWidth)
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "July",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B)
                        )
                        Text(
                            text = "2026",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                // 2. Legend List (Middle)
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LegendItem(color = Color(0xFF2E7D32), text = "Present: $presentCount d")
                    LegendItem(color = Color(0xFFD32F2F), text = "Leave: $leaveCount d")
                    LegendItem(color = Color(0xFFB0BEC5), text = "Remaining: $remainingCount d")
                }

                Spacer(modifier = Modifier.width(4.dp))

                // 3. Calendar Grid (Right)
                Column(
                    modifier = Modifier.width(126.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Week Days Header Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        listOf("S", "M", "T", "W", "T", "F", "S").forEach { day ->
                            Text(
                                text = day,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF64748B),
                                modifier = Modifier.weight(1f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Grid Days (July 2026, starting on Wednesday)
                    val cells = remember {
                        val list = mutableListOf<Int?>()
                        // 3 empty cells (Sunday, Monday, Tuesday)
                        list.add(null)
                        list.add(null)
                        list.add(null)
                        for (i in 1..31) {
                            list.add(i)
                        }
                        while (list.size < 35) {
                            list.add(null)
                        }
                        list
                    }

                    // 5 rows of 7 days
                    for (row in 0 until 5) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            for (col in 0 until 7) {
                                val day = cells[row * 7 + col]
                                val status = if (day != null) dayStatuses[day] else 3
                                val isToday = day == today && curMonth == java.util.Calendar.JULY && curYear == 2026
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CalendarCell(day = day, status = status, isToday = isToday)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LegendItem(color: Color, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(color, CircleShape)
        )
        Text(
            text = text,
            fontSize = 8.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF475569)
        )
    }
}

@Composable
fun CalendarCell(day: Int?, status: Int, isToday: Boolean) {
    Box(
        modifier = Modifier.size(18.dp),
        contentAlignment = Alignment.Center
    ) {
        if (day != null) {
            when {
                isToday -> {
                    val ringColor = if (status == 1) Color(0xFF2E7D32) else Color(0xFFD32F2F)
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .border(1.dp, ringColor, CircleShape)
                            .padding(1.5.dp)
                            .background(ringColor, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = day.toString(),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
                status == 1 -> { // Present
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(Color(0xFF2E7D32), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = day.toString(),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
                status == 2 -> { // Absent/Leave
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(Color(0xFFD32F2F), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = day.toString(),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
                status == 0 -> { // Weekend
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(Color(0xFFB0BEC5), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = day.toString(),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
                else -> { // Future
                    Text(
                        text = day.toString(),
                        fontSize = 8.sp,
                        color = Color(0xFF1E293B),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}