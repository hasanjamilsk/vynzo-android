package online.malaysiagrowthagency.vynzo

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    private lateinit var web: WebView
    private var fileCb: ValueCallback<Array<Uri>>? = null
    private var pendingWebPerm: PermissionRequest? = null
    private var pageReady = false

    inner class Bridge {
        @JavascriptInterface
        fun setSession(token: String?) {
            val t = token ?: ""
            val old = Store.session(this@MainActivity)
            Store.setSession(this@MainActivity, t)
            if (t.isEmpty()) {
                Push.unregister(this@MainActivity, old)
            } else {
                Push.ensureToken(this@MainActivity)
                Push.register(this@MainActivity)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifs.channels(this)
        web = WebView(this)
        setContentView(web)

        val s = web.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.mediaPlaybackRequiresUserGesture = false
        s.allowFileAccess = false
        s.cacheMode = WebSettings.LOAD_DEFAULT
        s.userAgentString = s.userAgentString + " VynzoApp"
        web.addJavascriptInterface(Bridge(), "VynzoNative")

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                val u = r.url
                if (u.toString().startsWith(Store.BASE)) return false
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, u))
                } catch (_: Exception) {
                }
                return true
            }

            override fun onPageFinished(v: WebView, url: String) {
                pageReady = true
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(req: PermissionRequest) {
                runOnUiThread {
                    val need = ArrayList<String>()
                    for (r in req.resources) {
                        if (r == PermissionRequest.RESOURCE_AUDIO_CAPTURE) need.add(Manifest.permission.RECORD_AUDIO)
                        if (r == PermissionRequest.RESOURCE_VIDEO_CAPTURE) need.add(Manifest.permission.CAMERA)
                    }
                    val missing = need.filter {
                        ContextCompat.checkSelfPermission(this@MainActivity, it) != PackageManager.PERMISSION_GRANTED
                    }
                    if (missing.isEmpty()) {
                        req.grant(req.resources)
                    } else {
                        pendingWebPerm = req
                        ActivityCompat.requestPermissions(this@MainActivity, missing.toTypedArray(), 2)
                    }
                }
            }

            override fun onShowFileChooser(
                w: WebView,
                cb: ValueCallback<Array<Uri>>,
                p: FileChooserParams
            ): Boolean {
                fileCb?.onReceiveValue(null)
                fileCb = cb
                return try {
                    startActivityForResult(p.createIntent(), 7)
                    true
                } catch (_: Exception) {
                    fileCb = null
                    false
                }
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (web.canGoBack()) {
                    web.goBack()
                } else {
                    moveTaskToBack(true)
                }
            }
        })

        askPermissionsOnce()

        val start = hashFor(intent)
        web.loadUrl(Store.BASE + "/" + start)
        Push.ensureToken(this)
    }

    private fun hashFor(i: Intent?): String {
        val a = i?.getStringExtra("answer")
        val c = i?.getStringExtra("chat")
        return when {
            !a.isNullOrEmpty() -> "#answer=" + a
            !c.isNullOrEmpty() -> "#chat=" + c
            else -> ""
        }
    }

    private fun askPermissionsOnce() {
        if (Store.asked(this)) return
        Store.setAsked(this)
        val list = ArrayList<String>()
        if (Build.VERSION.SDK_INT >= 33) list.add(Manifest.permission.POST_NOTIFICATIONS)
        list.add(Manifest.permission.RECORD_AUDIO)
        list.add(Manifest.permission.CAMERA)
        ActivityCompat.requestPermissions(this, list.toTypedArray(), 1)
    }

    private fun checkFullScreen() {
        if (Build.VERSION.SDK_INT >= 34) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            if (!nm.canUseFullScreenIntent()) {
                try {
                    startActivity(
                        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName"))
                    )
                } catch (_: Exception) {
                }
            }
        }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(code, perms, res)
        if (code == 2) {
            val r = pendingWebPerm
            pendingWebPerm = null
            if (r != null) {
                if (res.isNotEmpty() && res.all { it == PackageManager.PERMISSION_GRANTED }) r.grant(r.resources) else r.deny()
            }
        } else if (code == 1) {
            checkFullScreen()
        }
    }

    override fun onNewIntent(i: Intent) {
        super.onNewIntent(i)
        setIntent(i)
        val a = i.getStringExtra("answer")
        val c = i.getStringExtra("chat")
        if (!a.isNullOrEmpty() && a.matches(Regex("[A-Za-z0-9]+"))) {
            web.evaluateJavascript("window.__answer && window.__answer('" + a + "')", null)
        } else if (!c.isNullOrEmpty() && c.matches(Regex("[0-9]+"))) {
            web.evaluateJavascript("window.__openChat && window.__openChat(" + c + ")", null)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        if (req == 7) {
            val cb = fileCb
            fileCb = null
            cb?.onReceiveValue(
                if (res == Activity.RESULT_OK) WebChromeClient.FileChooserParams.parseResult(res, data) else null
            )
        } else {
            @Suppress("DEPRECATION")
            super.onActivityResult(req, res, data)
        }
    }

    override fun onResume() {
        super.onResume()
        Store.foreground = true
        Notifs.stopRing(this)
        web.onResume()
        try {
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).cancelAll()
        } catch (_: Exception) {
        }
    }

    override fun onPause() {
        Store.foreground = false
        super.onPause()
    }

    override fun onDestroy() {
        web.removeJavascriptInterface("VynzoNative")
        super.onDestroy()
    }
}
