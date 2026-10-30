package com.notee.pinyin.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnglishPlacesTest {
    @Test
    fun finishedPlaceNamesAreTitleCase() {
        assertEquals("Bangkok", EnglishPlaces.rewrite("bangkok"))
        assertEquals("Pattaya", EnglishPlaces.rewrite("pattaya"))
        assertEquals("Shanghai", EnglishPlaces.rewrite("shanghai"))
        assertEquals("China", EnglishPlaces.rewrite("china"))
        assertEquals("Turkey", EnglishPlaces.rewrite("turkey"))
        assertEquals("Xi'an", EnglishPlaces.rewrite("xi'an"))
        assertEquals("Xian", EnglishPlaces.rewrite("xian"))
    }

    @Test
    fun capitalsAndOrdinaryWordsStayAsTyped() {
        assertNull(EnglishPlaces.rewrite("Bangkok"))
        assertNull(EnglishPlaces.rewrite("BANGKOK"))
        assertNull(EnglishPlaces.rewrite("bAngkok"))
        assertNull(EnglishPlaces.rewrite("hello"))
        assertNull(EnglishPlaces.rewrite("march"))
        assertNull(EnglishPlaces.rewrite("may"))
        assertNull(EnglishPlaces.rewrite("mobile"))
        assertNull(EnglishPlaces.rewrite("nice"))
        assertNull(EnglishPlaces.rewrite("reading"))
        assertNull(EnglishPlaces.rewrite("split"))
        assertNull(EnglishPlaces.rewrite("bath"))
        assertNull(EnglishPlaces.rewrite("shang"))
        assertNull(EnglishPlaces.rewrite("bang"))
    }

    @Test
    fun aPrefixOffersTheLongerPlace() {
        assertTrue("Pattaya" in EnglishPlaces.longerThan("patt", 8))
        assertTrue("Bangkok" in EnglishPlaces.longerThan("bang", 8))
        assertTrue(EnglishPlaces.longerThan("bangkok", 8).none { it.equals("bangkok", ignoreCase = true) })
    }
}
