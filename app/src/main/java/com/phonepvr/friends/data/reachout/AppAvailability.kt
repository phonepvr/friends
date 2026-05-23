package com.phonepvr.friends.data.reachout

import android.content.Context
import android.content.pm.PackageManager

/** Whether a target app is installed on the device. */
object AppAvailability {
    fun isInstalled(context: Context, packageName: String): Boolean = try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
}
