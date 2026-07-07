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

import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight

@Composable
fun EmployeeDetailsScreen(
    onBack: () -> Unit
) {

    val employees =
        EmployeeRepository.getAllEmployees()

    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {

        Button(
            onClick = onBack,
            modifier = Modifier.align(Alignment.Start),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        ) {
            Text("< Back")
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Employee Details",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn {

            items(employees) { employee ->

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
                                employee.imagePath
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

                        Text("ID: ${employee.employeeId}")
                        Text("Name: ${employee.employeeName}")
                    }
                }
            }
        }
    }
}