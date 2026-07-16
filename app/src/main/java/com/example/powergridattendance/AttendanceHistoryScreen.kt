package com.example.powergridattendance

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AttendanceHistoryScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val records = remember { 
        mutableStateListOf<AttendanceRecord>().apply { 
            addAll(AttendanceRepository.getAllRecords()) 
        } 
    }
    var selectedIndex by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // 1. Header Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Circular back arrow container
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xFFEEF2FF), CircleShape)
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color(0xFF4F46E5),
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Attendance History",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Color(0xFF0F172A)
                )
            }

            // Clear All Button (Red Outline)
            if (records.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .border(
                            width = 1.dp,
                            color = Color(0xFFEF4444),
                            shape = RoundedCornerShape(18.dp)
                        )
                        .clip(RoundedCornerShape(18.dp))
                        .clickable {
                            AttendanceRepository.clearAllRecords(context)
                            records.clear()
                            selectedIndex = 0
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Clear All",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Clear All",
                            color = Color(0xFFEF4444),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (records.isEmpty()) {
            // Empty State
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = "No records",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "No attendance records found.",
                        color = Color(0xFF64748B),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        } else {
            // Records Available
            val selectedRecord = records.getOrNull(selectedIndex) ?: records[0]

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Selector Row for Multiple Records
                if (records.size > 1) {
                    item {
                        Column {
                            Text(
                                text = "Select Record to View Details",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF64748B),
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                itemsIndexed(records) { index, record ->
                                    val isSelected = index == selectedIndex
                                    val dateStr = try {
                                        val datePart = record.timestamp.split(" ")[0]
                                        val parts = datePart.split("-")
                                        val monthNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
                                        val month = monthNames.getOrNull(parts[1].toInt() - 1) ?: parts[1]
                                        "${parts[0]} $month"
                                    } catch (e: Exception) {
                                        record.timestamp
                                    }

                                    Card(
                                        modifier = Modifier.clickable { selectedIndex = index },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isSelected) Color(0xFFEEF2FF) else Color.White
                                        ),
                                        border = if (isSelected) {
                                            BoxBorder(1.dp, Color(0xFF4F46E5))
                                        } else null
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = dateStr,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) Color(0xFF4F46E5) else Color(0xFF0F172A)
                                            )
                                            Text(
                                                text = record.status,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (record.status == "SUCCESS") Color(0xFF16A34A) else Color(0xFFEF4444)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Card 1: Main Header Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Face Image
                            val bitmap = BitmapUtils.loadBitmap(context, selectedRecord.imagePath)
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Face Image",
                                    modifier = Modifier
                                        .size(height = 100.dp, width = 90.dp)
                                        .clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(height = 100.dp, width = 90.dp)
                                        .background(Color(0xFFE2E8F0), RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = "Face Placeholder",
                                        tint = Color(0xFF94A3B8)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            // Stats Column
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                // Attendance Marked Badge
                                Row(
                                    modifier = Modifier
                                        .background(Color(0xFFDCFCE7), RoundedCornerShape(12.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Marked",
                                        tint = Color(0xFF16A34A),
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = "Attendance Marked",
                                        color = Color(0xFF15803D),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Status Big Text
                                Text(
                                    text = selectedRecord.status,
                                    color = if (selectedRecord.status == "SUCCESS") Color(0xFF15803D) else Color(0xFFEF4444),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp
                                )
                            }
                        }
                    }
                }

                // Card 2: Main Details Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            DetailRow(
                                icon = Icons.Default.Person,
                                iconBgColor = Color(0xFFF3E8FF),
                                iconTintColor = Color(0xFF8B5CF6),
                                label = "Name",
                                value = selectedRecord.employeeName
                            )
                            DetailDivider()
                            DetailRow(
                                icon = Icons.Default.Check,
                                iconBgColor = Color(0xFFEEF2FF),
                                iconTintColor = Color(0xFF4F46E5),
                                label = "Status",
                                value = selectedRecord.status,
                                isSuccessStatus = selectedRecord.status == "SUCCESS"
                            )
                            DetailDivider()
                            DetailRow(
                                icon = Icons.Default.List,
                                iconBgColor = Color(0xFFF5F3FF),
                                iconTintColor = Color(0xFF7C3AED),
                                label = "Reason",
                                value = selectedRecord.reason
                            )
                            DetailDivider()
                            DetailRow(
                                icon = Icons.Default.Lock,
                                iconBgColor = Color(0xFFEFF6FF),
                                iconTintColor = Color(0xFF3B82F6),
                                label = "Spoof Score",
                                value = String.format("%.3f", selectedRecord.spoofScore)
                            )
                            DetailDivider()
                            DetailRow(
                                icon = Icons.Default.Warning,
                                iconBgColor = Color(0xFFFFF7ED),
                                iconTintColor = Color(0xFFF97316),
                                label = "NSFW Score",
                                value = String.format("%.8f", selectedRecord.nsfwScore)
                            )
                            DetailDivider()
                            val matchScoreVal = selectedRecord.matchScore
                            DetailRow(
                                icon = Icons.Default.Star,
                                iconBgColor = Color(0xFFF5F3FF),
                                iconTintColor = Color(0xFF8B5CF6),
                                label = "Match Score",
                                value = if (matchScoreVal != null && matchScoreVal >= 0f) String.format("%.8f", matchScoreVal) else "N/A"
                            )
                            DetailDivider()
                            DetailRow(
                                icon = Icons.Default.DateRange,
                                iconBgColor = Color(0xFFF0FDF4),
                                iconTintColor = Color(0xFF16A34A),
                                label = "Time",
                                value = selectedRecord.timestamp
                            )
                        }
                    }
                }

                // Card 3: Footer Warning Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFEEF2FF))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Scores Note",
                                tint = Color(0xFF4F46E5),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Scores indicate the confidence and security of the attendance verification.",
                                color = Color(0xFF4F46E5),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DetailRow(
    icon: ImageVector,
    iconBgColor: Color,
    iconTintColor: Color,
    label: String,
    value: String,
    isSuccessStatus: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(iconBgColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconTintColor,
                modifier = Modifier.size(16.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = label,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            color = Color(0xFF64748B),
            modifier = Modifier.width(90.dp)
        )

        Text(
            text = value,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = if (isSuccessStatus) Color(0xFF15803D) else Color(0xFF0F172A)
        )
    }
}

@Composable
fun DetailDivider() {
    Divider(
        color = Color(0xFFF1F5F9),
        thickness = 1.dp,
        modifier = Modifier.fillMaxWidth()
    )
}

// Simple Helper extension to create border outlines easily
@Stable
fun BoxBorder(width: androidx.compose.ui.unit.Dp, color: Color): androidx.compose.foundation.BorderStroke {
    return androidx.compose.foundation.BorderStroke(width, color)
}