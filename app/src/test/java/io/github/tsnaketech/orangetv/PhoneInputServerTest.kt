package io.github.tsnaketech.orangetv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.Socket
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class PhoneInputServerTest {
    private fun request(port: Int, head: String, body: String = ""): String =
        Socket("127.0.0.1", port).use { s ->
            s.getOutputStream().write("$head\r\nHost: tv\r\nContent-Length: ${body.length}\r\n\r\n$body".toByteArray())
            s.getInputStream().bufferedReader().readLine()
        }

    @Test
    fun `les identifiants ne sont transmis qu'avec le bon jeton`() {
        val got = LinkedBlockingQueue<Pair<String, String>>()
        val srv = PhoneInputServer(I18n.forLanguage("fr")) { id, pw -> got.add(id to pw) }
        try {
            assertEquals("HTTP/1.1 403 Forbidden", request(srv.port, "GET /?t=mauvais HTTP/1.1"))
            assertEquals("HTTP/1.1 200 OK", request(srv.port, "GET /?t=${srv.token} HTTP/1.1"))
            assertEquals("HTTP/1.1 403 Forbidden", request(srv.port, "POST /send HTTP/1.1", "t=mauvais&id=a&pw=secret"))
            assertEquals("HTTP/1.1 400 Bad Request", request(srv.port, "POST /send HTTP/1.1", "t=${srv.token}&id=a&pw="))
            assertTrue(got.isEmpty())

            assertEquals(
                "HTTP/1.1 200 OK",
                request(srv.port, "POST /send HTTP/1.1", "t=${srv.token}&id=+moi%40exemple.fr+&pw=p%40ss+w%26rd"),
            )
            assertEquals("moi@exemple.fr" to "p@ss w&rd", got.poll(2, TimeUnit.SECONDS))
        } finally {
            srv.stop()
        }
    }

    @Test
    fun `la page suit la langue du téléphone, sinon celle de la TV`() {
        val srv = PhoneInputServer(I18n.forLanguage("fr")) { _, _ -> }
        fun page(lang: String?): String = Socket("127.0.0.1", srv.port).use { s ->
            val hdr = lang?.let { "Accept-Language: $it\r\n" }.orEmpty()
            s.getOutputStream().write("GET /?t=${srv.token} HTTP/1.1\r\nHost: tv\r\n$hdr\r\n".toByteArray())
            s.getInputStream().readBytes().toString(Charsets.UTF_8)
        }
        try {
            assertTrue(page("es-ES,es;q=0.9,en;q=0.8").contains("Contraseña"))
            assertTrue(page("nl-BE,nl;q=0.9").contains("Wachtwoord"))
            assertTrue(page("ro-MD").contains("Parolă"))
            assertTrue(page("ja-JP,fr;q=0.5").contains("Mot de passe")) // 1st language unknown, 2nd known
            assertTrue(page(null).contains("Mot de passe"))             // none: TV language
        } finally {
            srv.stop()
        }
    }

    @Test
    fun `une langue inconnue de la TV donne l'anglais`() {
        assertEquals("en", I18n.forLanguage("ja").lang)
        assertEquals("de", I18n.forLanguage("de_LU").lang)
    }
}
