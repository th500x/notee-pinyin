package com.notee.pinyin.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidateRowTest {
    private val none = listOf(false, false)

    @Test
    fun twoShortWordsKeepTheInset() {
        val fit = CandidateRow.fit(listOf(40f, 40f), none, room = 300f, start = 8f, padding = 14f, minPadding = 4f, largeHole = 56f, minScale = 0.83f)
        assertEquals(14f, fit.padding, 0.01f)
        assertEquals(1f, fit.scale, 0.001f)
    }

    @Test
    fun aWideRowKeepsDocumentAtTheUsualInset() {
        // 392dp row, Emoji reserved: room 264. Roboto at 18sp already fits both words.
        val fit = row(listOf(84.6f, 110.4f), room = 264f)
        assertEquals(14f, fit.padding, 0.01f)
        assertEquals(1f, fit.scale, 0.001f)
        assertEquals(2, shown(listOf(84.6f, 110.4f), 264f, fit))
    }

    @Test
    fun aNarrowRowPullsDocumentaryInByTightening() {
        // 360dp row, Emoji reserved: room 238. At 14dp the second word misses by about 21dp.
        val cores = listOf(84.6f, 110.4f)
        val fit = row(cores, room = 238f)
        assertEquals(1f, fit.scale, 0.001f)
        assertTrue(fit.padding < 14f)
        assertTrue(fit.padding >= 8f)
        assertEquals(2, shown(cores, 238f, fit))
        assertEquals(1, CandidateRow.count(cores, none, 238f, 8f, fit.padding + 1f, 1f))
    }

    @Test
    fun aShortRemainderStaysAtTheUsualInset() {
        // One more character would need 46dp and only 30dp is left.
        val cores = listOf(18f, 18f)
        val fit = row(cores, room = 84f)
        assertEquals(14f, fit.padding, 0.01f)
        assertEquals(1f, fit.scale, 0.001f)
        assertEquals(1, shown(cores, 84f, fit))
    }

    @Test
    fun theTextShrinksOnlyAfterTheInsetCannot() {
        val cores = listOf(100f, 140f)
        val fit = row(cores, room = 238f)
        assertEquals(4f, fit.padding, 0.01f)
        assertTrue(fit.scale < 1f)
        assertTrue(fit.scale > 0.85f)
        assertEquals(2, shown(cores, 238f, fit))
    }

    @Test
    fun aWordThatStillDoesNotFitLeavesTheRowAlone() {
        val cores = listOf(90f, 400f)
        val fit = row(cores, room = 238f)
        assertEquals(14f, fit.padding, 0.01f)
        assertEquals(1f, fit.scale, 0.001f)
        assertEquals(1, shown(cores, 238f, fit))
    }

    private fun row(cores: List<Float>, room: Float) = CandidateRow.fit(
        cores, List(cores.size) { false }, room, start = 8f,
        padding = 14f, minPadding = 4f, largeHole = 56f, minScale = 14f / 18f,
    )

    private fun shown(cores: List<Float>, room: Float, fit: CandidateRow.Fit) =
        CandidateRow.count(cores, List(cores.size) { false }, room, 8f, fit.padding, fit.scale)
}
