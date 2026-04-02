package com.wealth.manager.ui.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wealth.manager.data.dao.CategoryDao
import com.wealth.manager.data.dao.ExpenseDao
import com.wealth.manager.data.dao.WeekStatsDao
import com.wealth.manager.data.entity.ExpenseEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    private val weekStatsDao: WeekStatsDao,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val prefs = context.getSharedPreferences("dashboard_prefs", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(DashboardState(
        customBackgroundImageUri = prefs.getString("custom_bg_uri", null)
    ))
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    private var allExpensesPage = mutableListOf<ExpenseEntity>()
    private var lastLoadedDate: Long = Long.MAX_VALUE

    init {
        loadDashboardData()
    }

    fun deleteExpense(id: Long) {
        viewModelScope.launch {
            expenseDao.deleteExpenseById(id)
            loadDashboardData(showLoading = false) // Refresh after delete
        }
    }

    fun updateCustomBackground(uri: String?) {
        prefs.edit().putString("custom_bg_uri", uri).apply()
        _state.value = _state.value.copy(customBackgroundImageUri = uri)
    }

    fun loadDashboardData(showLoading: Boolean = true) {
        viewModelScope.launch {
            if (showLoading) {
                _state.value = _state.value.copy(isLoading = true)
            }
            
            val (monthStart, monthEnd) = getCurrentMonthRange()
            val (sevenDaysStart, sevenDaysEnd) = getLast7DaysRange()
            
            // 限制在当月内：取当月开始时间和 7 天前开始时间的较晚者
            val recent7DaysStart = maxOf(monthStart, sevenDaysStart)

            // 并行加载所有数据（Flow → List）
            val categoriesDeferred = async { categoryDao.getAllCategories().first() }
            val weekStatsDeferred = async { weekStatsDao.getRecentWeekStats(4).first() }
            val monthExpensesDeferred = async { expenseDao.getExpensesByDateRange(monthStart, monthEnd).first() }
            val recent7DaysDeferred = async { expenseDao.getExpensesByDateRange(recent7DaysStart, sevenDaysEnd).first() }

            val firstPage = expenseDao.getExpensesPaginated(Long.MAX_VALUE, PAGE_SIZE)
            allExpensesPage = firstPage.toMutableList()
            lastLoadedDate = if (firstPage.isNotEmpty()) firstPage.last().date else Long.MAX_VALUE

            val categories = categoriesDeferred.await()
            val recentStats = weekStatsDeferred.await()
            val monthExpenses = monthExpensesDeferred.await()
            val recent7DaysExpenses = recent7DaysDeferred.await()

            // 区分收入和支出：使用 CategoryEntity 的 type 字段
            val incomeCategoryIds = categories.filter { it.type == "INCOME" }.map { it.id }.toSet()
            
            val monthIncome = monthExpenses.filter { it.categoryId in incomeCategoryIds }.sumOf { it.amount }
            val monthTotalExpense = monthExpenses.filter { it.categoryId !in incomeCategoryIds }.sumOf { it.amount }
            
            val recent7DaysTotalExpense = recent7DaysExpenses.filter { it.categoryId !in incomeCategoryIds }.sumOf { it.amount }

            val dailyExpenses = groupExpensesByDay(allExpensesPage, categories)

            // 激励层逻辑
            val avgLast4Weeks = recentStats.take(4).map { it.totalAmount }.average()
                .takeIf { !it.isNaN() } ?: recent7DaysTotalExpense
            val savedAmount = avgLast4Weeks - recent7DaysTotalExpense
            
            // 降低触发门槛以便展示激励层，或根据用户需求“启用”
            val isTriggered = savedAmount > 0 
            val wowPreview = if (isTriggered) {
                WowPreview(
                    savedAmount = savedAmount,
                    isTriggered = true,
                    reason = if (savedAmount > 100) "本周消费控制极佳！" else "继续保持良好的消费习惯"
                )
            } else null

            _state.value = _state.value.copy(
                isLoading = false,
                isLoadingMore = false,
                hasMorePages = firstPage.size >= PAGE_SIZE,
                monthTotal = monthTotalExpense,
                monthIncome = monthIncome,
                recent7DaysTotal = recent7DaysTotalExpense,
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
                categoryDao.getAllCategories().first()
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
                
                // 计算该日的支出总计（不含收入）
                val dayTotalExpense = expenseItems
                    .filter { it.category.type == "EXPENSE" }
                    .sumOf { it.expense.amount }

                DailyExpense(
                    dateLabel = dateLabel,
                    dateMillis = dayStart,
                    dayTotal = dayTotalExpense,
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
        
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val monthEnd = cal.timeInMillis
        
        return Pair(monthStart, monthEnd)
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
