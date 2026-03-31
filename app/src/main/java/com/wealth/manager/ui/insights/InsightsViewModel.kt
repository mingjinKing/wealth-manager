package com.wealth.manager.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wealth.manager.data.dao.CategoryDao
import com.wealth.manager.data.dao.ExpenseDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class InsightsViewModel @Inject constructor(
    private val expenseDao: ExpenseDao,
    private val categoryDao: CategoryDao
) : ViewModel() {

    private val _state = MutableStateFlow(InsightsState())
    val state: StateFlow<InsightsState> = _state.asStateFlow()

    init {
        loadInsights()
    }

    private fun loadInsights() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)

            val (weekStart, weekEnd) = getCurrentWeekRange()
            val expenses = expenseDao.getExpensesByDateRange(weekStart, weekEnd).first()
            val categories = categoryDao.getAllCategories().first()

            if (expenses.isEmpty()) {
                _state.value = InsightsState(isLoading = false, insights = emptyList())
                return@launch
            }

            val categoryMap = categories.associateBy { it.id }
            val insights = expenses
                .groupBy { it.categoryId }
                .mapNotNull { (categoryId, categoryExpenses) ->
                    val category = categoryMap[categoryId] ?: return@mapNotNull null
                    val total = categoryExpenses.sumOf { it.amount }
                    val count = categoryExpenses.size
                    val avgPerTime = total / count

                    Insight(
                        categoryEmoji = category.icon,
                        categoryName = category.name,
                        message = "本周${category.name}消费 ${count} 次，平均每次 ¥${String.format("%.0f", avgPerTime)}",
                        savings = "优化建议：适当控制频率可节省约 ¥${String.format("%.0f", avgPerTime * 0.2 * count)}"
                    )
                }

            _state.value = InsightsState(isLoading = false, insights = insights)
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

data class InsightsState(
    val isLoading: Boolean = true,
    val insights: List<Insight> = emptyList()
)
