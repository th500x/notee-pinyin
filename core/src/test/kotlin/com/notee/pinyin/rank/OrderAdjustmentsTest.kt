package com.notee.pinyin.rank

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OrderAdjustmentsTest {
    @Test
    fun baAndMinNinAreTheSameList() {
        assertEquals(listOf("吧", "把", "八"), OrderAdjustments.pinOrder("ba", listOf("把", "吧", "八")))
        assertEquals(listOf("您", "民", "敏"), OrderAdjustments.together(setOf("min", "nin")))
        assertEquals(listOf("您", "民", "敏"), OrderAdjustments.together(setOf("nin", "min")))
        assertEquals(listOf("了"), OrderAdjustments.together(setOf("ke", "le")))
        assertEquals(listOf("了"), OrderAdjustments.together(setOf("le", "ke")))
        assertEquals("租金", OrderAdjustments.phrases.single().earlier)
        assertEquals("租赁", OrderAdjustments.phrases.single().later)
    }

    @Test
    fun aChosenReadingIsNotTheGroup() {
        assertNull(OrderAdjustments.together(setOf("min")))
        assertNull(OrderAdjustments.together(setOf("nin")))
        assertNull(OrderAdjustments.together(setOf("ke")))
        assertNull(OrderAdjustments.together(setOf("le")))
        assertEquals(listOf("民", "敏"), OrderAdjustments.pinOrder("min", listOf("民", "敏")))
    }
}
