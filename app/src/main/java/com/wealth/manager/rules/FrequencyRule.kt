package com.wealth.manager.rules

import com.wealth.manager.config.BusinessConfig

/**
 * 频率分析规则
 *
 * 规则4：频率分析
 * 触发条件：totalCount > COUNT_THRESHOLD
 *
 * 来自：InsightsViewModel
 */
object FrequencyRule {

    // 使用 BusinessConfig 中的统一配置
    private val COUNT_THRESHOLD = BusinessConfig.FREQUENCY_THRESHOLD

    /**
     * 判断消费频率是否过高
     */
    fun isHighFrequency(totalCount: Int): Boolean {
        return totalCount > COUNT_THRESHOLD
    }

    /**
     * 触发高频分析时构建的洞察
     */
    fun buildInsight(totalCount: Int): Insight {
        return Insight(
            type = InsightType.HIGH_FREQUENCY,
            message = "该期间消费频率较高（共${totalCount}笔），建议减少小额琐碎支出。",
            metadata = mapOf("totalCount" to totalCount)
        )
    }
}
