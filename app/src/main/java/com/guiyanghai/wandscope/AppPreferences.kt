package com.guiyanghai.wandscope

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class MonitoredProject(val entity: String, val project: String)

class AppPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("wandscope_preferences", Context.MODE_PRIVATE)

    var lastEntity: String
        get() = prefs.getString("last_entity", "").orEmpty()
        set(value) { prefs.edit().putString("last_entity", value).apply() }

    fun selection(scope: String): List<String> = runCatching {
        val raw = prefs.getString("selection/$scope", "[]") ?: "[]"
        JSONArray(raw).let { array -> buildList { for (i in 0 until array.length()) add(array.optString(i)) } }
    }.getOrDefault(emptyList())

    fun saveSelection(scope: String, ids: List<String>) {
        prefs.edit().putString("selection/$scope", JSONArray(ids.take(8)).toString()).apply()
    }

    fun monitorProject(entity: String, project: String) {
        val next = (listOf(MonitoredProject(entity, project)) + monitoredProjects())
            .distinctBy { "${it.entity}/${it.project}" }.take(8)
        prefs.edit().putString("monitored_projects", JSONArray(next.map {
            JSONObject().put("entity", it.entity).put("project", it.project)
        }).toString()).apply()
    }

    fun monitoredProjects(): List<MonitoredProject> = runCatching {
        val array = JSONArray(prefs.getString("monitored_projects", "[]"))
        buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val entity = item.optString("entity")
                val project = item.optString("project")
                if (entity.isNotBlank() && project.isNotBlank()) add(MonitoredProject(entity, project))
            }
        }
    }.getOrDefault(emptyList())

    fun previousRunStates(scope: String): MutableMap<String, String> = runCatching {
        val obj = JSONObject(prefs.getString("run_states/$scope", "{}").orEmpty().ifBlank { "{}" })
        buildMap { obj.keys().forEach { put(it, obj.optString(it)) } }.toMutableMap()
    }.getOrDefault(mutableMapOf())

    fun saveRunStates(scope: String, states: Map<String, String>) {
        val obj = JSONObject()
        states.entries.take(100).forEach { obj.put(it.key, it.value) }
        prefs.edit().putString("run_states/$scope", obj.toString()).apply()
    }

    fun isRunBaselineInitialized(scope: String): Boolean = prefs.getBoolean("run_baseline/$scope", false)

    fun markRunBaselineInitialized(scope: String) {
        prefs.edit().putBoolean("run_baseline/$scope", true).apply()
    }

    fun notifiedRunIds(scope: String): MutableSet<String> = runCatching {
        val array = JSONArray(prefs.getString("notified_runs/$scope", "[]").orEmpty().ifBlank { "[]" })
        buildSet { for (i in 0 until array.length()) array.optString(i).takeIf(String::isNotBlank)?.let(::add) }.toMutableSet()
    }.getOrDefault(mutableSetOf())

    fun saveNotifiedRunIds(scope: String, runIds: Set<String>) {
        prefs.edit().putString("notified_runs/$scope", JSONArray(runIds.toList().takeLast(100)).toString()).apply()
    }

    fun clear() = prefs.edit().clear().apply()
}
