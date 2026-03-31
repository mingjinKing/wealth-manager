package com.wealth.manager.ui.achievements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wealth.manager.data.dao.ExpenseDao
import com.wealth.manager.data.dao.WeekStatsDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class AchievementsViewModel @Inject constructor(
    private val expenseDao: ExpenseDao,
    private val weekStatsDao: WeekStatsDao
) : ViewModel() {

    private val _state = MutableStateFlow(AchievementsState())
    val state: StateFlow<AchievementsState> = _state.asStateFlow()

    init {
        loadAchievements()
    }

    private fun loadAchievements() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)

            val (weekStart, weekEnd) = getCurrentWeekRange()
            val recentStats = weekStatsDao.getRecentWeekStats(4).first()

            if (recentStats.isNotEmpty()) {
                val currentWeek = recentStats.firstOrNull()
                if (currentWeek?.wowTriggered == true) {
                    val wow = CurrentWow(
                        savedAmount = currentWeek.savedAmount,
                        reason = "本周消费控制良好",
                        equivalent = "一顿双人火锅",
                        badge = "精打细算",
                        progressHint = "连续4周达标解锁 → 攒钱达人"
                    )
                    _state.value = AchievementsState(
                        isLoading = false,
                        currentWow = wow,
                        recentAchievements = emptyList()
                    )
                } else {
                    _state.value = AchievementsState(isLoading = false, currentWow = null, recentAchievements = emptyList())
                }
            } else {
                val currentWeekExpenses = expenseDao.getExpensesByDateRange(weekStart, weekEnd).first()
                val weeklyTotal = currentWeekExpenses.sumOf { it.amount }
                val avgLast4Weeks = 2000.0
                val savedAmount = avgLast4Weeks - weeklyTotal
                val isTriggered = savedAmount > 100 && savedAmount > avgLast4Weeks * 0.2

                if (isTriggered && weeklyTotal > 0) {
                    val wow = CurrentWow(
                        savedAmount = savedAmount,
                        reason = "本周消费低于历史平均",
                        equivalent = "一顿双人火锅",
                        badge = "精打细算",
                        progressHint = "连续4周达标解锁 → 攒钱达人"
                    )
                    _state.value = AchievementsState(
                        isLoading = false,
                        currentWow = wow,
                        recentAchievements = emptyList()
                    )
                } else {
                    _state.value = AchievementsState(isLoading = false, currentWow = null, recentAchievements = emptyList())
                }
            }
        }
    }

    private fun getCurrentWeekRange(): Pair<Long, Long> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val weekStart = calendar.timeInMillis

        calendar.add(Calendar.DAY_OF_WEEK, 6)
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        val weekEnd = calendar.timeInMillis

        return Pair(weekStart, weekEnd)
    }
}

data class AchievementsState(
    val isLoading: Boolean = true,
    val currentWow: CurrentWow? = null,
    val recentAchievements: List<Achievement> = emptyList()
)

data class CurrentWow(
    val savedAmount: Double,
    val reason: String,
    val equivalent: String?,
    val badge: String?,
    val progressHint: String?
)

data class Achievement(
    val emoji: String,
    val name: String,
    val description: String,
    val date: String
)
