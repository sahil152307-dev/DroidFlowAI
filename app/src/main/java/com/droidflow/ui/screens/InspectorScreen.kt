package com.droidflow.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.droidflow.R
import com.droidflow.accessibility.ServiceCheck
import com.droidflow.logging.formatTime
import com.droidflow.models.UIElement
import com.droidflow.ui.InspectorViewModel
import com.droidflow.ui.theme.DangerRed
import com.droidflow.ui.theme.ExecBg
import com.droidflow.ui.theme.ExecBorder
import com.droidflow.ui.theme.ExecCard
import com.droidflow.ui.theme.ExecText
import com.droidflow.ui.theme.ExecTextDim
import com.droidflow.ui.theme.InfoCyan
import com.droidflow.ui.theme.SuccessGreen
import com.droidflow.ui.theme.WarnAmber

/**
 * Phase-2 proof tool. Demonstrates on a real device that DroidFlow can:
 *   read the live accessibility tree → semantically match a target →
 *   act on it → re-observe → verify the state changed.
 */
@Composable
fun InspectorScreen(onBack: () -> Unit) {
    val vm: InspectorViewModel = viewModel()
    val ui = vm.ui
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ExecBg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = onBack,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ExecTextDim)
            ) { Text("←") }
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(R.string.inspector_title).uppercase(),
                color = ExecText,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.inspector_sub),
            color = ExecTextDim,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace
        )

        Spacer(Modifier.height(14.dp))

        // Service status
        InspectorCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(10.dp)
                        .background(if (ui.connected) SuccessGreen else DangerRed, CircleShape)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(
                        if (ui.connected) R.string.a11y_status_on else R.string.a11y_status_off
                    ),
                    color = if (ui.connected) SuccessGreen else DangerRed,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "${stringResource(R.string.inspector_foreground)}: ${ui.foreground ?: "—"}",
                color = ExecTextDim,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodyMedium
            )
            if (!ui.connected) {
                Spacer(Modifier.height(10.dp))
                Button(onClick = { ServiceCheck.openAccessibilitySettings(context) }) {
                    Text(stringResource(R.string.inspector_enable))
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Capture controls
        InspectorCard {
            Text(
                stringResource(R.string.inspector_capture).uppercase(),
                color = ExecTextDim,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { vm.captureNow() },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = InfoCyan)
                ) { Text(stringResource(R.string.inspector_capture_now)) }
            }
            Spacer(Modifier.height(6.dp))
            OutlinedButton(
                onClick = { vm.captureDelayed() },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = WarnAmber),
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.inspector_capture_delayed)) }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { vm.globalBack() },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ExecTextDim)
                ) { Text(stringResource(R.string.inspector_back)) }
                OutlinedButton(
                    onClick = { vm.globalHome() },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ExecTextDim)
                ) { Text(stringResource(R.string.inspector_home)) }
            }
            if (ui.countdown != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.inspector_countdown, ui.countdown ?: 0),
                    color = WarnAmber,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Snapshot
        val snap = ui.snapshot
        InspectorCard {
            Text(
                stringResource(R.string.inspector_snapshot).uppercase(),
                color = ExecTextDim,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.labelSmall
            )
            if (snap == null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "—",
                    color = ExecTextDim,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Spacer(Modifier.height(6.dp))
                Text(
                    "${snap.state.packageName}",
                    color = InfoCyan,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "${stringResource(R.string.inspector_screen_guess)}: ${snap.state.screen}  ·  ${stringResource(R.string.inspector_elements, snap.state.elements.size)}",
                    color = ExecTextDim,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (snap.isSelf) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.inspector_self_note),
                        color = WarnAmber,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Spacer(Modifier.height(8.dp))
                Column {
                    snap.state.elements.forEach { el -> ElementRow(el) }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Semantic match test
        InspectorCard {
            Text(
                stringResource(R.string.inspector_match).uppercase(),
                color = ExecTextDim,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = vm.query,
                onValueChange = { vm.query = it },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.inspector_query_hint), color = ExecTextDim) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = ExecText,
                    unfocusedTextColor = ExecText,
                    cursorColor = InfoCyan,
                    focusedBorderColor = InfoCyan,
                    unfocusedBorderColor = ExecBorder,
                    focusedPlaceholderColor = ExecTextDim,
                    unfocusedPlaceholderColor = ExecTextDim
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.findMatch() }) {
                    Text(stringResource(R.string.inspector_find))
                }
                OutlinedButton(
                    onClick = { vm.tapMatchNow() },
                    enabled = vm.matchResult?.best != null,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SuccessGreen)
                ) { Text(stringResource(R.string.inspector_tap_now)) }
            }
            Spacer(Modifier.height(6.dp))
            OutlinedButton(
                onClick = { vm.tapMatchDelayed() },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = WarnAmber),
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.inspector_tap_delayed)) }

            val res = vm.matchResult
            if (res != null) {
                Spacer(Modifier.height(8.dp))
                when {
                    res.best == null -> Text(
                        stringResource(R.string.inspector_no_match),
                        color = DangerRed,
                        fontFamily = FontFamily.Monospace
                    )
                    res.ambiguous -> Text(
                        stringResource(R.string.inspector_ambiguous),
                        color = WarnAmber,
                        fontFamily = FontFamily.Monospace
                    )
                    else -> Text(
                        stringResource(
                            R.string.inspector_matched,
                            res.label ?: "?",
                            "%.2f".format(res.best.score)
                        ),
                        color = SuccessGreen,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Event log
        InspectorCard {
            Text(
                stringResource(R.string.inspector_log).uppercase(),
                color = ExecTextDim,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(Modifier.height(8.dp))
            val listState = rememberLazyListState()
            LaunchedEffect(vm.log.size) {
                if (vm.log.isNotEmpty()) listState.animateScrollToItem(vm.log.size - 1)
            }
            LazyColumn(state = listState, modifier = Modifier.height(180.dp)) {
                items(vm.log) { (time, message) ->
                    Row(modifier = Modifier.padding(vertical = 2.dp)) {
                        Text(
                            formatTime(time),
                            color = ExecTextDim,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.labelSmall
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            message,
                            color = ExecText,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InspectorCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ExecCard, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        content()
    }
}

@Composable
private fun ElementRow(el: UIElement) {
    val typeColor = when {
        el.editable -> InfoCyan
        el.clickable -> SuccessGreen
        else -> ExecTextDim
    }
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            el.id,
            color = ExecTextDim,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.labelSmall
        )
        Spacer(Modifier.width(8.dp))
        Text(
            el.type,
            color = typeColor,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.labelSmall
        )
        Spacer(Modifier.width(8.dp))
        val label = el.text ?: el.contentDescription ?: el.hint
        if (label != null) {
            Text(
                "\"$label\"",
                color = ExecText,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.labelSmall
            )
        }
        if (el.clickable) {
            Spacer(Modifier.width(6.dp))
            Text(
                "·click",
                color = SuccessGreen,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.labelSmall
            )
        }
        if (el.editable) {
            Spacer(Modifier.width(6.dp))
            Text(
                "·edit",
                color = InfoCyan,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.labelSmall
            )
        }
        if (!el.enabled) {
            Spacer(Modifier.width(6.dp))
            Text(
                "·off",
                color = DangerRed,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
