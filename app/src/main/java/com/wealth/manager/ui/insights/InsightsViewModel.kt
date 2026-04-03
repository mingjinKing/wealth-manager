package com.wealth.manager.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wealth.manager.agent.AppInitializer
import com.wealth.manager.agent.WangcaiAgent
import com.wealth.manager.data.dao.CategoryDao
import com.wealth.manager.data.dao.ExpenseDao
import com.wealth.manager.data.entity.ExpenseEntity
import com.wealth.manager.rules.Insight
import com.wealth.manager.rules.ScaleRule
import com.wealth.manager.rules.StructureRule
import com.wealth.manager.rules.FrequencyRule
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
    private val categoryDao: CategoryDao,
    private val wangcaiAgent: WangcaiAgent,
    private val appInitializer: AppInitializer
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

            val expenseRecords = expenses.filter { categoryMap[it.categoryId]?.type == "EXPENSE" }
            val incomeRecords = expenses.filter { categoryMap[it.categoryId]?.type == "INCOME" }

            val totalExpense = expenseRecords.sumOf { it.amount }
            val totalIncome = incomeRecords.sumOf { it.amount }

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

    /**
     * 触发 AI 全面复盘（流式输出原始 Markdown）
     */
    fun triggerAiAnalysis() {
        viewModelScope.launch {
            // 立即重置 AI 状态
            _state.value = _state.value.copy(
                isAiAnalyzing = true,
                aiAnalysisResult = "", 
                aiAnalysisError = null
            )

            try {
                wangcaiAgent.clearContext()
                appInitializer.refreshAppData()
                
                val systemContext = buildSystemContextForAi()

                // thinkStream 返回 Flow<String>，delta 包含原始 Markdown
                wangcaiAgent.thinkStream(
                    userMessage = "请根据我最近31天的真实账单，为我生成一份专业的财务健康评估报告。",
                    systemContext = systemContext
                ).collect { delta ->
                    val current = _state.value.aiAnalysisResult ?: ""
                    // 保留 Markdown 标记，由 UI 层的渲染器处理
                    _state.value = _state.value.copy(
                        aiAnalysisResult = current + delta
                    )
                }

                appInitializer.recordAnalysis()
                _state.value = _state.value.copy(isAiAnalyzing = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isAiAnalyzing = false,
                    aiAnalysisError = e.message ?: "旺财分析中断了，请重试"
                )
            }
        }
    }

    private suspend fun buildSystemContextForAi(): String {
        val calendar = Calendar.getInstance()
        val end = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_YEAR, -30) // 确保覆盖 31 天，包含 3 月 3 日
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        val start = calendar.timeInMillis

        val expenses = expenseDao.getExpensesByDateRange(start, end).first()
        val total = expenses.sumOf { it.amount }

        return """
            当前分析口径：${formatTimeRange(start, end)} (共 31 天)
            数据库精确总支出：¥${String.format("%.2f", total)}
            指令：请以 Markdown 格式输出，包含加粗总结和分点建议。
        """.trimIndent()
    }

    private fun formatTimeRange(start: Long, end: Long): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        return "${sdf.format(java.util.Date(start))} ~ ${sdf.format(java.util.Date(end))}"
    }

    private fun generateGlobalAnalysis(summaries: List<CategorySummary>, total: Double): List<Insight> {
        val insights = mutableListOf<Insight>()
        insights.add(ScaleRule.buildInsight(total))
        summaries.firstOrNull()?.let { top ->
            if (StructureRule.isBiased(top.percentage)) {
                insights.add(StructureRule.buildInsight(top.categoryName, top.percentage))
            }
        }
        return insights
    }

    private fun getCurrentMonthRange(): Pair<Long, Long> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
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
    val globalAnalysis: List<Insight> = emptyList(),
    val startTime: Long = 0L,
    val endTime: Long = 0L,
    val isDefaultMonth: Boolean = true,
    val isAiAnalyzing: Boolean = false,
    val aiAnalysisResult: String? = null,
    val aiAnalysisError: String? = null
)

data class CategorySummary(
    val categoryEmoji: String,
    val categoryName: String,
    val amount: Double,
    val percentage: Float,
    val items: List<ExpenseEntity> = emptyList()
)
