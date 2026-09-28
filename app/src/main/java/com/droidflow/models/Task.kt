package com.droidflow.models

import kotlinx.serialization.Serializable

@Serializable
enum class RiskLevel { LOW, MEDIUM, HIGH }

@Serializable
enum class GoalType { COMPARE_PRICES, FIND_PRICE, FILL_FORM, UNKNOWN }

/**
 * Structured task representation produced by the Intent Engine.
 * Mirrors the TRD §31 API contract:
 * goals, apps, entities, constraints, risk class, stop conditions.
 */
@Serializable
data class Task(
    val taskId: String,
    val goal: String,
    val goalType: GoalType = GoalType.UNKNOWN,
    val apps: List<String> = emptyList(),
    val entities: Map<String, String> = emptyMap(),
    val constraints: List<String> = emptyList(),
    val finalAction: String? = null,
    val riskLevel: RiskLevel = RiskLevel.MEDIUM,
    val requiresConfirmation: Boolean = false,
    val stopConditions: List<String> = emptyList()
)
