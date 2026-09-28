package com.droidflow.logging

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Event categories shown with distinct colors on the Live Agent View. */
enum class EventKind { INFO, ACTION, VERIFY, WARN, SAFETY, SUCCESS, ERROR }

data class AgentLogEvent(
    val timeMs: Long,
    val message: String,
    val kind: EventKind
)

/**
 * Structured execution logger (TRD §27).
 * - Timestamp, app, action, confidence, outcome are recorded.
 * - Secrets are NEVER logged; callers must pass redacted values
 *   (SensitiveDataFilter enforces this from Phase 7).
 * - Persists one JSON-lines file per task in app-private storage.
 */
class AgentLogger(private val context: Context) {

    private val _events = MutableStateFlow<List<AgentLogEvent>>(emptyList())
    val events: StateFlow<List<AgentLogEvent>> = _events

    private var taskId: String = "task"

    fun beginTask(id: String) {
        taskId = id
    }

    fun clear() {
        _events.value = emptyList()
    }

    fun log(kind: EventKind, message: String) {
        val event = AgentLogEvent(System.currentTimeMillis(), message, kind)
        _events.value = _events.value + event
        persist(event)
    }

    val eventCount: Int get() = _events.value.size
    val actionCount: Int get() = _events.value.count { it.kind == EventKind.ACTION }

    private fun persist(event: AgentLogEvent) {
        try {
            val dir = File(context.filesDir, "logs").apply { mkdirs() }
            val safeMsg = event.message
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", " ")
            val line = "{\"ts\":${event.timeMs}," +
                "\"taskId\":\"$taskId\"," +
                "\"kind\":\"${event.kind.name}\"," +
                "\"message\":\"$safeMsg\"}"
            File(dir, "$taskId.jsonl").appendText(line + "\n")
        } catch (_: Exception) {
            // Logging must never break execution.
        }
    }
}

fun formatTime(ms: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(ms))
