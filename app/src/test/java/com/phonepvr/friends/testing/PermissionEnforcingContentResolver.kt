package com.phonepvr.friends.testing

import android.Manifest
import android.content.ContentResolver
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.CancellationSignal
import android.provider.CallLog
import android.provider.ContactsContract
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowContentResolver

/**
 * A ContentResolver that enforces provider permissions the way a real device does.
 *
 * On Android 8+ an app targeting O or later gets a SecurityException when it
 * observes or queries the contacts or call-log provider without the matching
 * permission. Robolectric's stock shadow allows everything, which is how a
 * startup crash on fresh installs (nothing granted yet) went unnoticed. Use it
 * with `@Config(shadows = [PermissionEnforcingContentResolver::class])`.
 */
@Implements(ContentResolver::class)
class PermissionEnforcingContentResolver : ShadowContentResolver() {

    @Implementation
    override fun registerContentObserver(
        uri: Uri,
        notifyForDescendents: Boolean,
        observer: ContentObserver,
    ) {
        enforceAccess(uri)
        super.registerContentObserver(uri, notifyForDescendents, observer)
    }

    @Implementation
    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
    ): Cursor? {
        enforceAccess(uri)
        return super.query(uri, projection, selection, selectionArgs, sortOrder)
    }

    @Implementation
    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
        cancellationSignal: CancellationSignal?,
    ): Cursor? {
        enforceAccess(uri)
        return super.query(uri, projection, selection, selectionArgs, sortOrder, cancellationSignal)
    }

    private fun enforceAccess(uri: Uri) {
        val (read, write) = when (uri.authority) {
            ContactsContract.AUTHORITY ->
                Manifest.permission.READ_CONTACTS to Manifest.permission.WRITE_CONTACTS
            CallLog.AUTHORITY ->
                Manifest.permission.READ_CALL_LOG to Manifest.permission.WRITE_CALL_LOG
            else -> return
        }
        val app = RuntimeEnvironment.getApplication()
        val granted = PackageManager.PERMISSION_GRANTED
        if (app.checkSelfPermission(read) != granted && app.checkSelfPermission(write) != granted) {
            // Same wording as the platform's ContentProviderHelper.
            throw SecurityException(
                "Permission Denial: opening provider for $uri requires $read or $write",
            )
        }
    }
}
