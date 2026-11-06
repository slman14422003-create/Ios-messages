package app.ios.messages.data

import android.content.Context
import android.telephony.SmsManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager

data class SimInfo(
    val subId: Int,
    val slot: Int,
    val carrier: String,
    val label: String,
    val color: Long
)

object Sims {
    /** Orange, purple, cyan, pink, indigo, gray (kept away from the blue/green bubble colors). */
    val palette = longArrayOf(0xFFFF9500, 0xFFAF52DE, 0xFF5AC8FA, 0xFFFF2D55, 0xFF5856D6, 0xFF8E8E93)

    fun list(ctx: Context): List<SimInfo> {
        val sm = ctx.getSystemService(SubscriptionManager::class.java) ?: return emptyList()
        val subs: List<SubscriptionInfo> = try {
            sm.activeSubscriptionInfoList ?: emptyList()
        } catch (e: SecurityException) {
            emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        val prefs = Prefs(ctx)
        return subs.sortedBy { it.simSlotIndex }.map { s ->
            val carrier = s.carrierName?.toString()?.takeIf { it.isNotBlank() }
                ?: s.displayName?.toString()?.takeIf { it.isNotBlank() }
                ?: "SIM ${s.simSlotIndex + 1}"
            val custom = prefs.simLabel(s.subscriptionId)
            val color = prefs.simColor(s.subscriptionId)
                ?: palette[s.simSlotIndex.coerceAtLeast(0) % palette.size]
            SimInfo(s.subscriptionId, s.simSlotIndex, carrier, custom ?: carrier, color)
        }
    }

    /** SmsManager bound to one SIM (falls back to the system default when subId is unknown). */
    fun manager(ctx: Context, subId: Int): SmsManager {
        val base = ctx.getSystemService(SmsManager::class.java)
        if (subId < 0) return base
        return try {
            base.createForSubscriptionId(subId)
        } catch (e: Exception) {
            base
        }
    }

    fun systemDefaultSms(): Int = try {
        SubscriptionManager.getDefaultSmsSubscriptionId()
    } catch (e: Exception) {
        -1
    }
}
