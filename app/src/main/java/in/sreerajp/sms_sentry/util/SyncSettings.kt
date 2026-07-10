package `in`.sreerajp.sms_sentry.util

import android.content.Context
import org.json.JSONObject

/**
 * The explicit **allow-list** of app settings that P2P sync may carry between phones.
 *
 * Only **non-sensitive, non-device-specific behaviour** settings are here (all read at
 * use-time, so a synced value takes effect without a restart). Everything else is excluded
 * on purpose: theme prefs (`selected_theme` / `is_dark_theme` / `is_system_theme`) would need
 * a restart to reflect; `default_sms_sim` is per-device (SIM slots differ); `muted_senders` /
 * `paid_message_ids` / drafts are device/message-specific. This is an allow-list, never a
 * blocklist — a new pref is NOT synced unless it is added here with a validator.
 *
 * All values live in the `theme_prefs` SharedPreferences file (the app's single settings store).
 */
object SyncSettings {

    private const val PREFS = "theme_prefs"

    // Each entry validates + clamps a received value before it is written. Booleans pass through;
    // ints are range-clamped. An entry returning null rejects the value (skipped, never applied).
    private sealed interface Spec {
        fun read(ctx: Context): Any?
        fun coerce(value: Any?): Any?
    }

    private class BoolSpec(val default: Boolean) : Spec {
        override fun read(ctx: Context): Any =
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(currentKey, default)
        override fun coerce(value: Any?): Any? = when (value) {
            is Boolean -> value
            else -> null
        }
        // key is bound when placed in ALLOWED (see below); set via a tiny wrapper.
        lateinit var currentKey: String
    }

    private class IntSpec(val default: Int, val min: Int, val max: Int) : Spec {
        override fun read(ctx: Context): Any =
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(currentKey, default)
        override fun coerce(value: Any?): Any? {
            val n = when (value) {
                is Int -> value
                is Number -> value.toInt()
                else -> return null
            }
            return n.coerceIn(min, max)
        }
        lateinit var currentKey: String
    }

    // The allow-list. Keys must match the SharedPreferences keys the app already uses.
    private val ALLOWED: Map<String, Spec> = buildMap {
        putSpec("auto_mark_read_secs", IntSpec(default = 3, min = 0, max = 3600))
        putSpec(ReminderAlarmScheduler.PREF_ALERTS_ENABLED, BoolSpec(default = true))
        putSpec(ReminderAlarmScheduler.PREF_LEAD_DAYS, IntSpec(default = ReminderAlarmScheduler.DEFAULT_LEAD_DAYS, min = 0, max = 30))
        putSpec("reminder_near_days", IntSpec(default = 3, min = 0, max = 30))
        putSpec(SmsNotificationHelper.PREF_REMINDER_VIBRATION, BoolSpec(default = false))
    }

    private fun MutableMap<String, Spec>.putSpec(key: String, spec: Spec) {
        when (spec) {
            is BoolSpec -> spec.currentKey = key
            is IntSpec -> spec.currentKey = key
        }
        put(key, spec)
    }

    /** Read the allow-listed settings into a JSON object for the host's sync payload. */
    fun collect(context: Context): JSONObject {
        val out = JSONObject()
        for ((key, spec) in ALLOWED) {
            out.put(key, spec.read(context))
        }
        return out
    }

    /**
     * Apply a received `settings` object to this device, restricted to the allow-list.
     *
     * - Unknown keys are ignored; each value is type-checked and range-clamped first.
     * - `overwrite = true` (a **full** sync to a fresh phone) writes every valid key.
     * - `overwrite = false` (an **incremental** sync) is **fill-only**: a key is applied only if
     *   this device has not already set it, so the receiver's own choices are never overridden.
     *
     * Returns the number of settings actually written.
     */
    fun apply(context: Context, settings: JSONObject, overwrite: Boolean): Int {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        var applied = 0
        val keys = settings.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val spec = ALLOWED[key] ?: continue                 // not allow-listed → ignore
            if (!overwrite && prefs.contains(key)) continue     // fill-only: never override receiver
            val coerced = spec.coerce(settings.opt(key)) ?: continue
            when (coerced) {
                is Boolean -> editor.putBoolean(key, coerced)
                is Int -> editor.putInt(key, coerced)
            }
            applied++
        }
        editor.apply()
        return applied
    }
}
