package com.phonepvr.friends.data.calllog

import com.phonepvr.friends.domain.model.CallType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CallHistoryCsvTest {

    @Test
    fun `empty list yields just the header`() {
        assertEquals(CallHistoryCsv.HEADER + "\n", CallHistoryCsv.toCsv(emptyList()))
    }

    @Test
    fun `each call becomes one row with the expected columns`() {
        val calls = listOf(
            DeviceCall("+1 555-0142", CallType.OUTGOING, timestampMillis = 0L, durationSeconds = 65L),
            DeviceCall("+1 555-0188", CallType.MISSED, timestampMillis = 1_000L, durationSeconds = 0L),
        )
        val lines = CallHistoryCsv.toCsv(calls).trimEnd('\n').split('\n')
        assertEquals(3, lines.size) // header + 2 rows
        assertEquals(CallHistoryCsv.HEADER, lines[0])

        val row0 = lines[1].split(',')
        assertEquals("+1 555-0142", row0[0])
        assertEquals("OUTGOING", row0[1])
        assertEquals("0", row0[2])
        assertEquals("65", row0[3])
        assertEquals("1970-01-01T00:00:00Z", row0[4])

        val row1 = lines[2].split(',')
        assertEquals("MISSED", row1[1])
        assertEquals("1970-01-01T00:00:01Z", row1[4])
    }

    @Test
    fun `commas in a number are sanitised so the row keeps five columns`() {
        val csv = CallHistoryCsv.toCsv(
            listOf(DeviceCall("1,2,3", CallType.INCOMING, timestampMillis = 0L, durationSeconds = 0L)),
        )
        val row = csv.trimEnd('\n').split('\n')[1]
        assertEquals(5, row.split(',').size)
        assertTrue(row.startsWith("1 2 3,"))
    }

    @Test
    fun `fromCsv round-trips toCsv (ignoring provider id)`() {
        val calls = listOf(
            DeviceCall("+1 555-0142", CallType.OUTGOING, timestampMillis = 1_700_000_000_000L, durationSeconds = 65L),
            DeviceCall("+1 555-0188", CallType.MISSED, timestampMillis = 1_700_000_100_000L, durationSeconds = 0L),
        )
        val parsed = CallHistoryCsv.fromCsv(CallHistoryCsv.toCsv(calls))
        assertEquals(2, parsed.size)
        assertEquals("+1 555-0142", parsed[0].number)
        assertEquals(CallType.OUTGOING, parsed[0].type)
        assertEquals(1_700_000_000_000L, parsed[0].timestampMillis)
        assertEquals(65L, parsed[0].durationSeconds)
        assertEquals(CallType.MISSED, parsed[1].type)
    }

    @Test
    fun `fromCsv skips header, blanks, and malformed rows`() {
        val csv = """
            ${CallHistoryCsv.HEADER}
            +1 555-0100,INCOMING,1000,30,1970-01-01T00:00:01Z

            garbage,row
            +1 555-0101,NOTATYPE,2000,0,x
            +1 555-0102,OUTGOING,notanumber,0,x
            +1 555-0103,OUTGOING,3000,12,1970-01-01T00:00:03Z
        """.trimIndent()
        val parsed = CallHistoryCsv.fromCsv(csv)
        assertEquals(2, parsed.size)
        assertEquals("+1 555-0100", parsed[0].number)
        assertEquals("+1 555-0103", parsed[1].number)
    }
}
