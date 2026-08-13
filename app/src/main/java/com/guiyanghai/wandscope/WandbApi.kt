package com.guiyanghai.wandscope

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class WandbApi(
    apiKey: String,
    private val endpoint: String = "https://api.wandb.ai/graphql",
    private val client: OkHttpClient = defaultClient,
) {
    private val authorization = "Basic " + Base64.encodeToString(
        "api:${apiKey.trim()}".toByteArray(Charsets.UTF_8),
        Base64.NO_WRAP,
    )

    init {
        require(apiKey.isNotBlank()) { "W&B API Key 不能为空" }
        require(endpoint.startsWith("https://")) { "W&B API 必须使用 HTTPS" }
    }

    suspend fun viewer(): Viewer {
        val data = execute(VIEWER_QUERY, JSONObject())
        val viewer = data.requiredObject("viewer")
        val teams = buildList {
            val edges = viewer.optJSONObject("teams")?.optJSONArray("edges") ?: JSONArray()
            for (i in 0 until edges.length()) {
                edges.optJSONObject(i)?.optJSONObject("node")?.optString("name")
                    ?.takeIf(String::isNotBlank)?.let(::add)
            }
        }
        return Viewer(viewer.optString("entity"), viewer.optString("username"), teams.distinct())
    }

    suspend fun projects(entity: String, cursor: String = "", pageSize: Int = 20): Page<Project> {
        val variables = JSONObject().put("entity", entity).put("perPage", pageSize.coerceIn(1, 50))
        if (cursor.isNotBlank()) variables.put("cursor", cursor)
        val models = execute(PROJECTS_QUERY, variables).requiredObject("models")
        val items = buildList {
            val edges = models.optJSONArray("edges") ?: JSONArray()
            for (i in 0 until edges.length()) {
                val node = edges.optJSONObject(i)?.optJSONObject("node") ?: continue
                val name = node.optString("name")
                if (name.isNotBlank()) add(Project(node.optString("id"), name, node.optString("entityName"), node.optString("createdAt")))
            }
        }
        return Page(items, pageCursor(models), hasNext(models))
    }

    suspend fun runs(entity: String, project: String, cursor: String = "", pageSize: Int = 20): Page<Run> {
        val variables = JSONObject()
            .put("entity", entity).put("project", project)
            .put("perPage", pageSize.coerceIn(1, 50)).put("order", "-created_at")
        if (cursor.isNotBlank()) variables.put("cursor", cursor)
        val projectData = execute(RUNS_QUERY, variables).requiredObject("project")
        val runs = projectData.requiredObject("runs")
        val items = buildList {
            val edges = runs.optJSONArray("edges") ?: JSONArray()
            for (i in 0 until edges.length()) {
                edges.optJSONObject(i)?.optJSONObject("node")?.let { add(decodeRun(it)) }
            }
        }
        return Page(items, pageCursor(runs), hasNext(runs), projectData.optInt("runCount"))
    }

    suspend fun runDetails(entity: String, project: String, runId: String): RunDetails {
        val variables = JSONObject().put("entity", entity).put("project", project).put("name", runId)
        val run = execute(RUN_DETAILS_QUERY, variables)
            .requiredObject("project").requiredObject("run")
        val config = jsonScalar(run.opt("config"))
        val system = jsonScalar(run.opt("systemMetrics"))
        val history = jsonScalar(run.opt("historyKeys"))
        return RunDetails(
            run = decodeRun(run),
            metrics = buildMetricCatalog(history, system),
            config = metricValues(config, MetricSource.CONFIG, unwrapConfig = true),
        )
    }

    suspend fun sampledHistory(
        entity: String,
        project: String,
        run: Run,
        keys: List<String>,
        samples: Int = 300,
    ): List<ChartSeries> {
        if (keys.isEmpty()) return emptyList()
        val spec = JSONObject().put("keys", JSONArray(listOf("_step") + keys)).put("samples", samples.coerceIn(20, 500))
        val variables = JSONObject().put("entity", entity).put("project", project).put("name", run.id)
            .put("specs", JSONArray().put(spec.toString()))
        val sampled = execute(SAMPLED_HISTORY_QUERY, variables).requiredObject("project")
            .requiredObject("run").optJSONArray("sampledHistory") ?: JSONArray()
        val rows = sampled.optJSONArray(0) ?: JSONArray()
        return rowsToSeries(rows, keys, "_step", run, MetricSource.HISTORY)
    }

    suspend fun systemHistory(
        entity: String,
        project: String,
        run: Run,
        keys: List<String>,
        samples: Int = 300,
    ): List<ChartSeries> {
        if (keys.isEmpty()) return emptyList()
        val variables = JSONObject().put("entity", entity).put("project", project).put("name", run.id)
            .put("samples", samples.coerceIn(20, 500))
        val raw = execute(SYSTEM_HISTORY_QUERY, variables).requiredObject("project")
            .requiredObject("run").optJSONArray("events") ?: JSONArray()
        val rows = JSONArray()
        for (i in 0 until raw.length()) {
            when (val item = raw.opt(i)) {
                is JSONObject -> rows.put(item)
                is String -> runCatching { JSONObject(item) }.getOrNull()?.let(rows::put)
            }
        }
        return rowsToSeries(rows, keys, "_timestamp", run, MetricSource.SYSTEM)
    }

    private suspend fun execute(query: String, variables: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val body = JSONObject().put("query", query).put("variables", variables).toString()
            .toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder().url(endpoint)
            .header("Authorization", authorization)
            .header("Accept", "application/json")
            .post(body).build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw WandbApiException("W&B 返回 HTTP ${response.code}", response.code)
            val envelope = runCatching { JSONObject(text) }.getOrElse { throw WandbApiException("W&B 返回了无法解析的数据") }
            val errors = envelope.optJSONArray("errors")
            if (errors != null && errors.length() > 0) {
                throw WandbApiException(errors.optJSONObject(0)?.optString("message")?.takeIf { it.isNotBlank() } ?: "W&B 请求失败")
            }
            envelope.requiredObject("data")
        }
    }

    private fun decodeRun(node: JSONObject): Run {
        val name = node.optString("name").ifBlank { node.optString("id") }
        return Run(
            id = name,
            name = name,
            displayName = node.optString("displayName").ifBlank { name },
            state = node.optString("state"),
            group = node.optString("group"),
            jobType = node.optString("jobType"),
            createdAt = node.optString("createdAt"),
            heartbeatAt = node.optString("heartbeatAt"),
            description = node.optString("description"),
            notes = node.optString("notes"),
            historyLineCount = node.optInt("historyLineCount"),
            tags = node.optJSONArray("tags").toStringList(),
        )
    }

    private fun buildMetricCatalog(historyRaw: JSONObject, system: JSONObject): List<MetricDefinition> {
        val history = historyRaw.optJSONObject("keys") ?: historyRaw
        val result = linkedMapOf<String, MetricDefinition>()
        history.keys().forEach { key ->
            if (key == "lastStep" || key.startsWith("_")) return@forEach
            val kind = historyKind(history.opt(key))
            if (kind == MetricKind.NUMBER) {
                result["history:$key"] = MetricDefinition(
                    "history:$key",
                    key,
                    MetricSource.HISTORY,
                    kind,
                    MetricGroupingPolicy.category(key, MetricSource.HISTORY),
                    true,
                )
            }
        }
        system.keys().forEach { key ->
            if (key.startsWith("_")) return@forEach
            val kind = valueKind(system.opt(key))
            if (kind == MetricKind.NUMBER) {
                result["system:$key"] = MetricDefinition("system:$key", key, MetricSource.SYSTEM, kind, "System", true)
            }
        }
        return result.values.sortedWith(compareBy<MetricDefinition> { it.group }.thenBy { it.key })
    }

    private fun metricValues(source: JSONObject, metricSource: MetricSource, unwrapConfig: Boolean): List<MetricValue> =
        source.keys().asSequence().filterNot { it.startsWith("_") }.map { key ->
            var raw = source.opt(key)
            if (unwrapConfig && raw is JSONObject && raw.has("value")) raw = raw.opt("value")
            MetricValue(key, formatValue(raw), valueKind(raw), metricSource)
        }.sortedBy { it.key }.toList()

    private fun rowsToSeries(rows: JSONArray, keys: List<String>, xKey: String, run: Run, source: MetricSource): List<ChartSeries> =
        keys.mapNotNull { key ->
            val points = buildList {
                for (i in 0 until rows.length()) {
                    val row = rows.optJSONObject(i) ?: continue
                    val x = finiteNumber(row.opt(xKey)) ?: i.toDouble()
                    val y = finiteNumber(row.opt(key)) ?: continue
                    add(ChartPoint(x, y))
                }
            }
            points.takeIf { it.isNotEmpty() }?.let {
                ChartSeries("${run.id}:$source:$key", key, run.displayName, source, downsample(it, 96))
            }
        }

    private fun downsample(points: List<ChartPoint>, max: Int): List<ChartPoint> {
        if (points.size <= max) return points
        return (0 until max).map { index -> points[(index.toLong() * (points.lastIndex) / (max - 1)).toInt()] }
    }

    private fun pageCursor(container: JSONObject) = container.optJSONObject("pageInfo")?.optString("endCursor").orEmpty()
    private fun hasNext(container: JSONObject) = container.optJSONObject("pageInfo")?.optBoolean("hasNextPage") == true
    private fun historyKind(raw: Any?): MetricKind {
        val types = buildList {
            when (raw) {
                is String -> add(raw)
                is JSONObject -> {
                    raw.optString("type").takeIf(String::isNotBlank)?.let(::add)
                    appendHistoryTypes(raw.opt("typeCounts"))
                    appendHistoryTypes(raw.opt("types"))
                }
            }
        }
        return if (MetricTypePolicy.isNumericHistory(types)) MetricKind.NUMBER else MetricKind.UNKNOWN
    }

    private fun MutableList<String>.appendHistoryTypes(raw: Any?) {
        when (raw) {
            is String -> add(raw)
            is JSONObject -> {
                val directType = raw.optString("type")
                if (directType.isNotBlank()) {
                    add(directType)
                } else {
                    raw.keys().forEach { key ->
                        if (raw.opt(key) is Number && raw.optDouble(key, 0.0) > 0.0) add(key)
                    }
                }
            }
            is JSONArray -> for (index in 0 until raw.length()) appendHistoryTypes(raw.opt(index))
        }
    }

    private fun valueKind(raw: Any?): MetricKind = when (raw) {
        is Number -> MetricKind.NUMBER
        is Boolean -> MetricKind.BOOLEAN
        is JSONObject, is JSONArray -> MetricKind.OBJECT
        null, JSONObject.NULL -> MetricKind.UNKNOWN
        else -> MetricKind.TEXT
    }

    private fun finiteNumber(raw: Any?): Double? = when (raw) {
        is Number -> raw.toDouble().takeIf { it.isFinite() }
        is String -> raw.toDoubleOrNull()?.takeIf { it.isFinite() }
        else -> null
    }

    private fun formatValue(raw: Any?): String = when (raw) {
        null, JSONObject.NULL -> "—"
        is Double -> if (raw.isFinite()) "%.6g".format(raw) else "—"
        is JSONObject, is JSONArray -> raw.toString()
        else -> raw.toString()
    }

    private fun jsonScalar(raw: Any?): JSONObject = when (raw) {
        is JSONObject -> raw
        is String -> runCatching { JSONObject(raw) }.getOrDefault(JSONObject())
        else -> JSONObject()
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val defaultClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(false).build()

        private const val VIEWER_QUERY = """query Viewer { viewer { entity username teams { edges { node { name } } } } }"""
        private const val PROJECTS_QUERY = """query GetProjects(${'$'}entity: String, ${'$'}cursor: String, ${'$'}perPage: Int = 20) { models(entityName: ${'$'}entity, after: ${'$'}cursor, first: ${'$'}perPage) { pageInfo { endCursor hasNextPage } edges { node { id name entityName createdAt isBenchmark } } } }"""
        private const val RUNS_QUERY = """query Runs(${'$'}project: String!, ${'$'}entity: String!, ${'$'}cursor: String, ${'$'}perPage: Int = 20, ${'$'}order: String) { project(name: ${'$'}project, entityName: ${'$'}entity) { runCount runs(after: ${'$'}cursor, first: ${'$'}perPage, order: ${'$'}order) { edges { node { id tags name displayName state group jobType createdAt heartbeatAt description notes historyLineCount } } pageInfo { endCursor hasNextPage } } } }"""
        private const val RUN_DETAILS_QUERY = """query RunDetails(${'$'}project: String!, ${'$'}entity: String!, ${'$'}name: String!) { project(name: ${'$'}project, entityName: ${'$'}entity) { run(name: ${'$'}name) { id tags name displayName state group jobType createdAt heartbeatAt description notes historyLineCount config systemMetrics historyKeys } } }"""
        private const val SAMPLED_HISTORY_QUERY = """query RunSampledHistory(${'$'}project: String!, ${'$'}entity: String!, ${'$'}name: String!, ${'$'}specs: [JSONString!]!) { project(name: ${'$'}project, entityName: ${'$'}entity) { run(name: ${'$'}name) { sampledHistory(specs: ${'$'}specs) } } }"""
        private const val SYSTEM_HISTORY_QUERY = """query RunSystemHistory(${'$'}project: String!, ${'$'}entity: String!, ${'$'}name: String!, ${'$'}samples: Int) { project(name: ${'$'}project, entityName: ${'$'}entity) { run(name: ${'$'}name) { events(samples: ${'$'}samples) } } }"""
    }
}

class WandbApiException(message: String, val statusCode: Int = 0) : IOException(message)

private fun JSONObject.requiredObject(key: String): JSONObject = optJSONObject(key)
    ?: throw WandbApiException("W&B 响应缺少 $key")

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return buildList { for (i in 0 until length()) optString(i).takeIf(String::isNotBlank)?.let(::add) }
}
