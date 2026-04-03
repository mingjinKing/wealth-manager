package com.wealth.manager.rules

/**
 * 规模分析规则
 *
 * 规则5：规模分析
 * 触发条件：total > SCALE_THRESHOLD（默认 ¥5000）
 *
 * 来自：InsightsViewModel
 */
object ScaleRule {

    private const val SCALE_THRESHOLD = 5000.0

    /**
     * 判断支出规模是否过大
     *
     * @param total 总支出金额
     * @return 是否规模较大
     */
    fun isLargeScale(total: Double): Boolean {
        return total > SCALE_THRESHOLD
    }

    /**
     * 判断支出规模是否适中（良好控制）
     *
     * @param total 总支出金额
     * @return 是否规模适中
     */
    fun isWellControlled(total: Double): Boolean {
        return total in 0.0..SCALE_THRESHOLD
    }

    /**
     * 构建规模分析洞察
     *
     * @param total 总支出金额
     * @return 规模分析洞察
     */
    fun buildInsight(total: Double): Insight {
        return if (isLargeScale(total)) {
            Insight(
                type = InsightType.SCALE_ANALYSIS,
                message = "该期间总支出 ¥${String.format("%.0f", total)}，规模较大，建议审查高额单项。",
                metadata = mapOf(
                    "total" to total,
                    "threshold" to SCALE_THRESHOLD,
                    "level" to "LARGE"
                )
            )
        } else {
            Insight(
                type = InsightType.SCALE_ANALYSIS,
                message = "该期间消费控制在 ¥${String.format("%.0f", total)}，整体预算管理良好。",
                metadata = mapOf(
                    "total" to total,
                    "threshold" to SCALE_THRESHOLD,
                    "level" to "NORMAL"
                )
            )
        }
    }
}
