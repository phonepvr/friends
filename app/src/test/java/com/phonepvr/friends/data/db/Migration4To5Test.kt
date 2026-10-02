package com.phonepvr.friends.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import com.phonepvr.friends.data.db.entity.PersonEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.DriverManager

/**
 * Runs the real [MIGRATION_4_5] SQL against a real (in-memory) SQLite holding the
 * schema Room generated for database version 4. Room's own MigrationTestHelper
 * needs an emulator or Robolectric; this covers the part that can silently break
 * an upgrade — that the statement is valid, keeps every existing row, and leaves
 * the table with exactly the columns [PersonEntity] declares.
 */
class Migration4To5Test {

    private lateinit var conn: Connection

    @Before
    fun openVersion4Database() {
        conn = DriverManager.getConnection("jdbc:sqlite::memory:")
        conn.createStatement().use { st ->
            // `people` exactly as Room created it for v4 (PersonEntity without contactId).
            st.execute(
                "CREATE TABLE people (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "uuid TEXT NOT NULL, displayName TEXT NOT NULL, relationshipTag TEXT, " +
                    "cadenceTargetDays INTEGER, photoRelativePath TEXT, contactLookupKey TEXT, " +
                    "notes TEXT, isArchived INTEGER NOT NULL, createdAt INTEGER NOT NULL, " +
                    "updatedAt INTEGER NOT NULL)",
            )
            st.execute("CREATE UNIQUE INDEX index_people_uuid ON people (uuid)")
            st.execute(
                "INSERT INTO people (uuid, displayName, relationshipTag, cadenceTargetDays, " +
                    "contactLookupKey, notes, isArchived, createdAt, updatedAt) VALUES " +
                    "('u1', 'Mom', 'family', 7, '0r1-abc', 'likes tea', 0, 100, 200), " +
                    "('u2', 'Solo', NULL, NULL, NULL, NULL, 1, 300, 400)",
            )
        }
    }

    @After
    fun close() = conn.close()

    /** Only `execSQL(String)` is needed by the migration; anything else is a bug. */
    private fun migrate() {
        val handler = InvocationHandler { _, method, args ->
            if (method.name == "execSQL" && args?.size == 1) {
                conn.createStatement().use { it.execute(args[0] as String) }
                null
            } else {
                throw UnsupportedOperationException("unexpected call: ${method.name}")
            }
        }
        val db = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java),
            handler,
        ) as SupportSQLiteDatabase
        MIGRATION_4_5.migrate(db)
    }

    private data class Column(val type: String, val notNull: Boolean)

    private fun columns(): Map<String, Column> =
        conn.createStatement().use { st ->
            st.executeQuery("PRAGMA table_info(people)").use { rs ->
                buildMap {
                    while (rs.next()) {
                        put(rs.getString("name"), Column(rs.getString("type"), rs.getInt("notnull") == 1))
                    }
                }
            }
        }

    @Test
    fun migrationCovers_version4_to_version5() {
        assertEquals(4, MIGRATION_4_5.startVersion)
        assertEquals(5, MIGRATION_4_5.endVersion)
    }

    @Test
    fun addsANullableIntegerContactIdColumn() {
        migrate()
        val contactId = columns().getValue("contactId")
        assertEquals("INTEGER", contactId.type)
        assertEquals(false, contactId.notNull)
    }

    @Test
    fun keepsEveryExistingRowAndLeavesContactIdNull() {
        migrate()
        conn.createStatement().use { st ->
            st.executeQuery(
                "SELECT uuid, displayName, contactLookupKey, notes, isArchived, contactId " +
                    "FROM people ORDER BY id",
            ).use { rs ->
                assertTrue(rs.next())
                assertEquals("u1", rs.getString("uuid"))
                assertEquals("Mom", rs.getString("displayName"))
                assertEquals("0r1-abc", rs.getString("contactLookupKey"))
                assertEquals("likes tea", rs.getString("notes"))
                assertEquals(0, rs.getInt("isArchived"))
                rs.getLong("contactId")
                assertTrue(rs.wasNull())
                assertTrue(rs.next())
                assertEquals("u2", rs.getString("uuid"))
                assertEquals(1, rs.getInt("isArchived"))
                rs.getLong("contactId")
                assertTrue(rs.wasNull())
            }
        }
    }

    @Test
    fun resultingTableHasExactlyThePersonEntityColumns() {
        migrate()
        val entityFields = PersonEntity::class.java.declaredFields
            .filter { !it.isSynthetic && !Modifier.isStatic(it.modifiers) }
            .map { it.name }
            .toSet()
        assertEquals(entityFields, columns().keys)
    }

    @Test
    fun newColumnIsWritableAndTheUuidIndexSurvives() {
        migrate()
        conn.createStatement().use { st ->
            st.execute("UPDATE people SET contactId = 42 WHERE uuid = 'u1'")
            st.executeQuery("SELECT contactId FROM people WHERE uuid = 'u1'").use { rs ->
                assertTrue(rs.next())
                assertEquals(42L, rs.getLong(1))
            }
            // The unique uuid index must still reject duplicates.
            val duplicateRejected = try {
                st.execute(
                    "INSERT INTO people (uuid, displayName, isArchived, createdAt, updatedAt) " +
                        "VALUES ('u1', 'Dup', 0, 1, 1)",
                )
                false
            } catch (e: java.sql.SQLException) {
                true
            }
            assertTrue(duplicateRejected)
        }
    }
}
