package com.guiyanghai.wandscope

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun WandScopeRoot(state: AppUiState, viewModel: AppViewModel) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        when {
            state.starting -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = WandColors.Accent) }
            !state.loggedIn -> LoginScreen(state, viewModel::login)
            else -> when (state.screen) {
                Screen.Projects -> ProjectsScreen(state, viewModel)
                is Screen.ProjectOverview -> ProjectOverviewScreen(state, viewModel)
                is Screen.RunDetail -> RunDetailScreen(state, viewModel)
            }
        }

        state.updateInfo?.let { info ->
            UpdateDialog(info, onDismiss = viewModel::dismissUpdate, onInstall = { viewModel.installUpdate(info) })
        }
        if (state.updateMessage.isNotBlank()) {
            Box(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp)
                    .clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.onBackground).clickable { viewModel.clearMessage() }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) { Text(state.updateMessage, color = MaterialTheme.colorScheme.background, fontSize = 13.sp) }
        }
    }
}

@Composable
private fun LoginScreen(state: AppUiState, onLogin: (String) -> Unit) {
    var key by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().imePadding().padding(horizontal = 26.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        BrandMark(size = 70)
        Spacer(Modifier.height(24.dp))
        Text("WandScope", style = MaterialTheme.typography.headlineLarge)
        Text("你的 W&B Projects，装进口袋。", fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(30.dp))
        SurfaceCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("连接 Weights & Biases", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text("API Key 仅加密保存在此设备的 Android Keystore 中，不会上传到任何第三方服务。", fontSize = 12.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("W&B API Key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    shape = RoundedCornerShape(14.dp),
                )
                PrimaryAction("安全连接", enabled = key.isNotBlank() && !state.loading) {
                    val submitted = key
                    key = ""
                    onLogin(submitted)
                }
                if (state.loading) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator(Modifier.size(22.dp), color = WandColors.Accent, strokeWidth = 2.dp) }
                if (state.error.isNotBlank()) Text(state.error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }
        }
        Text("只读访问 · 不修改或删除 W&B 数据", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 18.dp))
    }
}

