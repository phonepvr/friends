package com.phonepvr.friends.domain.review

import com.phonepvr.friends.domain.model.CallType

/** Which side of the bonds / not-bonds line a call's number falls on. */
enum class CallBucket { BOND, CONTACT, UNKNOWN }

/** A bonded person reduced to what the analytics + leaderboard need. */
data class BondRef(
    val personId: Long,
    val displayName: String,
    val photoRelativePath: String?,
)

/** One call reduced to the fields the analytics needs (no Android deps). */
data class CallRecord(
    val number: String,
    val type: CallType,
    val durationSeconds: Long,
    /** Epoch millis the call happened — used to tell whether a missed call
     *  was later "returned" by a subsequent call with the same bond. */
    val timestampMillis: Long = 0,
)

/** Per-person (or per-number) rollup for the window. */
data class CallPartyStat(
    val key: String,
    val number: String,
    val displayName: String?,
    val personId: Long?,
    val photoRelativePath: String?,
    val bucket: CallBucket,
    val callCount: Int,
    val totalDurationSec: Long,
    val incoming: Int,
    val outgoing: Int,
    val missed: Int,
)

/** Aggregate for one bucket (bond / saved-contact / unknown). */
data class BucketSummary(
    val callCount: Int = 0,
    val totalDurationSec: Long = 0,
    val uniqueParties: Int = 0,
)

data class CallAnalyticsResult(
    val windowDays: Int,
    val totalCalls: Int,
    val totalDurationSec: Long,
    val bond: BucketSummary,
    val contact: BucketSummary,
    val unknown: BucketSummary,
    val topParties: List<CallPartyStat>,
    val bondedReachedCount: Int,
    val bondedTotalCount: Int,
    val missedTotal: Int,
    /**
     * Bonds whose most recent call in the window is an *unreturned* missed or
     * rejected call — i.e. you haven't called back or spoken since. A missed
     * call followed by any later call with that bond is considered handled and
     * does NOT count here. These are the ones genuinely worth a call back.
     */
    val bondsToCallBack: List<CallPartyStat>,
    val incomingTotal: Int,
    val outgoingTotal: Int,
) {
    val isEmpty: Boolean get() = totalCalls == 0
    val bondsToCallBackCount: Int get() = bondsToCallBack.size
}

/**
 * Pure call-log analytics. Classifies each call by whether its number
 * belongs to a bonded person, a saved (but un-bonded) contact, or nobody
 * known — then rolls the window up into bond-vs-others comparisons, a
 * top-callers leaderboard, bonded reach, and missed/direction totals.
 *
 * No Android dependencies, so it's JVM-unit-testable.
 */
object CallAnalytics {

    /** Compare numbers on their last [SUFFIX_LEN] digits so country-code
     *  prefix differences (+44 7… vs 07…) still match. */
    private const val SUFFIX_LEN = 9
    private const val MIN_MATCH_LEN = 7
    private const val TOP_N = 8

    private class Mutable(
        val key: String,
        val number: String,
        var displayName: String?,
        var personId: Long?,
        var photoRelativePath: String?,
        var bucket: CallBucket,
    ) {
        var callCount = 0
        var totalDurationSec = 0L
        var incoming = 0
        var outgoing = 0
        var missed = 0

        /** Type + time of this party's most recent call, for call-back logic. */
        var latestType: CallType? = null
        var latestTimestamp = Long.MIN_VALUE
    }

