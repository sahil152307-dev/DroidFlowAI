package com.droidflow.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.droidflow.R
import com.droidflow.logging.AgentLogEvent
import com.droidflow.logging.EventKind
import com.droidflow.logging.formatTime
import com.droidflow.models.GoalType
import com.droidflow.ui.AgentPhase
import com.droidflow.ui.AgentViewModel
import com.droidflow.ui.theme.DangerRed
import com.droidflow.ui.theme.ExecBg
import com.droidflow.ui.theme.ExecBorder
import com.droidflow.ui.theme.ExecCard
import com.droidflow.ui.theme.ExecText
import com.droidflow.ui.theme.ExecTextDim
import com.droidflow.ui.theme.InfoCyan
import com.droidflow.ui.theme.PhaseViolet
import com.droidflow.ui.theme.SuccessGreen
import com.droidflow.ui.theme.WarnAmber

/**
 * Screen 3 (PRD §11–12): the judge-facing Live Agent View.
 * Makes the agentic loop VISIBLE: Observing → Planning → Acting →
 * Verifying → Verified, plus the safety boundary pause.
 */
@Composable
fun AgentExecutionScreen(vm: AgentViewModel) {
    val ui = vm.ui
    val events by vm.logger.events.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ExecBg)
            .padding(16.dp)
    ) {
        // Top bar
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "DROIDFLOW AGENT",
                color = ExecText,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.weight(1f))
            OutlinedButton(
                onClick = { vm.cancelTask() },
                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                    contentColor = ExecTextDim
                )
            ) {
                Text(stringResource(R.string.cancel_task), fontFamily = FontFamily.Monospace)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.live_banner),
            color = SuccessGreen,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace
        )

        Spacer(Modifier.height(14.dp))

        // Current task
        ExecCard {
            Text(
                stringResource(R.string.current_task),
                color = ExecTextDim,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.height(4.dp))
            Text(
                taskTitle(ui.task?.goalType),
                color = ExecText,
                style = MaterialTheme.typography.titleMedium
            )
            if (ui.task?.entities?.get("destination") != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    "→ ${ui.task.entities["destination"]}",
                    color = InfoCyan,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // Live phase panel
        PhasePanel(phase = ui.phase, detail = ui.detail)

        Spacer(Modifier.height(14.dp))

        // Collected fares
        if (ui.fares.isNotEmpty()) {
            ExecCard {
                Text(
                    stringResource(R.string.fares_collected).uppercase(),
                    color = ExecTextDim,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(Modifier.height(8.dp))
                ui.fares.forEach { fare ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "✓ ${fare.appLabel} · ${fare.itemLabel}",
                            color = ExecText,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "₹${fare.price}",
                            color = SuccessGreen,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        // Execution timeline
        ExecCard(modifier = Modifier.weight(1f)) {
            Text(
                "EXECUTION TIMELINE",
                color = ExecTextDim,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.height(8.dp))
            val listState = rememberLazyListState()
            LaunchedEffect(events.size) {
                if (events.isNotEmpty()) listState.animateScrollToItem(events.size - 1)
            }
            LazyColumn(state = listState) {
                items(items = events) { event ->
                    TimelineRow(event)
                }
            }
        }
    }

    // Safety boundary confirmation
    if (ui.awaitingConfirmation) {
        AlertDialog(
            onDismissRequest = { vm.declineBoundary() },
            title = {
                Text("⚠️ ${ui.confirmationTitle}", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(ui.confirmationBody, style = MaterialTheme.typography.bodyMedium)
            },
            confirmButton = {
                Button(onClick = { vm.confirmBoundary() }) {
                    Text(stringResource(R.string.confirm_btn))
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.declineBoundary() }) {
                    Text(stringResource(R.string.cancel_btn))
                }
            }
        )
    }
}

@Composable
private fun ExecCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ExecCard, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        content()
    }
}

@Composable
private fun PhasePanel(phase: AgentPhase, detail: String) {
    val (label, color, symbol) = phaseDisplay(phase)

    // Pulsing status dot
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulseAlpha"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ExecCard, RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .graphicsLayer { this.alpha = alpha }
                    .background(color, CircleShape)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                symbol + label,
                color = color,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.titleMedium
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            detail,
            color = ExecTextDim,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun phaseDisplay(phase: AgentPhase): Triple<String, Color, String> = when (phase) {
    AgentPhase.UNDERSTAND -> Triple(stringResource(R.string.phase_understanding), PhaseViolet, "◍ ")
    AgentPhase.OBSERVE -> Triple(stringResource(R.string.phase_observing), InfoCyan, "● ")
    AgentPhase.PLAN -> Triple(stringResource(R.string.phase_planning), PhaseViolet, "◆ ")
    AgentPhase.ACT -> Triple(stringResource(R.string.phase_acting), WarnAmber, "▶ ")
    AgentPhase.VERIFY -> Triple(stringResource(R.string.phase_verifying), InfoCyan, "◎ ")
    AgentPhase.VERIFIED -> Triple(stringResource(R.string.phase_verified), SuccessGreen, "✓ ")
    AgentPhase.SAFETY -> Triple(stringResource(R.string.phase_safety), DangerRed, "⏸ ")
    AgentPhase.DONE -> Triple(stringResource(R.string.phase_done), SuccessGreen, "★ ")
}

@Composable
private fun TimelineRow(event: AgentLogEvent) {
    TimelineRowImpl(event.timeMs, event.message, event.kind)
}

@Composable
private fun TimelineRowImpl(timeMs: Long, message: String, kind: EventKind) {
    val color = when (kind) {
        EventKind.INFO -> ExecTextDim
        EventKind.ACTION -> InfoCyan
        EventKind.VERIFY -> PhaseViolet
        EventKind.WARN -> WarnAmber
        EventKind.SAFETY -> DangerRed
        EventKind.SUCCESS -> SuccessGreen
        EventKind.ERROR -> DangerRed
    }
    Row(modifier = Modifier.padding(vertical = 3.dp)) {
        Text(
            formatTime(timeMs),
            color = ExecTextDim,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.labelSmall
        )
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .size(3.dp, 14.dp)
                .background(color, RoundedCornerShape(2.dp))
                .align(Alignment.CenterVertically)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            message,
            color = if (kind == EventKind.INFO) ExecTextDim else ExecText,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun taskTitle(goal: GoalType?): String = when (goal) {
    GoalType.COMPARE_PRICES -> "Compare cab prices & open cheaper"
    GoalType.FIND_PRICE -> "Find product price"
    GoalType.FILL_FORM -> "Fill registration form"
    else -> "Task"
}
