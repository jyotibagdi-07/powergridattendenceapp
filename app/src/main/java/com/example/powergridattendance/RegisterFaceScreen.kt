package com.example.powergridattendance

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun RegisterFaceScreen(
    onCaptureClick: () -> Unit
) {

    val context = LocalContext.current

    var employeeId by remember {
        mutableStateOf("")
    }

    var employeeName by remember {
        mutableStateOf("")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            text = "Register Employee Face",
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(20.dp))

        OutlinedTextField(
            value = employeeId,
            onValueChange = {
                employeeId = it
            },
            label = {
                Text("Employee ID")
            }
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = employeeName,
            onValueChange = {
                employeeName = it
            },
            label = {
                Text("Employee Name")
            }
        )

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {

                if (
                    employeeId.isNotBlank() &&
                    employeeName.isNotBlank()
                ) {
                    val trimmedId = employeeId.trim()
                    if (EmployeeRepository.getEmployeeById(trimmedId) != null) {
                        Toast.makeText(
                            context,
                            "Employee ID already registered!",
                            Toast.LENGTH_LONG
                        ).show()
                        return@Button
                    }

                    CurrentEmployee.employeeId =
                        trimmedId

                    CurrentEmployee.employeeName =
                        employeeName

                    CurrentEmployee.capturedFileName =
                        "${trimmedId}_face.jpg"

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
            }
        ) {
            Text("Capture Face")
        }
    }
}