package com.wealth.manager.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wealth.manager.data.dao.CategoryDao
import com.wealth.manager.data.dao.ExpenseDao
import com.wealth.manager.data.dao.WeekStatsDao
import com.wealth.manager.data.entity.ExpenseEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

private const val PAGE_SIZE = 30  // 每次加载约5-6天记录

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val expenseDao: ExpenseDao,
    private val categoryDao: CategoryDao,
    private val weekStatsDao: WeekStatsDao
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    private var allExpensesPage = mutableListOf<ExpenseEntity>()
    private var lastLoadedDate: Long = Long.MAX_VALUE

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
            allExpensesPage.clear()
            lastLoadedDate = Long.MAX_VALUE

            // 并行加载：本月汇总 + 首页分页数据
            val monthStart = getCurrentMonthRange().first
            val (recent7DaysStart, recent7DaysEnd) = getLast7DaysRange()

            val categoriesDeferred = async { categoryDao.getAllCategories().first }
            val weekStatsDeferred = async { weekStatsDao.getRecentWeekStats(4) }
            val monthExpensesDeferred = async { expenseDao.getExpensesByDateRange(monthStart, System.currentTimeMillis()).first }
            val recent7DaysDeferred = async { expenseDao.getExpensesByDateRange(recent7DaysStart, recent7DaysEnd).first }

            // 加载首页所有记录（按日期倒序）
            val firstPage = expenseDao.getExpensesPaginated(Long.MAX_VALUE, PAGE_SIZE)
            allExpensesPage = firstPage.toMutableList()
            if (firstPage.isNotEmpty()) {
                lastLoadedDate = firstPage.last().date
            }

            val categories = categoriesDeferred.await()
            val recentStats = weekStatsDeferred.await()
            val monthExpenses = monthExpensesDeferred.await()
            val recent7DaysExpenses = recent7DaysDeferred.await()

            val dailyExpenses = groupExpensesByDay(allExpensesPage, categories)
            val monthTotal = monthExpenses.sumOf { it.amount }
            val recent7DaysTotal = recent7DaysExpenses.sumOf { it.amount }

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

            _state.value = DashboardState(
                isLoading = false,
                isLoadingMore = false,
                hasMorePages = firstPage.size >= PAGE_SIZE,
                monthTotal = monthTotal,
                recent7DaysTotal = recent7DaysTotal,
                dailyExpenses = dailyExpenses,
                wowPreview = wowPreview,
                categories = categories
            )
        }
    }

    fun loadMoreExpenses() {
        val currentState = _state.value
        if (currentState.isLoadingMore || !currentState.hasMorePages) return

        viewModelScope.launch {
            _state.value = currentState.copy(isLoadingMore = true)

            val categories = _state.value.categories.ifEmpty {
                categoryDao.getAllCategories().first
            }

            val nextPage = expenseDao.getExpensesPaginated(lastLoadedDate, PAGE_SIZE)
            if (nextPage.isEmpty()) {
                _state.value = _state.value.copy(
                    isLoadingMore = false,
                    hasMorePages = false
                )
                return@launch
            }

            allExpensesPage.addAll(nextPage)
            lastLoadedDate = nextPage.last().date

            val dailyExpenses = groupExpensesByDay(allExpensesPage, categories)

            _state.value = _state.value.copy(
                isLoadingMore = false,
                hasMorePages = nextPage.size >= PAGE_SIZE,
                dailyExpenses = dailyExpenses
            )
        }
    }

    private fun groupExpensesByDay(
        expenses: List<ExpenseEntity>,
        categories: List<com.wealth.manager.data.entity.CategoryEntity>
    ): List<DailyExpense> {
        val categoryMap = categories.associateBy { it.id }
        val today = getTodayStartMillis()
        val yesterday = today - 24 * 60 * 60 * 1000

        return expenses
            .groupBy { expense ->
                val cal = Calendar.getInstance()
                cal.timeInMillis = expense.date
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
            .map { (dayStart, dayExpenses) ->
                val dateLabel = when (dayStart) {
                    today -> "今天"
                    yesterday -> "昨天"
                    else -> {
                        val cal = Calendar.getInstance()
                        cal.timeInMillis = dayStart
                        SimpleDateFormat("M月d日", Locale.CHINA).format(cal.time)
                    }
                }
                val expenseItems = dayExpenses.mapNotNull { expense ->
                    val cat = categoryMap[expense.categoryId]
                    if (cat != null) ExpenseItem(expense = expense, category = cat) else null
                }
                DailyExpense(
                    dateLabel = dateLabel,
                    dateMillis = dayStart,
                    dayTotal = dayExpenses.sumOf { it.amount },
                    expenses = expenseItems
                )
            }
            .sortedByDescending { it.dateMillis }
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
        return Pair(monthStart, System.currentTimeMillis())
    }

    private fun getLast7DaysRange(): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.add(Calendar.DAY_OF_YEAR, -6)
        val sevenDaysStart = cal.timeInMillis

        cal.add(Calendar.DAY_OF_YEAR, 6)
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        val sevenDaysEnd = cal.timeInMillis

        return Pair(sevenDaysStart, sevenDaysEnd)
    }
}
