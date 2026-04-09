package com.wealth.manager.config

/**
 * 业务配置统一管理
 *
 * 所有业务规则阈值集中管理，支持运行时动态调整
 */
object BusinessConfig {

    // ==================== 哇时刻规则 ====================
    /** 哇时刻最低节省金额阈值（元）*/
    const val WOW_MIN_SAVED_AMOUNT = 100.0

    /** 哇时刻相对节省比例阈值（相对于历史均值）*/
    const val WOW_SAVED_RATIO_THRESHOLD = 0.2

    // ==================== 规模分析规则 ====================
    /** 规模分析阈值（元），超过此值认为是大额消费 */
    const val SCALE_THRESHOLD = 5000.0

    // ==================== 频率分析规则 ====================
    /** 高频消费阈值（笔），超过此值认为是高频消费 */
    const val FREQUENCY_THRESHOLD = 50

    // ==================== 结构分析规则 ====================
    /** 结构偏向阈值（0.0-1.0），超过此值认为存在结构偏向 */
    const val PERCENTAGE_THRESHOLD = 0.4f

    // ==================== 时间范围配置 ====================
    /** 默认历史分析时间范围（毫秒）*/
    const val DEFAULT_HISTORY_RANGE_MS = 30L * 24 * 60 * 60 * 1000  // 30天

    /** 年度目标时间范围（毫秒）*/
    const val YEAR_GOAL_RANGE_MS = 365L * 24 * 60 * 60 * 1000  // 365天

    // ==================== UI 显示配置 ====================
    /** 金额显示阈值，超过此值显示为 "Xk" 格式 */
    const val AMOUNT_DISPLAY_THRESHOLD = 10000

    /** AI 消息截断长度 */
    const val AI_MESSAGE_TRUNCATE_LENGTH = 100

    /** 日志收集器最大日志条数 */
    const val MAX_LOGS = 500
}
