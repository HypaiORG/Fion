package com.hypai.fion.ui.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PaneSpansTest {
    private fun spans(
        start: Int,
        end: Int,
        expanded: Boolean = false,
        large: Boolean = false,
        heightOk: Boolean = true,
        minWidth: Int = 280,
        vararg folds: IntRange,
    ) = computePaneSpans(
        start = start,
        end = end,
        widthAtLeastExpanded = expanded,
        widthAtLeastLarge = large,
        heightAtLeastMedium = heightOk,
        gap = 24,
        minWidth = minWidth,
        folds = folds.toList(),
    )

    @Test
    fun `compact and medium widths without folds stay single pane`() {
        assertEquals(listOf(12 until 399), spans(12, 399))
        assertEquals(listOf(12 until 588), spans(12, 588))
    }

    @Test
    fun `expanded and large widths split evenly without folds`() {
        assertEquals(listOf(12 until 408, 432 until 828), spans(12, 828, expanded = true))
        assertEquals(listOf(12 until 416, 440 until 844, 868 until 1272), spans(12, 1272, expanded = true, large = true))
    }

    @Test
    fun `compact height stays single pane even on wide windows`() {
        assertEquals(listOf(12 until 900), spans(12, 900, expanded = true, heightOk = false))
        assertEquals(listOf(12 until 828), spans(12, 828, expanded = true, heightOk = false, folds = arrayOf(420 until 420)))
    }

    @Test
    fun `gutter centers on each vertical fold`() {
        assertEquals(listOf(12 until 324, 348 until 661), spans(12, 661, folds = arrayOf(336 until 336)))
        assertEquals(
            listOf(12 until 416, 440 until 844, 868 until 1272),
            spans(12, 1272, expanded = true, large = true, folds = arrayOf(856 until 856, 428 until 428)),
        )
    }

    @Test
    fun `single vertical fold on large screen stays dual pane to avoid straddling hinge`() {
        assertEquals(listOf(12 until 638, 662 until 1288), spans(12, 1288, expanded = true, large = true, folds = arrayOf(650 until 650)))
    }

    @Test
    fun `occluding hinge widens the gutter to cover it`() {
        assertEquals(listOf(12 until 400, 440 until 828), spans(12, 828, expanded = true, folds = arrayOf(400 until 440)))
    }

    @Test
    fun `narrow fold after cutout and margin falls back to single instead of straddling fold`() {
        assertEquals(listOf(52 until 528), spans(52, 528, folds = arrayOf(260 until 260)))
    }

    @Test
    fun `overlapping or narrow folds fall back cleanly to window breakpoints`() {
        assertEquals(
            listOf(12 until 416, 440 until 844, 868 until 1272),
            spans(12, 1272, expanded = true, large = true, folds = arrayOf(200 until 200)),
        )
        assertEquals(
            listOf(12 until 408, 432 until 828),
            spans(12, 828, expanded = true, folds = arrayOf(400 until 450, 420 until 470)),
        )
    }

    @Test
    fun `large width with narrow available space steps down from triple to dual`() {
        assertEquals(listOf(100 until 438, 462 until 800), spans(100, 800, expanded = true, large = true))
    }

    @Test
    fun `split merges trailing columns into one wide pane`() {
        val tri = WindowPanes(listOf(12 until 416, 440 until 844, 868 until 1272))
        assertEquals(listOf(12..415, 440..1271), tri.split(1))
        assertEquals(listOf(12..843, 868..1271), tri.split(2))
        assertEquals(listOf(12..407, 432..827), WindowPanes(listOf(12 until 408, 432 until 828)).split(1))
    }

    @Test
    fun `split rejects out of range lead`() {
        val single = WindowPanes(listOf(12 until 399))
        assertThrows(IllegalArgumentException::class.java) { single.split(1) }
        val dual = WindowPanes(listOf(12 until 408, 432 until 828))
        assertThrows(IllegalArgumentException::class.java) { dual.split(0) }
        assertThrows(IllegalArgumentException::class.java) { dual.split(2) }
    }
}
