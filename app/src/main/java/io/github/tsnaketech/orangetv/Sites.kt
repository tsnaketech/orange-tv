package io.github.tsnaketech.orangetv

/**
 * Orange TV web app of each country and the details the app relies on.
 * Labels are JS regex sources (case-insensitive) matched against a button's aria-label, title, text or image alt.
 *
 * shortcut: apart from France, these values come from reading each site's public code (2026-11), not from
 * a signed-in session on the TV; check them with tools/cdp.mjs before relying on them.
 */
class Site(
    val country: String,
    val startUrl: String,
    /** Sign-in page (full URL): the QR code only shows there. Null when the site has its own phone sign-in. */
    val loginUrl: Regex?,
    /** First selection in the player (Play/Pause). */
    val playLabel: String,
    /** Buttons that close a panel, used by the Back key. */
    val closeLabel: String,
    /** aria-labels of the site's next / previous channel buttons; null when the site has none. */
    val nextChannel: String? = null,
    val previousChannel: String? = null,
    /** Extra CSS rules: tooltips that are noise with a remote, pages that misread the TV. */
    val css: String = "",
    /** Extra CSS selectors of clickable elements that look like nothing clickable (no hand cursor, no role). */
    val clickable: String = "",
    /** Carousel previous / next controls, clicked when left / right finds nothing more in the row. */
    val carouselPrev: String = "",
    val carouselNext: String = "",
    /** The site maps channel ± to Page Up / Page Down (instead of channel buttons). */
    val channelPageKeys: Boolean = false,
)

object Sites {
    val FR = Site(
        "FR", "https://tv.orange.fr/",
        loginUrl = Regex("^https://login(\\.[a-z0-9-]+)*\\.orange\\.fr/"),
        playLabel = "^\\s*(pause|play|lecture|reprendre)\\b",
        closeLabel = "^(fermer|close|×|masquer\\b.*)$",
        nextChannel = "Chaîne suivante", previousChannel = "Chaîne précédente",
        css = ".tooltip{display:none!important}",
    )

    val all = listOf(
        FR,
        // Keycloak sign-in: first a method choice ("S'identifier"), then the credentials.
        Site(
            "BE", "https://orangetvgo.be/",
            loginUrl = Regex("^https://idp\\.titan\\.sdscloud\\.orange\\.be/"),
            playLabel = "^\\s*(lire|mettre en pause|afspelen|pauzeren|abspielen|pause|play)\\b",
            closeLabel = "^(close|fermer|sluiten|schließen|×)$",
            // The sign-in page takes the TV (no hover, coarse pointer, 540 px high in landscape) for a phone held
            // sideways and hides the form behind "Pivotez l'appareil" (checked on the TV): show the form again.
            css = "body:has(>.container-mobile-landscape)>.container{display:flex!important}" +
                ".container-mobile-landscape{display:none!important}",
        ),
        Site(
            "PL", "https://tvgo.orange.pl/",
            loginUrl = Regex("^https://tvgo\\.orange\\.pl/login"),
            playLabel = "^\\s*(odtwarzaj|zatrzymaj)\\b",
            closeLabel = "^(zamknij|×)$",
        ),
        // RO "Pause" label repeats "Reia redarea…" (site translation bug): both start the same way.
        Site(
            "RO", "https://tvgo.orange.ro/",
            loginUrl = Regex("^https://www\\.orange\\.ro/accounts/login"),
            playLabel = "^\\s*(resumes|pauses|reia)\\b",
            closeLabel = "^(close|closes the video player|închide|închideți\\b.*|×)$",
            css = ".sc-rxp-tooltip{display:none!important}",
        ),
        // Player buttons only carry an <img alt="play|pause">.
        Site(
            "SK", "https://tv.orange.sk/",
            loginUrl = Regex("^https://tv\\.orange\\.sk/login"),
            playLabel = "^(play|pause)$",
            closeLabel = "^(zatvoriť|close|×)$",
        ),
        // 10-foot app (same code as Orange Spain's Smart TV apps) with its own QR sign-in. Its own D-pad focus
        // only covers the top menu in the browser build (checked on the TV), so spatial navigation stays on.
        // Player buttons are icon-only: no label to start on.
        Site(
            "ES", "https://orangetv.orange.es/",
            loginUrl = null,
            playLabel = "(?!)", closeLabel = "^(cerrar|volver|×)$",
            // Content tiles are plain divs with an Angular click handler (checked on the TV).
            clickable = ".card,app-generic-button,.channel-item",
            // Carousels only slide through their invisible arrow areas.
            carouselPrev = ".carousel-control__block-area.prev", carouselNext = ".carousel-control__block-area.next",
            channelPageKeys = true,
        ),
    ).associateBy { it.country }

    /** Site for a country code ("BE", "be"…); France for any country without an Orange web app. */
    fun forCountry(code: String?): Site = all[code.orEmpty().uppercase()] ?: FR
}
