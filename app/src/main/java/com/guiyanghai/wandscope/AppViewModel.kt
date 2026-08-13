package com.guiyanghai.wandscope

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppUiState(
    val starting: Boolean = true,
    val loggedIn: Boolean = false,
    val hasSavedApiKey: Boolean = false,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val loadingCurves: Boolean = false,
    val error: String = "",
    val screen: Screen = Screen.Projects,
    val viewer: Viewer? = null,
    val selectedEntity: String = "",
    val projects: List<Project> = emptyList(),
    val projectsCursor: String = "",
    val hasMoreProjects: Boolean = false,
    val currentProject: Project? = null,
    val runs: List<Run> = emptyList(),
    val runsCursor: String = "",
    val hasMoreRuns: Boolean = false,
    val totalRuns: Int = 0,
    val projectMetrics: List<MetricDefinition> = emptyList(),
    val projectSelection: List<String> = emptyList(),
    val projectCurves: List<ChartSeries> = emptyList(),
    val currentRun: Run? = null,
    val runDetails: RunDetails? = null,
    val runSelection: List<String> = emptyList(),
    val runCurves: List<ChartSeries> = emptyList(),
    val rejectedCurveCount: Int = 0,
    val updateInfo: UpdateInfo? = null,
    val checkingUpdate: Boolean = false,
    val downloadingUpdate: Boolean = false,
    val updateDownloadProgress: Int? = null,
    val updateMessage: String = "",
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val secrets = SecretStore(application)
    private val preferences = AppPreferences(application)
    private var api: WandbApi? = null
    private var requestSerial = 0L
    private var foregroundPoll: Job? = null

    private val _state = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val existing = secrets.readApiKey()
            if (existing == null) {
                _state.update { it.copy(starting = false) }
            } else {
                _state.update { it.copy(hasSavedApiKey = true) }
                authenticate(existing)
            }
        }
    }

    fun login(apiKey: String) {
        if (apiKey.isBlank()) return
        viewModelScope.launch {
            runCatching { secrets.saveApiKey(apiKey) }
                .onSuccess {
                    _state.update { it.copy(hasSavedApiKey = true) }
                    authenticate(apiKey)
                }
                .onFailure { error ->
                    _state.update { it.copy(starting = false, loading = false, error = safeMessage(error)) }
                }
        }
    }

    fun retrySavedLogin() {
        viewModelScope.launch {
            val existing = secrets.readApiKey()
            if (existing == null) {
                _state.update { it.copy(hasSavedApiKey = false, error = "未找到已保存的 API Key") }
            } else {
                authenticate(existing)
            }
        }
    }

    private suspend fun authenticate(apiKey: String) {
        _state.update { it.copy(starting = false, loading = true, error = "") }
        runCatching {
            val nextApi = WandbApi(apiKey)
            val viewer = nextApi.viewer()
            nextApi to viewer
        }.onSuccess { (nextApi, viewer) ->
            api = nextApi
            val entity = preferences.lastEntity.takeIf { it in viewer.entities }
                ?: viewer.entity.takeIf { it.isNotBlank() }
                ?: viewer.entities.firstOrNull().orEmpty()
            preferences.lastEntity = entity
            _state.update {
                AppUiState(
                    starting = false,
                    loggedIn = true,
                    hasSavedApiKey = true,
                    viewer = viewer,
                    selectedEntity = entity,
                    loading = false,
                )
            }
            RunMonitorWorker.schedule(getApplication())
            loadProjects(refresh = true)
            checkForUpdate(silent = true)
        }.onFailure { error ->
            _state.update {
                it.copy(
                    starting = false,
                    loggedIn = false,
                    hasSavedApiKey = secrets.readApiKey() != null,
                    loading = false,
                    error = safeMessage(error),
                )
            }
        }
    }

    fun logout() {
        requestSerial++
        foregroundPoll?.cancel()
        api = null
        RunMonitorWorker.cancel(getApplication())
        _state.value = AppUiState(starting = false, hasSavedApiKey = secrets.readApiKey() != null)
    }

    fun forgetApiKey() {
        requestSerial++
        foregroundPoll?.cancel()
        api = null
        secrets.clear()
        preferences.clear()
        RunMonitorWorker.cancel(getApplication())
        _state.value = AppUiState(starting = false)
    }

    fun selectEntity(entity: String) {
        if (entity == _state.value.selectedEntity) return
        preferences.lastEntity = entity
        _state.update { it.copy(selectedEntity = entity, projects = emptyList(), error = "") }
        loadProjects(refresh = true)
    }

    fun loadProjects(refresh: Boolean = false) {
        val activeApi = api ?: return
        val snapshot = _state.value
        val entity = snapshot.selectedEntity
        if (entity.isBlank() || (snapshot.loading && !refresh)) return
        val cursor = if (refresh) "" else snapshot.projectsCursor
        val serial = ++requestSerial
        _state.update { it.copy(loading = refresh, loadingMore = !refresh, error = if (refresh) "" else it.error) }
        viewModelScope.launch {
            runCatching { activeApi.projects(entity, cursor) }
                .onSuccess { page ->
                    if (serial != requestSerial) return@onSuccess
                    _state.update {
                        it.copy(
                            projects = if (refresh) page.items else (it.projects + page.items).distinctBy(Project::id),
                            projectsCursor = page.endCursor,
                            hasMoreProjects = page.hasNextPage,
                            loading = false,
                            loadingMore = false,
                            error = "",
                        )
                    }
                }.onFailure { error ->
                    if (serial == requestSerial) _state.update { it.copy(loading = false, loadingMore = false, error = safeMessage(error)) }
                }
        }
    }

    fun openProject(project: Project) {
        requestSerial++
        foregroundPoll?.cancel()
        val selection = preferences.selection("project/${project.entity}/${project.name}")
        _state.update {
            it.copy(
                screen = Screen.ProjectOverview(project), currentProject = project,
                runs = emptyList(), projectMetrics = emptyList(), projectCurves = emptyList(),
                projectSelection = selection, error = "",
            )
        }
        preferences.monitorProject(project.entity, project.name)
        loadRuns(refresh = true)
        foregroundPoll = viewModelScope.launch {
            while (true) {
                delay(60_000)
                refreshRunsInBackground(project)
            }
        }
    }

    fun loadRuns(refresh: Boolean = false) {
        val activeApi = api ?: return
        val project = _state.value.currentProject ?: return
        val cursor = if (refresh) "" else _state.value.runsCursor
        val serial = ++requestSerial
        _state.update { it.copy(loading = refresh, loadingMore = !refresh, error = if (refresh) "" else it.error) }
        viewModelScope.launch {
            runCatching { activeApi.runs(project.entity, project.name, cursor) }
                .onSuccess { page ->
                    if (serial != requestSerial) return@onSuccess
                    val combined = if (refresh) page.items else (_state.value.runs + page.items).distinctBy(Run::id)
                    _state.update {
                        it.copy(
                            runs = combined, runsCursor = page.endCursor, hasMoreRuns = page.hasNextPage,
                            totalRuns = page.totalCount, loading = false, loadingMore = false, error = "",
                        )
                    }
                    RunCompletionTracker(getApplication()).process(project.entity, project.name, page.items, notify = true)
                    if (refresh && page.items.isNotEmpty()) loadProjectMetricCatalog(project, page.items.first(), serial)
                }.onFailure { error ->
                    if (serial == requestSerial) _state.update { it.copy(loading = false, loadingMore = false, error = safeMessage(error)) }
                }
        }
    }

    private fun loadProjectMetricCatalog(project: Project, run: Run, parentSerial: Long) {
        val activeApi = api ?: return
        viewModelScope.launch {
            runCatching { activeApi.runDetails(project.entity, project.name, run.id) }
                .onSuccess { details ->
                    if (parentSerial != requestSerial) return@onSuccess
                    _state.update { current ->
                        val allowed = details.metrics.map(MetricDefinition::id).toSet()
                        current.copy(
                            projectMetrics = details.metrics,
                            projectSelection = current.projectSelection.filter { it in allowed }.take(8),
                        )
                    }
                }
        }
    }

    private suspend fun refreshRunsInBackground(project: Project) {
        val activeApi = api ?: return
        runCatching { activeApi.runs(project.entity, project.name) }.onSuccess { page ->
            if (_state.value.currentProject?.id != project.id) return@onSuccess
            _state.update { it.copy(runs = page.items, totalRuns = page.totalCount, runsCursor = page.endCursor, hasMoreRuns = page.hasNextPage) }
            RunCompletionTracker(getApplication()).process(project.entity, project.name, page.items, notify = true)
        }
    }

    fun openRun(run: Run) {
        val project = _state.value.currentProject ?: return
        requestSerial++
        val selection = preferences.selection("run/${project.entity}/${project.name}/${run.id}")
        _state.update {
            it.copy(
                screen = Screen.RunDetail(project, run), currentRun = run,
                runDetails = null, runCurves = emptyList(), runSelection = selection,
                loading = true, error = "",
            )
        }
        val serial = requestSerial
        val activeApi = api ?: return
        viewModelScope.launch {
            runCatching { activeApi.runDetails(project.entity, project.name, run.id) }
                .onSuccess { details ->
                    if (serial != requestSerial) return@onSuccess
                    val allowed = details.metrics.map(MetricDefinition::id).toSet()
                    _state.update { it.copy(runDetails = details, currentRun = details.run, runSelection = it.runSelection.filter { id -> id in allowed }.take(8), loading = false) }
                }.onFailure { error ->
                    if (serial == requestSerial) _state.update { it.copy(loading = false, error = safeMessage(error)) }
                }
        }
    }

    fun selectProjectCurves(ids: List<String>) {
        val project = _state.value.currentProject ?: return
        val valid = normalizedSelection(ids, _state.value.projectMetrics)
        preferences.saveSelection("project/${project.entity}/${project.name}", valid)
        _state.update { it.copy(projectSelection = valid) }
        loadProjectCurves()
    }

    fun selectRunCurves(ids: List<String>) {
        val project = _state.value.currentProject ?: return
        val run = _state.value.currentRun ?: return
        val metrics = _state.value.runDetails?.metrics.orEmpty()
        val valid = normalizedSelection(ids, metrics)
        preferences.saveSelection("run/${project.entity}/${project.name}/${run.id}", valid)
        _state.update { it.copy(runSelection = valid) }
        loadRunCurves()
    }

    fun removeProjectCurve(id: String) {
        val selected = _state.value.projectSelection
        if (id !in selected) return
        selectProjectCurves(MetricSelectionPolicy.remove(selected, id))
    }

    fun removeRunCurve(id: String) {
        val selected = _state.value.runSelection
        if (id !in selected) return
        selectRunCurves(MetricSelectionPolicy.remove(selected, id))
    }

    private fun loadProjectCurves() {
        val activeApi = api ?: return
        val snapshot = _state.value
        val project = snapshot.currentProject ?: return
        val metrics = snapshot.projectMetrics.filter { it.id in snapshot.projectSelection }
        if (metrics.isEmpty()) {
            _state.update { it.copy(projectCurves = emptyList(), rejectedCurveCount = 0) }
            return
        }
        val serial = ++requestSerial
        _state.update { it.copy(loadingCurves = true, error = "", rejectedCurveCount = 0) }
        viewModelScope.launch {
            runCatching {
                buildList {
                    snapshot.runs.take(5).forEach { run ->
                        val history = metrics.filter { it.source == MetricSource.HISTORY }.map(MetricDefinition::key)
                        val system = metrics.filter { it.source == MetricSource.SYSTEM }.map(MetricDefinition::key)
                        addAll(activeApi.sampledHistory(project.entity, project.name, run, history))
                        addAll(activeApi.systemHistory(project.entity, project.name, run, system))
                    }
                }
            }.onSuccess { curves ->
                if (serial == requestSerial) {
                    val expected = metrics.size * snapshot.runs.take(5).size
                    _state.update { it.copy(projectCurves = curves, loadingCurves = false, rejectedCurveCount = (expected - curves.size).coerceAtLeast(0)) }
                }
            }.onFailure { error -> if (serial == requestSerial) _state.update { it.copy(loadingCurves = false, error = safeMessage(error)) } }
        }
    }

    private fun loadRunCurves() {
        val activeApi = api ?: return
        val snapshot = _state.value
        val project = snapshot.currentProject ?: return
        val run = snapshot.currentRun ?: return
        val metrics = snapshot.runDetails?.metrics.orEmpty().filter { it.id in snapshot.runSelection }
        if (metrics.isEmpty()) {
            _state.update { it.copy(runCurves = emptyList(), rejectedCurveCount = 0) }
            return
        }
        val serial = ++requestSerial
        _state.update { it.copy(loadingCurves = true, error = "", rejectedCurveCount = 0) }
        viewModelScope.launch {
            runCatching {
                activeApi.sampledHistory(project.entity, project.name, run, metrics.filter { it.source == MetricSource.HISTORY }.map { it.key }) +
                    activeApi.systemHistory(project.entity, project.name, run, metrics.filter { it.source == MetricSource.SYSTEM }.map { it.key })
            }.onSuccess { curves ->
                if (serial == requestSerial) _state.update { it.copy(runCurves = curves, loadingCurves = false, rejectedCurveCount = (metrics.size - curves.size).coerceAtLeast(0)) }
            }.onFailure { error -> if (serial == requestSerial) _state.update { it.copy(loadingCurves = false, error = safeMessage(error)) } }
        }
    }

    fun back() {
        requestSerial++
        when (_state.value.screen) {
            is Screen.RunDetail -> _state.update { it.copy(screen = Screen.ProjectOverview(it.currentProject!!), currentRun = null, runDetails = null, runCurves = emptyList(), error = "") }
            is Screen.ProjectOverview -> {
                foregroundPoll?.cancel()
                _state.update { it.copy(screen = Screen.Projects, currentProject = null, runs = emptyList(), projectMetrics = emptyList(), projectCurves = emptyList(), error = "") }
            }
            Screen.Projects -> Unit
        }
    }

    fun checkForUpdate(silent: Boolean = false) {
        if (_state.value.checkingUpdate) return
        _state.update { it.copy(checkingUpdate = true, updateMessage = if (silent) "" else "正在检查更新…") }
        viewModelScope.launch {
            runCatching { ReleaseUpdateService(getApplication()).check() }
                .onSuccess { result ->
                    _state.update {
                        when (result) {
                            is UpdateCheckResult.Available -> it.copy(
                                checkingUpdate = false,
                                updateInfo = result.info,
                                updateMessage = "",
                            )
                            UpdateCheckResult.UpToDate -> it.copy(
                                checkingUpdate = false,
                                updateInfo = null,
                                updateMessage = if (silent) "" else "已是最新版本",
                            )
                            UpdateCheckResult.NotPublished -> it.copy(
                                checkingUpdate = false,
                                updateInfo = null,
                                updateMessage = if (silent) "" else "当前没有可用的在线更新",
                            )
                        }
                    }
                }
                .onFailure { error -> _state.update { it.copy(checkingUpdate = false, updateMessage = if (silent) "" else safeMessage(error)) } }
        }
    }

    fun dismissUpdate() = _state.update {
        if (it.downloadingUpdate) it else it.copy(updateInfo = null)
    }

    fun installUpdate(info: UpdateInfo) {
        if (_state.value.downloadingUpdate) return
        _state.update { it.copy(downloadingUpdate = true, updateDownloadProgress = 0, updateMessage = "") }
        viewModelScope.launch {
            runCatching {
                ReleaseUpdateService(getApplication()).downloadAndOpen(info) { progress ->
                    _state.update { it.copy(updateDownloadProgress = progress) }
                }
            }
                .onSuccess {
                    _state.update {
                        it.copy(
                            downloadingUpdate = false,
                            updateDownloadProgress = null,
                            updateMessage = "已交给系统安装器",
                            updateInfo = null,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            downloadingUpdate = false,
                            updateDownloadProgress = null,
                            updateMessage = safeMessage(error),
                        )
                    }
                }
        }
    }

    fun clearMessage() = _state.update { it.copy(updateMessage = "") }

    private fun normalizedSelection(ids: List<String>, metrics: List<MetricDefinition>): List<String> {
        return MetricSelectionPolicy.normalize(ids, metrics)
    }

    private fun safeMessage(error: Throwable): String = error.message?.takeIf { it.isNotBlank() } ?: "请求失败，请稍后重试"
}
