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

            // 当期数据
            val expenseRecords = expenses.filter { categoryMap[it.categoryId]?.type == "EXPENSE" }
            val incomeRecords = expenses.filter { categoryMap[it.categoryId]?.type == "INCOME" }

            val totalExpense = expenseRecords.sumOf { it.amount }
            val totalIncome = incomeRecords.sumOf { it.amount }

            // 累计数据（从第一笔到现在）
            val allExpenses = expenseDao.getAllExpenses().first()
            val lifetimeExpense = allExpenses.filter { categoryMap[it.categoryId]?.type == "EXPENSE" }.sumOf { it.amount }
            val lifetimeIncome = allExpenses.filter { categoryMap[it.categoryId]?.type == "INCOME" }.sumOf { it.amount }
            val lifetimeStart = allExpenses.minOfOrNull { it.date } ?: 0L

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
                globalAnalysis = globalAnalysis,
                lifetimeTotalExpense = lifetimeExpense,
                lifetimeTotalIncome = lifetimeIncome,
                lifetimeStartTime = lifetimeStart
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
                aiAnalysisError = null,
                showExplanationInput = false,
                isExplanationVisible = false,
                isReplyingToExplanation = false
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
                _state.value = _state.value.copy(isAiAnalyzing = false, showExplanationInput = true)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isAiAnalyzing = false,
                    aiAnalysisError = e.message ?: "旺财刚才开小差了，请重试"
                )
            }
        }
    }

    fun toggleExplanationVisibility() {
        _state.value = _state.value.copy(isExplanationVisible = !_state.value.isExplanationVisible)
    }

    fun toggleCategoryExpanded(categoryName: String) {
        val current = _state.value.expandedCategories
        _state.value = _state.value.copy(
            expandedCategories = if (current.contains(categoryName)) {
                current - categoryName
            } else {
                current + categoryName
            }
        )
    }

    fun onExplanationChange(text: String) {
        _state.value = _state.value.copy(userExplanation = text)
    }

    fun submitExplanation() {
        val explanation = _state.value.userExplanation
        if (explanation.isBlank()) return

        viewModelScope.launch {
            _state.value = _state.value.copy(
                isAiAnalyzing = true,
                isReplyingToExplanation = true,
                aiThoughtStatus = "旺财正在记录记忆...",
                userExplanation = "",
                isExplanationVisible = false,
                showExplanationInput = false,
                aiAnalysisResult = (_state.value.aiAnalysisResult ?: "") + "\n\n> 我：$explanation\n\n"
            )

            try {
                val progressRegex = Regex("\\[PROGRESS: (.*?)\\]")
                
                wangcaiAgent.thinkStream(
                    userMessage = "这是我对刚才复盘的解释：\"$explanation\"。请调用 record_user_explanation 记录它，并给我一个极其简短的鼓励或建议作为结束。"
                ).collect { delta ->
                    val progressMatch = progressRegex.find(delta)
                    if (progressMatch != null) {
                        val status = progressMatch.groupValues[1]
                        _state.value = _state.value.copy(aiThoughtStatus = status)
                    } else {
                        // AI 一旦开始说话（ delta 不为空且不是进度指令），说明思考结束，转入回复阶段
                        val current = _state.value.aiAnalysisResult ?: ""
                        _state.value = _state.value.copy(
                            aiAnalysisResult = current + delta,
                            isReplyingToExplanation = delta.isEmpty() // 只要有内容输出，就停止显示“转圈”
                        )
                    }
                }
                _state.value = _state.value.copy(isAiAnalyzing = false, isReplyingToExplanation = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isAiAnalyzing = false, isReplyingToExplanation = false)
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
    val aiAnalysisError: String? = null,
    val userExplanation: String = "",
    val showExplanationInput: Boolean = false,
    val isExplanationVisible: Boolean = false,
    val isReplyingToExplanation: Boolean = false,
    val expandedCategories: Set<String> = emptySet(),
    // 累计数据
    val lifetimeTotalExpense: Double = 0.0,
    val lifetimeTotalIncome: Double = 0.0,
    val lifetimeStartTime: Long = 0L
)

data class CategorySummary(
    val categoryEmoji: String,
    val categoryName: String,
    val amount: Double,
    val percentage: Float,
    val items: List<ExpenseEntity> = emptyList()
)
