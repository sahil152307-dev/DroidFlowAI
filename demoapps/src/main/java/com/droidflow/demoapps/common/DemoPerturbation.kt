package com.droidflow.demoapps.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * Demo perturbation system (TRD §38 "UI Variation Tests").
 *
 * Hidden control: LONG-PRESS the app header to open the demo sheet.
 * Toggles inject real UI changes the agent must recover from:
 *  - popupOnResults : an unexpected popup dialog
 *  - renameButton   : button text changes (semantic matching test)
 *  - slowResults    : slow loading screen
 *  - layoutShift    : element order changes
 *
 * Nothing here is visible during a normal demo run.
 */
data class PerturbationState(
    val popupOnResults: Boolean = false,
    val renameButton: Boolean = false,
    val slowResults: Boolean = false,
    val layoutShift: Boolean = false
)

@Composable
fun DemoControlSheet(
    visible: Boolean,
    appName: String,
    state: PerturbationState,
    onToggle: (PerturbationState) -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return
    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    "$appName · demo control",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.padding(4.dp))
                Text(
                    "Perturbations apply to the next run — used to prove the agent recovers instead of replaying coordinates.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.padding(8.dp))

                ToggleRow("Unexpected popup", state.popupOnResults) {
                    onToggle(state.copy(popupOnResults = it))
                }
                ToggleRow("Rename button text", state.renameButton) {
                    onToggle(state.copy(renameButton = it))
                }
                ToggleRow("Slow screen load", state.slowResults) {
                    onToggle(state.copy(slowResults = it))
                }
                ToggleRow("Layout shift", state.layoutShift) {
                    onToggle(state.copy(layoutShift = it))
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

/** App header; long-press anywhere on it opens the hidden demo control sheet. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DemoAppHeader(
    appName: String,
    primary: Color,
    onRequestDemoControl: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = onRequestDemoControl
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(primary, CircleShape)
        )
        Spacer(Modifier.padding(8.dp))
        Text(appName, style = MaterialTheme.typography.titleLarge)
    }
}
