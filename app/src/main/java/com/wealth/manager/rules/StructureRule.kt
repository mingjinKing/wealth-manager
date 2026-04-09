package com.wealth.manager.rules

import com.wealth.manager.config.BusinessConfig

/**
 * 结构偏向规则
 *
 * 规则3：结构偏向
 * 触发条件：topPercentage > PERCENTAGE_THRESHOLD
 *
 * 来自：InsightsViewModel
 */
object StructureRule {

    // 使用 BusinessConfig 中的统一配置
    private val PERCENTAGE_THRESHOLD = BusinessConfig.PERCENTAGE_THRESHOLD

    /**
     * 判断是否存在结构偏向
     *
     * @param topPercentage 最大分类占比（0.0 ~ 1.0）
     * @return 是否存在偏向
     */
    fun isBiased(topPercentage: Float): Boolean {
        return topPercentage > PERCENTAGE_THRESHOLD
    }

    /**
     * 触发结构偏向时构建的洞察
     */
    fun buildInsight(topCategoryName: String, topPercentage: Float): Insight {
        return Insight(
            type = InsightType.STRUCTURE_BIAS,
            message = "支出结构偏向明显，${topCategoryName}占比达${(topPercentage * 100).toInt()}%，存在优化空间。",
            metadata = mapOf(
                "categoryName" to topCategoryName,
                "percentage" to topPercentage
            )
        )
    }
}
