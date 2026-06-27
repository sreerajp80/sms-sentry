package `in`.sreerajp.sms_sentry.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat

/**
 * Central dual-SIM helper. Bridges the app's 1-based SIM **slot** index (the durable `simId`
 * stored on every message) and the platform's real **subscriptionId** used by `SmsManager`.
 *
 * The slot index is what the app stores and labels with, because it survives reboots and SIM
 * swaps; the volatile `subscriptionId` is resolved on demand right before a radio send and right
 * after a receive. Everything degrades gracefully to "single default SIM" without the
 * `READ_PHONE_STATE` permission or on devices/emulators without telephony.
 */
object SimManager {

    /** One active SIM. [slot] is 1-based (slot 1 / slot 2); [subscriptionId] is the platform id. */
    data class SimInfo(
        val slot: Int,
        val subscriptionId: Int,
        val displayLabel: String
    )

    /** Sentinel meaning "no specific subscription — use the system default SmsManager". */
    const val NO_SUBSCRIPTION = -1

    fun hasPhoneStatePermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

    /** Active subscriptions as [SimInfo], ordered by slot. Empty without permission / telephony. */
    fun activeSubscriptions(context: Context): List<SimInfo> {
        if (!hasPhoneStatePermission(context)) return emptyList()
        val sm = ContextCompat.getSystemService(context, SubscriptionManager::class.java)
            ?: return emptyList()
        val list = try {
            sm.activeSubscriptionInfoList
        } catch (e: SecurityException) {
            null
        } ?: return emptyList()
        return list.map { it.toSimInfo() }.sortedBy { it.slot }
    }

    private fun SubscriptionInfo.toSimInfo(): SimInfo {
        val slot = simSlotIndex + 1 // simSlotIndex is 0-based
        val carrier = carrierName?.toString()?.takeIf { it.isNotBlank() }
        return SimInfo(
            slot = slot,
            subscriptionId = subscriptionId,
            displayLabel = carrier ?: "SIM $slot"
        )
    }

    /** True when more than one active SIM is present (drives whether SIM pickers are shown). */
    fun isMultiSim(context: Context): Boolean = activeSubscriptions(context).size > 1

    /**
     * Real subscriptionId for a 1-based [slot], or [NO_SUBSCRIPTION] when it can't be resolved
     * (no permission, single-SIM, or unknown slot) — callers then send on the system default SIM.
     */
    fun subscriptionIdForSlot(context: Context, slot: Int): Int =
        activeSubscriptions(context).firstOrNull { it.slot == slot }?.subscriptionId ?: NO_SUBSCRIPTION

    /** 1-based slot for an incoming [subscriptionId], or 1 when it can't be resolved. */
    fun slotForSubscriptionId(context: Context, subscriptionId: Int): Int {
        if (subscriptionId < 0) return 1
        return activeSubscriptions(context)
            .firstOrNull { it.subscriptionId == subscriptionId }?.slot ?: 1
    }

    /**
     * Best-effort subscriptionId carried by an incoming SMS_RECEIVED / SMS_DELIVER broadcast,
     * or [NO_SUBSCRIPTION] when absent. AOSP uses the "subscription" extra; some OEMs use the
     * documented [SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX].
     */
    fun subscriptionIdFromIntent(intent: Intent): Int {
        val subId = intent.getIntExtra("subscription", NO_SUBSCRIPTION)
        if (subId >= 0) return subId
        return intent.getIntExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, NO_SUBSCRIPTION)
    }
}
