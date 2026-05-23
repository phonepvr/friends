package com.phonepvr.friends.data.reachout

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.phonepvr.friends.domain.model.InteractionType

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
    const val SIGNAL_PACKAGE = "org.thoughtcrime.securesms"

    /** True when the target is reachable on this device. */
    fun isAvailable(context: Context, method: ReachOutMethod): Boolean = when (method) {
        // ACTION_DIAL and sms: schemes are always resolvable by the system.
        ReachOutMethod.CALL, ReachOutMethod.SMS -> true
        ReachOutMethod.WHATSAPP -> AppAvailability.isInstalled(context, WHATSAPP_PACKAGE)
        ReachOutMethod.SIGNAL -> AppAvailability.isInstalled(context, SIGNAL_PACKAGE)
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
                Intent(Intent.ACTION_DIAL, Uri.parse("tel:$rawNumber"))
            ReachOutMethod.SMS ->
                Intent(Intent.ACTION_VIEW, Uri.parse("sms:$rawNumber"))
            ReachOutMethod.WHATSAPP ->
                Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/${digits(rawNumber)}"))
                    .setPackage(WHATSAPP_PACKAGE)
            ReachOutMethod.SIGNAL ->
                Intent(Intent.ACTION_VIEW, Uri.parse("https://signal.me/#p/+${digits(rawNumber)}"))
                    .setPackage(SIGNAL_PACKAGE)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    private fun digits(raw: String): String = raw.filter { it.isDigit() }
}
