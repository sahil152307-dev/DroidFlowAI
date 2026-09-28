package com.droidflow.ui.screens

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.droidflow.R
import com.droidflow.agent.IntentEngine
import com.droidflow.data.SettingsStore
import com.droidflow.ui.AgentViewModel
import com.droidflow.ui.theme.Violet
import com.droidflow.ui.theme.VioletDark
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: AgentViewModel,
    onTaskParsed: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenInspector: () -> Unit
) {
    val context = LocalContext.current
    val settings = remember { SettingsStore(context) }
    val scope = rememberCoroutineScope()
    val languageTag by settings.languageTag.collectAsState(initial = "en-IN")

    var input by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf(false) }
    var parsing by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }

    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val text = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
        if (!text.isNullOrBlank()) {
            input = text
            error = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            try {
                voiceLauncher.launch(speechIntent(languageTag))
            } catch (_: ActivityNotFoundException) {
                toast = context.getString(R.string.voice_unavailable)
            }
        } else {
            toast = context.getString(R.string.voice_error)
        }
    }

    LaunchedEffect(toast) {
        if (toast != null) {
            kotlinx.coroutines.delay(2500)
            toast = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(listOf(Violet, VioletDark)),
                    RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
                )
                .padding(24.dp)
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White
                        )
                        Text(
                            stringResource(R.string.tagline),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFDDDBFF)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0x33FFFFFF), CircleShape)
                            .clickable { onOpenSettings() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("⚙", color = Color.White)
                    }
                }
                Spacer(Modifier.height(20.dp))
                if (toast != null) {
                    Text(
                        toast!!,
                        color = Color(0xFFFFF3C4),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        Column(Modifier.padding(20.dp)) {
            // Input card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it; error = false },
                        placeholder = { Text(stringResource(R.string.input_hint)) },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Voice button
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .background(Violet, CircleShape)
                                .clickable {
                                    val granted = ContextCompat.checkSelfPermission(
                                        context, Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (granted) {
                                        try {
                                            voiceLauncher.launch(speechIntent(languageTag))
                                        } catch (_: ActivityNotFoundException) {
                                            toast = context.getString(R.string.voice_unavailable)
                                        }
                                    } else {
                                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🎤", color = Color.White)
                        }
                        Spacer(Modifier.width(12.dp))
                        Button(
                            onClick = {
                                if (parsing) return@Button
                                parsing = true
                                error = false
                                scope.launch {
                                    val apiKey = settings.geminiApiKey.first()
                                    val task = IntentEngine.parse(input, apiKey)
                                    parsing = false
                                    if (task == null) {
                                        error = true
                                    } else {
                                        vm.prepareTask(task)
                                        onTaskParsed()
                                    }
                                }
                            },
                            enabled = input.isNotBlank() && !parsing,
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                        ) {
                            Text(
                                if (parsing) stringResource(R.string.parsing_lbl)
                                else stringResource(R.string.run_task)
                            )
                        }
                    }
                    if (error) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.task_unrecognized),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Suggested tasks
            Text(
                stringResource(R.string.suggested_tasks),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(10.dp))
            Suggestion(
                label = stringResource(R.string.sugg_compare),
                example = "Compare cab prices on RideNow and SwiftRide to the airport and open the cheaper one",
                onPick = { input = it; error = false }
            )
            Spacer(Modifier.height(8.dp))
            Suggestion(
                label = stringResource(R.string.sugg_find),
                example = "Find the price of wireless headphones on MockMart",
                onPick = { input = it; error = false }
            )
            Spacer(Modifier.height(8.dp))
            Suggestion(
                label = stringResource(R.string.sugg_form),
                example = "Open the QuickForm registration form and fill in my name and email",
                onPick = { input = it; error = false }
            )

            Spacer(Modifier.height(24.dp))
            Text(
                "Built on Android Accessibility — no-code, no per-app integrations",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            TextButton(
                onClick = onOpenInspector,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.dev_inspector))
            }
        }
    }
}

@Composable
private fun Suggestion(label: String, example: String, onPick: (String) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPick(example) }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            Text("▸", color = Violet)
        }
    }
}

private fun speechIntent(languageTag: String): Intent =
    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
        putExtra(RecognizerIntent.EXTRA_PROMPT, "DroidFlow is listening…")
    }
