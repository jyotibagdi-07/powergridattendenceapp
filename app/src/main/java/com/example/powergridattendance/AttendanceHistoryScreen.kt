package com.example.powergridattendance

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asImageBitmap

@Composable
fun AttendanceHistoryScreen(
    onBack: () -> Unit
) {

    val context = LocalContext.current
    val records = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateListOf<AttendanceRecord>().apply { addAll(AttendanceRepository.getAllRecords()) } }
 
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
 
        Button(
            onClick = onBack,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        ) {
            Text("< Back")
        }
 
        Spacer(modifier = Modifier.height(16.dp))
 
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text(
                text = "Attendance History",
                style = MaterialTheme.typography.headlineSmall
            )
            Button(
                onClick = {
                    AttendanceRepository.clearAllRecords(context)
                    records.clear()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Text("Clear All")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn {

            items(records) { record ->

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {

                        val bitmap =
                            BitmapUtils.loadBitmap(
                                context,
                                record.imagePath
                            )

                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.size(150.dp)
                            )

                            Spacer(
                                modifier = Modifier.height(8.dp)
                            )
                        }

                        Text("Name: ${record.employeeName}")
                        Text("Status: ${record.status}")
                        Text("Reason: ${record.reason}")
                        Text("Spoof: ${record.spoofScore}")
                        Text("NSFW: ${record.nsfwScore}")
                        Text("Match Score: ${record.matchScore}")
                        Text("Time: ${record.timestamp}")
                    }
                }
            }
        }
    }
}