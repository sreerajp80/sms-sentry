package `in`.sreerajp.sms_sentry.util

import android.content.Context
import org.json.JSONObject

/**
 * Values shown on the Settings → About screen, loaded at runtime from the
 * `about_config.json` asset so they can be edited without touching code.
 */
data class AboutInfo(
    val author: String,
    val lastBuildDate: String,
    val ideUsed: String,
    val aiUsed: String,
)

private const val ABOUT_CONFIG_ASSET = "about_config.json"
private const val MISSING = "—"

/**
 * Read [ABOUT_CONFIG_ASSET] from assets. Any missing file/key falls back to "—"
 * so the About screen always renders something sensible.
 */
fun loadAboutConfig(context: Context): AboutInfo {
    return try {
        val raw = context.assets.open(ABOUT_CONFIG_ASSET).bufferedReader().use { it.readText() }
        val json = JSONObject(raw)
        AboutInfo(
            author = json.optString("author", MISSING).ifBlank { MISSING },
            lastBuildDate = json.optString("lastBuildDate", MISSING).ifBlank { MISSING },
            ideUsed = json.optString("ideUsed", MISSING).ifBlank { MISSING },
            aiUsed = json.optString("aiUsed", MISSING).ifBlank { MISSING },
        )
    } catch (e: Exception) {
        AboutInfo(MISSING, MISSING, MISSING, MISSING)
    }
}
