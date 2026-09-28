package com.droidflow.execution

import android.content.Context
import android.view.accessibility.AccessibilityNodeInfo
import com.droidflow.accessibility.DroidFlowAccessibilityService
import com.droidflow.accessibility.NodeMatcher
import com.droidflow.accessibility.ScreenSnapshot
import com.droidflow.accessibility.TargetQuery
import com.droidflow.models.ActionType
import com.droidflow.models.AgentAction
import com.droidflow.models.ExecutionResult
import com.droidflow.models.ExecutionStatus
import com.droidflow.models.FailureReason
import com.droidflow.models.UIElement
import com.droidflow.safety.SensitiveDataFilter
import kotlinx.coroutines.delay

/** A semantic target resolved to a live node at interaction time. */
class ResolvedTarget(
    val element: UIElement,
    val node: AccessibilityNodeInfo,
    val score: Double
) {
    val displayText: String
        get() = element.text ?: element.contentDescription ?: element.hint ?: element.id
}

/**
 * Executes ONE validated action against the real UI (TRD §13-18).
 * The LLM/planner proposes; this class disposes. No coordinates are ever
 * stored — targets are re-resolved from a fresh snapshot every cycle.
 */
class ActionExecutor(private val context: Context) {

    private val service: DroidFlowAccessibilityService?
        get() = DroidFlowAccessibilityService.instance

    // ---------- resolution ----------

    fun resolve(action: AgentAction, snap: ScreenSnapshot): ResolvedTarget? {
        return when (action.type) {
            ActionType.TYPE -> resolveEditable(action.targetText, snap)
            else -> resolveByQuery(action.targetText, snap)
        }
    }

    /** Text/label based resolution for CLICK and READ. */
    private fun resolveByQuery(query: String?, snap: ScreenSnapshot): ResolvedTarget? {
        if (query.isNullOrBlank()) return null
        val result = NodeMatcher.match(snap, TargetQuery(text = query))
        val best = result.best ?: return null
        if (result.ambiguous) return null // top-2 too close — abstain (TRD §15)
        return ResolvedTarget(best.element, best.node, best.score)
    }

    /**
     * TYPE resolution with label-to-field association (TRD §14):
     * first try a direct editable match; otherwise find the label/placeholder
     * element and take the nearest editable element in tree order.
     */
    private fun resolveEditable(query: String?, snap: ScreenSnapshot): ResolvedTarget? {
        if (query.isNullOrBlank()) return null

        val direct = NodeMatcher.match(snap, TargetQuery(text = query, wantEditable = true))
        val bestDirect = direct.best
        if (bestDirect != null && bestDirect.score >= 0.55 && !direct.ambiguous) {
            return ResolvedTarget(bestDirect.element, bestDirect.node, bestDirect.score)
        }

        val labelMatch = NodeMatcher.match(snap, TargetQuery(text = query))
        val label = labelMatch.best ?: return null
        val idx = snap.state.elements.indexOfFirst { it.id == label.element.id }
        if (idx < 0) return null

        for (d in 1..5) {
            for (j in intArrayOf(idx + d, idx - d)) {
                val el = snap.state.elements.getOrNull(j) ?: continue
                if (el.editable) {
                    val node = snap.nodes[el.id] ?: continue
                    return ResolvedTarget(el, node, 0.7) // associated-field confidence
                }
            }
        }
        return null
    }

    // ---------- execution ----------

