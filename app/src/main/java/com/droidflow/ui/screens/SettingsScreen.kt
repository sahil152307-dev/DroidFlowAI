package com.droidflow.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.droidflow.R
import com.droidflow.accessibility.ServiceCheck
import com.droidflow.data.SettingsStore
import kotlinx.coroutines.launch

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { SettingsStore(context) }
    val scope = rememberCoroutineScope()

    val apiKey by settings.geminiApiKey.collectAsState(initial = "")
    val languageTag by settings.languageTag.collectAsState(initial = "en-IN")
    val confHigh by settings.confidenceHigh.collectAsState(initial = 0.75f)
    val confLow by settings.confidenceLow.collectAsState(initial = 0.45f)
    val maxSteps by settings.maxSteps.collectAsState(initial = 20)
    val plannerMode by settings.plannerMode.collectAsState(initial = "auto")

    var keyInput by remember(apiKey) { mutableStateOf(apiKey) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Row {
            Button(onClick = onBack) { Text("←") }
            Spacer(Modifier.padding(4.dp))
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge)
        }

        Spacer(Modifier.height(20.dp))

        // Gemini API key
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.api_key_lbl), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    placeholder = { Text("AIza…") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row {
                    Button(onClick = {
                        scope.launch { settings.setGeminiApiKey(keyInput) }
                    }) { Text("Save") }
                    Spacer(Modifier.padding(4.dp))
                    if (apiKey.isNotBlank() && keyInput == apiKey) {
                        Text(
                            "✓",
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.api_key_help),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Voice / input language
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.language_lbl), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val languages = listOf(
                        "en-IN" to R.string.lang_en,
                        "hi-IN" to R.string.lang_hi,
                        "mr-IN" to R.string.lang_mr,
                        "gu-IN" to R.string.lang_gu,
                        "bn-IN" to R.string.lang_bn,
                        "ta-IN" to R.string.lang_ta,
                        "te-IN" to R.string.lang_te,
                        "kn-IN" to R.string.lang_kn
                    )
                    items(languages) { (tag, labelRes) ->
                        FilterChip(
                            selected = languageTag == tag,
                            onClick = { scope.launch { settings.setLanguageTag(tag) } },
                            label = { Text(stringResource(labelRes)) }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Planner mode
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.planner_lbl), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row {
                    FilterChip(
                        selected = plannerMode == "auto",
                        onClick = { scope.launch { settings.setPlannerMode("auto") } },
                        label = { Text(stringResource(R.string.planner_auto)) }
                    )
                    Spacer(Modifier.padding(4.dp))
                    FilterChip(
                        selected = plannerMode == "ai",
                        onClick = { scope.launch { settings.setPlannerMode("ai") } },
                        label = { Text(stringResource(R.string.planner_ai)) }
                    )
                    Spacer(Modifier.padding(4.dp))
                    FilterChip(
                        selected = plannerMode == "scripted",
                        onClick = { scope.launch { settings.setPlannerMode("scripted") } },
                        label = { Text(stringResource(R.string.planner_scripted)) }
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.planner_mode_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Planner thresholds
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.planner_lbl), style = MaterialTheme.typography.titleMedium)

                Spacer(Modifier.height(10.dp))
                Text(
                    "${stringResource(R.string.conf_high_lbl)}: ${"%.2f".format(confHigh)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = confHigh,
                    onValueChange = { scope.launch { settings.setConfidenceHigh(it) } },
                    valueRange = 0.5f..0.95f
                )

                Text(
                    "${stringResource(R.string.conf_low_lbl)}: ${"%.2f".format(confLow)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = confLow,
                    onValueChange = { scope.launch { settings.setConfidenceLow(it) } },
                    valueRange = 0.2f..0.6f
                )

                Spacer(Modifier.height(6.dp))
                Text(
                    "${stringResource(R.string.max_steps_lbl)}: $maxSteps",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = maxSteps.toFloat(),
                    onValueChange = { scope.launch { settings.setMaxSteps(it.toInt()) } },
                    valueRange = 5f..40f,
                    steps = 34
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // About
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.about_lbl), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.about_body),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (ServiceCheck.isAccessibilityServiceEnabled(context))
                        stringResource(R.string.a11y_status_on)
                    else stringResource(R.string.a11y_status_off),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
