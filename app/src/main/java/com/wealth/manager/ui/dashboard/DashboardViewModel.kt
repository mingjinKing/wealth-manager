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
import java.util.Calendar
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

            val currentWeekFlow = expenseDao.getExpensesByDateRange(weekStart, weekEnd)
            val lastWeekFlow = expenseDao.getExpensesByDateRange(lastWeekStart, lastWeekEnd)
            val categoriesFlow = categoryDao.getAllCategories()
            val recentStatsFlow = weekStatsDao.getRecentWeekStats(4)

            combine(
                combine(currentWeekFlow, lastWeekFlow, categoriesFlow) { cw, lw, cat ->
                    Triple(cw, lw, cat)
                },
                recentStatsFlow
            ) { triple, recentStats ->
                calculateDashboardState(
                    triple.first,
                    triple.second,
                    triple.third,
                    recentStats
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
        recentStats: List<WeekStatsEntity>
    ): DashboardState {
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

        val aiSuggestions = generateAiSuggestions(categorySpending)

        return DashboardState(
            isLoading = false,
            weeklyTotal = weeklyTotal,
            weeklyChange = weeklyChange,
            categoryBreakdown = categorySpending,
            aiSuggestions = aiSuggestions,
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

    private fun generateAiSuggestions(categorySpending: List<CategorySpending>): List<String> {
        if (categorySpending.isEmpty()) return emptyList()

        val suggestions = mutableListOf<String>()
        val topCategory = categorySpending.firstOrNull()

        if (topCategory != null && topCategory.isOverBaseline) {
            suggestions.add("${topCategory.category.name}消费偏高，建议适当控制")
        }

        if (suggestions.size < 2) {
            val overCategories = categorySpending.filter { it.isOverBaseline }
            if (overCategories.size >= 2) {
                suggestions.add("多个类别超出预算，注意整体支出")
            }
        }

        return suggestions.take(3)
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
}
