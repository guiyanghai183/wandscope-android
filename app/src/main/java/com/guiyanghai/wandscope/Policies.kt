package com.guiyanghai.wandscope

import java.net.URI
import java.util.Locale

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
