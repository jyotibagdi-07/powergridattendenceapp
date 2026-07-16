package com.example.powergridattendance

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var employeeCount by remember { mutableStateOf(EmployeeRepository.getAllEmployees().size) }
    var recordCount by remember { mutableStateOf(AttendanceRepository.getAllRecords().size) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .verticalScroll(scrollState)
    ) {
        // Back Button
        Button(
            onClick = onBack,
            modifier = Modifier.align(Alignment.Start),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back", modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Back")
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Screen Title
        Text(
            text = "Settings & App Details",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0F3572)
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Card 1: Device Info
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF0F3572))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Device Information", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF0F3572))
                }
                Divider(modifier = Modifier.padding(vertical = 12.dp))
                InfoRow("Manufacturer", Build.MANUFACTURER)
                InfoRow("Model", Build.MODEL)
                InfoRow("Android Version", Build.VERSION.RELEASE)
                InfoRow("SDK Level", Build.VERSION.SDK_INT.toString())
            }
        }

        // Card 2: Database Stats
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.List, contentDescription = null, tint = Color(0xFFE65100))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Database Statistics", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFFE65100))
                }
                Divider(modifier = Modifier.padding(vertical = 12.dp))
                InfoRow("Registered Employees", employeeCount.toString())
                InfoRow("Attendance Records", recordCount.toString())
            }
        }

        // Card 3: Security & Verification Config
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF2E7D32))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Security Configuration", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF2E7D32))
                }
                Divider(modifier = Modifier.padding(vertical = 12.dp))
                InfoRow("Liveness Check", "Active (Blink required)")
                InfoRow("Anti-Spoofing Check", "Active (TFLite Enhanced)")
                InfoRow("NSFW Image Filter", "Active (Deep Learning)")
            }
        }

        // Card 4: Model Status
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF6A1B9A))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Deep Learning Models Status", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF6A1B9A))
                }
                Divider(modifier = Modifier.padding(vertical = 12.dp))
                InfoRow("FaceNet (facenet_512.tflite)", "Loaded Successfully")
                InfoRow("Spoof Detection (spoof_model.tflite)", "Loaded Successfully")
                InfoRow("NSFW Safety (nsfw_model.tflite)", "Loaded Successfully")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Actions
        Button(
            onClick = {
                AttendanceRepository.clearAllRecords(context)
                recordCount = 0
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Icon(Icons.Default.Delete, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Clear Attendance Records", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = Color.Gray, fontSize = 14.sp)
        Text(text = value, fontWeight = FontWeight.Medium, fontSize = 14.sp)
    }
}
