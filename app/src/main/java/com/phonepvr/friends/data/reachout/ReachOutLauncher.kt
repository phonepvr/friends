package com.phonepvr.friends.data.reachout

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import com.phonepvr.friends.data.dialer.telUri
import com.phonepvr.friends.domain.model.InteractionType
import java.util.Locale

/** The four reach-out channels surfaced on the Person Detail screen. */
enum class ReachOutMethod {
    CALL,
    SMS,
    WHATSAPP,
    SIGNAL;

    /** Which interaction type a follow-up log should record. */
    val interactionType: InteractionType
        get() = when (this) {
            CALL -> InteractionType.CALL
            SMS, WHATSAPP, SIGNAL -> InteractionType.MESSAGE
        }
}

/** Launches the appropriate Intent for a reach-out method. */
object ReachOutLauncher {

    const val WHATSAPP_PACKAGE = "com.whatsapp"
    const val WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b"
    const val SIGNAL_PACKAGE = "org.thoughtcrime.securesms"

    /** True when the target is reachable on this device. */
    fun isAvailable(context: Context, method: ReachOutMethod): Boolean = when (method) {
        // ACTION_DIAL and sms: schemes are always resolvable by the system.
        ReachOutMethod.CALL, ReachOutMethod.SMS -> true
        ReachOutMethod.WHATSAPP -> whatsAppPackage(context) != null
        ReachOutMethod.SIGNAL -> AppAvailability.isInstalled(context, SIGNAL_PACKAGE)
    }

    /** WhatsApp, or WhatsApp Business when only that is installed; null if neither. */
    private fun whatsAppPackage(context: Context): String? = when {
        AppAvailability.isInstalled(context, WHATSAPP_PACKAGE) -> WHATSAPP_PACKAGE
        AppAvailability.isInstalled(context, WHATSAPP_BUSINESS_PACKAGE) -> WHATSAPP_BUSINESS_PACKAGE
        else -> null
    }

    /**
     * The country to complete local numbers with for chat links: the SIM's, then
     * the network's, then the device locale's. Needs no permission.
     */
    fun deviceCountryIso(context: Context): String? {
        val telephony = context.getSystemService(TelephonyManager::class.java)
        return listOfNotNull(
            runCatching { telephony?.simCountryIso }.getOrNull(),
            runCatching { telephony?.networkCountryIso }.getOrNull(),
            Locale.getDefault().country,
        ).firstOrNull { it.length == 2 }
    }

    /** [rawNumber] as chat apps need it; see [ChatNumber.internationalDigits]. */
    fun chatNumber(context: Context, rawNumber: String): String? =
        ChatNumber.internationalDigits(rawNumber, deviceCountryIso(context)) { number, iso ->
            PhoneNumberUtils.formatNumberToE164(number, iso)
        }

    /**
     * Launches [method] against [rawNumber]. Returns true when an activity
     * actually started; false when no app handled the intent (this normally
     * means the WhatsApp/Signal package was uninstalled between the
     * availability check and the tap).
     */
    fun launch(context: Context, method: ReachOutMethod, rawNumber: String): Boolean {
        val intent = when (method) {
            ReachOutMethod.CALL ->
                Intent(Intent.ACTION_DIAL, telUri(rawNumber))
            ReachOutMethod.SMS ->
                Intent(Intent.ACTION_VIEW, Uri.parse("sms:$rawNumber"))
            // Both links need the international number (country code included).
            ReachOutMethod.WHATSAPP -> {
                val number = chatNumber(context, rawNumber) ?: return false
                Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$number"))
                    .setPackage(whatsAppPackage(context) ?: WHATSAPP_PACKAGE)
            }
            ReachOutMethod.SIGNAL -> {
                val number = chatNumber(context, rawNumber) ?: return false
                Intent(Intent.ACTION_VIEW, Uri.parse("https://signal.me/#p/+$number"))
                    .setPackage(SIGNAL_PACKAGE)
            }
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }
}
