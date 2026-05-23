package com.phonepvr.friends.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phonepvr.friends.data.db.dao.TimelineDao
import com.phonepvr.friends.data.db.entity.TimelineEntryEntity
import com.phonepvr.friends.data.repository.PeopleRepository
import com.phonepvr.friends.domain.review.EventInfo
import com.phonepvr.friends.domain.review.PersonInfo
import com.phonepvr.friends.domain.review.ReviewYear
import com.phonepvr.friends.domain.review.TimelinePoint
import com.phonepvr.friends.domain.review.YearInReview
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

private const val SPARSE_THRESHOLD = 5

sealed interface ReviewUiState {
    data object Loading : ReviewUiState
    data class Sparse(val year: Int) : ReviewUiState
    data class Loaded(val review: ReviewYear) : ReviewUiState
}

@HiltViewModel
class YearInReviewViewModel @Inject constructor(
    peopleRepository: PeopleRepository,
    timelineDao: TimelineDao,
) : ViewModel() {

    private val today: LocalDate = LocalDate.now()

    private val _selectedYear = MutableStateFlow(today.year)
    val selectedYear: StateFlow<Int> = _selectedYear

    private val _includeSilent = MutableStateFlow(false)
    val includeSilent: StateFlow<Boolean> = _includeSilent

    private val timelinePoints = timelineDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val peopleAndEvents = peopleRepository.observeActiveWithDetails()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val availableYears: StateFlow<List<Int>> = timelinePoints
        .map { entries ->
            YearInReview.availableYears(entries.map { it.toReviewPoint() }, today.year)
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            listOf(today.year),
        )

    val uiState: StateFlow<ReviewUiState> = combine(
        timelinePoints,
        peopleAndEvents,
        _selectedYear,
        _includeSilent,
    ) { timelineEntries, peopleWithDetails, year, includeSilent ->
        val people = peopleWithDetails.map {
            PersonInfo(
                id = it.person.id,
                displayName = it.person.displayName,
                isTracked = (it.person.cadenceTargetDays ?: 0) > 0,
            )
        }
        val events = peopleWithDetails.flatMap { detail ->
            detail.events.map { EventInfo(detail.person.id, it.month, it.day) }
        }
        val points = timelineEntries.map { it.toReviewPoint() }
        val review = YearInReview.compute(year, people, points, events, includeSilent)
        if (review.totalInteractions < SPARSE_THRESHOLD) {
            ReviewUiState.Sparse(year)
        } else {
            ReviewUiState.Loaded(review)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReviewUiState.Loading)

    fun setYear(year: Int) {
        _selectedYear.value = year
    }

    fun setIncludeSilent(include: Boolean) {
        _includeSilent.value = include
    }
}

private fun TimelineEntryEntity.toReviewPoint(): TimelinePoint = TimelinePoint(
    personId = personId,
    date = Instant.ofEpochMilli(occurredAt).atZone(ZoneId.systemDefault()).toLocalDate(),
    type = type,
    countsAsContact = countsAsContact,
)
