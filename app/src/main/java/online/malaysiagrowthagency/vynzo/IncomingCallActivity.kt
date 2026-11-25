package online.malaysiagrowthagency.vynzo

import android.app.Activity
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

/** লক স্ক্রিনের ওপরে পুরো স্ক্রিনের ইনকামিং কল পেজ (রিংটোন বাজে নোটিফিকেশন থেকে) */
class IncomingCallActivity : Activity() {
    private var callId = ""
    private var from = ""
    private val handler = Handler(Looper.getMainLooper())

    private val doneReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (i.getStringExtra("call") == callId) finish()
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        callId = intent.getStringExtra("call") ?: ""
        from = intent.getStringExtra("from") ?: ""
        val name = intent.getStringExtra("name") ?: "কেউ"
        val video = intent.getBooleanExtra("video", false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        try {
            (getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).requestDismissKeyguard(this, null)
        } catch (_: Exception) {
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.parseColor("#0B141A"))
            setPadding(dp(24), dp(72), dp(24), dp(56))
        }
        root.addView(text(if (video) "VYNZO ভিডিও কল" else "VYNZO অডিও কল", 15f, "#9FB3AA", false))

        val av = TextView(this).apply {
            this.text = name.trim().take(1).uppercase()
            textSize = 52f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#5B6CFF")) }
        }
        root.addView(av, LinearLayout.LayoutParams(dp(130), dp(130)).apply { topMargin = dp(64) })
        root.addView(text(name, 30f, "#FFFFFF", true).apply { setPadding(0, dp(24), 0, 0) })
        root.addView(text("কল করছে…", 16f, "#9FB3AA", false).apply { setPadding(0, dp(8), 0, 0) })

        root.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f))

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        row.addView(button("✕", "কেটে দাও", "#E53935") { decline() })
        row.addView(View(this), LinearLayout.LayoutParams(dp(72), 1))
        row.addView(button("✓", "ধরো", "#25C05A") { accept() })
        root.addView(row)
        setContentView(root)

        ContextCompat.registerReceiver(
            this, doneReceiver, IntentFilter(Notifs.ACTION_CALL_DONE), ContextCompat.RECEIVER_NOT_EXPORTED
        )
        handler.postDelayed({ finish() }, 46000)
    }

    private fun text(t: String, size: Float, color: String, bold: Boolean) = TextView(this).apply {
        this.text = t
        textSize = size
        gravity = Gravity.CENTER
        setTextColor(Color.parseColor(color))
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun button(sym: String, label: String, color: String, onClick: () -> Unit): LinearLayout {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        val c = TextView(this).apply {
            this.text = sym
            textSize = 30f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor(color)) }
            setOnClickListener { onClick() }
        }
        col.addView(c, LinearLayout.LayoutParams(dp(76), dp(76)))
        col.addView(text(label, 14f, "#FFFFFF", false).apply { setPadding(0, dp(10), 0, 0) })
        return col
    }

    private fun accept() {
        Notifs.callDone(this, callId, null, false)
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("answer", callId)
            }
        )
        finish()
    }

    private fun decline() {
        CallActionReceiver.rejectCall(this, callId, from)
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        try {
            unregisterReceiver(doneReceiver)
        } catch (_: Exception) {
        }
        super.onDestroy()
    }
}
