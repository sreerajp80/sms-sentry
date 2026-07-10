package `in`.sreerajp.sms_sentry

import android.content.Context
import `in`.sreerajp.sms_sentry.util.SyncSettings
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Tests the settings allow-list: only known keys are applied, values are clamped, incremental
 * sync is fill-only (never overrides the receiver), and full sync overwrites.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SyncSettingsTest {

    private val ctx: Context get() = RuntimeEnvironment.getApplication()
    private fun prefs() = ctx.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)

    @Test
    fun `incremental sync is fill-only and never overrides an existing value`() {
        prefs().edit().clear().putInt("reminder_lead_days", 5).apply()

        val settings = JSONObject().put("reminder_lead_days", 9).put("reminder_near_days", 4)
        val applied = SyncSettings.apply(ctx, settings, overwrite = false)

        // reminder_lead_days already set -> kept; reminder_near_days was unset -> filled.
        assertEquals(1, applied)
        assertEquals(5, prefs().getInt("reminder_lead_days", -1))
        assertEquals(4, prefs().getInt("reminder_near_days", -1))
    }

    @Test
    fun `full sync overwrites and clamps out-of-range values`() {
        prefs().edit().clear().putInt("reminder_lead_days", 5).apply()

        val settings = JSONObject().put("reminder_lead_days", 999) // above the 0..30 cap
        SyncSettings.apply(ctx, settings, overwrite = true)

        assertEquals(30, prefs().getInt("reminder_lead_days", -1)) // clamped, and overwritten
    }

    @Test
    fun `unknown keys are ignored`() {
        prefs().edit().clear().apply()

        val settings = JSONObject().put("selected_theme", "MIDNIGHT").put("default_sms_sim", "SIM 2")
        val applied = SyncSettings.apply(ctx, settings, overwrite = true)

        assertEquals(0, applied)
        assertFalse(prefs().contains("selected_theme"))
        assertFalse(prefs().contains("default_sms_sim"))
    }
}
