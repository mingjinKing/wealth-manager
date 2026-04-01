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
        loadMonthlyInsights()
    }

    private fun loadMonthlyInsights() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)

            val (monthStart, monthEnd) = getCurrentMonthRange()
            val expenses = expenseDao.getExpensesByDateRange(monthStart, monthEnd).first()
            val categories = categoryDao.getAllCategories().first()

            if (expenses.isEmpty()) {
                _state.value = InsightsState(isLoading = false, summaryItems = emptyList())
                return@launch
            }

            val categoryMap = categories.associateBy { it.id }
            val totalMonthAmount = expenses.sumOf { it.amount }
            
            val summaryItems = expenses
                .groupBy { it.categoryId }
                .mapNotNull { (categoryId, categoryExpenses) ->
                    val category = categoryMap[categoryId] ?: return@mapNotNull null
                    val total = categoryExpenses.sumOf { it.amount }
                    val percentage = (total / totalMonthAmount).toFloat()
                    
                    CategorySummary(
                        categoryEmoji = category.icon,
                        categoryName = category.name,
                        amount = total,
                        percentage = percentage,
                        items = categoryExpenses.sortedByDescending { it.date }
                    )
                }
                .sortedByDescending { it.amount }

            val globalAnalysis = generateGlobalAnalysis(summaryItems, totalMonthAmount)

            _state.value = InsightsState(
                isLoading = false,
                summaryItems = summaryItems,
                totalAmount = totalMonthAmount,
                globalAnalysis = globalAnalysis
            )
        }
    }

    private fun generateGlobalAnalysis(summaries: List<CategorySummary>, total: Double): List<String> {
        val analysis = mutableListOf<String>()
        
        // 1. 总体规模分析
        if (total > 5000) {
            analysis.add("本月总支出 ¥${String.format("%.0f", total)}，规模较大，建议审查高额单项。")
        } else {
            analysis.add("本月消费控制在 ¥${String.format("%.0f", total)}，整体预算管理良好。")
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
            analysis.add("本月消费频率较高（共${totalCount}笔），建议减少小额琐碎支出。")
        }

        // 4. 具体类别建议
        summaries.find { it.categoryName.contains("餐饮") || it.categoryName.contains("外卖") }?.let { 
            if (it.amount > 1000) {
                analysis.add("餐饮支出是主要开销之一，适当增加居家烹饪可有效省钱。")
            }
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
    val summaryItems: List<CategorySummary> = emptyList(),
    val globalAnalysis: List<String> = emptyList()
)

data class CategorySummary(
    val categoryEmoji: String,
    val categoryName: String,
    val amount: Double,
    val percentage: Float, // 0.0 to 1.0
    val items: List<ExpenseEntity> = emptyList()
)
