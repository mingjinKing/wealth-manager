package com.wealth.manager.rules

import com.wealth.manager.config.BusinessConfig

/**
 * 哇时刻触发规则
 *
 * 规则1：哇时刻触发
 * 触发条件：savedAmount > MIN_SAVED_AMOUNT && savedAmount > avgLast4Weeks * SAVED_RATIO_THRESHOLD
 *
 * 来自：DashboardViewModel
 */
object WowRule {

    // 使用 BusinessConfig 中的统一配置
    private val MIN_SAVED_AMOUNT = BusinessConfig.WOW_MIN_SAVED_AMOUNT
    private val SAVED_RATIO_THRESHOLD = BusinessConfig.WOW_SAVED_RATIO_THRESHOLD

    /**
     * 判断是否触发哇时刻
     *
     * @param savedAmount 本周节省金额（上周均值 - 本周消费）
     * @param avgLast4Weeks 过去4周均值
     * @return 是否触发
     */
    fun isTriggered(savedAmount: Double, avgLast4Weeks: Double): Boolean {
        return savedAmount > MIN_SAVED_AMOUNT && savedAmount > avgLast4Weeks * SAVED_RATIO_THRESHOLD
    }

    /**
     * 触发哇时刻时构建的洞察
     */
    fun buildInsight(savedAmount: Double): Insight {
        return Insight(
            type = InsightType.WOW_MOMENT,
            message = "本周消费控制极佳！",
            metadata = mapOf("savedAmount" to savedAmount)
        )
    }
}
