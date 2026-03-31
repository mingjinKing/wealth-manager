package com.wealth.manager.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wealth.manager.data.dao.CategoryDao
import com.wealth.manager.data.dao.ExpenseDao
import com.wealth.manager.data.dao.WeekStatsDao
import com.wealth.manager.data.entity.ExpenseEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val expenseDao: ExpenseDao,
    private val categoryDao: CategoryDao,
    private val weekStatsDao: WeekStatsDao
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    init {
        loadDashboardData()
    }

    fun deleteExpense(id: Long) {
        viewModelScope.launch {
            expenseDao.deleteExpenseById(id)
        }
    }

    fun loadDashboardData() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)

            val (monthStart, monthEnd) = getCurrentMonthRange()
            val (sevenDaysStart, sevenDaysEnd) = getLast7DaysRange()

            combine(
                expenseDao.getExpensesByDateRange(monthStart, monthEnd),
                expenseDao.getExpensesByDateRange(sevenDaysStart, sevenDaysEnd),
                categoryDao.getAllCategories(),
                weekStatsDao.getRecentWeekStats(4)
            ) { monthExpenses, recent7DaysExpenses, categories, recentStats ->
                calculateDashboardState(
                    monthExpenses,
                    recent7DaysExpenses,
                    categories,
                    recentStats
                )
            }.collect { newState ->
                _state.value = newState
            }
        }
    }

    private fun calculateDashboardState(
        monthExpenses: List<ExpenseEntity>,
        recent7DaysExpenses: List<ExpenseEntity>,
        categories: List<com.wealth.manager.data.entity.CategoryEntity>,
        recentStats: List<com.wealth.manager.data.entity.WeekStatsEntity>
    ): DashboardState {
        val monthTotal = monthExpenses.sumOf { it.amount }
        val recent7DaysTotal = recent7DaysExpenses.sumOf { it.amount }

        // Group expenses by day (only show days with expenses)
        val dailyExpenses = groupExpensesByDay(monthExpenses, categories)

        // Wow calculation based on 4-week average
        val avgLast4Weeks = recentStats.take(4).map { it.totalAmount }.average()
            .takeIf { !it.isNaN() } ?: recent7DaysTotal
        val savedAmount = avgLast4Weeks - recent7DaysTotal
        val isTriggered = savedAmount > 100 && savedAmount > avgLast4Weeks * 0.2
        val wowPreview = if (isTriggered) {
            WowPreview(
                savedAmount = savedAmount,
                isTriggered = true,
                reason = "本周消费控制良好"
            )
        } else null

        return DashboardState(
            isLoading = false,
            monthTotal = monthTotal,
            recent7DaysTotal = recent7DaysTotal,
            dailyExpenses = dailyExpenses,
            wowPreview = wowPreview,
            categories = categories
        )
    }

    private fun groupMonthDaysByDay(
        expenses: List<ExpenseEntity>,
        categories: List<com.wealth.manager.data.entity.CategoryEntity>,
        monthStart: Long,
        monthEnd: Long
    ): List<DailyExpense> {
        val categoryMap = categories.associateBy { it.id }
        val today = getTodayStartMillis()
        val yesterday = today - 24 * 60 * 60 * 1000

        // Group expenses by day start
        val expensesByDay = expenses.groupBy { expense ->
            val cal = Calendar.getInstance()
            cal.timeInMillis = expense.date
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.timeInMillis
        }

        // Generate all days of the month
        val result = mutableListOf<DailyExpense>()
        val cal = Calendar.getInstance()
        cal.timeInMillis = monthStart
        while (cal.timeInMillis <= monthEnd) {
            val dayStart = cal.apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val dayExpenses = expensesByDay[dayStart] ?: emptyList()
            val dateLabel = when (dayStart) {
                today -> "今天"
                yesterday -> "昨天"
                else -> SimpleDateFormat("M月d日", Locale.CHINA).format(cal.time)
            }
            val expenseItems = dayExpenses.mapNotNull { expense ->
                val cat = categoryMap[expense.categoryId]
                if (cat != null) ExpenseItem(expense = expense, category = cat) else null
            }
            result.add(
                DailyExpense(
                    dateLabel = dateLabel,
                    dateMillis = dayStart,
                    dayTotal = dayExpenses.sumOf { it.amount },
                    expenses = expenseItems
                )
            )
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }

        return result.sortedByDescending { it.dateMillis }
    }

    private fun getTodayStartMillis(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun getCurrentMonthRange(): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val monthStart = cal.timeInMillis

        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        val monthEnd = cal.timeInMillis

        return Pair(monthStart, monthEnd)
    }

    private fun getLast7DaysRange(): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.add(Calendar.DAY_OF_YEAR, -6)  // Last 7 days including today
        val sevenDaysStart = cal.timeInMillis

        cal.add(Calendar.DAY_OF_YEAR, 6)
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        val sevenDaysEnd = cal.timeInMillis

        return Pair(sevenDaysStart, sevenDaysEnd)
    }
}
