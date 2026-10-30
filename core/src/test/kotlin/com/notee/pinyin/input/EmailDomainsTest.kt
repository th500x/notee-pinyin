package com.notee.pinyin.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailDomainsTest {
    @Test
    fun orderFollowsCitedAccountScale() {
        assertEquals(
            listOf(
                "gmail.com",
                "icloud.com",
                "163.com",
                "qq.com",
                "outlook.com",
                "yahoo.com",
                "hotmail.com",
            ),
            EmailDomains.offers("name@", "").map { it.label },
        )
    }

    @Test
    fun atAloneStillOffersEveryDomain() {
        assertEquals(EmailDomains.names, EmailDomains.offers("@", "").map { it.label })
        assertEquals("gmail.com", EmailDomains.offers("@", "").first().missing)
    }

    @Test
    fun prefixCommitsOnlyTheRemainder() {
        val offers = EmailDomains.offers("name@g", "")
        assertEquals(listOf("gmail.com"), offers.map { it.label })
        assertEquals("mail.com", offers.single().missing)
    }

    @Test
    fun prefixIgnoresCase() {
        assertEquals("ail.com", EmailDomains.offers("name@GM", "").single().missing)
    }

    @Test
    fun digitNarrowsTo163() {
        val offer = EmailDomains.offers("a@1", "").single()
        assertEquals("163.com", offer.label)
        assertEquals("63.com", offer.missing)
    }

    @Test
    fun finishedDomainOffersNothing() {
        assertTrue(EmailDomains.offers("name@gmail.com", "").isEmpty())
    }

    @Test
    fun cursorInsideTheAddressOffersNothing() {
        assertTrue(EmailDomains.offers("name@gm", "ail.com").isEmpty())
        assertTrue(EmailDomains.offers("na", "me@gmail.com").isEmpty())
    }

    @Test
    fun noAtOrASecondAtOffersNothing() {
        assertTrue(EmailDomains.offers("name", "").isEmpty())
        assertTrue(EmailDomains.offers("a@b@", "").isEmpty())
        assertTrue(EmailDomains.offers("name＠", "").isEmpty())
    }

    @Test
    fun spaceEndsTheToken() {
        assertTrue(EmailDomains.offers("name@gmail.com ", "").isEmpty())
        assertEquals("mail.com", EmailDomains.offers("say name@g", "").single().missing)
        assertEquals("mail.com", EmailDomains.offers("name@g", " next").single().missing)
    }
}
