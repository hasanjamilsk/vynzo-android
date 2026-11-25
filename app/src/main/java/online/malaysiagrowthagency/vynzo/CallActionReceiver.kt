package online.malaysiagrowthagency.vynzo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** নোটিফিকেশনের "কেটে দাও" বাটন */
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        val call = i.getStringExtra("call") ?: return
        val from = i.getStringExtra("from") ?: ""
        rejectCall(ctx, call, from)
    }

    companion object {
        fun rejectCall(ctx: Context, call: String, from: String) {
            val f = from.toIntOrNull()
            val s = Store.session(ctx)
            if (f != null && s.isNotEmpty() && call.matches(Regex("[A-Za-z0-9]{8,32}"))) {
                Net.postAsync("/call/signal", "{\"to\":$f,\"call\":\"$call\",\"type\":\"reject\"}", s)
            }
            Notifs.callDone(ctx, call, null, false)
        }
    }
}
