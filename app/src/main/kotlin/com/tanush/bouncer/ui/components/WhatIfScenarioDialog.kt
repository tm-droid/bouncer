package com.tanush.bouncer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.tanush.bouncer.ui.theme.*

@Composable
fun WhatIfScenarioDialog(
    onSimulate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(GlassSurface)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Simulate Next Ball", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { onSimulate("WICKET") }, modifier = Modifier.fillMaxWidth()) {
                    Text("Wicket")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { onSimulate("BOUNDARY") }, modifier = Modifier.fillMaxWidth()) {
                    Text("Boundary (4/6)")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { onSimulate("DOT_BALL") }, modifier = Modifier.fillMaxWidth()) {
                    Text("Dot Ball")
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancel")
                }
            }
        }
    }
}
