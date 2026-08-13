package com.guiyanghai.wandscope

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
fun MetricChart(title: String, subtitle: String, series: List<ChartSeries>) {
    SurfaceCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (series.all { it.points.isEmpty() }) {
                Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                    Text("暂无可绘制数据", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
            } else {
                ChartCanvas(series, Modifier.fillMaxWidth().height(168.dp))
                HorizontalAxis(series)
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
private fun ChartCanvas(series: List<ChartSeries>, modifier: Modifier) {
    val grid = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val axis = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
    Canvas(modifier) {
        val all = series.flatMap { it.points }.filter { it.x.isFinite() && it.y.isFinite() }
        if (all.isEmpty()) return@Canvas
        var minX = all.minOf { it.x }; var maxX = all.maxOf { it.x }
        var minY = all.minOf { it.y }; var maxY = all.maxOf { it.y }
        if (abs(maxX - minX) < 1e-12) { minX -= 1.0; maxX += 1.0 }
        if (abs(maxY - minY) < 1e-12) { minY -= 1.0; maxY += 1.0 }
        repeat(5) { i ->
            val y = size.height * i / 4f
            drawLine(grid, Offset(0f, y), Offset(size.width, y), 1f)
        }
        repeat(3) { i ->
            val x = size.width * i / 2f
            drawLine(grid, Offset(x, 0f), Offset(x, size.height), 1f)
        }
        drawLine(axis, Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f), 1.5.dp.toPx())
        series.forEachIndexed { index, item ->
            val points = item.points.filter { it.x.isFinite() && it.y.isFinite() }
            if (points.isEmpty()) return@forEachIndexed
            val path = Path()
            points.forEachIndexed { pointIndex, point ->
                val x = ((point.x - minX) / (maxX - minX) * size.width).toFloat()
                val y = (size.height - (point.y - minY) / (maxY - minY) * size.height).toFloat()
                if (pointIndex == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            val color = WandColors.ChartPalette[index % WandColors.ChartPalette.size]
            if (points.size == 1) drawCircle(color, 4.dp.toPx(), path.getBounds().center)
            else drawPath(path, color, style = Stroke(width = 2.3.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

@Composable
private fun HorizontalAxis(series: List<ChartSeries>) {
    val points = series.flatMap { it.points }.filter { it.x.isFinite() }
    if (points.isEmpty()) return
    val min = points.minOf { it.x }
    val max = points.maxOf { it.x }
    val middle = (min + max) / 2.0
    val source = series.firstOrNull()?.source ?: MetricSource.HISTORY
    Column(Modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(7.dp)) {
            val color = androidx.compose.ui.graphics.Color.Gray.copy(alpha = 0.75f)
            drawLine(color, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
            repeat(3) { index ->
                val x = size.width * index / 2f
                drawLine(color, Offset(x, 0f), Offset(x, 5.dp.toPx()), 1.dp.toPx())
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Text(formatAxisValue(min, source), Modifier.weight(1f), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatAxisValue(middle, source), Modifier.weight(1f), fontSize = 10.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatAxisValue(max, source), Modifier.weight(1f), fontSize = 10.sp, textAlign = TextAlign.End, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        Column(Modifier.fillMaxWidth().height(560.dp)) {
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
                val items = groups[category].orEmpty()
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    items(items, key = MetricDefinition::id) { metric ->
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
