package com.wealth.manager.agent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 旺财 Agent 初始化器
 *
 * 将 bootstrap.md 的伪代码逻辑转化为实际可执行代码。
 * 负责 App 启动时的上下文初始化和填充。
 */
@Singleton
class AppInitializer @Inject constructor(
    private val agentContext: AgentContext
) {

    /**
     * 执行完整初始化（App 启动时调用）
     *
     * 流程：
     * 1. 检查是否首次初始化，若是则填默认值
     * 2. 补充 App 数据（当月消费、年度累计等）
     * 3. 确保必要字段存在
     */
    suspend fun bootstrap() = withContext(Dispatchers.IO) {
        // 1. 首次初始化检测
        if (agentContext.read("identity.version").isEmpty()) {
            initDefaultContext()
        }

        // 2. 补充 App 数据
        refreshAppData()

        // 3. 确保必要字段存在
        ensureDefaultFields()
    }

    /**
     * 仅刷新 App 数据（每次打开透视页时调用）
     * 注：消费数据由 InsightsViewModel 在加载时直接提供，此处仅更新时间戳
     */
    suspend fun refreshAppData() = withContext(Dispatchers.IO) {
        // 更新时间戳，保持上下文新鲜
        val monthRange = getCurrentMonthRange()
        val yearRange = getCurrentYearRange()

        val updates = mutableMapOf<String, String>()
        updates["app_data.month_period_start"] = monthRange.first.toString()
        updates["app_data.month_period_end"] = monthRange.second.toString()
        updates["app_data.year_period_start"] = yearRange.first.toString()
        updates["app_data.year_period_end"] = yearRange.second.toString()
        agentContext.writeBatch(updates)
    }

    /**
     * 记录一次分析完成（更新分析计数）
     */
    fun recordAnalysis() {
        val current = agentContext.read("memory.analysis_count").toIntOrNull() ?: 0
        agentContext.write("memory.analysis_count", (current + 1).toString())
        agentContext.write("memory.last_analysis", System.currentTimeMillis().toString())
    }

    /**
     * 首次初始化：填入默认值
     */
    private fun initDefaultContext() {
        agentContext.write("identity.name", "旺财")
        agentContext.write("identity.version", "v1.0")
        agentContext.write("identity.initialized_at", System.currentTimeMillis().toString())

        agentContext.write("user.monthly_budget", "")
        agentContext.write("user.income_monthly", "")
        agentContext.write("user.savings_goal", "")
        agentContext.write("user.asset_growth_target", "")
        agentContext.write("user.financial_stage", "")

        agentContext.write("preferences.alert_threshold", "0.8")
        agentContext.write("preferences.focus_categories", "[]")
        agentContext.write("preferences.language", "zh_CN")

        agentContext.write("memory.analysis_count", "0")
        agentContext.write("memory.last_analysis", "")
        agentContext.write("memory.last_insights", "[]")
    }

    /**
     * 确保必要字段存在（防止文件损坏导致字段丢失）
     */
    private fun ensureDefaultFields() {
        if (agentContext.read("preferences.alert_threshold").isEmpty()) {
            agentContext.write("preferences.alert_threshold", "0.8")
        }
        if (agentContext.read("preferences.language").isEmpty()) {
            agentContext.write("preferences.language", "zh_CN")
        }
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

    private fun getCurrentYearRange(): Pair<Long, Long> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.MONTH, Calendar.JANUARY)
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val yearStart = calendar.timeInMillis

        calendar.set(Calendar.MONTH, Calendar.DECEMBER)
        calendar.set(Calendar.DAY_OF_MONTH, 31)
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        val yearEnd = calendar.timeInMillis

        return Pair(yearStart, yearEnd)
    }
}
