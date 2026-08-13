package com.guiyanghai.wandscope

enum class MetricSource { HISTORY, SYSTEM, SUMMARY, CONFIG, INTERNAL }
enum class MetricKind { NUMBER, BOOLEAN, TEXT, OBJECT, UNKNOWN }

data class Viewer(
    val entity: String,
    val username: String,
    val teams: List<String>,
) {
    val entities: List<String> = (listOf(entity) + teams).filter { it.isNotBlank() }.distinct()
}

data class Project(
    val id: String,
    val name: String,
    val entity: String,
    val createdAt: String = "",
)

data class Run(
    val id: String,
    val name: String,
    val displayName: String,
    val state: String,
    val group: String = "",
    val jobType: String = "",
    val createdAt: String = "",
    val heartbeatAt: String = "",
    val description: String = "",
    val notes: String = "",
    val historyLineCount: Int = 0,
    val tags: List<String> = emptyList(),
)

data class MetricValue(
    val key: String,
    val value: String,
    val kind: MetricKind,
    val source: MetricSource,
)

data class MetricDefinition(
    val id: String,
    val key: String,
    val source: MetricSource,
    val kind: MetricKind,
    val group: String,
    val plottable: Boolean,
)

data class ChartPoint(val x: Double, val y: Double)

data class ChartSeries(
    val id: String,
    val metricKey: String,
    val label: String,
    val source: MetricSource,
    val points: List<ChartPoint>,
)

data class Page<T>(
    val items: List<T>,
    val endCursor: String = "",
    val hasNextPage: Boolean = false,
    val totalCount: Int = 0,
)

data class RunDetails(
    val run: Run,
    val metrics: List<MetricDefinition>,
    val config: List<MetricValue>,
)

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val releaseUrl: String,
)

sealed interface UpdateCheckResult {
    data class Available(val info: UpdateInfo) : UpdateCheckResult
    data object UpToDate : UpdateCheckResult
    data object NotPublished : UpdateCheckResult
}

sealed interface Screen {
    data object Projects : Screen
    data class ProjectOverview(val project: Project) : Screen
    data class RunDetail(val project: Project, val run: Run) : Screen
}