    suspend fun execute(
        action: AgentAction,
        snap: ScreenSnapshot,
        resolved: ResolvedTarget?
    ): ExecutionResult {
        val svc = service ?: return ExecutionResult(
            ExecutionStatus.FAILURE, FailureReason.UNKNOWN_UI.name, "Accessibility service not running"
        )

        return when (action.type) {
            ActionType.CLICK -> {
                val node = resolved?.node
                    ?: return ExecutionResult(ExecutionStatus.FAILURE, FailureReason.TARGET_NOT_FOUND.name)
                val ok = svc.tapNode(node)
                ExecutionResult(
                    if (ok) ExecutionStatus.SUCCESS else ExecutionStatus.FAILURE,
                    if (ok) null else FailureReason.ACTION_FAILED.name,
                    detail = "tapped '${resolved.displayText}'"
                )
            }

            ActionType.TYPE -> {
                val node = resolved?.node
                    ?: return ExecutionResult(ExecutionStatus.FAILURE, FailureReason.TARGET_NOT_FOUND.name)
                val text = action.text ?: ""
                val ok = svc.setText(node, text)
                ExecutionResult(
                    if (ok) ExecutionStatus.SUCCESS else ExecutionStatus.FAILURE,
                    if (ok) null else FailureReason.ACTION_FAILED.name,
                    detail = "typed '${SensitiveDataFilter.redact(text)}' into '${resolved.displayText}'"
                )
            }

            ActionType.READ -> readValue(snap, resolved)

            ActionType.OPEN_APP -> {
                val app = AppController.resolve(action.packageToOpen)
                    ?: return ExecutionResult(
                        ExecutionStatus.FAILURE,
                        FailureReason.APP_NOT_AVAILABLE.name,
                        "unknown app '${action.packageToOpen}'"
                    )
                val intent = AppController.launchIntent(context, app)
                    ?: return ExecutionResult(
                        ExecutionStatus.FAILURE,
                        FailureReason.APP_NOT_AVAILABLE.name,
                        "'${app.label}' is not installed"
                    )
                return try {
                    context.startActivity(intent)
                    val ready = AppController.awaitForeground(app)
                    ExecutionResult(
                        if (ready) ExecutionStatus.SUCCESS else ExecutionStatus.TIMEOUT,
                        if (ready) null else FailureReason.TIMEOUT.name,
                        detail = "opened ${app.label}"
                    )
                } catch (t: Exception) {
                    ExecutionResult(
                        ExecutionStatus.FAILURE,
                        FailureReason.APP_NOT_AVAILABLE.name,
                        "could not open ${app.label}: ${t.message}"
                    )
                }
            }

            ActionType.SCROLL, ActionType.SWIPE -> {
                val down = (action.direction ?: "down").equals("down", ignoreCase = true)
                val ok = if (down) svc.scrollDown() else svc.scrollUp()
                ExecutionResult(
                    if (ok) ExecutionStatus.SUCCESS else ExecutionStatus.FAILURE,
                    if (ok) null else FailureReason.ACTION_FAILED.name,
                    detail = "scrolled ${if (down) "down" else "up"}"
                )
            }

            ActionType.BACK -> {
                val ok = svc.globalBack()
                ExecutionResult(
                    if (ok) ExecutionStatus.SUCCESS else ExecutionStatus.FAILURE,
                    detail = "global back"
                )
            }

            ActionType.WAIT -> {
                delay((action.durationMs.coerceIn(200, 3000)))
                ExecutionResult(ExecutionStatus.SUCCESS, detail = "waited")
            }

            ActionType.ASK_USER, ActionType.STOP ->
                // Handled by the engine, never executed here.
                ExecutionResult(ExecutionStatus.SUCCESS, detail = "no-op")
        }
    }

    // ---------- READ: value extraction (TRD §8 perception) ----------

    private val PRICE_RE = Regex("""[₹]\s*(\d{2,5})|(?i)rs\.?\s*(\d{2,5})""")

    /**
     * READ anchors on a label (ride class / product name) and scans the
     * nearby elements in tree order for a price-like value. This survives
     * layout shifts and reordering because label+price stay siblings.
     */
    private fun readValue(snap: ScreenSnapshot, resolved: ResolvedTarget?): ExecutionResult {
        if (resolved == null) {
            return ExecutionResult(ExecutionStatus.FAILURE, FailureReason.TARGET_NOT_FOUND.name)
        }
        val elements = snap.state.elements
        val anchorText = resolved.displayText

        // 1. Does the anchor itself contain a price?
        PRICE_RE.find(anchorText)?.let { m ->
            val p = (m.groupValues[1].ifEmpty { m.groupValues[2] })
            return ExecutionResult(
                ExecutionStatus.SUCCESS,
                extracted = mapOf("label" to anchorText.substringBefore("₹").trim(), "price" to p)
            )
        }

        // 2. Scan neighbours for the price.
        val idx = elements.indexOfFirst { it.id == resolved.element.id }
        if (idx >= 0) {
            for (d in 1..6) {
                for (j in intArrayOf(idx + d, idx - d)) {
                    val el = elements.getOrNull(j) ?: continue
                    val t = el.text ?: el.contentDescription ?: continue
                    val m = PRICE_RE.find(t) ?: continue
                    val p = (m.groupValues[1].ifEmpty { m.groupValues[2] })
                    return ExecutionResult(
                        ExecutionStatus.SUCCESS,
                        detail = "read '$anchorText' → ₹$p",
                        extracted = mapOf("label" to anchorText, "price" to p)
                    )
                }
            }
        }
        return ExecutionResult(
            ExecutionStatus.FAILURE,
            FailureReason.TARGET_NOT_FOUND.name,
            "no price found near '$anchorText'"
        )
    }
}
