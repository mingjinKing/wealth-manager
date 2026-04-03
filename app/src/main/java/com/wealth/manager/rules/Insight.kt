package com.wealth.manager.rules

/**
 * 统一洞察结果结构
 *
 * 规则引擎产出的所有洞察均使用此结构，
 * 保证 ViewModel 消费侧接口一致。
 */
data class Insight(
    val type: InsightType,
    val message: String,
    val metadata: Map<String, Any> = emptyMap()
)

enum class InsightType {
    WOW_MOMENT,       // 哇时刻触发
    STRUCTURE_BIAS,    // 结构偏向
    HIGH_FREQUENCY,   // 高频消费
    SCALE_ANALYSIS;   // 规模分析（阈值待改）
}