@Composable
private fun ProjectsScreen(state: AppUiState, viewModel: AppViewModel) {
    Column(Modifier.fillMaxSize()) {
        PageHeader(
            title = "Projects",
            subtitle = state.viewer?.username.orEmpty(),
            action = {
                IconButton(onClick = { viewModel.checkForUpdate() }) { Icon(Icons.Rounded.CloudSync, "检查更新") }
                IconButton(onClick = viewModel::logout) { Icon(Icons.AutoMirrored.Rounded.Logout, "退出") }
            },
        )
        val entities = state.viewer?.entities.orEmpty()
        if (entities.size > 1) {
            androidx.compose.foundation.lazy.LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(entities) { entity ->
                    val selected = entity == state.selectedEntity
                    Text(
                        entity,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clip(CircleShape)
                            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { viewModel.selectEntity(entity) }.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.loading && state.projects.isEmpty()) {
                item { LoadingCard("正在加载 Projects") }
            } else if (state.error.isNotBlank() && state.projects.isEmpty()) {
                item { ErrorCard("无法加载 Projects", state.error) { viewModel.loadProjects(refresh = true) } }
            } else if (state.projects.isEmpty()) {
                item { EmptyCard("还没有 Project", "当前 Entity 下没有可显示的项目。") }
            } else {
                items(state.projects, key = Project::id) { project ->
                    ProjectRow(project) { viewModel.openProject(project) }
                }
                if (state.error.isNotBlank()) item { Text(state.error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().padding(8.dp)) }
                if (state.hasMoreProjects || state.loadingMore) {
                    item { SecondaryButton(if (state.loadingMore) "正在加载…" else "加载更多 Projects", !state.loadingMore) { viewModel.loadProjects() } }
                }
            }
        }
    }
}

@Composable
private fun ProjectRow(project: Project, onClick: () -> Unit) {
    SurfaceCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(WandColors.Accent.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Text(project.name.take(1).uppercase(), color = WandColors.Accent, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(project.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(project.entity, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 25.sp)
        }
    }
}

@Composable
private fun ProjectOverviewScreen(state: AppUiState, viewModel: AppViewModel) {
    val project = state.currentProject ?: return
    var picker by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        PageHeader(project.name, "${project.entity} / ${project.name}", viewModel::back)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("Running", state.runs.count { it.state.equals("running", true) }.toString(), WandColors.Green, Modifier.weight(1f))
                    StatTile("Finished", state.runs.count { it.state.lowercase() in setOf("finished", "completed") }.toString(), WandColors.Accent, Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("Crashed", state.runs.count { it.state.lowercase() in setOf("failed", "crashed", "killed") }.toString(), WandColors.Red, Modifier.weight(1f))
                    StatTile("Total", if (state.totalRuns > 0) state.totalRuns.toString() else "—", MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
                }
            }
            item { Text("状态统计基于已加载的 ${state.runs.size} 个 Runs；Total 为服务端总数。", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item {
                PrimaryAction(
                    if (state.projectSelection.isEmpty()) "选择曲线" else "选择曲线  ·  已选 ${state.projectSelection.size}",
                    enabled = state.projectMetrics.isNotEmpty() && !state.loadingCurves,
                ) { picker = true }
            }
            if (state.loadingCurves) item { LoadingCard("正在加载所选曲线…") }
            if (state.projectSelection.isEmpty() && !state.loadingCurves) {
                item {
                    EmptyCard(
                        if (state.projectMetrics.isEmpty() && !state.loading) "暂无可绘制曲线" else "还没有选择曲线",
                        if (state.projectMetrics.isEmpty()) "当前 Run 没有数值历史指标。" else "点击“选择曲线”，最多对比 8 个指标。",
                    )
                }
            } else if (!state.loadingCurves) {
                state.projectMetrics.filter { it.id in state.projectSelection }.forEach { metric ->
                    item(metric.id) { MetricChart(metric.key, "多 Run 对比", state.projectCurves.filter { it.metricKey == metric.key }) }
                }
            }
            if (state.rejectedCurveCount > 0) item { InfoCard("${state.rejectedCurveCount} 条曲线没有足够的数值历史，暂时无法绘制。") }
            item { SectionLabel("RUNS") }
            if (state.loading && state.runs.isEmpty()) item { LoadingCard("正在加载 Runs") }
            else if (state.error.isNotBlank() && state.runs.isEmpty()) item { ErrorCard("无法加载 Project 内容", state.error) { viewModel.loadRuns(refresh = true) } }
            else if (state.runs.isEmpty()) item { EmptyCard("还没有 Run", "新的 Run 出现后会显示在这里。") }
            else {
                items(state.runs, key = Run::id) { run -> RunRow(run) { viewModel.openRun(run) } }
                if (state.hasMoreRuns || state.loadingMore) item { SecondaryButton(if (state.loadingMore) "正在加载…" else "加载更多 Runs", !state.loadingMore) { viewModel.loadRuns() } }
            }
        }
    }
    if (picker) MetricPickerSheet(state.projectMetrics, state.projectSelection, { picker = false }) { picker = false; viewModel.selectProjectCurves(it) }
}

@Composable
private fun RunRow(run: Run, onClick: () -> Unit) {
    SurfaceCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(run.displayName, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                StatusPill(run.state)
            }
            Row {
                Text(run.name, Modifier.weight(1f), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                Text(run.heartbeatAt.ifBlank { run.createdAt }, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

@Composable
private fun RunDetailScreen(state: AppUiState, viewModel: AppViewModel) {
    val run = state.currentRun ?: return
    var tab by remember(run.id) { mutableIntStateOf(0) }
    var picker by remember { mutableStateOf(false) }
    val details = state.runDetails
    Column(Modifier.fillMaxSize()) {
        PageHeader(run.displayName, "Run ${run.name}", viewModel::back)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SurfaceCard(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusPill(run.state)
                            Spacer(Modifier.weight(1f))
                            Text(run.heartbeatAt.ifBlank { "—" }, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row {
                            SmallValue("History", if (run.historyLineCount > 0) run.historyLineCount.toString() else "—", Modifier.weight(1f))
                            SmallValue("最后更新", run.heartbeatAt.ifBlank { "—" }, Modifier.weight(1f))
                        }
                    }
                }
            }
            item { SegmentedControl(listOf("Curves", "Summary", "Config"), tab) { tab = it } }
            if (state.loading && details == null) item { LoadingCard("正在加载 Run 详情") }
            else if (state.error.isNotBlank() && details == null) item { ErrorCard("无法加载 Run 详情", state.error) { viewModel.openRun(run) } }
            else when (tab) {
                0 -> {
                    item {
                        PrimaryAction(
                            if (state.runSelection.isEmpty()) "选择曲线" else "选择曲线  ·  已选 ${state.runSelection.size}",
                            enabled = details?.metrics?.isNotEmpty() == true && !state.loadingCurves,
                        ) { picker = true }
                    }
                    if (state.loadingCurves) item { LoadingCard("正在加载所选曲线…") }
                    if (state.runSelection.isEmpty() && !state.loadingCurves) {
                        item { EmptyCard(if (details?.metrics.isNullOrEmpty()) "暂无可绘制曲线" else "选择要关注的曲线", if (details?.metrics.isNullOrEmpty()) "这个 Run 没有数值历史指标。" else "点击“选择曲线”，最多显示 8 个指标。") }
                    } else if (!state.loadingCurves) {
                        details?.metrics.orEmpty().filter { it.id in state.runSelection }.forEach { metric ->
                            item(metric.id) { MetricChart(metric.key, run.displayName, state.runCurves.filter { it.metricKey == metric.key }) }
                        }
                    }
                    if (state.rejectedCurveCount > 0) item { InfoCard("${state.rejectedCurveCount} 个指标没有足够的数值历史，无法绘制。") }
                }
                1 -> {
                    item { SectionLabel("SUMMARY") }
                    if (details?.summary.isNullOrEmpty()) item { EmptyCard("暂无 Summary", "") }
                    else item { KeyValueCard(requireNotNull(details).summary) }
                    if (!details?.system.isNullOrEmpty()) {
                        item { SectionLabel("SYSTEM") }
                        item { KeyValueCard(requireNotNull(details).system) }
                    }
                }
                else -> {
                    item { SectionLabel("CONFIG") }
                    if (details?.config.isNullOrEmpty()) item { EmptyCard("暂无 Config", "") }
                    else item { KeyValueCard(requireNotNull(details).config) }
                }
            }
            if (state.error.isNotBlank() && details != null) item { Text(state.error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
        }
    }
    if (picker) MetricPickerSheet(details?.metrics.orEmpty(), state.runSelection, { picker = false }) { picker = false; viewModel.selectRunCurves(it) }
}

@Composable
private fun KeyValueCard(items: List<MetricValue>) {
    SurfaceCard(Modifier.fillMaxWidth()) {
        Column {
            items.forEachIndexed { index, item ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
                    Text(item.key, Modifier.weight(0.44f), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.width(10.dp))
                    Text(item.value, Modifier.weight(0.56f), fontSize = 12.sp, maxLines = 5, overflow = TextOverflow.Ellipsis)
                }
                if (index != items.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
            }
        }
    }
}

@Composable
private fun SmallValue(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable private fun SectionLabel(text: String) = Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun LoadingCard(message: String) {
    SurfaceCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(20.dp), color = WandColors.Accent, strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp)); Text(message, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ErrorCard(title: String, message: String, retry: () -> Unit) {
    SurfaceCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(message, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = retry) { Icon(Icons.Rounded.Refresh, null); Spacer(Modifier.width(4.dp)); Text("重试") }
        }
    }
}

@Composable
private fun InfoCard(text: String) {
    Text(text, Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SecondaryButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().height(46.dp), shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
    ) { Text(text) }
}

@Composable
private fun UpdateDialog(info: UpdateInfo, onDismiss: () -> Unit, onInstall: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("发现 WandScope ${info.versionName}") },
        text = { Text("更新包下载后会先校验 SHA-256，再交给 Android 系统安装器。系统仍会要求你确认安装，应用不会静默更新。") },
        dismissButton = { TextButton(onClick = onDismiss) { Text("稍后") } },
        confirmButton = { TextButton(onClick = onInstall) { Text("下载更新") } },
    )
}
