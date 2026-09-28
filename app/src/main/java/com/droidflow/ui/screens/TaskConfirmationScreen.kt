package com.droidflow.ui.screens

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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.droidflow.R
import com.droidflow.models.GoalType
import com.droidflow.models.RiskLevel
import com.droidflow.models.Task
import com.droidflow.ui.AgentViewModel
import com.droidflow.ui.theme.SuccessGreen
import com.droidflow.ui.theme.WarnAmber
import com.droidflow.ui.theme.DangerRed

/**
 * Screen 2 (PRD §10): the parsed goal is shown BEFORE any action is taken,
 * so the agent never silently acts on a misread request.
 */
@Composable
fun TaskConfirmationScreen(
    vm: AgentViewModel,
    onStart: () -> Unit,
    onEdit: () -> Unit
) {
    val task = vm.pendingTask

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(stringResource(R.string.your_task), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.control_note),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))

        if (task == null) {
            Card {
                Text(
                    stringResource(R.string.no_task),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            TaskCard(task)
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { onStart() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(stringResource(R.string.start_task))
            }
            TextButton(
                onClick = onEdit,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.edit_task))
            }
        }
    }
}

@Composable
private fun TaskCard(task: Task) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            InfoRow(stringResource(R.string.goal_lbl), prettyGoal(task.goalType))
            Spacer(Modifier.height(10.dp))

            InfoRow(stringResource(R.string.apps_lbl), task.apps.joinToString(" + "))
            Spacer(Modifier.height(10.dp))

            task.entities.forEach { (key, value) ->
                InfoRow(entityLabel(key), value)
                Spacer(Modifier.height(10.dp))
            }

            task.finalAction?.let {
                InfoRow(stringResource(R.string.action_lbl), prettyAction(it))
                Spacer(Modifier.height(10.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    stringResource(R.string.risk_lbl),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                RiskBadge(task.riskLevel)
            }

            if (task.stopConditions.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        task.stopConditions.forEach { stop ->
                            Text(
                                "⛔ $stop",
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
fun RiskBadge(level: RiskLevel) {
    val (labelRes, color) = when (level) {
        RiskLevel.LOW -> R.string.risk_low to SuccessGreen
        RiskLevel.MEDIUM -> R.string.risk_medium to WarnAmber
        RiskLevel.HIGH -> R.string.risk_high to DangerRed
    }
    Text(
        stringResource(labelRes),
        color = color,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.bodyMedium
    )
}

@Composable
private fun entityLabel(key: String): String = when (key) {
    "destination" -> stringResource(R.string.destination_lbl)
    "query" -> stringResource(R.string.query_lbl)
    "name" -> "Name"
    "email" -> "Email"
    else -> key
}

private fun prettyGoal(goal: GoalType): String = when (goal) {
    GoalType.COMPARE_PRICES -> "Compare cab prices"
    GoalType.FIND_PRICE -> "Find a price"
    GoalType.FILL_FORM -> "Fill a form"
    GoalType.UNKNOWN -> "Unknown"
}

private fun prettyAction(action: String): String = when (action) {
    "open_cheaper_option" -> "Open the cheaper option"
    "report_price" -> "Report the price to you"
    "fill_fields_then_pause_before_submit" -> "Fill fields, pause before submit"
    else -> action
}
