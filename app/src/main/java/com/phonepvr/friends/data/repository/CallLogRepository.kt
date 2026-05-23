package com.phonepvr.friends.data.repository

import com.phonepvr.friends.data.calllog.CallLogReader
import com.phonepvr.friends.data.calllog.DeviceCall
import com.phonepvr.friends.data.db.dao.PhoneNumberDao
import com.phonepvr.friends.data.db.dao.TimelineDao
import com.phonepvr.friends.data.phone.AndroidPhoneNumberMatcher
import com.phonepvr.friends.domain.phone.PhoneNumberMatcher
import javax.inject.Inject
import javax.inject.Singleton

/** A device call matched to a specific tracked person and ready to be logged. */
data class PersonCallCandidate(
    val deviceCall: DeviceCall,
    val callDedupKey: String,
)

@Singleton
class CallLogRepository @Inject constructor(
    private val callLogReader: CallLogReader,
    private val phoneNumberDao: PhoneNumberDao,
    private val timelineDao: TimelineDao,
) {
    /**
     * Reads the last [windowDays] of device call log, keeps only the calls
     * to / from one of [personId]'s stored phones, drops anything whose
     * `callDedupKey` is already present in the timeline.
     *
     * Matching reuses the two-stage approach (last-7-digit prefilter, then
     * `PhoneNumberUtils.compare` strict confirm) so `+CC`, `0`-prefix and
     * no-prefix variants all match correctly.
     *
     * The list is sorted newest-first so the UI can show recent calls at the
     * top. Returns empty when the person has no phones, when no calls
     * matched, or when every match is already in the timeline.
     */
    suspend fun scanForPerson(personId: Long, windowDays: Int = 120): List<PersonCallCandidate> {
        val phones = phoneNumberDao.getForPerson(personId)
        if (phones.isEmpty()) return emptyList()
        val sinceMillis =
            System.currentTimeMillis() - windowDays.toLong() * 24L * 60L * 60L * 1000L
        val deviceCalls = callLogReader.recentCalls(sinceMillis)
        if (deviceCalls.isEmpty()) return emptyList()

        // Prefilter: index this person's phones by their last 7 digits.
        val matchKeys = phones
            .mapNotNull { PhoneNumberMatcher.matchKey(it.normalizedNumber) }
            .toSet()
        if (matchKeys.isEmpty()) return emptyList()

        val matched = deviceCalls
            .asSequence()
            .filter { PhoneNumberMatcher.matchKey(it.number) in matchKeys }
            .filter { call ->
                // Strict confirm: every candidate phone gets compared via
                // PhoneNumberUtils.compare so locale-prefix variants resolve.
                phones.any { AndroidPhoneNumberMatcher.strictMatches(call.number, it.rawNumber) }
            }
            .map { call ->
                PersonCallCandidate(
                    deviceCall = call,
                    callDedupKey = PhoneNumberMatcher.callDedupKey(
                        call.number,
                        call.timestampMillis,
                    ),
                )
            }
            .toList()
        // Dedupe against the timeline. The DAO call is suspend, so it has to
        // live outside any Sequence/List functional pipeline (those don't
        // propagate suspend through their lambdas).
        val notYetLogged = mutableListOf<PersonCallCandidate>()
        for (candidate in matched) {
            if (!timelineDao.existsByCallDedupKey(candidate.callDedupKey)) {
                notYetLogged.add(candidate)
            }
        }
        return notYetLogged.sortedByDescending { it.deviceCall.timestampMillis }
    }
}
