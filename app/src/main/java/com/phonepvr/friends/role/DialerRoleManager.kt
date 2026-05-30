package com.phonepvr.friends.role

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telecom.TelecomManager
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads + acquires the platform's "default phone app" status.
 *
 * Phase 4 plumbs the surface (Settings shows the state, the activity-alias
 * makes Bondwidth a candidate target for ACTION_DIAL / tel: intents).
 * Actually qualifying for the role requires an InCallService backed by a
 * working in-call UI — that lands in Phase 5, at which point [isDefaultDialer]
 * stops being purely informational.
 */
@Singleton
class DialerRoleManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun isDefaultDialer(): Boolean {
        val telecom = context.getSystemService<TelecomManager>() ?: return false
        return telecom.defaultDialerPackage == context.packageName
    }

    /**
     * Intent that opens the system "Default phone app" picker. The caller
     * launches it with an ActivityResultLauncher and re-reads
     * [isDefaultDialer] when the launcher returns.
     *
     * Returns null on platforms / images where the picker isn't available.
     * On Android 10+ also returns null when the dialer role isn't available
     * on this device (e.g. Android Go without a dialer).
     */
    fun makeAcquireRoleIntent(): Intent? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = context.getSystemService<RoleManager>() ?: return null
            if (!rm.isRoleAvailable(RoleManager.ROLE_DIALER)) return null
            return rm.createRequestRoleIntent(RoleManager.ROLE_DIALER)
        }
        @Suppress("DEPRECATION")
        return Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
            .putExtra(
                TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME,
                context.packageName,
            )
    }
}
