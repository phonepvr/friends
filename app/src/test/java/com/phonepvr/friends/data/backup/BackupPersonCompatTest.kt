package com.phonepvr.friends.data.backup

import com.phonepvr.friends.data.db.entity.PersonEntity
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The backup file format must not change when bonds gain a device-local contact
 * id: new exports stay readable by old builds, and old exports import as-is.
 */
class BackupPersonCompatTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun contactIdIsNeverExported() {
        val person = PersonEntity(
            id = 1,
            uuid = "u1",
            displayName = "Mom",
            contactLookupKey = "0r1-abc",
            contactId = 42,
            createdAt = 1,
            updatedAt = 2,
        )
        val exported = json.encodeToString(BackupPerson.serializer(), person.toBackup())
        assertFalse(exported.contains("contactId"))
        assertFalse(exported.contains("42"))
        assertEquals("0r1-abc", person.toBackup().contactLookupKey)
    }

    @Test
    fun restoredBondsStartWithoutAContactId() {
        val restored = BackupPerson(
            id = 1,
            uuid = "u1",
            displayName = "Mom",
            contactLookupKey = "0r1-abc",
            createdAt = 1,
            updatedAt = 2,
        ).toEntity()
        assertNull(restored.contactId)
        assertEquals("0r1-abc", restored.contactLookupKey)
    }

    @Test
    fun anExportFromBeforeContactIdsStillDecodes() {
        val v3Person = """
            {"id":7,"uuid":"abc","displayName":"Ann","relationshipTag":"friend",
             "cadenceTargetDays":14,"contactLookupKey":"k1","isArchived":false,
             "createdAt":10,"updatedAt":20}
        """.trimIndent()
        val person = json.decodeFromString(BackupPerson.serializer(), v3Person).toEntity()
        assertEquals("Ann", person.displayName)
        assertEquals("k1", person.contactLookupKey)
        assertNull(person.contactId)
    }
}
