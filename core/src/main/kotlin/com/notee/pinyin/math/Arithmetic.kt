package com.notee.pinyin.math

import java.math.BigDecimal
import java.math.RoundingMode

/** An expression found before the cursor and its value. Tapping it appends [appended]. */
class Answer(val expression: String, val value: String) {
    val shown: String get() = "$expression=$value"
    val appended: String get() = "=$value"
}

/**
 * Numbers joined by `+ − × ÷`, `×` and `÷` first (docs/02-0 §3).
 * A number may contain one `.` or `．`. The shown value keeps two decimal places.
 */
object Arithmetic {
    private enum class Operator { ADD, SUBTRACT, MULTIPLY, DIVIDE }

    private val OPERATORS = mapOf(
        '+' to Operator.ADD, '＋' to Operator.ADD,
        '-' to Operator.SUBTRACT, '−' to Operator.SUBTRACT, '－' to Operator.SUBTRACT,
        '×' to Operator.MULTIPLY, '*' to Operator.MULTIPLY, '＊' to Operator.MULTIPLY,
        '÷' to Operator.DIVIDE, '/' to Operator.DIVIDE, '／' to Operator.DIVIDE,
    )

    /** One of these may sit inside a number. `。` is a Chinese full stop, not a decimal point. */
    private val DECIMAL_POINTS = setOf('.', '．')

    /** Longest operand worth computing, counting digits and not the decimal point. */
    const val MAX_DIGITS = 18

    /** Longest expression worth computing. */
    const val MAX_LENGTH = 64

    /** Places the answer is rounded to. Extra digits use half-up; trailing zeros are dropped. */
    const val ANSWER_SCALE = 2

    /** Places a quotient keeps while the rest of the expression is worked out. */
    private const val WORKING_SCALE = 20

    /** How much text before the cursor to read: the longest expression and one more character. */
    const val LOOK_BACK = MAX_LENGTH + 1

    fun isExpressionChar(c: Char): Boolean = c in '0'..'9' || c in OPERATORS || c in DECIMAL_POINTS

    /**
     * The value of [expression], e.g. `1+2×3` → `7`, `7500*5.1` → `38250`.
     * Null unless each piece is a number, one operator sits between each two, at least one operator
     * is present, and nothing is divided by zero. A trailing decimal point is still being typed.
     */
    fun evaluate(expression: CharSequence): String? {
        if (expression.length > MAX_LENGTH) return null
        val numbers = ArrayList<BigDecimal>()
        val operators = ArrayList<Operator>()
        var start = 0
        for (i in 0..expression.length) {
            val operator = if (i < expression.length) OPERATORS[expression[i]] ?: continue else null
            val number = parseNumber(expression.substring(start, i)) ?: return null
            numbers += number
            operator?.let(operators::add)
            start = i + 1
        }
        if (operators.isEmpty()) return null

        var total = BigDecimal.ZERO
        var sign = Operator.ADD
        var term = numbers[0]
        for ((k, operator) in operators.withIndex()) {
            val next = numbers[k + 1]
            when (operator) {
                Operator.MULTIPLY -> term *= next
                Operator.DIVIDE -> {
                    if (next.signum() == 0) return null
                    term = term.divide(next, WORKING_SCALE, RoundingMode.HALF_UP)
                }
                Operator.ADD, Operator.SUBTRACT -> {
                    total = add(total, sign, term)
                    sign = operator
                    term = next
                }
            }
        }
        total = add(total, sign, term)
        return total.setScale(ANSWER_SCALE, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    }

    /**
     * The expression that ends [before], e.g. `12+7` in `买了12+7`, with its value.
     * [before] is the editor text before the cursor, at most [LOOK_BACK] characters.
     */
    fun answer(before: CharSequence): Answer? {
        var start = before.length
        while (start > 0 && isExpressionChar(before[start - 1])) start--
        if (start == 0 && before.length >= LOOK_BACK) return null
        val expression = before.substring(start)
        return evaluate(expression)?.let { Answer(expression, it) }
    }

    private fun add(total: BigDecimal, sign: Operator, term: BigDecimal): BigDecimal =
        if (sign == Operator.SUBTRACT) total - term else total + term

    /** Digits with at most one decimal point. A point with nothing after it is not finished. */
    private fun parseNumber(text: String): BigDecimal? {
        if (text.isEmpty() || text.length > MAX_DIGITS + 1) return null
        var digits = 0
        var point = false
        val normalized = CharArray(text.length)
        for (i in text.indices) {
            val c = text[i]
            when {
                c in '0'..'9' -> {
                    digits++
                    normalized[i] = c
                }
                c in DECIMAL_POINTS -> {
                    if (point || i == text.lastIndex) return null
                    point = true
                    normalized[i] = '.'
                }
                else -> return null
            }
        }
        if (digits !in 1..MAX_DIGITS) return null
        return BigDecimal(String(normalized))
    }
}
