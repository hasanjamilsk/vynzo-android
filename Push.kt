package online.malaysiagrowthagency.vynzo

import android.content.Context
import com.google.firebase.messaging.FirebaseMessaging

/** FCM টোকেন নিয়ে সার্ভারে নিবন্ধন করা */
object Push {
    fun ensureToken(ctx: Context) {
        val app = ctx.applicationContext
        try {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { tk ->
                if (!tk.isNullOrEmpty()) {
                    Store.setFcm(app, tk)
                    register(app)
                }
            }
        } catch (_: Exception) {
        }
    }

    fun register(ctx: Context) {
        val s = Store.session(ctx)
        val t = Store.fcm(ctx)
        if (s.isNotEmpty() && t.isNotEmpty()) {
            Net.postAsync("/fcm/register", "{\"token\":\"$t\"}", s, 4)
        }
    }

    fun unregister(ctx: Context, oldSession: String) {
        val t = Store.fcm(ctx)
        if (oldSession.isNotEmpty() && t.isNotEmpty()) {
            Net.postAsync("/fcm/unregister", "{\"token\":\"$t\"}", oldSession)
        }
    }
}
