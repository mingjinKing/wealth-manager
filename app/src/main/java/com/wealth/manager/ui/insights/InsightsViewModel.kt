package com.wealth.manager.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wealth.manager.data.dao.CategoryDao
import com.wealth.manager.data.dao.ExpenseDao
import com.wealth.manager.data.entity.ExpenseEntity
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
        val (start, end) = getCurrentMonthRange()
        loadInsights(start, end, isDefaultMonth = true)
    }

    fun loadInsights(startTime: Long, endTime: Long, isDefaultMonth: Boolean = false) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isLoading = true,
                startTime = startTime,
                endTime = endTime,
                isDefaultMonth = isDefaultMonth
            )

            val expenses = expenseDao.getExpensesByDateRange(startTime, endTime).first()
            val categories = categoryDao.getAllCategories().first()

            val categoryMap = categories.associateBy { it.id }
            
            // 区分收入和支出
            val expenseRecords = expenses.filter { categoryMap[it.categoryId]?.type == "EXPENSE" }
            val incomeRecords = expenses.filter { categoryMap[it.categoryId]?.type == "INCOME" }

            val totalExpense = expenseRecords.sumOf { it.amount }
            val totalIncome = incomeRecords.sumOf { it.amount }
            
            // 仅对支出进行分类统计
            val summaryItems = expenseRecords
                .groupBy { it.categoryId }
                .mapNotNull { (categoryId, categoryExpenses) ->
                    val category = categoryMap[categoryId] ?: return@mapNotNull null
                    val total = categoryExpenses.sumOf { it.amount }
                    val percentage = if (totalExpense > 0) (total / totalExpense).toFloat() else 0f
                    
                    CategorySummary(
                        categoryEmoji = category.icon,
                        categoryName = category.name,
                        amount = total,
                        percentage = percentage,
                        items = categoryExpenses.sortedByDescending { it.date }
                    )
                }
                .sortedByDescending { it.amount }

            val globalAnalysis = if (expenseRecords.isNotEmpty()) generateGlobalAnalysis(summaryItems, totalExpense) else emptyList()

            _state.value = _state.value.copy(
                isLoading = false,
                summaryItems = summaryItems,
                totalAmount = totalExpense,
                totalIncome = totalIncome,
                globalAnalysis = globalAnalysis
            )
        }
    }

    private fun generateGlobalAnalysis(summaries: List<CategorySummary>, total: Double): List<String> {
        val analysis = mutableListOf<String>()
        
        // 1. 总体规模分析
        if (total > 5000) {
            analysis.add("该期间总支出 ¥${String.format("%.0f", total)}，规模较大，建议审查高额单项。")
        } else {
            analysis.add("该期间消费控制在 ¥${String.format("%.0f", total)}，整体预算管理良好。")
        }

        // 2. 结构分析
        summaries.firstOrNull()?.let { top ->
            if (top.percentage > 0.4) {
                analysis.add("支出结构偏向明显，${top.categoryName}占比达${(top.percentage * 100).toInt()}%，存在优化空间。")
            }
        }

        // 3. 频率分析
        val totalCount = summaries.sumOf { it.items.size }
        if (totalCount > 50) {
            analysis.add("该期间消费频率较高（共${totalCount}笔），建议减少小额琐碎支出。")
        }

        return analysis
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

data class InsightsState(
    val isLoading: Boolean = true,
    val totalAmount: Double = 0.0,
    val totalIncome: Double = 0.0,
    val summaryItems: List<CategorySummary> = emptyList(),
    val globalAnalysis: List<String> = emptyList(),
    val startTime: Long = 0L,
    val endTime: Long = 0L,
    val isDefaultMonth: Boolean = true
)

data class CategorySummary(
    val categoryEmoji: String,
    val categoryName: String,
    val amount: Double,
    val percentage: Float, // 0.0 to 1.0
    val items: List<ExpenseEntity> = emptyList()
)
