package com.notee.pinyin.math

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArithmeticTest {
    @Test
    fun fourOperators() {
        assertEquals("19", Arithmetic.evaluate("12+7"))
        assertEquals("-2", Arithmetic.evaluate("3−5"))
        assertEquals("42", Arithmetic.evaluate("6×7"))
        assertEquals("42", Arithmetic.evaluate("6*7"))
        assertEquals("4", Arithmetic.evaluate("8÷2"))
        assertEquals("4", Arithmetic.evaluate("8/2"))
    }

    @Test
    fun chainsMultiplyAndDivideFirst() {
        assertEquals("6", Arithmetic.evaluate("1+2+3"))
        assertEquals("7", Arithmetic.evaluate("1+2×3"))
        assertEquals("3", Arithmetic.evaluate("1+2×3−4"))
        assertEquals("4", Arithmetic.evaluate("10−2×3"))
        assertEquals("-4", Arithmetic.evaluate("1-2-3"))
        assertEquals("2", Arithmetic.evaluate("8÷2÷2"))
        assertEquals("26", Arithmetic.evaluate("2×3+4×5"))
        assertEquals("1", Arithmetic.evaluate("1÷3×3"))
        assertEquals("0", Arithmetic.evaluate("1/3-1/3"))
    }

    @Test
    fun answersKeepTwoPlacesWithoutTrailingZeros() {
        assertEquals("0.33", Arithmetic.evaluate("1/3"))
        assertEquals("0.67", Arithmetic.evaluate("2/3"))
        assertEquals("2.5", Arithmetic.evaluate("5/2"))
        assertEquals("100", Arithmetic.evaluate("1000/10"))
        assertEquals("1.5", Arithmetic.evaluate("1+1/2"))
        assertEquals("0.01", Arithmetic.evaluate("10.005-10"))
        assertEquals("1.01", Arithmetic.evaluate("1.005+0"))
    }

    @Test
    fun decimalsArePartOfTheNumber() {
        assertEquals("38250", Arithmetic.evaluate("7500*5.1"))
        assertEquals("38250", Arithmetic.evaluate("7500＊5．1"))
        assertEquals("3.5", Arithmetic.evaluate("1.5+2"))
        assertEquals("1", Arithmetic.evaluate(".5*2"))
        assertNull(Arithmetic.evaluate("12+3."))
        assertNull(Arithmetic.evaluate("1.2.3+1"))
        assertNull(Arithmetic.evaluate("5."))
    }

    @Test
    fun onlyIntegersBetweenOperators() {
        assertNull(Arithmetic.evaluate("12+"))
        assertNull(Arithmetic.evaluate("+12"))
        assertNull(Arithmetic.evaluate("1++2"))
        assertNull(Arithmetic.evaluate("1+2×"))
        assertNull(Arithmetic.evaluate("5/0"))
        assertNull(Arithmetic.evaluate("1+5/0"))
        assertNull(Arithmetic.evaluate("12"))
    }

    @Test
    fun answerReadsTheTailOfTheEditorText() {
        val answer = Arithmetic.answer("买了12+7")
        assertEquals("12+7=19", answer?.shown)
        assertEquals("=19", answer?.appended)
        assertEquals("=12", Arithmetic.answer("共3×2+2×3")?.appended)
        assertEquals("7500*5.1=38250", Arithmetic.answer("7500*5.1")?.shown)
        assertEquals("=3.5", Arithmetic.answer("1.5+2")?.appended)
        assertNull(Arithmetic.answer("12+3."))
        assertEquals("=7", Arithmetic.answer("12。3+4")?.appended)
        assertNull(Arithmetic.answer("-3+5"))
        assertNull(Arithmetic.answer("1".repeat(Arithmetic.LOOK_BACK - 2) + "+1"))
        assertEquals("=20", Arithmetic.answer("12+7=19+1")?.appended)
    }
}
