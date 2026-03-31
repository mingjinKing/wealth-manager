package com.wealth.manager.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wealth.manager.data.dao.CategoryDao
import com.wealth.manager.data.dao.ExpenseDao
import com.wealth.manager.data.dao.WeekStatsDao
import com.wealth.manager.data.entity.CategoryEntity
import com.wealth.manager.data.entity.ExpenseEntity
import com.wealth.manager.data.entity.WeekStatsEntity
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

    fun loadDashboardData() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)

            val (weekStart, weekEnd) = getCurrentWeekRange()
            val (lastWeekStart, lastWeekEnd) = getLastWeekRange()
            val (monthStart, monthEnd) = getCurrentMonthRange()

            val currentWeekFlow = expenseDao.getExpensesByDateRange(weekStart, weekEnd)
            val lastWeekFlow = expenseDao.getExpensesByDateRange(lastWeekStart, lastWeekEnd)
            val currentMonthFlow = expenseDao.getExpensesByDateRange(monthStart, monthEnd)
            val categoriesFlow = categoryDao.getAllCategories()
            val recentStatsFlow = weekStatsDao.getRecentWeekStats(4)

            combine(
                combine(
                    combine(currentWeekFlow, lastWeekFlow, categoriesFlow) { cw, lw, cat ->
                        Triple(cw, lw, cat)
                    },
                    currentMonthFlow
                ) { triple, monthExpenses -> Pair(triple, monthExpenses) },
                recentStatsFlow
            ) { pair, recentStats ->
                val triple = pair.first
                val monthExpenses = pair.second
                calculateDashboardState(
                    triple.first,  // currentWeekExpenses
                    triple.second, // lastWeekExpenses
                    triple.third,  // categories
                    recentStats,
                    weekStart,
                    weekEnd,
                    monthExpenses
                )
            }.collect { newState ->
                _state.value = newState
            }
        }
    }

    private fun calculateDashboardState(
        currentWeekExpenses: List<ExpenseEntity>,
        lastWeekExpenses: List<ExpenseEntity>,
        categories: List<CategoryEntity>,
        recentStats: List<WeekStatsEntity>,
        weekStart: Long,
        weekEnd: Long,
        monthExpenses: List<ExpenseEntity>
    ): DashboardState {
        val dateFormat = SimpleDateFormat("M.d", Locale.CHINA)
        val weekStartDate = dateFormat.format(weekStart)
        val weekEndDate = dateFormat.format(weekEnd)
        val monthlyTotal = monthExpenses.sumOf { it.amount }
        val weeklyTotal = currentWeekExpenses.sumOf { it.amount }
        val lastWeekTotal = lastWeekExpenses.sumOf { it.amount }
        val weeklyChange = if (lastWeekTotal > 0) {
            ((weeklyTotal - lastWeekTotal) / lastWeekTotal * 100).toFloat()
        } else 0f

        val categoryMap = categories.associateBy { it.id }
        val categorySpending = currentWeekExpenses
            .groupBy { it.categoryId }
            .mapNotNull { (categoryId, expenses) ->
                val category = categoryMap[categoryId] ?: return@mapNotNull null
                val amount = expenses.sumOf { it.amount }
                val percentage = if (weeklyTotal > 0) (amount / weeklyTotal * 100).toFloat() else 0f
                val baseline = recentStats.lastOrNull()?.let {
                    parseBaseline(it.categoryBreakdown, categoryId)
                } ?: 0.0
                CategorySpending(
                    category = category,
                    amount = amount,
                    percentage = percentage,
                    isOverBaseline = amount > baseline
                )
            }
            .sortedByDescending { it.amount }

        val avgLast4Weeks = recentStats.take(4).map { it.totalAmount }.average().takeIf { !it.isNaN() } ?: weeklyTotal
        val savedAmount = avgLast4Weeks - weeklyTotal
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
            weekStartDate = weekStartDate,
            weekEndDate = weekEndDate,
            weeklyTotal = weeklyTotal,
            weeklyChange = weeklyChange,
            monthlyTotal = monthlyTotal,
            categoryBreakdown = categorySpending,
            wowPreview = wowPreview,
            recentExpenses = currentWeekExpenses.take(5),
            categories = categories
        )
    }

    private fun parseBaseline(categoryBreakdown: String, categoryId: Long): Double {
        return try {
            categoryBreakdown.split(";")
                .map { it.split(":") }
                .find { it.getOrNull(0)?.toLongOrNull() == categoryId }
                ?.getOrNull(1)
                ?.toDoubleOrNull() ?: 0.0
        } catch (e: Exception) {
            0.0
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

    private fun getLastWeekRange(): Pair<Long, Long> {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.WEEK_OF_YEAR, -1)
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

    private fun getCurrentMonthRange(): Pair<Long, Long> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val monthStart = calendar.timeInMillis

        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        val monthEnd = calendar.timeInMillis

        return Pair(monthStart, monthEnd)
    }
}
