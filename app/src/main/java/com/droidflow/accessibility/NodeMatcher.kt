package com.droidflow.accessibility

import android.view.accessibility.AccessibilityNodeInfo
import com.droidflow.models.UIElement

/**
 * Semantic target query (TRD §14): the planner describes WHAT it wants to
 * interact with, and the execution layer resolves it to a concrete node at
 * interaction time — never stored coordinates.
 */
data class TargetQuery(
    val text: String? = null,
    val wantClickable: Boolean = false,
    val wantEditable: Boolean = false,
    val type: String? = null
)

data class MatchCandidate(
    val element: UIElement,
    val node: AccessibilityNodeInfo,
    val score: Double
)

data class MatchResult(
    val best: MatchCandidate?,
    val ambiguous: Boolean,
    val candidates: List<MatchCandidate>
) {
    val label: String?
        get() = best?.element?.let { it.text ?: it.contentDescription ?: it.hint }
}

/**
 * Ranks candidate elements using the TRD §15 signals:
 * text similarity + content description + element type + clickable/editable
 * state + enabled state. Detects ambiguity when the top two scores are close.
 */
object NodeMatcher {

    private const val AMBIGUITY_GAP = 0.08
    private const val AMBIGUITY_FLOOR = 0.35

    fun match(snapshot: ScreenSnapshot, query: TargetQuery): MatchResult {
        val scored = snapshot.state.elements
            .mapNotNull { el ->
                val node = snapshot.nodes[el.id] ?: return@mapNotNull null
                val s = score(el, query)
                if (s > 0.05) MatchCandidate(el, node, s) else null
            }
            .sortedByDescending { it.score }

        if (scored.isEmpty()) return MatchResult(null, false, emptyList())

        val best = scored.first()
        val second = scored.getOrNull(1)
        val ambiguous = second != null &&
            (best.score - second.score) < AMBIGUITY_GAP &&
            second.score > AMBIGUITY_FLOOR

        return MatchResult(best, ambiguous, scored.take(5))
    }

    fun score(el: UIElement, q: TargetQuery): Double {
        var s = 0.0
        val queryText = q.text?.trim()?.lowercase()
        if (queryText != null) {
            val textSim = similarity(queryText, el.text)
            val descSim = similarity(queryText, el.contentDescription)
            val hintSim = similarity(queryText, el.hint)
            s += 0.55 * maxOf(textSim, descSim * 0.9, hintSim * 0.8)
        }
        if (q.wantEditable) {
            s += if (el.editable) 0.25 else -0.15
        }
        if (q.wantClickable) {
            s += if (el.clickable) 0.2 else -0.2
        }
        if (q.type != null && el.type == q.type) s += 0.15
        if (el.enabled) s += 0.03
        return s.coerceIn(0.0, 1.0)
    }

    /** Containment + token-overlap similarity, both normalized to 0..1. */
    fun similarity(query: String, target: String?): Double {
        if (target.isNullOrBlank()) return 0.0
        val t = target.trim().lowercase()
        if (t == query) return 1.0
        if (t.contains(query) || query.contains(t)) return 0.9
        val qTokens = query.split(Regex("\\s+")).filter { it.length > 1 }.toSet()
        val tTokens = t.split(Regex("\\s+")).filter { it.length > 1 }.toSet()
        if (qTokens.isEmpty() || tTokens.isEmpty()) return 0.0
        val inter = qTokens.intersect(tTokens).size.toDouble()
        val union = qTokens.union(tTokens).size.toDouble()
        return (inter / union) * 0.75
    }
}
