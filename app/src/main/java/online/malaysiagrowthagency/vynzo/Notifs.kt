package online.malaysiagrowthagency.vynzo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat

object Notifs {
    private const val CH_CALL = "calls_v2"
    private const val CH_MSG = "messages_v1"
    private const val CH_MISSED = "missed_v1"
    const val ACTION_CALL_DONE = "online.malaysiagrowthagency.vynzo.CALL_DONE"

    fun callNotifId(callId: String) = 1000 + (callId.hashCode() and 0x0FFFFFFF) % 100000

    fun channels(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val ring = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        val callCh = NotificationChannel(CH_CALL, "ইনকামিং কল", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "কল আসলে (রিংটোন আলাদা সার্ভিস বাজায়)"
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val msgCh = NotificationChannel(CH_MSG, "মেসেজ", NotificationManager.IMPORTANCE_HIGH).apply {
            enableVibration(true)
        }
        val missedCh = NotificationChannel(CH_MISSED, "মিসড কল", NotificationManager.IMPORTANCE_DEFAULT)
        nm.createNotificationChannels(listOf(callCh, msgCh, missedCh))
    }

    private fun pi(ctx: Context, code: Int, i: Intent, activity: Boolean): PendingIntent {
        val f = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return if (activity) PendingIntent.getActivity(ctx, code, i, f) else PendingIntent.getBroadcast(ctx, code, i, f)
    }

    fun buildCall(ctx: Context, callId: String, from: String, name: String, video: Boolean): Notification {
        channels(ctx)
        val id = callNotifId(callId)
        val full = Intent(ctx, IncomingCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION
            putExtra("call", callId); putExtra("from", from); putExtra("name", name); putExtra("video", video)
        }
        val accept = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("answer", callId)
        }
        val decline = Intent(ctx, CallActionReceiver::class.java).apply {
            putExtra("call", callId); putExtra("from", from)
        }
        val person = androidx.core.app.Person.Builder().setName(name).setImportant(true).build()
        val b = NotificationCompat.Builder(ctx, CH_CALL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(name)
            .setContentText(if (video) "VYNZO ভিডিও কল" else "VYNZO অডিও কল")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setTimeoutAfter(45000)
            .setContentIntent(pi(ctx, id, accept, true))
            .setFullScreenIntent(pi(ctx, id + 1, full, true), true)
            .setStyle(
                NotificationCompat.CallStyle.forIncomingCall(
                    person, pi(ctx, id + 2, decline, false), pi(ctx, id + 3, accept, true)
                )
            )
        return b.build()
    }

    fun incomingCall(ctx: Context, callId: String, from: String, name: String, video: Boolean) {
        channels(ctx)
        val i = Intent(ctx, RingService::class.java).apply {
            putExtra("call", callId); putExtra("from", from); putExtra("name", name); putExtra("video", video)
        }
        try {
            androidx.core.content.ContextCompat.startForegroundService(ctx, i)
        } catch (_: Exception) {
            // সার্ভিস না চললে অন্তত নোটিফিকেশন (রিংটোনসহ) দেখাও
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val n = buildCall(ctx, callId, from, name, video)
            n.flags = n.flags or Notification.FLAG_INSISTENT
            nm.notify(callNotifId(callId), n)
        }
    }

    fun stopRing(ctx: Context) {
        try {
            ctx.stopService(Intent(ctx, RingService::class.java))
        } catch (_: Exception) {
        }
    }

    /** কলারের কল কেটে দেওয়া বা ধরা হয়ে গেলে ঘণ্টা বন্ধ */
    fun callDone(ctx: Context, callId: String, name: String?, showMissed: Boolean) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val id = callNotifId(callId)
        val wasRinging = try {
            nm.activeNotifications.any { it.id == id }
        } catch (_: Exception) {
            true
        }
        nm.cancel(id)
        stopRing(ctx)
        ctx.sendBroadcast(Intent(ACTION_CALL_DONE).setPackage(ctx.packageName).putExtra("call", callId))
        if (showMissed && wasRinging) {
            channels(ctx)
            val open = Intent(ctx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val n = NotificationCompat.Builder(ctx, CH_MISSED)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("মিসড কল")
                .setContentText((name ?: "কেউ") + " কল করেছিল")
                .setAutoCancel(true)
                .setContentIntent(pi(ctx, id + 4, open, true))
                .build()
            nm.notify(id + 5, n)
        }
    }

    fun message(ctx: Context, from: String, name: String, text: String) {
        channels(ctx)
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val fid = from.toIntOrNull() ?: 0
        val open = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("chat", from)
        }
        val n = NotificationCompat.Builder(ctx, CH_MSG)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(if (name.isEmpty()) "নতুন মেসেজ" else name)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pi(ctx, 50000 + fid, open, true))
            .build()
        nm.notify(50000 + fid, n)
    }
}
