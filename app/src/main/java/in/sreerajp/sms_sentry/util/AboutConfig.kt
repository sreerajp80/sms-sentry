package `in`.sreerajp.sms_sentry.util

import android.content.Context
import `in`.sreerajp.sms_sentry.BuildConfig
import org.json.JSONObject

/**
 * Values shown on the Settings → About screen. [author], [ideUsed] and [aiUsed]
 * are loaded at runtime from the `about_config.json` asset so they can be edited
 * without touching code. [lastBuildDate] comes from [BuildConfig.BUILD_DATE],
 * which is injected at build time, so it always reflects the real build.
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
            lastBuildDate = BuildConfig.BUILD_DATE,
            ideUsed = json.optString("ideUsed", MISSING).ifBlank { MISSING },
            aiUsed = json.optString("aiUsed", MISSING).ifBlank { MISSING },
        )
    } catch (e: Exception) {
        AboutInfo(MISSING, BuildConfig.BUILD_DATE, MISSING, MISSING)
    }
}
