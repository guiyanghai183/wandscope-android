package com.guiyanghai.wandscope

import java.net.URI
import java.util.Locale

object MetricTypePolicy {
    private val numericTypes = setOf(
        "number",
        "float",
        "float16",
        "float32",
        "float64",
        "double",
        "int",
        "int8",
        "int16",
        "int32",
        "int64",
        "integer",
        "long",
        "short",
        "uint",
        "uint8",
        "uint16",
        "uint32",
        "uint64",
    )

    fun isNumericHistory(types: Collection<String>): Boolean {
        val meaningful = types
            .map { it.trim().lowercase(Locale.US) }
            .filterNot { it.isBlank() || it in setOf("none", "null", "unknown") }
        return meaningful.isNotEmpty() && meaningful.all { it in numericTypes }
    }
}

object MetricGroupingPolicy {
    fun category(key: String, source: MetricSource): String {
        if (source == MetricSource.SYSTEM) return "System"
        val prefix = key.substringBefore('/').trim()
        return if ('/' in key && prefix.isNotEmpty()) prefix.displayCategory() else "Charts"
    }

    fun displayName(key: String, group: String): String =
        if (group != "Charts" && '/' in key && key.substringBefore('/').displayCategory() == group) {
            key.substringAfter('/')
        } else {
            key
        }

    private fun String.displayCategory(): String = replaceFirstChar { character ->
        if (character.isLowerCase()) character.titlecase(Locale.US) else character.toString()
    }
}

object MetricSelectionPolicy {
    fun selectable(metrics: List<MetricDefinition>): List<MetricDefinition> = metrics.filter {
        it.plottable &&
            it.kind == MetricKind.NUMBER &&
            it.source in setOf(MetricSource.HISTORY, MetricSource.SYSTEM)
    }

    fun normalize(ids: List<String>, metrics: List<MetricDefinition>, limit: Int = 8): List<String> {
        val allowed = selectable(metrics).map(MetricDefinition::id).toSet()
        return ids.filter { it in allowed }.distinct().take(limit)
    }
}

object ReleaseUrlPolicy {
    private val trustedHosts = setOf(
        "github.com",
        "objects.githubusercontent.com",
        "github-releases.githubusercontent.com",
        "release-assets.githubusercontent.com",
    )

    fun isAllowed(value: String, repository: String): Boolean = runCatching {
        val uri = URI(value)
        val host = uri.host?.lowercase(Locale.US).orEmpty()
        uri.scheme == "https" &&
            uri.port in listOf(-1, 443) &&
            uri.userInfo == null &&
            host in trustedHosts &&
            (host != "github.com" || uri.path.orEmpty().startsWith("/$repository/"))
    }.getOrDefault(false)

    fun requireAllowed(value: String, repository: String) {
        require(isAllowed(value, repository)) { "更新地址不受信任" }
    }
}
