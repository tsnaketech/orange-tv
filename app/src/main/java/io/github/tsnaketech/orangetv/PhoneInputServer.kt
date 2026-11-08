package io.github.tsnaketech.orangetv

import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.concurrent.thread

private const val MAX_BODY = 4096

/**
 * Small HTTP server on the local network: the phone (after scanning the QR code) sends the
 * username and password in one go; the TV types them into the sign-in page.
 * A random token, only present in the QR code, is required on every request.
 *
 * shortcut: plain HTTP on the local Wi-Fi (no TLS); fine on a home network,
 * switch to HTTPS if the network is shared.
 */
class PhoneInputServer(
    /** Default language (the TV's) if the phone announces none that we know. */
    private val defaultTexts: Texts,
    private val onCredentials: (id: String, password: String) -> Unit,
) {
    val token: String = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
    private val socket = ServerSocket(0) // free port chosen by the system
    val port: Int get() = socket.localPort

    init {
        thread(isDaemon = true, name = "phone-input") {
            while (!socket.isClosed) {
                try {
                    socket.accept().use { handle(it) }
                } catch (_: Exception) {
                    // invalid request or closed socket: move on to the next one
                }
            }
        }
    }

    fun stop() {
        runCatching { socket.close() }
    }

    private fun handle(c: Socket) {
        c.soTimeout = 5000
        val r = c.getInputStream().bufferedReader(Charsets.UTF_8)
        val parts = (r.readLine() ?: return).split(' ')
        var len = 0
        var acceptLanguage: String? = null
        while (true) {
            val h = r.readLine() ?: return
            if (h.isEmpty()) break
            if (h.startsWith("content-length:", ignoreCase = true)) len = h.substringAfter(':').trim().toIntOrNull() ?: -1
            if (h.startsWith("accept-language:", ignoreCase = true)) acceptLanguage = h.substringAfter(':')
        }
        // The page is shown in the phone's language.
        val t = I18n.fromAcceptLanguage(acceptLanguage, defaultTexts)
        if (len !in 0..MAX_BODY) return respond(c, "413 Payload Too Large", page(t, t.tooLong, form = false))
        // URL-encoded body = ASCII, so number of chars = number of bytes.
        val buf = CharArray(len)
        var read = 0
        while (read < len) {
            val n = r.read(buf, read, len - read)
            if (n < 0) break
            read += n
        }
        val body = String(buf, 0, read)

        val method = parts.getOrElse(0) { "" }
        val target = parts.getOrElse(1) { "" }
        val expired = page(t, t.expired, form = false)
        when {
            method == "GET" && target.substringBefore('?') == "/" ->
                if (valid(parseForm(target.substringAfter('?', ""))["t"])) respond(c, "200 OK", page(t, ""))
                else respond(c, "403 Forbidden", expired)
            method == "POST" && target == "/send" -> {
                val f = parseForm(body)
                val pw = f["pw"]
                if (!valid(f["t"])) return respond(c, "403 Forbidden", expired)
                if (pw.isNullOrEmpty()) return respond(c, "400 Bad Request", page(t, t.emptyPw))
                onCredentials(f["id"].orEmpty().trim(), pw)
                respond(c, "200 OK", page(t, t.sent, form = false))
            }
            else -> respond(c, "404 Not Found", page(t, t.unknownPage, form = false))
        }
    }

    private fun valid(t: String?) = t != null && MessageDigest.isEqual(t.toByteArray(), token.toByteArray())

    private fun respond(c: Socket, status: String, html: String) {
        val bytes = html.toByteArray(Charsets.UTF_8)
        c.getOutputStream().apply {
            write(
                ("HTTP/1.1 $status\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\n" +
                    "Cache-Control: no-store\r\nConnection: close\r\n\r\n").toByteArray()
            )
            write(bytes)
            flush()
        }
    }

    private fun page(t: Texts, msg: String, form: Boolean = true) = """<!doctype html>
<html lang="${t.lang}"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>${t.pageTitle}</title>
<style>
body{font:18px system-ui,sans-serif;margin:24px;background:#111;color:#eee}
input,button{font:inherit;width:100%;padding:14px;margin:8px 0;box-sizing:border-box;border-radius:8px;border:1px solid #555}
input{background:#222;color:#eee}button{background:#fff;color:#000;font-weight:bold}
label{display:flex;gap:10px;align-items:center;margin:10px 0}label input{width:auto;margin:0}
.msg{color:#7fd67f}
</style></head><body>
<h2>${t.pageTitle}</h2>
<p class="msg">$msg</p>
${if (!form) "" else """
<form method="post" action="/send">
<input type="hidden" name="t" value="$token">
<label for="id">${t.idLabel}</label>
<input id="id" name="id" type="text" inputmode="email" autocomplete="username" autocapitalize="off" spellcheck="false" placeholder="${t.idPlaceholder}">
<label for="pw">${t.pwLabel}</label>
<input id="pw" name="pw" type="password" autocomplete="current-password" required>
<label><input type="checkbox" onclick="pw.type=this.checked?'text':'password'"> ${t.showPw}</label>
<button>${t.submit}</button>
</form>"""}
</body></html>"""
}

internal fun parseForm(s: String): Map<String, String> =
    s.split('&').filter { '=' in it }.associate {
        URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('='), "UTF-8")
    }
