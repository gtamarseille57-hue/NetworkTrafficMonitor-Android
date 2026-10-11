package com.example.networktrafficmonitor

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import java.util.UUID

enum class TrafficAction {
    PASS,
    BLOCK,
    MODIFY
}

data class TrafficRule(
    val id: String = UUID.randomUUID().toString(),
    val packageName: String,
    val host: String,
    val path: String,
    val method: String = "GET",
    val action: TrafficAction = TrafficAction.PASS,
    val modifiedResponseBody: String = "",
    val modifiedStatusCode: Int = 200,
    val modifiedContentType: String = "application/json",
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toJson(): String = JSONObject().apply {
        put("id", id)
        put("packageName", packageName)
        put("host", host)
        put("path", path)
        put("method", method)
        put("action", action.name)
        put("modifiedResponseBody", modifiedResponseBody)
        put("modifiedStatusCode", modifiedStatusCode)
        put("modifiedContentType", modifiedContentType)
        put("createdAt", createdAt)
    }.toString()

    companion object {
        fun fromJson(json: String): TrafficRule {
            val obj = JSONObject(json)
            return TrafficRule(
                id = obj.optString("id", UUID.randomUUID().toString()),
                packageName = obj.optString("packageName", ""),
                host = obj.optString("host", ""),
                path = obj.optString("path", ""),
                method = obj.optString("method", "GET"),
                action = TrafficAction.valueOf(obj.optString("action", TrafficAction.PASS.name)),
                modifiedResponseBody = obj.optString("modifiedResponseBody", ""),
                modifiedStatusCode = obj.optInt("modifiedStatusCode", 200),
                modifiedContentType = obj.optString("modifiedContentType", "application/json"),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis())
            )
        }
    }
}

data class TrafficRequest(
    val packageName: String,
    val url: String,
    val method: String = "GET",
    val body: String = ""
) {
    val host: String
        get() = runCatching { java.net.URI(url).host.orEmpty() }.getOrDefault("")

    val path: String
        get() = runCatching { java.net.URI(url).path.ifBlank { "/" } }.getOrDefault("/")
}

data class TrafficDecision(
    val action: TrafficAction,
    val responseBody: String = "",
    val statusCode: Int = 200,
    val contentType: String = "application/json",
    val reason: String = ""
)

data class TrafficEvent(
    val timestamp: Long,
    val appPackage: String,
    val url: String,
    val method: String,
    val action: TrafficAction,
    val statusCode: Int,
    val responsePreview: String
) {
    val jsonPreview: String
        get() = try {
            JSONObject(responsePreview.ifBlank { "{}" }).toString(2)
        } catch (_: Exception) {
            responsePreview.ifBlank { "{}" }
        }

    val javaPreview: String
        get() = "String response = \"${responsePreview.replace("\\", "\\\\").replace("\"", "\\\"")}\";"

    val hexPreview: String
        get() = responsePreview
            .toByteArray(Charsets.UTF_8)
            .joinToString(separator = " ") { "%02x".format(it) }
}

class TrafficRuleStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("traffic_rule_store", Context.MODE_PRIVATE)

    fun saveRule(rule: TrafficRule) {
        val existing = getAll().filterNot { it.id == rule.id } + rule
        prefs.edit().putString("rules", existing.joinToString(";;") { it.toJson() }).apply()
    }

    fun deleteRule(ruleId: String) {
        val existing = getAll().filterNot { it.id == ruleId }
        prefs.edit().putString("rules", existing.joinToString(";;") { it.toJson() }).apply()
    }

    fun getAll(): List<TrafficRule> {
        val raw = prefs.getString("rules", "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split(";;")
            .filter { it.isNotBlank() }
            .mapNotNull { runCatching { TrafficRule.fromJson(it) }.getOrNull() }
            .sortedByDescending { it.createdAt }
    }

    fun findMatchingRule(request: TrafficRequest): TrafficRule? {
        return getAll().firstOrNull { rule ->
            rule.packageName == request.packageName &&
                rule.host == request.host &&
                rule.path == request.path &&
                rule.method.equals(request.method, ignoreCase = true)
        }
    }
}