    fun compute(
        windowDays: Int,
        calls: List<CallRecord>,
        bondBySuffix: Map<String, BondRef>,
        contactNameBySuffix: Map<String, String>,
        bondedTotalCount: Int,
    ): CallAnalyticsResult {
        val parties = HashMap<String, Mutable>()

        for (call in calls) {
            val digits = call.number.filter(Char::isDigit)
            val matchKey = if (digits.length >= MIN_MATCH_LEN) {
                digits.takeLast(SUFFIX_LEN)
            } else {
                null
            }
            val bond = matchKey?.let { bondBySuffix[it] }
            val contactName = matchKey?.let { contactNameBySuffix[it] }
            val bucket = when {
                bond != null -> CallBucket.BOND
                contactName != null -> CallBucket.CONTACT
                else -> CallBucket.UNKNOWN
            }
            // Group bonded calls under the person id so multiple numbers for
            // the same bond collapse into one leaderboard row.
            val groupKey = when (bucket) {
                CallBucket.BOND -> "bond:${bond!!.personId}"
                CallBucket.CONTACT -> matchKey ?: digits.ifEmpty { "private" }
                CallBucket.UNKNOWN -> matchKey ?: digits.ifEmpty { "private" }
            }
            val party = parties.getOrPut(groupKey) {
                Mutable(
                    key = groupKey,
                    number = call.number.ifBlank { "Private number" },
                    displayName = bond?.displayName ?: contactName,
                    personId = bond?.personId,
                    photoRelativePath = bond?.photoRelativePath,
                    bucket = bucket,
                )
            }
            party.callCount++
            party.totalDurationSec += call.durationSeconds.coerceAtLeast(0)
            when (call.type) {
                CallType.INCOMING -> party.incoming++
                CallType.OUTGOING -> party.outgoing++
                CallType.MISSED, CallType.REJECTED -> party.missed++
            }
            // Track the single most recent call so we can tell whether a missed
            // call was the *last* contact (still owed a call back) or has since
            // been followed by another call (already handled).
            if (call.timestampMillis >= party.latestTimestamp) {
                party.latestTimestamp = call.timestampMillis
                party.latestType = call.type
            }
        }

        val partyStats = parties.values.map {
            CallPartyStat(
                key = it.key,
                number = it.number,
                displayName = it.displayName,
                personId = it.personId,
                photoRelativePath = it.photoRelativePath,
                bucket = it.bucket,
                callCount = it.callCount,
                totalDurationSec = it.totalDurationSec,
                incoming = it.incoming,
                outgoing = it.outgoing,
                missed = it.missed,
            )
        }

        fun summary(bucket: CallBucket): BucketSummary {
            val inBucket = partyStats.filter { it.bucket == bucket }
            return BucketSummary(
                callCount = inBucket.sumOf { it.callCount },
                totalDurationSec = inBucket.sumOf { it.totalDurationSec },
                uniqueParties = inBucket.size,
            )
        }

        val topParties = partyStats
            .sortedWith(
                compareByDescending<CallPartyStat> { it.totalDurationSec }
                    .thenByDescending { it.callCount },
            )
            .take(TOP_N)

        val reachedBondIds = partyStats
            .filter { it.bucket == CallBucket.BOND }
            .mapNotNull { it.personId }
            .toSet()

        // A bond is "worth a call back" only if its most recent call is an
        // unreturned missed/rejected one. Any later call (answered incoming or
        // an outgoing attempt) means you've reconnected, so it drops off.
        val statByKey = partyStats.associateBy { it.key }
        val bondsToCallBack = parties.values
            .asSequence()
            .filter { it.bucket == CallBucket.BOND }
            .filter { it.latestType == CallType.MISSED || it.latestType == CallType.REJECTED }
            .sortedByDescending { it.latestTimestamp }
            .mapNotNull { statByKey[it.key] }
            .toList()

        return CallAnalyticsResult(
            windowDays = windowDays,
            totalCalls = calls.size,
            totalDurationSec = calls.sumOf { it.durationSeconds.coerceAtLeast(0) },
            bond = summary(CallBucket.BOND),
            contact = summary(CallBucket.CONTACT),
            unknown = summary(CallBucket.UNKNOWN),
            topParties = topParties,
            bondedReachedCount = reachedBondIds.size,
            bondedTotalCount = bondedTotalCount,
            missedTotal = partyStats.sumOf { it.missed },
            bondsToCallBack = bondsToCallBack,
            incomingTotal = partyStats.sumOf { it.incoming },
            outgoingTotal = partyStats.sumOf { it.outgoing },
        )
    }
}
