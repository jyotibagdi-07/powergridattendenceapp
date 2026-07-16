package com.example.powergridattendance

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun RegisterFaceScreen(
    onCaptureClick: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    var employeeId by remember { mutableStateOf("") }
    var employeeName by remember { mutableStateOf("") }

    val scrollState = rememberScrollState()

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
            verticalAlignment = Alignment.CenterVertically
        ) {
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
                text = "Register Employee Face",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = Color(0xFF0F172A)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Card 1: Illustration Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F3FF))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Illustration Image
                    Image(
                        painter = painterResource(id = R.drawable.face_reg_illustration),
                        contentDescription = "Face Registration Scan Illustration",
                        modifier = Modifier
                            .size(160.dp)
                            .clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Fit
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Face Registration",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color(0xFF0F172A)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Capture employee face for secure biometric attendance.",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }

            // Card 2: Form Input Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    // Employee ID Field
                    Text(
                        text = "Employee ID",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = employeeId,
                        onValueChange = { employeeId = it },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF8B5CF6),
                            unfocusedBorderColor = Color(0xFFE2E8F0),
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .padding(start = 8.dp, end = 4.dp)
                                    .size(32.dp)
                                    .background(Color(0xFFEEF2FF), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccountBox,
                                    contentDescription = null,
                                    tint = Color(0xFF4F46E5),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        placeholder = {
                            Text(
                                text = "Enter employee ID",
                                color = Color(0xFF94A3B8),
                                fontSize = 14.sp
                            )
                        },
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Employee Name Field
                    Text(
                        text = "Employee Name",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = employeeName,
                        onValueChange = { employeeName = it },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF8B5CF6),
                            unfocusedBorderColor = Color(0xFFE2E8F0),
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .padding(start = 8.dp, end = 4.dp)
                                    .size(32.dp)
                                    .background(Color(0xFFEEF2FF), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = Color(0xFF4F46E5),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        placeholder = {
                            Text(
                                text = "Enter employee name",
                                color = Color(0xFF94A3B8),
                                fontSize = 14.sp
                            )
                        },
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Capture Face Button
                    Button(
                        onClick = {
                            if (employeeId.isNotBlank() && employeeName.isNotBlank()) {
                                val trimmedId = employeeId.trim()
                                if (EmployeeRepository.getEmployeeById(trimmedId) != null) {
                                    Toast.makeText(
                                        context,
                                        "Employee ID already registered!",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    return@Button
                                }

                                CurrentEmployee.employeeId = trimmedId
                                CurrentEmployee.employeeName = employeeName
                                CurrentEmployee.capturedFileName = "${trimmedId}_face.jpg"
                                CurrentEmployee.isRegisterMode = true

                                Toast.makeText(
                                    context,
                                    "Open camera and capture face",
                                    Toast.LENGTH_SHORT
                                ).show()

                                onCaptureClick()
                            } else {
                                Toast.makeText(
                                    context,
                                    "Enter Employee Details",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Face,
                                contentDescription = "Capture",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Capture Face",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Card 3: Tips for best results
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEEF2FF))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Shield Icon",
                            tint = Color(0xFF4F46E5),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Tips for best results",
                            color = Color(0xFF4F46E5),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TipItem(text = "Ensure good lighting on your face")
                        TipItem(text = "Look directly at the camera")
                        TipItem(text = "Remove glasses, mask or headgear")
                    }
                }
            }
        }
    }
}

@Composable
fun TipItem(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = "Tip Checked",
            tint = Color(0xFF4F46E5),
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = text,
            color = Color(0xFF475569),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}