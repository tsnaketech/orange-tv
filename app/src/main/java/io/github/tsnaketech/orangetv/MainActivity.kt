package io.github.tsnaketech.orangetv

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.json.JSONObject
import java.net.Inet4Address

// Desktop Chrome UA: Android UAs are often redirected to "download the app".
private const val DESKTOP_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

class MainActivity : Activity() {
    private lateinit var root: FrameLayout
    private lateinit var web: WebView
    private lateinit var cursor: CursorView
    private var fullscreenView: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null
    // Menu key (≡): switches to the free cursor when element navigation is not enough.
    private var cursorMode = false
    private var menuHeld = false
    private val menuLongPress = Runnable { menuHeld = true; chooseSite() }
    // QR code panel + local server, active only on the Orange sign-in page.
    private lateinit var qrPanel: LinearLayout
    private lateinit var qrImage: ImageView
    private var phoneServer: PhoneInputServer? = null
    private val handler = Handler(Looper.getMainLooper())
    // Fire TV language (Settings > Preferences > Language); English if it is not supported.
    private val texts by lazy { I18n.forLanguage(java.util.Locale.getDefault().language) }
    // Orange site (official site; the user signs in themselves): the one picked with a long press on Menu,
    // otherwise the Fire TV's region, France by default.
    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }
    private val regionSite get() = Sites.forCountry(java.util.Locale.getDefault().country)
    private var site = Sites.FR
    private var nav = SpatialNav(site)
    private var resetHistory = false
    private var pendingLogin: Runnable? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Debug build only: lets the PC inspect the page (chrome://inspect).
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) WebView.setWebContentsDebuggingEnabled(true)

        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.userAgentString = DESKTOP_UA
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            webViewClient = object : WebViewClient() {
                // Also called for in-page URL changes of single-page sites.
                override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                    updatePhoneLogin(url)
                    view.evaluateJavascript(nav.install, null)
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    // After a site change, Back must not lead to the previous country's site.
                    if (resetHistory) { view.clearHistory(); resetHistory = false }
                    view.evaluateJavascript(nav.install, null) // hides the channel ± buttons right away
                    // Single-page sites still draw for a moment after "finished": keep the screen covered.
                    handler.removeCallbacks(hideLoading)
                    handler.postDelayed(hideLoading, 800)
                }
            }
            webChromeClient = Chrome()
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)

        cursor = CursorView(this).apply { visibility = View.GONE }
        qrImage = ImageView(this)
        qrPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(Color.argb(230, 20, 20, 20))
            visibility = View.GONE
            addView(TextView(context).apply {
                text = texts.qrTitle
                setTextColor(Color.WHITE)
                textSize = 18f
                gravity = Gravity.CENTER
            })
            addView(qrImage, LinearLayout.LayoutParams(320, 320).apply { topMargin = 16; bottomMargin = 16 })
            addView(TextView(context).apply {
                text = texts.qrHint
                setTextColor(Color.LTGRAY)
                textSize = 14f
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
        root = FrameLayout(this).apply {
            addView(web, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            addView(qrPanel, FrameLayout.LayoutParams(420, WRAP_CONTENT, Gravity.END or Gravity.CENTER_VERTICAL)
                .apply { marginEnd = 48 })
            addView(cursor, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            addView(loading, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        }
        setContentView(root)

        site = prefs.getString("site", null)?.let { Sites.all[it] } ?: regionSite
        nav = SpatialNav(site)
        Log.i("TvWeb", "site ${site.country} ${site.startUrl}")
        if (savedInstanceState == null) open(site.startUrl) else web.restoreState(savedInstanceState)
    }

    // Loading screen while a site opens, instead of the blank page the WebView shows meanwhile.
    private val loadingHost by lazy { TextView(this).apply { setTextColor(Color.LTGRAY); textSize = 16f; gravity = Gravity.CENTER } }
    private val loading by lazy {
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.rgb(16, 16, 16))
            visibility = View.GONE
            addView(ProgressBar(context).apply {
                indeterminateTintList = android.content.res.ColorStateList.valueOf(Color.rgb(255, 121, 0))
            }, LinearLayout.LayoutParams(96, 96).apply { bottomMargin = 24 })
            addView(loadingHost)
        }
    }
    private val hideLoading = Runnable { loading.visibility = View.GONE }

    private fun open(url: String) {
        loadingHost.text = Uri.parse(url).host
        loading.visibility = View.VISIBLE
        handler.removeCallbacks(hideLoading)
        handler.postDelayed(hideLoading, 20_000) // never stuck if the site never finishes loading
        web.loadUrl(url)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    override fun onPause() {
        super.onPause()
        CookieManager.getInstance().flush() // keeps the Orange session after closing
        updatePhoneLogin(null) // no open server while the app is not on screen
        cancelLogin()
    }

    override fun onResume() {
        super.onResume()
        updatePhoneLogin(web.url)
    }

    override fun onDestroy() {
        updatePhoneLogin(null)
        web.destroy()
        super.onDestroy()
    }

    /** Shows the QR code (and starts the server) only on the Orange sign-in page. */
    private fun updatePhoneLogin(url: String?) {
        val isLogin = url != null && site.loginUrl?.containsMatchIn(url) == true
        val ip = if (isLogin) localIp() else null
        if (ip == null) {
            phoneServer?.stop()
            phoneServer = null
            qrPanel.visibility = View.GONE
            return
        }
        if (phoneServer != null) return
        val srv = PhoneInputServer(texts) { id, pw -> runOnUiThread { loginWith(id, pw) } }
        phoneServer = srv
        qrImage.setImageBitmap(qrBitmap("http://$ip:${srv.port}/?t=${srv.token}"))
        qrPanel.visibility = View.VISIBLE
    }

    /**
     * Orange sign-in in one or two steps: fills in the username and submits, then fills in the password
     * as soon as its field appears. Credentials only live in memory, ~30 s at most.
     */
    private fun loginWith(id: String, pw: String) {
        cancelLogin()
        var idDone = false
        var tries = 0
        val step = object : Runnable {
            override fun run() {
                if (++tries > 60) return cancelLogin() // the page did not respond: forget the credentials
                // Only ever type them into the sign-in page (not, say, the search box of a page it redirected to).
                if (site.loginUrl?.containsMatchIn(web.url.orEmpty()) != true) { handler.postDelayed(this, 500); return }
                web.evaluateJavascript(fillLoginJs(id, pw, idDone)) { res ->
                    if (pendingLogin !== this) return@evaluateJavascript // cancelled in the meantime
                    when (res.trim('"')) {
                        "pw" -> cancelLogin() // password sent: done
                        "id" -> { idDone = true; handler.postDelayed(this, 500) }
                        else -> handler.postDelayed(this, 500) // page still loading
                    }
                }
            }
        }
        pendingLogin = step
        handler.post(step)
    }

    private fun cancelLogin() {
        pendingLogin?.let(handler::removeCallbacks)
        pendingLogin = null
    }

    private fun fillLoginJs(id: String, pw: String, idDone: Boolean) = """
(function(id,pw,idDone){
  function vis(e){return e.offsetParent!==null&&!e.disabled&&!e.readOnly;}
  var ins=[].slice.call(document.querySelectorAll('input')).filter(vis);
  var p=ins.filter(function(e){return e.type==='password';})[0];
  var u=ins.filter(function(e){return /^(text|email|tel)$/.test(e.type);})[0];
  // Native setter: frameworks (React…) see the value.
  function set(e,v){
    Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(e,v);
    e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}));
  }
  function submit(e){
    e.focus();
    if(e.form&&e.form.requestSubmit){e.form.requestSubmit();return;}
    var b=document.querySelector('button[type=submit]');
    if(b){b.click();return;}
    e.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',code:'Enter',keyCode:13,bubbles:true}));
  }
  if(p){if(u&&id&&!idDone)set(u,id);set(p,pw);submit(p);return 'pw';}
  if(u&&id&&!idDone){set(u,id);submit(u);return 'id';}
  return 'wait';
})(${JSONObject.quote(id)},${JSONObject.quote(pw)},$idDone)"""

    private fun localIp(): String? {
        val cm = getSystemService(ConnectivityManager::class.java)
        return cm.getLinkProperties(cm.activeNetwork)?.linkAddresses
            ?.map { it.address }
            ?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
            ?.hostAddress
    }

    private fun qrBitmap(content: String): Bitmap {
        val m = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0) // minimal size, margin included
        val bmp = Bitmap.createBitmap(m.width, m.height, Bitmap.Config.ARGB_8888)
        for (x in 0 until m.width) for (y in 0 until m.height) bmp.setPixel(x, y, if (m[x, y]) Color.BLACK else Color.WHITE)
        return Bitmap.createScaledBitmap(bmp, 320, 320, false) // false: sharp pixels, readable by the camera
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val down = event.action == KeyEvent.ACTION_DOWN
        if (down && event.repeatCount == 0) Log.i("TvWeb", "touche ${KeyEvent.keyCodeToString(event.keyCode)}")
        // Speeds up while the key is held down.
        val step = 12f + minOf(event.repeatCount, 20) * 4f
        val arrows = mapOf(
            KeyEvent.KEYCODE_DPAD_LEFT to "left", KeyEvent.KEYCODE_DPAD_RIGHT to "right",
            KeyEvent.KEYCODE_DPAD_UP to "up", KeyEvent.KEYCODE_DPAD_DOWN to "down",
        )
        // In full-screen video, the arrows stay with the player.
        if (fullscreenView == null && event.keyCode in arrows) {
            if (down) {
                if (cursorMode) when (event.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> move(-step, 0f)
                    KeyEvent.KEYCODE_DPAD_RIGHT -> move(step, 0f)
                    KeyEvent.KEYCODE_DPAD_UP -> move(0f, -step)
                    else -> move(0f, step)
                } else navigate(nav.move(arrows.getValue(event.keyCode)))
            }
            return true
        }
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (!down || event.repeatCount != 0) return true
                if (cursorMode || fullscreenView != null) tap(cursor.x0, cursor.y0)
                else navigate(nav.center()) { res ->
                    val xy = res.trim('[', ']').split(',').mapNotNull { it.toFloatOrNull() }
                    if (xy.size == 2) tap(xy[0] * web.width, xy[1] * web.height)
                    else web.evaluateJavascript(nav.move("down"), null) // nothing selected
                }
                return true
            }
            KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                if (down) web.evaluateJavascript(
                    nav.seek(if (event.keyCode == KeyEvent.KEYCODE_MEDIA_REWIND) -10 else 10), null
                )
                return true
            }
            // Short press: free cursor on / off. Long press: choose the Orange site, shown while still held:
            // on the key's first repeat, or after the long-press delay for a remote that does not repeat.
            KeyEvent.KEYCODE_MENU -> {
                when {
                    down && event.repeatCount == 0 -> {
                        menuHeld = false
                        handler.postDelayed(menuLongPress, ViewConfiguration.getLongPressTimeout().toLong())
                    }
                    down -> if (!menuHeld) { handler.removeCallbacks(menuLongPress); menuLongPress.run() }
                    !menuHeld -> {
                        handler.removeCallbacks(menuLongPress)
                        cursorMode = !cursorMode
                        cursor.visibility = if (cursorMode) View.VISIBLE else View.GONE
                    }
                }
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                if (down) web.evaluateJavascript(
                    "(()=>{const v=document.querySelector('video');if(v){v.paused?v.play():v.pause()}})()", null
                )
                return true
            }
            KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                if (site.channelPageKeys) channelAsPageKey(event)
                else if (down && event.repeatCount == 0) {
                    web.evaluateJavascript(nav.zap(event.keyCode == KeyEvent.KEYCODE_CHANNEL_UP), null)
                }
                return true
            }
            KeyEvent.KEYCODE_BACK -> {
                if (!down || event.repeatCount != 0) return true
                if (fullscreenView != null) fullscreenCallback?.onCustomViewHidden()
                else web.evaluateJavascript(nav.closeOverlay()) { closed ->
                    if (closed == "true") return@evaluateJavascript
                    if (web.canGoBack()) web.goBack() else finish()
                }
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    /** Site list (first entry: automatic, from the region); the choice is kept and the site opens at once. */
    private fun chooseSite() {
        val saved = prefs.getString("site", null)
        val sites = Sites.all.values.toList()
        fun name(s: Site) = "${java.util.Locale("", s.country).displayCountry} — ${Uri.parse(s.startUrl).host}"
        val items = listOf(texts.siteAuto.format(name(regionSite))) + sites.map(::name)
        val checked = if (saved == null) 0 else sites.indexOfFirst { it.country == saved } + 1
        AlertDialog.Builder(this)
            .setTitle(texts.siteTitle)
            .setSingleChoiceItems(items.toTypedArray(), checked) { dialog, i ->
                dialog.dismiss()
                val chosen = if (i == 0) null else sites[i - 1].country
                prefs.edit().putString("site", chosen).apply()
                val next = chosen?.let { Sites.all[it] } ?: regionSite
                if (next === site && chosen == saved) return@setSingleChoiceItems
                site = next
                nav = SpatialNav(site)
                cancelLogin()
                updatePhoneLogin(null)
                Log.i("TvWeb", "site ${site.country} ${site.startUrl}")
                resetHistory = true
                open(site.startUrl)
            }
            .show()
    }

    /** Channel ± as the Page Up / Page Down keys the site (Spain) maps to channels. */
    private fun channelAsPageKey(event: KeyEvent) {
        val mapped = if (event.keyCode == KeyEvent.KEYCODE_CHANNEL_UP) KeyEvent.KEYCODE_PAGE_UP else KeyEvent.KEYCODE_PAGE_DOWN
        web.dispatchKeyEvent(KeyEvent(event.downTime, event.eventTime, event.action, mapped, event.repeatCount))
    }

    /**
     * In the video player, the controls only appear on mouse movement:
     * wiggle the mouse at the centre, let the controls show, then navigate.
     */
    private fun navigate(js: String, callback: ((String) -> Unit)? = null) {
        web.evaluateJavascript(nav.videoMode()) { isVideo ->
            if (isVideo != "true") return@evaluateJavascript web.evaluateJavascript(js, callback)
            val cx = web.width / 2f
            val cy = web.height / 2f
            mouse(MotionEvent.ACTION_HOVER_MOVE, cx, cy)
            mouse(MotionEvent.ACTION_HOVER_MOVE, cx + 3, cy + 3)
            handler.postDelayed({ web.evaluateJavascript(js, callback) }, 200)
        }
    }

    /** Moves the cursor; at the screen edge, scrolls the page under the cursor. */
    private fun move(dx: Float, dy: Float) {
        val w = root.width.toFloat()
        val h = root.height.toFloat()
        val nx = cursor.x0 + dx
        val ny = cursor.y0 + dy
        cursor.x0 = nx.coerceIn(0f, w - 1)
        cursor.y0 = ny.coerceIn(0f, h - 1)
        cursor.invalidate()

        val overX = nx - cursor.x0
        val overY = ny - cursor.y0
        if (overX != 0f || overY != 0f) scroll(overX, overY)
        mouse(MotionEvent.ACTION_HOVER_MOVE)
    }

    /** Real screen touch: also opens the keyboard on input fields. */
    private fun tap(x: Float, y: Float) {
        val t = SystemClock.uptimeMillis()
        listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEach { action ->
            MotionEvent.obtain(t, t, action, x, y, 0).apply {
                source = InputDevice.SOURCE_TOUCHSCREEN
                target().dispatchTouchEvent(this)
                recycle()
            }
        }
    }

    /** Mouse wheel at the cursor: also scrolls the page's inner blocks. */
    private fun scroll(dx: Float, dy: Float) {
        val props = arrayOf(MotionEvent.PointerProperties().apply { toolType = MotionEvent.TOOL_TYPE_MOUSE })
        val coords = arrayOf(MotionEvent.PointerCoords().apply {
            x = cursor.x0; y = cursor.y0
            setAxisValue(MotionEvent.AXIS_HSCROLL, -dx / 20f)
            setAxisValue(MotionEvent.AXIS_VSCROLL, -dy / 20f)
        })
        val t = SystemClock.uptimeMillis()
        MotionEvent.obtain(t, t, MotionEvent.ACTION_SCROLL, 1, props, coords, 0, 0, 1f, 1f, 0, 0,
            InputDevice.SOURCE_MOUSE, 0).apply {
            target().dispatchGenericMotionEvent(this)
            recycle()
        }
    }

    private fun mouse(action: Int, x: Float = cursor.x0, y: Float = cursor.y0) {
        val t = SystemClock.uptimeMillis()
        MotionEvent.obtain(t, t, action, x, y, 0).apply {
            source = InputDevice.SOURCE_MOUSE
            target().dispatchGenericMotionEvent(this)
            recycle()
        }
    }

    private fun target(): View = fullscreenView ?: web

    private inner class Chrome : WebChromeClient() {
        // Allows protected playback (Widevine) requested by the site's video player.
        override fun onPermissionRequest(request: PermissionRequest) {
            val wanted = request.resources.filter { it == PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID }
            if (wanted.isEmpty()) request.deny() else request.grant(wanted.toTypedArray())
        }

        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
            fullscreenView = view
            fullscreenCallback = callback
            root.addView(view, 1, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        }

        override fun onHideCustomView() {
            fullscreenView?.let { root.removeView(it) }
            fullscreenView = null
            fullscreenCallback = null
        }
    }
}

/** Pointer drawn over the page. */
private class CursorView(context: Context) : View(context) {
    var x0 = 960f
    var y0 = 540f
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 4f
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        x0 = w / 2f; y0 = h / 2f
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawCircle(x0, y0, 14f, fill)
        canvas.drawCircle(x0, y0, 14f, ring)
    }
}
