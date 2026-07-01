package com.example.powergridattendance

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp

@Composable
fun ResultScreen(
    onBack: () -> Unit
) {

    val bitmap =
        CaptureResultState.capturedImage.value

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        Text("Capture Result")

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {

                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(250.dp)
                            .clickable {
                                CaptureResultState.showResult.value = false
                                onBack()
                            }
                    )
                }

                Spacer(
                    modifier = Modifier.height(12.dp)
                )

                Text(
                    "Recognized: ${
                        CaptureResultState.recognizedName.value
                    }"
                )

                Text(
                    "Status: ${
                        CaptureResultState.status.value
                    }"
                )

                Text(
                    "Spoof Score: ${
                        CaptureResultState.spoofScore.value
                    }"
                )

                Text(
                    "Blur Score: ${
                        CaptureResultState.blurScore.value
                    }"
                )

                Text(
                    "NSFW Score: ${
                        CaptureResultState.nsfwScore.value
                    }"
                )

                Text(
                    "Match Score: ${
                        CaptureResultState.matchScore.value
                    }"
                )
            }
        }

        Spacer(
            modifier = Modifier.height(20.dp)
        )

        Button(
            onClick = {
                CaptureResultState.showResult.value =
                    false
                onBack()
            }
        ) {
            Text("Back to Dashboard")
        }
    }
}