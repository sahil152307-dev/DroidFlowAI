package com.droidflow.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "droidflow_settings")

/**
 * Lightweight local settings (TRD §32).
 * Stores: Gemini API key, voice/input language, confidence thresholds,
 * step budget, onboarding flag. Never stores secrets of target apps.
 */
class SettingsStore(private val context: Context) {

    private object Keys {
        val API_KEY = stringPreferencesKey("gemini_api_key")
        val LANGUAGE = stringPreferencesKey("language_tag")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val CONF_HIGH = floatPreferencesKey("confidence_high")
        val CONF_LOW = floatPreferencesKey("confidence_low")
        val MAX_STEPS = intPreferencesKey("max_steps")
        val PLANNER_MODE = stringPreferencesKey("planner_mode")
    }

    val geminiApiKey: Flow<String> = context.dataStore.data.map { it[Keys.API_KEY] ?: "" }
    val languageTag: Flow<String> = context.dataStore.data.map { it[Keys.LANGUAGE] ?: "en-IN" }
    val onboardingDone: Flow<Boolean> = context.dataStore.data.map { it[Keys.ONBOARDING_DONE] ?: false }
    val confidenceHigh: Flow<Float> = context.dataStore.data.map { it[Keys.CONF_HIGH] ?: 0.75f }
    val confidenceLow: Flow<Float> = context.dataStore.data.map { it[Keys.CONF_LOW] ?: 0.45f }
    val maxSteps: Flow<Int> = context.dataStore.data.map { it[Keys.MAX_STEPS] ?: 20 }
    val plannerMode: Flow<String> =
        context.dataStore.data.map { it[Keys.PLANNER_MODE] ?: "auto" }

    suspend fun setPlannerMode(value: String) {
        context.dataStore.edit { it[Keys.PLANNER_MODE] = value }
    }

    suspend fun setGeminiApiKey(value: String) {
        context.dataStore.edit { it[Keys.API_KEY] = value.trim() }
    }

    suspend fun setLanguageTag(value: String) {
        context.dataStore.edit { it[Keys.LANGUAGE] = value }
    }

    suspend fun setOnboardingDone(value: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_DONE] = value }
    }

    suspend fun setConfidenceHigh(value: Float) {
        context.dataStore.edit { it[Keys.CONF_HIGH] = value }
    }

    suspend fun setConfidenceLow(value: Float) {
        context.dataStore.edit { it[Keys.CONF_LOW] = value }
    }

    suspend fun setMaxSteps(value: Int) {
        context.dataStore.edit { it[Keys.MAX_STEPS] = value.coerceIn(5, 60) }
    }
}
