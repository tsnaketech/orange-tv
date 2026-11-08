package io.github.tsnaketech.orangetv

/**
 * App texts (QR code panel on the TV, sign-in page on the phone).
 * Languages of the countries where Orange operates: FR, NL (Belgium), DE (Luxembourg), RO (Romania, Moldova),
 * PL, SK, ES; English for any other language.
 * A plain dictionary rather than Android resources: the phone server (pure JVM, tested outside Android)
 * picks the language on each request from the phone's Accept-Language header.
 */
class Texts(
    val lang: String,
    val qrTitle: String,
    val qrHint: String,
    val pageTitle: String,
    val idLabel: String,
    val idPlaceholder: String,
    val pwLabel: String,
    val showPw: String,
    val submit: String,
    val sent: String,
    val emptyPw: String,
    val expired: String,
    val tooLong: String,
    val unknownPage: String,
    val siteTitle: String,
    /** %s = the site picked from the TV's region. */
    val siteAuto: String,
)

object I18n {
    private val all = listOf(
        Texts(
            "fr", "Se connecter avec\nle téléphone", "Scannez avec l'appareil photo\n(même Wi-Fi que la TV)",
            "Connexion sur la TV", "Identifiant (e-mail ou n° de mobile)", "Laisser vide s'il est déjà affiché sur la TV",
            "Mot de passe", "Afficher le mot de passe", "Se connecter sur la TV", "Envoyé ✓ La TV se connecte…",
            "Le mot de passe est vide.", "Code expiré : rescannez le QR code affiché sur la TV.", "Texte trop long.",
            "Page inconnue.", "Choisir le site Orange", "Automatique : %s",
        ),
        Texts(
            "en", "Sign in with\nyour phone", "Scan with your camera\n(same Wi-Fi as the TV)",
            "Sign in on the TV", "Username (email or mobile number)", "Leave empty if already shown on the TV",
            "Password", "Show password", "Sign in on the TV", "Sent ✓ The TV is signing in…",
            "The password is empty.", "Code expired: scan the QR code shown on the TV again.", "Text too long.",
            "Unknown page.", "Choose the Orange site", "Automatic: %s",
        ),
        Texts(
            "nl", "Aanmelden met\nje telefoon", "Scan met je camera\n(zelfde wifi als de tv)",
            "Aanmelden op de tv", "Gebruikersnaam (e-mail of gsm-nummer)", "Leeg laten als hij al op de tv staat",
            "Wachtwoord", "Wachtwoord tonen", "Aanmelden op de tv", "Verzonden ✓ De tv meldt zich aan…",
            "Het wachtwoord is leeg.", "Code verlopen: scan de QR-code op de tv opnieuw.", "Tekst te lang.",
            "Onbekende pagina.", "Kies de Orange-site", "Automatisch: %s",
        ),
        Texts(
            "de", "Mit dem Handy\nanmelden", "Mit der Kamera scannen\n(gleiches WLAN wie der Fernseher)",
            "Auf dem Fernseher anmelden", "Benutzername (E-Mail oder Handynummer)",
            "Leer lassen, wenn er schon auf dem Fernseher steht",
            "Passwort", "Passwort anzeigen", "Auf dem Fernseher anmelden", "Gesendet ✓ Der Fernseher meldet sich an…",
            "Das Passwort ist leer.", "Code abgelaufen: Bitte den QR-Code auf dem Fernseher erneut scannen.",
            "Text zu lang.", "Unbekannte Seite.", "Orange-Seite wählen", "Automatisch: %s",
        ),
        Texts(
            "ro", "Conectează-te\ncu telefonul", "Scanează cu camera\n(aceeași rețea Wi-Fi ca televizorul)",
            "Conectare pe televizor", "Utilizator (e-mail sau număr de mobil)", "Lasă gol dacă apare deja pe televizor",
            "Parolă", "Afișează parola", "Conectează-te pe televizor", "Trimis ✓ Televizorul se conectează…",
            "Parola este goală.", "Cod expirat: scanează din nou codul QR afișat pe televizor.", "Text prea lung.",
            "Pagină necunoscută.", "Alege site-ul Orange", "Automat: %s",
        ),
        Texts(
            "pl", "Zaloguj się\ntelefonem", "Zeskanuj aparatem\n(ta sama sieć Wi-Fi co telewizor)",
            "Logowanie na telewizorze", "Login (e-mail lub numer telefonu)",
            "Zostaw puste, jeśli jest już widoczny na telewizorze",
            "Hasło", "Pokaż hasło", "Zaloguj na telewizorze", "Wysłano ✓ Telewizor się loguje…",
            "Hasło jest puste.", "Kod wygasł: zeskanuj ponownie kod QR z telewizora.", "Tekst jest za długi.",
            "Nieznana strona.", "Wybierz stronę Orange", "Automatycznie: %s",
        ),
        Texts(
            "sk", "Prihláste sa\ntelefónom", "Naskenujte fotoaparátom\n(rovnaká sieť Wi-Fi ako televízor)",
            "Prihlásenie na televízore", "Prihlasovacie meno (e-mail alebo číslo mobilu)",
            "Nechajte prázdne, ak je už zobrazené na televízore",
            "Heslo", "Zobraziť heslo", "Prihlásiť sa na televízore", "Odoslané ✓ Televízor sa prihlasuje…",
            "Heslo je prázdne.", "Platnosť kódu vypršala: znova naskenujte QR kód z televízora.",
            "Text je príliš dlhý.", "Neznáma stránka.", "Vyberte stránku Orange", "Automaticky: %s",
        ),
        Texts(
            "es", "Inicia sesión\ncon el móvil", "Escanea con la cámara\n(misma wifi que la TV)",
            "Iniciar sesión en la TV", "Usuario (correo o número de móvil)", "Déjalo vacío si ya aparece en la TV",
            "Contraseña", "Mostrar contraseña", "Iniciar sesión en la TV", "Enviado ✓ La TV está iniciando sesión…",
            "La contraseña está vacía.", "Código caducado: vuelve a escanear el código QR de la TV.",
            "Texto demasiado largo.", "Página desconocida.", "Elige la web de Orange", "Automático: %s",
        ),
    ).associateBy { it.lang }

    /** Texts for a language code ("fr", "fr-BE", "ro_MD"…), English by default. */
    fun forLanguage(code: String?): Texts =
        all[code.orEmpty().take(2).lowercase()] ?: all.getValue("en")

    /** First known language of an Accept-Language header ("es-ES,es;q=0.9,en;q=0.8"), otherwise [fallback]. */
    fun fromAcceptLanguage(header: String?, fallback: Texts): Texts =
        header.orEmpty().split(',')
            .map { it.substringBefore(';').trim().take(2).lowercase() }
            .firstNotNullOfOrNull { all[it] } ?: fallback
}
