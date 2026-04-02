package com.wealth.manager.ui.achievements

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wealth.manager.data.dao.AssetDao
import com.wealth.manager.data.dao.BudgetDao
import com.wealth.manager.data.dao.CategoryDao
import com.wealth.manager.data.dao.ExpenseDao
import com.wealth.manager.data.entity.BudgetEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class AchievementsViewModel @Inject constructor(
    private val expenseDao: ExpenseDao,
    private val assetDao: AssetDao,
    private val budgetDao: BudgetDao,
    private val categoryDao: CategoryDao,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val prefs = context.getSharedPreferences("achievements_prefs", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(loadInitialState())
    val state: StateFlow<AchievementsState> = _state.asStateFlow()

    init {
        loadData()
    }

    private fun loadInitialState(): AchievementsState {
        return AchievementsState(
            assetGoal = prefs.getFloat("asset_goal", 100000f).toDouble(),
            goalStartDate = prefs.getLong("goal_start_date", System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000),
            goalDate = prefs.getLong("goal_date", System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000),
            isAssetVisible = prefs.getBoolean("is_asset_visible", true),
            isGoalVisible = prefs.getBoolean("is_goal_visible", true),
            isBudgetVisible = prefs.getBoolean("is_budget_visible", true),
            isTrendVisible = prefs.getBoolean("is_trend_visible", true)
        )
    }

    private fun loadData() {
        val calendar = Calendar.getInstance()
        val currentMonthStr = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(calendar.time)
        
        // 获取本月起止时间
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
        calendar.set(Calendar.MILLISECOND, 999)
        val monthEnd = calendar.timeInMillis

        // 获取本周起止时间
        val weeklyCalendar = Calendar.getInstance()
        weeklyCalendar.set(Calendar.DAY_OF_WEEK, weeklyCalendar.firstDayOfWeek)
        weeklyCalendar.set(Calendar.HOUR_OF_DAY, 0)
        weeklyCalendar.set(Calendar.MINUTE, 0)
        weeklyCalendar.set(Calendar.SECOND, 0)
        weeklyCalendar.set(Calendar.MILLISECOND, 0)
        val weekStart = weeklyCalendar.timeInMillis
        weeklyCalendar.add(Calendar.DAY_OF_WEEK, 6)
        weeklyCalendar.set(Calendar.HOUR_OF_DAY, 23)
        weeklyCalendar.set(Calendar.MINUTE, 59)
        weeklyCalendar.set(Calendar.SECOND, 59)
        weeklyCalendar.set(Calendar.MILLISECOND, 999)
        val weekEnd = weeklyCalendar.timeInMillis

        viewModelScope.launch {
            combine(
                expenseDao.getExpensesByDateRange(monthStart, monthEnd),
                expenseDao.getExpensesByDateRange(weekStart, weekEnd),
                categoryDao.getAllCategories(),
                assetDao.getTotalAssets(),
                assetDao.getTotalLiabilities(),
                budgetDao.getGlobalBudget(currentMonthStr),
                budgetDao.getGlobalBudget("WEEKLY")
            ) { args: Array<Any?> ->
                val monthExpenses = args[0] as List<com.wealth.manager.data.entity.ExpenseEntity>? ?: emptyList()
                val weekExpenses = args[1] as List<com.wealth.manager.data.entity.ExpenseEntity>? ?: emptyList()
                val allCategories = args[2] as List<com.wealth.manager.data.entity.CategoryEntity>? ?: emptyList()
                val totalAssets = args[3] as Double? ?: 0.0
                val totalLiabilities = args[4] as Double? ?: 0.0
                val monthlyBudget = args[5] as BudgetEntity?
                val weeklyBudget = args[6] as BudgetEntity?

                val incomeCategoryIds = allCategories.filter { it.type == "INCOME" }.map { it.id }.toSet()
                
                // 仅计算支出类型账单的总额
                val monthlySpent = monthExpenses.filter { it.categoryId !in incomeCategoryIds }.sumOf { it.amount }
                val weeklySpent = weekExpenses.filter { it.categoryId !in incomeCategoryIds }.sumOf { it.amount }
                
                val netWorth = totalAssets + totalLiabilities
                val activeType = if ((monthlyBudget?.amount ?: 0.0) <= 0.0 && (weeklyBudget?.amount ?: 0.0) > 0.0) "WEEKLY" else "MONTHLY"

                val trendPoints = calculateTrendPoints(netWorth, _state.value.assetGoal, _state.value.goalStartDate, _state.value.goalDate)

                _state.value.copy(
                    isLoading = false,
                    netWorth = netWorth,
                    monthlyBudget = monthlyBudget?.amount ?: 0.0,
                    monthlySpent = monthlySpent,
                    weeklyBudget = weeklyBudget?.amount ?: 0.0,
                    weeklySpent = weeklySpent,
                    budgetType = activeType,
                    trendPoints = trendPoints
                )
            }.collect { newState ->
                _state.value = newState
            }
        }
    }

    private fun calculateTrendPoints(currentNetWorth: Double, goalAmount: Double, startDate: Long, endDate: Long): List<TrendPoint> {
        val totalDuration = endDate - startDate
        if (totalDuration <= 0) return emptyList()

        val points = mutableListOf<TrendPoint>()
        val step = totalDuration / 5
        
        for (i in 0..5) {
            val time = startDate + i * step
            val expected = (goalAmount / 5) * i
            
            val actual = if (time <= System.currentTimeMillis()) {
                val progress = (System.currentTimeMillis() - startDate).toDouble() / totalDuration
                val currentProgressInPath = i.toDouble() / 5
                if (currentProgressInPath <= progress) {
                    (currentNetWorth / progress.coerceAtLeast(0.01)) * currentProgressInPath
                } else null
            } else null
            
            points.add(TrendPoint(label = SimpleDateFormat("MM/dd", Locale.getDefault()).format(Date(time)), expected = expected, actual = actual))
        }
        return points
    }

    fun toggleAssetVisibility() {
        val newVal = !_state.value.isAssetVisible
        prefs.edit().putBoolean("is_asset_visible", newVal).apply()
        _state.value = _state.value.copy(isAssetVisible = newVal)
    }

    fun toggleGoalVisibility() {
        val newVal = !_state.value.isGoalVisible
        prefs.edit().putBoolean("is_goal_visible", newVal).apply()
        _state.value = _state.value.copy(isGoalVisible = newVal)
    }

    fun toggleBudgetVisibility() {
        val newVal = !_state.value.isBudgetVisible
        prefs.edit().putBoolean("is_budget_visible", newVal).apply()
        _state.value = _state.value.copy(isBudgetVisible = newVal)
    }

    fun toggleTrendVisibility() {
        val newVal = !_state.value.isTrendVisible
        prefs.edit().putBoolean("is_trend_visible", newVal).apply()
        _state.value = _state.value.copy(isTrendVisible = newVal)
    }

    fun updateBudget(type: String, amount: Double) {
        val key = if (type == "MONTHLY") {
            SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
        } else {
            "WEEKLY"
        }
        
        viewModelScope.launch {
            val otherKey = if (type == "MONTHLY") "WEEKLY" else SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
            
            val current = budgetDao.getGlobalBudget(key).first()
            if (current != null) {
                budgetDao.updateBudget(current.copy(amount = amount))
            } else {
                budgetDao.insertBudget(BudgetEntity(amount = amount, month = key))
            }
            
            val other = budgetDao.getGlobalBudget(otherKey).first()
            if (other != null) {
                budgetDao.updateBudget(other.copy(amount = 0.0))
            }
        }
    }
    
    fun updateAssetGoal(goal: Double, date: Long) {
        val startTime = System.currentTimeMillis()
        prefs.edit()
            .putFloat("asset_goal", goal.toFloat())
            .putLong("goal_date", date)
            .putLong("goal_start_date", startTime)
            .apply()

        _state.value = _state.value.copy(
            assetGoal = goal, 
            goalDate = date,
            goalStartDate = startTime
        )
        loadData()
    }
}

data class TrendPoint(
    val label: String,
    val expected: Double,
    val actual: Double?
)

data class AchievementsState(
    val isLoading: Boolean = true,
    val netWorth: Double = 0.0,
    val monthlyBudget: Double = 0.0,
    val monthlySpent: Double = 0.0,
    val weeklyBudget: Double = 0.0,
    val weeklySpent: Double = 0.0,
    val budgetType: String = "MONTHLY",
    
    val isAssetVisible: Boolean = true,
    val isGoalVisible: Boolean = true,
    val isBudgetVisible: Boolean = true,
    val isTrendVisible: Boolean = true,

    val assetGoal: Double = 100000.0,
    val goalStartDate: Long = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000,
    val goalDate: Long = System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000,
    
    val trendPoints: List<TrendPoint> = emptyList()
)
