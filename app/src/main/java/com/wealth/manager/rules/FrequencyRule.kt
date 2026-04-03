package com.wealth.manager.rules

/**
 * 频率分析规则
 *
 * 规则4：频率分析
 * 触发条件：totalCount > 50
 *
 * 来自：InsightsViewModel
 */
object FrequencyRule {

    private const val COUNT_THRESHOLD = 50

    /**
     * 判断消费频率是否过高
     *
     * @param totalCount 总消费笔数
     * @return 是否过高
     */
    fun isHighFrequency(totalCount: Int): Boolean {
        return totalCount > COUNT_THRESHOLD
    }

    /**
     * 触发高频分析时构建的洞察
     *
     * @param totalCount 总消费笔数
     */
    fun buildInsight(totalCount: Int): Insight {
        return Insight(
            type = InsightType.HIGH_FREQUENCY,
            message = "该期间消费频率较高（共${totalCount}笔），建议减少小额琐碎支出。",
            metadata = mapOf("totalCount" to totalCount)
        )
    }
}