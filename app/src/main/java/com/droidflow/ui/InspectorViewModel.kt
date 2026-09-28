package com.droidflow.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.droidflow.accessibility.DroidFlowAccessibilityService
import com.droidflow.accessibility.MatchResult
import com.droidflow.accessibility.NodeMatcher
import com.droidflow.accessibility.ScreenSnapshot
import com.droidflow.accessibility.ServiceCheck
import com.droidflow.accessibility.TargetQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

/**
 * Drives the UI Inspector: the Phase-2 proof tool that demonstrates the
 * full TRD Step-2 loop live on a real device:
 *
 *   read UI tree → semantic match → act → re-read → verify the state changed
 */
@OptIn(FlowPreview::class)
class InspectorViewModel(application: Application) : AndroidViewModel(application) {

    data class InspectorUi(
        val connected: Boolean = false,
        val a11yEnabled: Boolean = false,
        val foreground: String? = null,
        val snapshot: ScreenSnapshot? = null,
        val countdown: Int? = null,
        val busy: Boolean = false
    )

    var ui by mutableStateOf(InspectorUi())
        private set

    var query by mutableStateOf("Search rides")

    var matchResult by mutableStateOf<MatchResult?>(null)
        private set

    var log by mutableStateOf<List<Pair<Long, String>>>(emptyList())
        private set

    private val selfPackage: String = application.packageName

    init {
        // Service connection state
        viewModelScope.launch(Dispatchers.Main) {
            DroidFlowAccessibilityService.connected.collect { connected ->
                ui = ui.copy(
                    connected = connected,
                    a11yEnabled = ServiceCheck.isAccessibilityServiceEnabled(getApplication())
                )
            }
        }
        // Foreground app tracking
        viewModelScope.launch(Dispatchers.Main) {
            DroidFlowAccessibilityService.foreground.collect { fg ->
                ui = ui.copy(foreground = fg)
            }
        }
        // Auto-capture whenever a foreign (non-self, non-system) app comes to front
        viewModelScope.launch(Dispatchers.Main) {
            DroidFlowAccessibilityService.windowEvents
                .debounce(450)
                .collect { pkg -> autoCapture(pkg) }
        }
    }

    // ---------- capture ----------

    fun captureNow() {
        viewModelScope.launch(Dispatchers.Main) { doCapture() }
    }

    fun captureDelayed() {
        viewModelScope.launch(Dispatchers.Main) {
            ui = ui.copy(busy = true)
            for (i in 6 downTo 1) {
                ui = ui.copy(countdown = i)
                delay(1000)
            }
            ui = ui.copy(countdown = null, busy = false)
            doCapture()
        }
    }

    private suspend fun doCapture() {
        val svc = DroidFlowAccessibilityService.instance
        if (svc == null) {
            addLog("Service not connected — enable it first")
            return
        }
        val snap = try {
            svc.snapshot()
        } catch (_: Exception) {
            null
        }
        if (snap == null) {
            addLog("No accessible window (secure screen or none focused)")
            return
        }
        ui = ui.copy(snapshot = snap, a11yEnabled = ServiceCheck.isAccessibilityServiceEnabled(getApplication()))
        if (snap.isSelf) {
            addLog("Captured ${snap.state.packageName} (the inspector itself — switch to a demo app)")
        } else {
            addLog("Captured ${snap.state.packageName} — screen '${snap.state.screen}', ${snap.state.elements.size} elements")
        }
    }

    private suspend fun autoCapture(pkg: String) {
        if (DroidFlowAccessibilityService.isSystemNoise(pkg)) return
        if (pkg == selfPackage) return
        doCapture()
    }

    // ---------- semantic matching ----------

    fun findMatch() {
        viewModelScope.launch(Dispatchers.Main) {
            if (ui.snapshot == null) doCapture()
            val snap = ui.snapshot ?: return@launch
            val res = NodeMatcher.match(
                snap,
                TargetQuery(text = query.ifBlank { null }, wantClickable = true)
            )
            matchResult = res
            when {
                res.best == null -> addLog("No match for \"$query\"")
                res.ambiguous -> addLog(
                    "Ambiguous: \"${res.label}\" vs \"${res.candidates.getOrNull(1)?.element?.text ?: "?"}\" — too close to choose"
                )
                else -> addLog(
                    "Matched \"${res.label}\" (score %.2f)".format(res.best.score)
                )
            }
        }
    }

    // ---------- act + verify ----------

    /** Find, tap, and re-read — the full deterministic loop, delayed so the user can switch apps. */
    fun tapMatchDelayed() {
        viewModelScope.launch(Dispatchers.Main) {
            ui = ui.copy(busy = true)
            for (i in 6 downTo 1) {
                ui = ui.copy(countdown = i)
                delay(1000)
            }
            ui = ui.copy(countdown = null, busy = false)

            val svc = DroidFlowAccessibilityService.instance
            if (svc == null) {
                addLog("Service not connected — enable it first")
                return@launch
            }
            val snap = try { svc.snapshot() } catch (_: Exception) { null }
            if (snap == null) {
                addLog("No accessible window — is a demo app in the foreground?")
                return@launch
            }
            ui = ui.copy(snapshot = snap)

            val res = NodeMatcher.match(
                snap,
                TargetQuery(text = query.ifBlank { null }, wantClickable = true)
            )
            matchResult = res

            when {
                res.best == null -> {
                    addLog("No match for \"$query\" — try a different label")
                    return@launch
                }
                res.ambiguous -> {
                    addLog(
                        "Ambiguous match — abstaining (this is the safe behavior). " +
                            "Top: \"${res.label}\", runner-up score %.2f"
                                .format(res.candidates.getOrNull(1)?.score ?: 0.0)
                    )
                    return@launch
                }
                else -> {
                    addLog("Matched \"${res.label}\" (score %.2f)".format(res.best.score))
                    val ok = try { svc.tapNode(res.best.node) } catch (_: Exception) { false }
                    addLog(if (ok) "Tap dispatched ✓" else "Tap FAILED (node rejected the action)")
                    delay(1300)
                    val after = try { svc.snapshot() } catch (_: Exception) { null }
                    if (after != null) {
                        ui = ui.copy(snapshot = after)
                        addLog("Re-observed — screen is now '${after.state.screen}'")
                    }
                }
            }
        }
    }

    /** Tap the previously matched node (only safe while its app is foreground). */
    fun tapMatchNow() {
        viewModelScope.launch(Dispatchers.Main) {
            val best = matchResult?.best ?: return@launch
            val svc = DroidFlowAccessibilityService.instance ?: return@launch
            val ok = try { svc.tapNode(best.node) } catch (_: Exception) { false }
            addLog(if (ok) "Tap dispatched ✓" else "Tap FAILED (node may be stale — switch apps and retry)")
            delay(1200)
            doCapture()
        }
    }

    // ---------- global navigation ----------

    fun globalBack() {
        viewModelScope.launch(Dispatchers.Main) {
            val ok = DroidFlowAccessibilityService.instance?.globalBack() ?: false
            addLog(if (ok) "GLOBAL_ACTION_BACK ✓" else "Back failed")
            delay(800)
            doCapture()
        }
    }

    fun globalHome() {
        viewModelScope.launch(Dispatchers.Main) {
            val ok = DroidFlowAccessibilityService.instance?.globalHome() ?: false
            addLog(if (ok) "GLOBAL_ACTION_HOME ✓" else "Home failed")
            delay(800)
            doCapture()
        }
    }

    private fun addLog(message: String) {
        log = (log + (System.currentTimeMillis() to message)).takeLast(80)
    }
}
