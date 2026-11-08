package io.github.tsnaketech.orangetv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SitesTest {
    @Test
    fun `le site suit le pays de la TV, la France par défaut`() {
        assertEquals("https://orangetvgo.be/", Sites.forCountry("BE").startUrl)
        assertEquals("https://tvgo.orange.pl/", Sites.forCountry("pl").startUrl)
        assertEquals("https://tv.orange.fr/", Sites.forCountry("LU").startUrl) // pas de site web Orange
        assertEquals("https://tv.orange.fr/", Sites.forCountry("").startUrl)
        assertEquals("https://tv.orange.fr/", Sites.forCountry(null).startUrl)
    }

    @Test
    fun `le QR code ne s'affiche que sur la page de connexion du pays`() {
        fun login(country: String, url: String) = Sites.forCountry(country).loginUrl?.containsMatchIn(url) == true
        assertTrue(login("FR", "https://login.orange.fr/?return_url=x"))
        assertTrue(login("FR", "https://login.sso.orange.fr/"))
        assertFalse(login("FR", "https://tv.orange.fr/"))
        assertFalse(login("FR", "https://login.orange.fr.evil.com/"))
        assertTrue(login("BE", "https://idp.titan.sdscloud.orange.be/auth/realms/Orange/protocol/openid-connect/auth?x"))
        assertTrue(login("PL", "https://tvgo.orange.pl/login"))
        assertTrue(login("RO", "https://www.orange.ro/accounts/login-user?ak=1&ud=1"))
        assertTrue(login("SK", "https://tv.orange.sk/login"))
        assertFalse(login("SK", "https://tv.orange.sk/"))
        assertFalse(login("ES", "https://orangetv.orange.es/")) // QR propre au site
    }

    @Test
    fun `les libellés sont des expressions valides`() {
        Sites.all.values.forEach { Regex(it.playLabel); Regex(it.closeLabel) }
        assertTrue(Regex(Sites.forCountry("FR").playLabel, RegexOption.IGNORE_CASE).containsMatchIn("Lecture"))
        assertTrue(Regex(Sites.forCountry("PL").playLabel, RegexOption.IGNORE_CASE).containsMatchIn("Zatrzymaj (Spacja)"))
        assertTrue(Regex(Sites.forCountry("RO").closeLabel, RegexOption.IGNORE_CASE).containsMatchIn("Închide"))
    }
}
