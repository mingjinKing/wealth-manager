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
import java.text.SimpleDateFormat
import java.util.*
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

    fun triggerAiAnalysis() {
        val currentState = _state.value
        
        val (analysisStart, analysisEnd, rangeText) = if (currentState.isDefaultMonth) {
            val cal = Calendar.getInstance()
            val end = cal.timeInMillis
            cal.add(Calendar.DAY_OF_YEAR, -30)
            Triple(cal.timeInMillis, end, "最近30天")
        } else {
            val df = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
            Triple(
                currentState.startTime, 
                currentState.endTime, 
                "${df.format(Date(currentState.startTime))} 至 ${df.format(Date(currentState.endTime))}"
            )
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(
                isAiAnalyzing = true,
                aiAnalysisResult = "",
                aiThoughtStatus = "正在唤醒旺财...",
                aiAnalysisError = null
            )

            try {
                wangcaiAgent.clearContext()
                appInitializer.refreshAppData()
                
                val systemContext = buildSystemContextForAi(analysisStart, analysisEnd, rangeText)

                val progressRegex = Regex("\\[PROGRESS: (.*?)\\]")

                wangcaiAgent.thinkStream(
                    userMessage = "请根据我 $rangeText 的真实账单，为我生成一份精简的财务复盘报告。直接说重点，不要啰嗦。",
                    systemContext = systemContext
                ).collect { delta ->
                    val progressMatch = progressRegex.find(delta)
                    if (progressMatch != null) {
                        val status = progressMatch.groupValues[1]
                        _state.value = _state.value.copy(aiThoughtStatus = status)
                    } else {
                        val current = _state.value.aiAnalysisResult ?: ""
                        _state.value = _state.value.copy(
                            aiAnalysisResult = current + delta
                        )
                    }
                }

                appInitializer.recordAnalysis()
                _state.value = _state.value.copy(isAiAnalyzing = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isAiAnalyzing = false,
                    aiAnalysisError = e.message ?: "旺财刚才开小差了，请重试"
                )
            }
        }
    }

    private suspend fun buildSystemContextForAi(start: Long, end: Long, rangeDesc: String): String {
        val expenses = expenseDao.getExpensesByDateRange(start, end).first()
        val total = expenses.sumOf { it.amount }

        return """
            当前分析口径：$rangeDesc
            时间戳参数（必须传给工具）：startTime=$start, endTime=$end
            该范围内总支出：¥${String.format("%.2f", total)}
            
            指令：
            1. 调用 rule_engine 工具时，**必须**带上上述 startTime 和 endTime 参数。
            2. 严禁使用“最近30天”等惯性描述，应称呼此范围为“${rangeDesc}”。
            3. 输出请保持 Markdown 格式。
            
            限制：结果必须极其精简，字数控制在 150 字以内。
        """.trimIndent()
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
    val aiThoughtStatus: String = "",
    val aiAnalysisError: String? = null
)

data class CategorySummary(
    val categoryEmoji: String,
    val categoryName: String,
    val amount: Double,
    val percentage: Float,
    val items: List<ExpenseEntity> = emptyList()
)
