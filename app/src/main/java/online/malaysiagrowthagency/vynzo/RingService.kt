package online.malaysiagrowthagency.vynzo

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator

/** কল আসা থেকে ধরা/কাটা/৪৫ সেকেন্ড পর্যন্ত রিংটোন বাজায় (WhatsApp-এর মতো) */
class RingService : Service() {
    private var player: MediaPlayer? = null
    private var vib: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val call = intent?.getStringExtra("call") ?: ""
        if (call.isEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }
        val n = Notifs.buildCall(
            this, call,
            intent?.getStringExtra("from") ?: "",
            intent?.getStringExtra("name") ?: "কেউ",
            intent?.getBooleanExtra("video", false) ?: false
        )
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(Notifs.callNotifId(call), n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(Notifs.callNotifId(call), n)
            }
        } catch (_: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }
        startRing()
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ stopSelf() }, 45000)
        return START_NOT_STICKY
    }

    private fun startRing() {
        stopRing()
        try {
            val p = MediaPlayer()
            p.setDataSource(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))
            p.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            p.isLooping = true
            p.prepare()
            p.start()
            player = p
        } catch (_: Exception) {
        }
        try {
            @Suppress("DEPRECATION")
            val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            vib = v
            val pattern = longArrayOf(0, 700, 400, 700, 400)
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(pattern, 0)
            }
        } catch (_: Exception) {
        }
    }

    private fun stopRing() {
        try {
            player?.stop()
        } catch (_: Exception) {
        }
        try {
            player?.release()
        } catch (_: Exception) {
        }
        player = null
        try {
            vib?.cancel()
        } catch (_: Exception) {
        }
        vib = null
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        stopRing()
        super.onDestroy()
    }
}
