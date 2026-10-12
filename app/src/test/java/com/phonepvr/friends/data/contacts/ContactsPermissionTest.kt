package com.phonepvr.friends.data.contacts

import android.Manifest
import android.provider.ContactsContract
import com.phonepvr.friends.FriendsApplication
import com.phonepvr.friends.testing.PermissionEnforcingContentResolver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Contacts code must behave when READ_CONTACTS is missing (fresh install, or the
 * user said no): observing or querying the provider then throws on a device, which
 * [PermissionEnforcingContentResolver] reproduces here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], shadows = [PermissionEnforcingContentResolver::class])
class ContactsPermissionTest {

    private val app: FriendsApplication
        get() = RuntimeEnvironment.getApplication() as FriendsApplication

    private fun contactObservers() =
        shadowOf(app.contentResolver).getContentObservers(ContactsContract.Contacts.CONTENT_URI)

    @Test
    fun reconciler_waitsForPermission_thenObservesContactsOnce() {
        // The app has started (and called reconciler.start) without permission.
        assertTrue(contactObservers().isEmpty())

        app.bondContactReconciler.onPermissionsMaybeChanged()
        assertTrue("still no permission, so still not observing", contactObservers().isEmpty())

        shadowOf(app).grantPermissions(Manifest.permission.READ_CONTACTS)
        app.bondContactReconciler.onPermissionsMaybeChanged()
        assertEquals(1, contactObservers().size)

        // Repeated resumes must not stack observers.
        app.bondContactReconciler.onPermissionsMaybeChanged()
        assertEquals(1, contactObservers().size)
    }

    @Test
    fun reconcile_withoutPermission_doesNothingAndDoesNotThrow() = runBlocking {
        app.bondContactReconciler.reconcile()
        assertTrue(app.bondContactReconciler.unlinkedPersonIds.value.isEmpty())
        assertTrue(contactObservers().isEmpty())
    }

    @Test
    fun repository_withoutPermission_emitsNothingInsteadOfThrowing() = runBlocking {
        val repository = SystemContactsRepository(app, ContactsReader(app))
        assertEquals(emptyList<DeviceContact>(), repository.observeAll().first())
        assertEquals(Unit, repository.observeChanges().first())
        assertTrue(contactObservers().isEmpty())
    }

    @Test
    fun repository_withPermission_observesContacts() = runBlocking {
        shadowOf(app).grantPermissions(Manifest.permission.READ_CONTACTS)
        val repository = SystemContactsRepository(app, ContactsReader(app))
        // Robolectric has no contacts provider, so the list is empty; what matters is
        // that the flow registers its observer and emits rather than failing.
        assertEquals(emptyList<DeviceContact>(), repository.observeAll().first())
    }
}
