package com.guiyanghai.wandscope

import org.junit.Assert.assertEquals
import org.junit.Test

class MetricSelectionPolicyTest {
    @Test
    fun `wandb history type counts accept numeric types but reject mixed and strings`() {
        assertEquals(true, MetricTypePolicy.isNumericHistory(listOf("number")))
        assertEquals(true, MetricTypePolicy.isNumericHistory(listOf("float64", "none")))
        assertEquals(false, MetricTypePolicy.isNumericHistory(listOf("string")))
        assertEquals(false, MetricTypePolicy.isNumericHistory(listOf("number", "string")))
        assertEquals(false, MetricTypePolicy.isNumericHistory(emptyList()))
    }

    @Test
    fun `history categories follow the run metric namespace`() {
        assertEquals("Train", MetricGroupingPolicy.category("train/loss", MetricSource.HISTORY))
        assertEquals("Validation", MetricGroupingPolicy.category("validation/accuracy", MetricSource.HISTORY))
        assertEquals("Charts", MetricGroupingPolicy.category("epoch", MetricSource.HISTORY))
        assertEquals("System", MetricGroupingPolicy.category("cpu", MetricSource.SYSTEM))
        assertEquals("loss", MetricGroupingPolicy.displayName("train/loss", "Train"))
        assertEquals("epoch", MetricGroupingPolicy.displayName("epoch", "Charts"))
    }

    @Test
    fun `metric search filters inside a category using full and display names`() {
        val metrics = listOf(
            MetricDefinition("1", "train/loss", MetricSource.HISTORY, MetricKind.NUMBER, "Train", true),
            MetricDefinition("2", "train/accuracy", MetricSource.HISTORY, MetricKind.NUMBER, "Train", true),
        )

        assertEquals(listOf("1"), MetricSearchPolicy.filter(metrics, "LOSS").map { it.id })
        assertEquals(listOf("2"), MetricSearchPolicy.filter(metrics, "train/acc").map { it.id })
        assertEquals(listOf("1", "2"), MetricSearchPolicy.filter(metrics, "  ").map { it.id })
    }

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

    @Test
    fun `swiping a chart removes only that metric selection`() {
        val selected = listOf("history:loss", "history:accuracy", "system:cpu")

        assertEquals(
            listOf("history:loss", "system:cpu"),
            MetricSelectionPolicy.remove(selected, "history:accuracy"),
        )
        assertEquals(selected, MetricSelectionPolicy.remove(selected, "missing"))
    }

    @Test
    fun `curve delete action is revealed only by a sufficient left drag`() {
        assertEquals(false, CurveDismissPolicy.shouldReveal(-27f, 100f))
        assertEquals(true, CurveDismissPolicy.shouldReveal(-100f, 100f))
        assertEquals(false, CurveDismissPolicy.shouldReveal(20f, 100f))
    }

    @Test
    fun `download progress reserves the final ten percent for verification`() {
        assertEquals(null, UpdateProgressPolicy.percentage(20, -1))
        assertEquals(0, UpdateProgressPolicy.percentage(0, 100))
        assertEquals(45, UpdateProgressPolicy.percentage(50, 100))
        assertEquals(90, UpdateProgressPolicy.percentage(100, 100))
    }

    @Test
    fun `run timestamps are rendered as standard Beijing time`() {
        assertEquals("2026-08-13 14:24:09 北京时间", RunTimeFormatter.beijing("2026-08-13T06:24:09Z"))
        assertEquals("2026-08-13 14:24:09 北京时间", RunTimeFormatter.beijing("2026-08-13T14:24:09+08:00"))
        assertEquals("—", RunTimeFormatter.beijing(""))
        assertEquals("not-a-time", RunTimeFormatter.beijing("not-a-time"))
    }

    @Test
    fun `run completion notifications skip baseline and notify new finished runs once`() {
        val running = run("running", "running")
        val finished = run("finished", "finished")
        val completed = run("completed", "completed")
        val failed = run("failed", "failed")

        assertEquals(
            emptyList<Run>(),
            RunCompletionPolicy.notificationCandidates(false, listOf(finished), emptySet()),
        )
        assertEquals(
            listOf(finished, completed),
            RunCompletionPolicy.notificationCandidates(true, listOf(running, finished, completed, failed), emptySet()),
        )
        assertEquals(
            listOf(completed),
            RunCompletionPolicy.notificationCandidates(true, listOf(finished, completed), setOf(finished.id)),
        )
    }

    private fun metric(id: String, source: MetricSource, kind: MetricKind, plottable: Boolean) =
        MetricDefinition(id, id.substringAfter(':'), source, kind, "Charts", plottable)

    private fun run(id: String, state: String) = Run(id, id, id, state)
}
