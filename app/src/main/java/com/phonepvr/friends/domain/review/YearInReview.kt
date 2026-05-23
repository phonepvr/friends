package com.phonepvr.friends.domain.review

import com.phonepvr.friends.domain.model.InteractionType
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** A person with their contact-interaction count for a given year. */
data class PersonStat(
    val personId: Long,
    val displayName: String,
    val count: Int,
)

/** Longest stretch of silence between two interactions with a tracked person. */
data class GapStat(
    val personId: Long,
    val displayName: String,
    val days: Long,
    val fromDate: LocalDate,
    val toDate: LocalDate,
)

/** Plain-Kotlin snapshot of one year of activity, ready to render. */
data class ReviewYear(
    val year: Int,
    val totalInteractions: Int,
    val contactInteractions: Int,
    val mostConnected: PersonStat?,
    val leastConnectedTracked: PersonStat?,
    val longestGap: GapStat?,
    val acknowledgedEventCount: Int,
    val totalEvents: Int,
    val typeBreakdown: Map<InteractionType, Int>,
)

/** Lightweight view of one person for the review computation. */
data class PersonInfo(
    val id: Long,
    val displayName: String,
    val isTracked: Boolean,
)

/** One interaction reduced to the fields the review needs. */
data class TimelinePoint(
    val personId: Long,
    val date: LocalDate,
    val type: InteractionType,
    val countsAsContact: Boolean,
)

/** One annual event reduced to its month/day anchor. */
data class EventInfo(
    val personId: Long,
    val month: Int,
    val day: Int,
)

/**
 * Read-only "year in review" computation. Pure Kotlin so it's JVM-testable
 * with no Android dependencies.
 */
object YearInReview {

    fun availableYears(timeline: List<TimelinePoint>, currentYear: Int): List<Int> {
        val withData = timeline.mapTo(mutableSetOf()) { it.date.year }
        withData.add(currentYear)
        return withData.sortedDescending()
    }

    fun compute(
        year: Int,
        people: List<PersonInfo>,
        timeline: List<TimelinePoint>,
        events: List<EventInfo>,
        includeSilent: Boolean = false,
    ): ReviewYear {
        val inYear = timeline.filter { it.date.year == year }
        val contactsInYear = inYear.filter { it.countsAsContact }
        val personById = people.associateBy { it.id }
        val trackedPeople = people.filter { it.isTracked }

        val contactCountByPerson = contactsInYear
            .groupBy { it.personId }
            .mapValues { it.value.size }

        val mostConnected = contactCountByPerson
            .filterValues { it > 0 }
            .maxByOrNull { it.value }
            ?.let { (personId, count) ->
                val name = personById[personId]?.displayName ?: return@let null
                PersonStat(personId, name, count)
            }

        val leastConnectedTracked = trackedPeople
            .map { person ->
                PersonStat(
                    personId = person.id,
                    displayName = person.displayName,
                    count = contactCountByPerson[person.id] ?: 0,
                )
            }
            .filter { it.count > 0 || includeSilent }
            .minByOrNull { it.count }

        val longestGap = trackedPeople
            .mapNotNull { person ->
                val dates = contactsInYear
                    .filter { it.personId == person.id }
                    .map { it.date }
                    .sorted()
                if (dates.size < 2) return@mapNotNull null
                dates.zipWithNext { a, b ->
                    GapStat(
                        personId = person.id,
                        displayName = person.displayName,
                        days = ChronoUnit.DAYS.between(a, b),
                        fromDate = a,
                        toDate = b,
                    )
                }.maxByOrNull { it.days }
            }
            .maxByOrNull { it.days }

        val acknowledgedEvents = events.count { event ->
            contactsInYear.any { contact ->
                contact.personId == event.personId &&
                    contact.date.monthValue == event.month &&
                    contact.date.dayOfMonth == event.day
            }
        }

        val typeBreakdown = InteractionType.entries.associateWith { type ->
            inYear.count { it.type == type }
        }

        return ReviewYear(
            year = year,
            totalInteractions = inYear.size,
            contactInteractions = contactsInYear.size,
            mostConnected = mostConnected,
            leastConnectedTracked = leastConnectedTracked,
            longestGap = longestGap,
            acknowledgedEventCount = acknowledgedEvents,
            totalEvents = events.size,
            typeBreakdown = typeBreakdown,
        )
    }
}
