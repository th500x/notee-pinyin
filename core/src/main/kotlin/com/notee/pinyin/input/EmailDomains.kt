package com.notee.pinyin.input

/**
 * Domains offered after `@` when the field itself is an email address (docs/01-0 §6).
 * Order is cited account scale, highest first. hotmail.com has no separate published
 * count and stays last, after the domains that do.
 */
object EmailDomains {
    val names: List<String> = listOf(
        "gmail.com",
        "icloud.com",
        "163.com",
        "qq.com",
        "outlook.com",
        "yahoo.com",
        "hotmail.com",
    )

    /** [label] is the full domain. [missing] is the part not yet typed, in the list's own case. */
    data class Offer(val label: String, val missing: String)

    /**
     * Offers for the token that ends at the cursor. [before] is the text before the cursor,
     * [after] is the text after it. Empty when the cursor is not at the end of a single `@`.
     */
    fun offers(before: String, after: String): List<Offer> {
        if (after.isNotEmpty() && isTokenChar(after[0])) return emptyList()
        val token = before.takeLastWhile(::isTokenChar)
        val at = token.indexOf('@')
        if (at < 0 || token.indexOf('@', at + 1) >= 0) return emptyList()
        val typed = token.substring(at + 1)
        if (typed.any { !isDomainChar(it) }) return emptyList()
        return names.mapNotNull { domain ->
            if (!domain.startsWith(typed, ignoreCase = true)) return@mapNotNull null
            val missing = domain.substring(typed.length)
            if (missing.isEmpty()) null else Offer(domain, missing)
        }
    }

    private fun isTokenChar(c: Char): Boolean = c == '@' || isDomainChar(c) || c == '_' || c == '%' || c == '+'

    private fun isDomainChar(c: Char): Boolean =
        c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '.' || c == '-'
}
