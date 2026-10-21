package com.notee.pinyin.festival

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlessingCopyTest {
    @Test
    fun everyFestivalHasAnOriginAndFiveBlessingsWithinTheLimit() {
        for (festival in Festival.entries) {
            val originZh = BlessingCopy.origin(festival, false)
            val originEn = BlessingCopy.origin(festival, true)
            assertTrue("${festival.name} origin zh ${originZh.length}: $originZh", originZh.length in 1..50)
            assertTrue("${festival.name} origin en ${originEn.length}: $originEn", originEn.length in 1..100)
            for (index in 0 until BlessingCopy.COUNT) {
                val zh = BlessingCopy.blessing(festival, false, index)
                val en = BlessingCopy.blessing(festival, true, index)
                assertTrue("${festival.name} $index zh ${zh.length}: $zh", zh.length in 1..50)
                assertTrue("${festival.name} $index en ${en.length}: $en", en.length in 1..100)
            }
            assertEquals(5, BlessingCopy.COUNT)
        }
    }
}
