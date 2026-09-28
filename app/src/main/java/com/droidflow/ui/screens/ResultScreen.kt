package com.droidflow.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.droidflow.R
import com.droidflow.models.OutcomeStatus
import com.droidflow.ui.AgentViewModel
import com.droidflow.ui.theme.DangerRed
import com.droidflow.ui.theme.ExecBg
import com.droidflow.ui.theme.ExecCard
import com.droidflow.ui.theme.ExecText
import com.droidflow.ui.theme.ExecTextDim
import com.droidflow.ui.theme.SuccessGreen
import com.droidflow.ui.theme.WarnAmber

/** Screen 4: outcome, comparison table, safety note and honest metrics. */
@Composable
fun ResultScreen(
    vm: AgentViewModel,
    onRunAgain: () -> Unit,
    onNewTask: () -> Unit
) {
    val outcome = vm.outcome
    val ui = vm.ui

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        if (outcome == null) {
            Text(stringResource(R.string.no_task), style = MaterialTheme.typography.bodyMedium)
            return@Column
        }

        // Status header
        val (symbol, color, title) = when (outcome.status) {
            OutcomeStatus.COMPLETED -> Triple("✓", SuccessGreen, stringResource(R.string.status_completed))
            OutcomeStatus.SAFETY_STOP -> Triple("⏸", WarnAmber, stringResource(R.string.status_safety_stop))
            OutcomeStatus.CANCELLED -> Triple("✕", DangerRed, stringResource(R.string.status_cancelled))
            OutcomeStatus.FAILED -> Triple("!", DangerRed, "Failed safely")
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(symbol, style = MaterialTheme.typography.displaySmall, color = color)
                Spacer(Modifier.height(4.dp))
                Text(title, style = MaterialTheme.typography.titleLarge)
            }
        }

        Spacer(Modifier.height(16.dp))

        // Comparison / collected values
        if (outcome.fares.isNotEmpty()) {
            Text(
                stringResource(R.string.comparison),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    outcome.fares.forEach { fare ->
                        val chosen = outcome.chosenApp == fare.appLabel
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .background(
                                    if (chosen) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                (if (chosen) "★ " else "") + fare.appLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal
                            )
                            Text(
                                "₹${fare.price}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    outcome.savings?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "${outcome.chosenApp} is cheaper by ₹$it",
                            color = SuccessGreen,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // Safety note
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Text(
                "🛡 ${outcome.note ?: stringResource(R.string.safety_note_hero)}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp)
            )
        }

        Spacer(Modifier.height(16.dp))

        // Honest metrics (measured, never claimed)
        Text(stringResource(R.string.metrics_label), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricChip(stringResource(R.string.steps_lbl), "${outcome.metrics.events}")
            MetricChip(stringResource(R.string.actions_lbl), "${outcome.metrics.actions}")
            MetricChip(
                stringResource(R.string.duration_lbl),
                "%.1fs".format(outcome.metrics.durationMs / 1000.0)
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.live_banner),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                vm.ui.task?.let { vm.startTask(it) }
                onRunAgain()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text(stringResource(R.string.run_again))
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onNewTask,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text(stringResource(R.string.new_task))
        }
    }
}

@Composable
private fun MetricChip(label: String, value: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
