package com.guiyanghai.wandscope

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val ChartYAxisWidth = 54.dp
private val ChartPlotHeight = 180.dp

private data class ChartBounds(
    val minX: Double,
    val maxX: Double,
    val minY: Double,
    val maxY: Double,
)

@Composable
fun BrandMark(modifier: Modifier = Modifier, size: Int = 58) {
    Box(
        modifier = modifier.size(size.dp).clip(RoundedCornerShape((size * 0.25f).dp)).background(Color(0xFF111114)),
        contentAlignment = Alignment.Center,
    ) {
        Text("W", color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size * 0.45f).sp)
    }
}

@Composable
fun PageHeader(title: String, subtitle: String, onBack: (() -> Unit)? = null, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
            ) { Icon(Icons.Rounded.ArrowBackIosNew, contentDescription = "返回", modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1)
        }
        action?.invoke()
    }
}

@Composable
fun SurfaceCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.75f), RoundedCornerShape(20.dp))
            .padding(16.dp),
    ) { content() }
}

@Composable
fun StatTile(label: String, value: String, tint: Color, modifier: Modifier = Modifier) {
    SurfaceCard(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(tint))
            Text(value, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun StatusPill(state: String) {
    val normalized = state.lowercase()
    val color = when (normalized) {
        "running" -> WandColors.Green
        "finished", "completed" -> WandColors.Accent
        "failed", "crashed", "killed" -> WandColors.Red
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        state.ifBlank { "unknown" }.replaceFirstChar { it.uppercase() },
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(CircleShape).background(color.copy(alpha = 0.12f)).padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
fun SegmentedControl(labels: List<String>, selected: Int, onSelected: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(3.dp),
    ) {
        labels.forEachIndexed { index, label ->
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                    .background(if (index == selected) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable { onSelected(index) }.padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) { Text(label, fontSize = 13.sp, fontWeight = if (index == selected) FontWeight.SemiBold else FontWeight.Normal) }
        }
    }
}

@Composable
fun PrimaryAction(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
    ) { Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun EmptyCard(title: String, message: String) {
    SurfaceCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            if (message.isNotBlank()) Text(message, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun MetricChart(
    title: String,
    subtitle: String,
    series: List<ChartSeries>,
    onRemove: (() -> Unit)? = null,
) {
    if (onRemove == null) {
        MetricChartCard(title, subtitle, series)
        return
    }
    var dragOffset by remember(title) { mutableFloatStateOf(0f) }
    var dragging by remember(title) { mutableStateOf(false) }
    var cardWidth by remember(title) { mutableFloatStateOf(1f) }
    val threshold = cardWidth * 0.28f
    val visibleOffset by animateFloatAsState(
        targetValue = dragOffset,
        animationSpec = if (dragging) snap() else spring(),
        label = "curve-remove-offset",
    )
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(WandColors.Red),
    ) {
        Row(
            Modifier.matchParentSize().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = Color.White)
            Text("移除曲线", color = Color.White, fontWeight = FontWeight.SemiBold)
        }
        Box(
            Modifier.fillMaxWidth()
                .onSizeChanged { cardWidth = it.width.toFloat().coerceAtLeast(1f) }
                .offset { IntOffset(visibleOffset.roundToInt(), 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        dragOffset = (dragOffset + delta).coerceIn(0f, cardWidth)
                    },
                    onDragStarted = { dragging = true },
                    onDragStopped = {
                        dragging = false
                        if (CurveDismissPolicy.shouldDismiss(dragOffset, threshold)) onRemove()
                        else dragOffset = 0f
                    },
                ),
        ) {
            MetricChartCard(title, "$subtitle · 向右滑动移除", series)
        }
    }
}

@Composable
private fun MetricChartCard(title: String, subtitle: String, series: List<ChartSeries>) {
    val bounds = remember(series) { chartBounds(series) }
    SurfaceCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (bounds == null) {
                Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                    Text("暂无可绘制数据", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
            } else {
                ChartPlot(series, bounds)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    series.take(6).forEachIndexed { index, item ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(7.dp).clip(CircleShape).background(WandColors.ChartPalette[index % WandColors.ChartPalette.size]))
                            Spacer(Modifier.width(5.dp))
                            Text(item.label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartPlot(series: List<ChartSeries>, bounds: ChartBounds) {
    val source = series.firstOrNull()?.source ?: MetricSource.HISTORY
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(ChartPlotHeight)) {
            VerticalAxis(bounds, Modifier.width(ChartYAxisWidth).fillMaxHeight())
            ChartCanvas(series, bounds, Modifier.weight(1f).fillMaxHeight())
        }
        HorizontalAxis(bounds, source, Modifier.fillMaxWidth().padding(start = ChartYAxisWidth))
    }
}

@Composable
private fun VerticalAxis(bounds: ChartBounds, modifier: Modifier = Modifier) {
    Box(modifier.padding(end = 7.dp, top = 4.dp, bottom = 4.dp)) {
        val color = MaterialTheme.colorScheme.onSurfaceVariant
        Text(compactNumber(bounds.maxY), Modifier.align(Alignment.TopEnd), fontSize = 9.sp, color = color, maxLines = 1)
        Text(compactNumber((bounds.minY + bounds.maxY) / 2.0), Modifier.align(Alignment.CenterEnd), fontSize = 9.sp, color = color, maxLines = 1)
        Text(compactNumber(bounds.minY), Modifier.align(Alignment.BottomEnd), fontSize = 9.sp, color = color, maxLines = 1)
    }
}

@Composable
private fun ChartCanvas(series: List<ChartSeries>, bounds: ChartBounds, modifier: Modifier) {
    val grid = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val axis = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
    Canvas(modifier) {
        val inset = 5.dp.toPx()
        val left = inset
        val right = (size.width - inset).coerceAtLeast(left + 1f)
        val top = inset
        val bottom = (size.height - inset).coerceAtLeast(top + 1f)
        val plotWidth = right - left
        val plotHeight = bottom - top
        repeat(5) { i ->
            val y = top + plotHeight * i / 4f
            drawLine(grid, Offset(left, y), Offset(right, y), 1f)
        }
        repeat(3) { i ->
            val x = left + plotWidth * i / 2f
            drawLine(grid, Offset(x, top), Offset(x, bottom), 1f)
        }
        drawLine(axis, Offset(left, bottom), Offset(right, bottom), 1.5.dp.toPx())
        drawLine(axis, Offset(left, top), Offset(left, bottom), 1.5.dp.toPx())
        series.forEachIndexed { index, item ->
            val points = item.points.filter { it.x.isFinite() && it.y.isFinite() }
            if (points.isEmpty()) return@forEachIndexed
            val path = Path()
            points.forEachIndexed { pointIndex, point ->
                val x = (left + (point.x - bounds.minX) / (bounds.maxX - bounds.minX) * plotWidth).toFloat()
                val y = (bottom - (point.y - bounds.minY) / (bounds.maxY - bounds.minY) * plotHeight).toFloat()
                if (pointIndex == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            val color = WandColors.ChartPalette[index % WandColors.ChartPalette.size]
            if (points.size == 1) drawCircle(color, 4.dp.toPx(), path.getBounds().center)
            else drawPath(path, color, style = Stroke(width = 2.3.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

@Composable
private fun HorizontalAxis(bounds: ChartBounds, source: MetricSource, modifier: Modifier = Modifier) {
    val middle = (bounds.minX + bounds.maxX) / 2.0
    Column(modifier.padding(horizontal = 5.dp)) {
        Canvas(Modifier.fillMaxWidth().height(7.dp)) {
            val color = androidx.compose.ui.graphics.Color.Gray.copy(alpha = 0.75f)
            drawLine(color, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
            repeat(3) { index ->
                val x = size.width * index / 2f
                drawLine(color, Offset(x, 0f), Offset(x, 5.dp.toPx()), 1.dp.toPx())
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Text(formatAxisValue(bounds.minX, source), Modifier.weight(1f), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatAxisValue(middle, source), Modifier.weight(1f), fontSize = 10.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatAxisValue(bounds.maxX, source), Modifier.weight(1f), fontSize = 10.sp, textAlign = TextAlign.End, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            if (source == MetricSource.SYSTEM) "时间" else "Step",
            Modifier.fillMaxWidth().padding(top = 2.dp),
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun chartBounds(series: List<ChartSeries>): ChartBounds? {
    val points = series.flatMap { it.points }.filter { it.x.isFinite() && it.y.isFinite() }
    if (points.isEmpty()) return null
    var minX = points.minOf { it.x }
    var maxX = points.maxOf { it.x }
    var minY = points.minOf { it.y }
    var maxY = points.maxOf { it.y }
    if (abs(maxX - minX) < 1e-12) {
        minX -= 1.0
        maxX += 1.0
    }
    if (abs(maxY - minY) < 1e-12) {
        val padding = maxOf(abs(minY) * 0.05, 1.0)
        minY -= padding
        maxY += padding
    }
    return ChartBounds(minX, maxX, minY, maxY)
}

private fun formatAxisValue(value: Double, source: MetricSource): String {
    if (source == MetricSource.SYSTEM && value.isFinite() && value > 1_000_000_000.0) {
        val milliseconds = if (value > 10_000_000_000.0) value.toLong() else (value * 1000.0).toLong()
        return runCatching {
            DateTimeFormatter.ofPattern("HH:mm:ss", Locale.getDefault())
                .withZone(ZoneId.systemDefault())
                .format(Instant.ofEpochMilli(milliseconds))
        }.getOrNull() ?: compactNumber(value)
    }
    return compactNumber(value)
}

private fun compactNumber(value: Double): String = when {
    !value.isFinite() -> "—"
    abs(value) >= 1_000_000 || (abs(value) in 0.0..0.001 && value != 0.0) -> "%.2e".format(Locale.US, value)
    abs(value - value.toLong()) < 1e-9 -> value.toLong().toString()
    else -> "%.3f".format(Locale.US, value).trimEnd('0').trimEnd('.')
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetricPickerSheet(
    metrics: List<MetricDefinition>,
    initial: List<String>,
    onDismiss: () -> Unit,
    onDone: (List<String>) -> Unit,
) {
    val selectable = remember(metrics) { MetricSelectionPolicy.selectable(metrics) }
    var selected by remember(initial) { mutableStateOf(initial.filter { id -> selectable.any { it.id == id } }.take(8)) }
    var category by remember { mutableStateOf<String?>(null) }
    val groups = remember(selectable) { selectable.groupBy { it.group.ifBlank { "Charts" } } }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxWidth().height(620.dp).imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (category != null) {
                    IconButton(onClick = { category = null }) { Icon(Icons.Rounded.ArrowBackIosNew, "返回") }
                } else Spacer(Modifier.width(48.dp))
                Text(category ?: "选择曲线", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text("完成", color = WandColors.Accent, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { onDone(selected) }.padding(12.dp))
            }
            Text("仅显示数值历史指标 · 最多 8 个 · 已选 ${selected.size}", modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (category == null) {
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    items(groups.keys.sortedWith(compareBy<String> { if (it == "System") 0 else if (it == "Charts") 1 else 2 }.thenBy { it })) { group ->
                        val groupMetrics = groups[group].orEmpty()
                        val count = groupMetrics.count { it.id in selected }
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { category = group }
                                .background(MaterialTheme.colorScheme.surface).padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(group, fontWeight = FontWeight.SemiBold)
                                Text("${groupMetrics.size} 个指标${if (count > 0) " · 已选 $count" else ""}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            } else {
                var query by remember(category) { mutableStateOf("") }
                val categoryMetrics = groups[category].orEmpty()
                val filteredMetrics = remember(categoryMetrics, query) { MetricSearchPolicy.filter(categoryMetrics, query) }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    placeholder = { Text("搜索 ${category.orEmpty()} 指标") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "清除搜索") }
                        }
                    },
                )
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    if (filteredMetrics.isEmpty()) {
                        item {
                            Text(
                                "没有匹配的指标",
                                Modifier.fillMaxWidth().padding(vertical = 32.dp),
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(filteredMetrics, key = MetricDefinition::id) { metric ->
                        val checked = metric.id in selected
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                selected = if (checked) selected - metric.id else if (selected.size < 8) selected + metric.id else selected
                            }.padding(horizontal = 4.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(MetricGroupingPolicy.displayName(metric.key, metric.group), Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Box(
                                Modifier.size(24.dp).clip(CircleShape)
                                    .background(if (checked) WandColors.Accent else Color.Transparent)
                                    .border(1.dp, if (checked) WandColors.Accent else MaterialTheme.colorScheme.outline, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) { if (checked) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    }
                }
            }
        }
    }
}
