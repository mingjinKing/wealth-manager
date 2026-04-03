package com.wealth.manager.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wealth.manager.agent.AppInitializer
import com.wealth.manager.agent.WangcaiAgent
import com.wealth.manager.data.dao.CategoryDao
import com.wealth.manager.data.dao.ExpenseDao
import com.wealth.manager.data.entity.ExpenseEntity
import com.wealth.manager.rules.FrequencyRule
import com.wealth.manager.rules.Insight
import com.wealth.manager.rules.ScaleRule
import com.wealth.manager.rules.StructureRule
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

    /**
     * 触发 AI 全面复盘
     *
     * 调用旺财 Agent，基于规则引擎数据和用户上下文生成个性化分析报告
     */
    fun triggerAiAnalysis() {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isAiAnalyzing = true,
                aiAnalysisResult = null,
                aiAnalysisError = null
            )

            try {
                // 1. 刷新 App 数据到上下文
                appInitializer.refreshAppData()

                // 2. 构建系统上下文（附加数据摘要）
                val systemContext = buildSystemContext()

                // 3. 调用旺财 Agent
                val result = wangcaiAgent.think(
                    userMessage = "请分析我当前的消费情况，给我一份全面的财务健康报告。",
                    systemContext = systemContext
                )

                // 4. 记录分析
                appInitializer.recordAnalysis()

                _state.value = _state.value.copy(
                    isAiAnalyzing = false,
                    aiAnalysisResult = result
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isAiAnalyzing = false,
                    aiAnalysisError = e.message ?: "分析失败，请重试"
                )
            }
        }
    }

    private fun buildSystemContext(): String {
        val s = state.value
        val summaries = s.summaryItems

        val topCategory = summaries.firstOrNull()?.let {
            "${it.categoryEmoji} ${it.categoryName} ¥${String.format("%.0f", it.amount)}（${(it.percentage * 100).toInt()}%）"
        } ?: "无"

        val top3Categories = summaries.take(3).joinToString("、") {
            "${it.categoryEmoji}${it.categoryName}"
        }

        return buildString {
            appendLine("=== 当前周期数据 ===")
            appendLine("时间范围：${formatTimeRange(s.startTime, s.endTime)}")
            appendLine("总支出：¥${String.format("%.0f", s.totalAmount)}")
            appendLine("总收入：¥${String.format("%.0f", s.totalIncome)}")
            appendLine("支出笔数：${summaries.sumOf { it.items.size }}")
            appendLine()
            appendLine("=== 分类概况 ===")
            appendLine("最大分类：$topCategory")
            appendLine("Top3：$top3Categories")
            appendLine()
            appendLine("=== 用户上下文 ===")
            appendLine("(请从 context 工具读取用户预算、目标、偏好等上下文信息)")
        }
    }

    private fun formatTimeRange(start: Long, end: Long): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        return "${sdf.format(java.util.Date(start))} ~ ${sdf.format(java.util.Date(end))}"
    }

    private fun generateGlobalAnalysis(summaries: List<CategorySummary>, total: Double): List<Insight> {
        val insights = mutableListOf<Insight>()

        // 1. 规模分析（ScaleRule）
        insights.add(ScaleRule.buildInsight(total))

        // 2. 结构偏向（StructureRule）
        summaries.firstOrNull()?.let { top ->
            if (StructureRule.isBiased(top.percentage)) {
                insights.add(StructureRule.buildInsight(top.categoryName, top.percentage))
            }
        }

        // 3. 高频分析（FrequencyRule）
        val totalCount = summaries.sumOf { it.items.size }
        if (FrequencyRule.isHighFrequency(totalCount)) {
            insights.add(FrequencyRule.buildInsight(totalCount))
        }

        return insights
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
    val globalAnalysis: List<Insight> = emptyList(),
    val startTime: Long = 0L,
    val endTime: Long = 0L,
    val isDefaultMonth: Boolean = true,
    // AI 分析相关
    val isAiAnalyzing: Boolean = false,
    val aiAnalysisResult: String? = null,
    val aiAnalysisError: String? = null
)

data class CategorySummary(
    val categoryEmoji: String,
    val categoryName: String,
    val amount: Double,
    val percentage: Float, // 0.0 to 1.0
    val items: List<ExpenseEntity> = emptyList()
)
