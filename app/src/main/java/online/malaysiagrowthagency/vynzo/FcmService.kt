package online.malaysiagrowthagency.vynzo

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class FcmService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        Store.setFcm(this, token)
        Push.register(this)
    }

    override fun onMessageReceived(m: RemoteMessage) {
        val d = m.data
        val call = d["call"] ?: ""
        val name = d["name"] ?: ""
        when (d["t"]) {
            "call" -> if (!Store.foreground && call.isNotEmpty()) {
                Notifs.incomingCall(this, call, (d["uid"] ?: d["from"] ?: ""), name.ifEmpty { "কেউ" }, d["v"] == "1")
            }
            "cancel" -> if (call.isNotEmpty()) Notifs.callDone(this, call, name, true)
            "msg" -> if (!Store.foreground) Notifs.message(this, (d["uid"] ?: d["from"] ?: ""), name, d["text"] ?: "")
        }
    }
}
