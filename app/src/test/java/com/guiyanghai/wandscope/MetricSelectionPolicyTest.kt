package com.guiyanghai.wandscope

import org.junit.Assert.assertEquals
import org.junit.Test

class MetricSelectionPolicyTest {
    @Test
    fun `only explicit numeric history and system metrics are selectable`() {
        val metrics = listOf(
            metric("history:number", MetricSource.HISTORY, MetricKind.NUMBER, true),
            metric("history:unknown", MetricSource.HISTORY, MetricKind.UNKNOWN, true),
            metric("system:number", MetricSource.SYSTEM, MetricKind.NUMBER, true),
            metric("summary:number", MetricSource.SUMMARY, MetricKind.NUMBER, true),
            metric("history:hidden", MetricSource.HISTORY, MetricKind.NUMBER, false),
        )

        assertEquals(listOf("history:number", "system:number"), MetricSelectionPolicy.selectable(metrics).map { it.id })
    }

    @Test
    fun `selection is deduplicated limited and removes stale ids`() {
        val metrics = (1..12).map { metric("history:$it", MetricSource.HISTORY, MetricKind.NUMBER, true) }
        val input = listOf("stale", "history:1", "history:1") + (2..12).map { "history:$it" }

        assertEquals((1..8).map { "history:$it" }, MetricSelectionPolicy.normalize(input, metrics))
    }

    private fun metric(id: String, source: MetricSource, kind: MetricKind, plottable: Boolean) =
        MetricDefinition(id, id.substringAfter(':'), source, kind, "Charts", plottable)
}
