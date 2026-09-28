package com.droidflow.agent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Minimal Gemini REST client (TRD §28). The API key lives only in local
 * DataStore; screen context sent to the model is pre-redacted by
 * SensitiveDataFilter. If the key is absent or the call fails, the engine
 * silently falls back to the deterministic scripted planner — the demo
 * can never die because of the network.
 */
class GeminiClient(private val apiKey: String, private val model: String = "gemini-1.5-flash") {

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val isConfigured: Boolean get() = apiKey.isNotBlank()

    /** Returns the model's text reply or a failure with a human-readable cause. */
    suspend fun generate(
        systemPrompt: String,
        userPrompt: String,
        temperature: Double = 0.2,
        maxTokens: Int = 700
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext Result.failure(IllegalStateException("No Gemini API key set"))
        runCatching {
            val body = JSONObject().apply {
                put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt))))
                put("contents", JSONArray().put(
                    JSONObject().put("role", "user").put(
                        "parts", JSONArray().put(JSONObject().put("text", userPrompt))
                    )
                ))
                put("generationConfig", JSONObject().apply {
                    put("temperature", temperature)
                    put("maxOutputTokens", maxTokens)
                    put("responseMimeType", "application/json")
                })
            }

            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey")
                .header("Content-Type", "application/json")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            http.newCall(request).execute().use { resp ->
                val text = resp.body?.string() ?: ""
                if (!resp.isSuccessful) {
                    throw IllegalStateException(mapHttpError(resp.code, text))
                }
                val parsed = JSONObject(text)
                val cand = parsed.optJSONArray("candidates")?.optJSONObject(0)
                val parts = cand?.optJSONObject("content")?.optJSONArray("parts")
                val reply = StringBuilder()
                for (i in 0 until (parts?.length() ?: 0)) {
                    reply.append(parts!!.optJSONObject(i).optString("text"))
                }
                if (reply.isBlank()) throw IllegalStateException("Empty reply from Gemini")
                reply.toString()
            }
        }
    }

    /** Fast connectivity/key check used by the Settings screen. */
    suspend fun testConnection(): Result<String> =
        generate(
            "You are a connectivity probe. Reply with JSON only.",
            """Reply with exactly: {"ok": true}""",
            temperature = 0.0,
            maxTokens = 20
        )

    private fun mapHttpError(code: Int, body: String): String = when (code) {
        400 -> "Invalid request or API key (400). Check the key in Settings."
        403 -> "API key rejected (403). Get a free key at aistudio.google.com."
        429 -> "Gemini rate limit hit (429). Wait a few seconds and retry."
        else -> "Gemini error $code: ${body.take(160)}"
    }
}

/** Strips markdown fences / prose around a JSON object. */
internal fun extractJsonObject(text: String): String? {
    val cleaned = text.replace("```json", "").replace("```", "").trim()
    val s = cleaned.indexOf('{')
    val e = cleaned.lastIndexOf('}')
    return if (s >= 0 && e > s) cleaned.substring(s, e + 1) else null
}
