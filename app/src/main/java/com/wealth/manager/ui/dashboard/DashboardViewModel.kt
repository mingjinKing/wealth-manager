package com.wealth.manager.ui.dashboard

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wealth.manager.agent.WangcaiAgent
import com.wealth.manager.data.dao.AssetDao
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

private const val PAGE_SIZE = 30 

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val expenseDao: ExpenseDao,
    private val categoryDao: CategoryDao,
    private val weekStatsDao: WeekStatsDao,
    private val assetDao: AssetDao,
    private val wangcaiAgent: WangcaiAgent,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val TAG = "DashboardVM"
    private val prefs = context.getSharedPreferences("dashboard_prefs", Context.MODE_PRIVATE)

    // 注意：DashboardState 已在独立文件中定义，此处不再声明，防止 Redeclaration 错误
    private val _state = MutableStateFlow(DashboardState(
        customBackgroundImageUri = prefs.getString("custom_bg_uri", null)
    ))
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    private var allExpensesPage = mutableListOf<ExpenseEntity>()
    private var lastLoadedDate: Long = Long.MAX_VALUE

    init {
        loadDashboardData()
    }

    fun generateRealtimeInsight() {
        if (_state.value.isAnalyzingInsight) return
        
        viewModelScope.launch {
            _state.value = _state.value.copy(isAnalyzingInsight = true, aiInsightText = null)
            
            try {
                val (monthStart, monthEnd) = getCurrentMonthRange()
                val monthExpenses = expenseDao.getExpensesByDateRange(monthStart, monthEnd).first()
                val categories = categoryDao.getAllCategories().first()
                val incomeCategoryIds = categories.filter { it.type == "INCOME" }.map { it.id }.toSet()
                
                val expenses = monthExpenses.filter { it.categoryId !in incomeCategoryIds }
                val total = expenses.sumOf { it.amount }
                val top3 = expenses.groupBy { it.categoryId }
                    .map { entry -> 
                        val cat = categories.find { it.id == entry.key }
                        (cat?.name ?: "其他") to entry.value.sumOf { it.amount }
                    }
                    .sortedByDescending { it.second }
                    .take(3)

                val dataSnapshot = """
                    本月支出: ¥$total
                    消费前三: ${top3.joinToString { "${it.first}(¥${it.second})" }}
                    账单总数: ${monthExpenses.size}
                """.trimIndent()

                val prompt = "请根据以下本月财务数据快照，给出一条简短、专业且有温度的理财建议（50字以内称述句，直接给建议，不要寒暄）：\n$dataSnapshot"
                
                var result = ""
                wangcaiAgent.thinkStream(userMessage = prompt).collect { delta ->
                    if (!delta.startsWith("[PROGRESS")) {
                        result += delta
                        _state.value = _state.value.copy(aiInsightText = result)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Insight generation failed", e)
                _state.value = _state.value.copy(aiInsightText = "旺财思考时出了点小状况，稍后再试试吧。")
            } finally {
                _state.value = _state.value.copy(isAnalyzingInsight = false)
            }
        }
    }

    fun deleteExpense(id: Long) {
        viewModelScope.launch {
            val expense = expenseDao.getExpenseById(id)
            expense?.let {
                if (it.assetId != null && it.amount > 0) {
                    val asset = assetDao.getAssetById(it.assetId!!)
                    asset?.let { a ->
                        assetDao.updateAsset(a.copy(balance = a.balance + it.amount))
                    }
                }
            }
            expenseDao.deleteExpenseById(id)
            loadDashboardData(showLoading = false)
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
            val recent7DaysStart = maxOf(monthStart, sevenDaysStart)

            val categoriesDeferred = async { categoryDao.getAllCategories().first() }
            val weekStatsDeferred = async { weekStatsDao.getRecentWeekStats(4).first() }
            val monthExpensesDeferred = async { expenseDao.getExpensesByDateRange(monthStart, monthEnd).first() }

            val firstPage = expenseDao.getExpensesPaginated(Long.MAX_VALUE, PAGE_SIZE)
            allExpensesPage = firstPage.toMutableList()
            lastLoadedDate = if (firstPage.isNotEmpty()) firstPage.last().date else Long.MAX_VALUE

            val categories = categoriesDeferred.await()
            val recentStats = weekStatsDeferred.await()
            val monthExpenses = monthExpensesDeferred.await()

            val incomeCategoryIds = categories.filter { it.type == "INCOME" }.map { it.id }.toSet()
            val monthIncome = monthExpenses.filter { it.categoryId in incomeCategoryIds }.sumOf { it.amount }
            val monthTotalExpense = monthExpenses.filter { it.categoryId !in incomeCategoryIds }.sumOf { it.amount }
            
            val recent7DaysTotalExpense = monthExpenses.filter { 
                it.categoryId !in incomeCategoryIds && it.date >= recent7DaysStart 
            }.sumOf { it.amount }

            val dailyExpenses = groupExpensesByDay(allExpensesPage, categories)

            val avgLast4Weeks = recentStats.take(4).map { it.totalAmount }.average()
                .takeIf { !it.isNaN() } ?: (monthTotalExpense / 4.0).coerceAtLeast(recent7DaysTotalExpense)
            val savedAmount = avgLast4Weeks - recent7DaysTotalExpense

            val isTriggered = savedAmount > 100 && savedAmount > avgLast4Weeks * 0.2
            val wowPreview = if (isTriggered) {
                WowPreview(
                    savedAmount = savedAmount,
                    lastWeekAmount = avgLast4Weeks,
                    isTriggered = true,
                    reason = "你的省钱意志力太强了！"
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
            val categories = _state.value.categories.ifEmpty { categoryDao.getAllCategories().first() }
            val nextPage = expenseDao.getExpensesPaginated(lastLoadedDate, PAGE_SIZE)
            
            if (nextPage.isEmpty()) {
                _state.value = _state.value.copy(isLoadingMore = false, hasMorePages = false)
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
        return Pair(monthStart, cal.timeInMillis)
    }

    private fun getLast7DaysRange(): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.add(Calendar.DAY_OF_YEAR, -6)
        val start = cal.timeInMillis
        return Pair(start, System.currentTimeMillis())
    }
}
