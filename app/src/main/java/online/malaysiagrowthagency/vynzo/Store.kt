package online.malaysiagrowthagency.vynzo

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** অ্যাপের ছোট সেভ-করা তথ্য আর সার্ভারের সাথে কথা বলার সাহায্যকারী */
object Store {
    // তোমার সাইটের ঠিকানা (শেষে / নেই)
    const val BASE = "https://malaysiagrowthagency.online"

    @Volatile
    var foreground = false

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("vynzo", Context.MODE_PRIVATE)

    fun session(c: Context): String = prefs(c).getString("session", "") ?: ""
    fun setSession(c: Context, v: String) = prefs(c).edit().putString("session", v).apply()
    fun fcm(c: Context): String = prefs(c).getString("fcm", "") ?: ""
    fun setFcm(c: Context, v: String) = prefs(c).edit().putString("fcm", v).apply()
    fun asked(c: Context): Boolean = prefs(c).getBoolean("asked", false)
    fun setAsked(c: Context) = prefs(c).edit().putBoolean("asked", true).apply()
}

object Net {
    /** সার্ভারে POST পাঠায় (আলাদা থ্রেডে)। retries থাকলে ব্যর্থ হলে কিছুক্ষণ পরে আবার চেষ্টা করে */
    fun postAsync(path: String, json: String, session: String, retries: Int = 0) {
        Thread {
            var attempt = 0
            while (true) {
                var ok = false
                try {
                    val u = URL(Store.BASE + "/api.php?r=" + URLEncoder.encode(path, "UTF-8"))
                    val c = u.openConnection() as HttpURLConnection
                    c.requestMethod = "POST"
                    c.connectTimeout = 8000
                    c.readTimeout = 8000
                    c.doOutput = true
                    c.setRequestProperty("Content-Type", "application/json")
                    if (session.isNotEmpty()) c.setRequestProperty("X-Auth-Token", session)
                    c.outputStream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                    ok = c.responseCode in 200..299
                    c.disconnect()
                } catch (_: Exception) {
                }
                if (ok || attempt >= retries) break
                attempt++
                try {
                    Thread.sleep(4000L * attempt)
                } catch (_: Exception) {
                    break
                }
            }
        }.start()
    }
}
