package com.wealth.manager.rules

import org.junit.Assert.*
import org.junit.Test

/**
 * FrequencyRule 单元测试
 *
 * 测试频率分析规则的各种场景
 */
class FrequencyRuleTest {

    // ==================== isHighFrequency 测试 ====================

    @Test
    fun `isHighFrequency - 超过阈值返回 true`() {
        // given - 阈值是 50
        val totalCount = 51

        // when
        val result = FrequencyRule.isHighFrequency(totalCount)

        // then
        assertTrue("51 > 50，应返回 true", result)
    }

    @Test
    fun `isHighFrequency - 刚好等于阈值返回 false`() {
        // given - 使用 > 而非 >=
        val totalCount = 50

        // when
        val result = FrequencyRule.isHighFrequency(totalCount)

        // then
        assertFalse("50 不大于 50，应返回 false", result)
    }

    @Test
    fun `isHighFrequency - 略低于阈值返回 false`() {
        // given
        val totalCount = 49

        // when
        val result = FrequencyRule.isHighFrequency(totalCount)

        // then
        assertFalse("49 < 50，应返回 false", result)
    }

    @Test
    fun `isHighFrequency - 零返回 false`() {
        // given
        val totalCount = 0

        // when
        val result = FrequencyRule.isHighFrequency(totalCount)

        // then
        assertFalse("0 不是高频", result)
    }

    @Test
    fun `isHighFrequency - 负值返回 false`() {
        // given - 不正常情况
        val totalCount = -1

        // when
        val result = FrequencyRule.isHighFrequency(totalCount)

        // then
        assertFalse("负值不是高频", result)
    }

    @Test
    fun `isHighFrequency - 100笔返回 true`() {
        // given
        val totalCount = 100

        // when
        val result = FrequencyRule.isHighFrequency(totalCount)

        // then
        assertTrue("100 > 50，应返回 true", result)
    }

    @Test
    fun `isHighFrequency - Int 最大值`() {
        // given
        val totalCount = Int.MAX_VALUE

        // when
        val result = FrequencyRule.isHighFrequency(totalCount)

        // then
        assertTrue(result)
    }

    // ==================== buildInsight 测试 ====================

    @Test
    fun `buildInsight - 返回正确类型`() {
        // when
        val insight = FrequencyRule.buildInsight(60)

        // then
        assertEquals(InsightType.HIGH_FREQUENCY, insight.type)
    }

    @Test
    fun `buildInsight - 消息包含笔数`() {
        // given
        val totalCount = 80

        // when
        val insight = FrequencyRule.buildInsight(totalCount)

        // then
        assertTrue(
            "消息应包含笔数信息",
            insight.message.contains("80") || insight.message.contains("80")
        )
    }

    @Test
    fun `buildInsight - 元数据包含 totalCount`() {
        // given
        val totalCount = 75

        // when
        val insight = FrequencyRule.buildInsight(totalCount)

        // then
        assertEquals(75, insight.metadata["totalCount"])
    }

    @Test
    fun `buildInsight - 消息建议减少小额支出`() {
        // when
        val insight = FrequencyRule.buildInsight(60)

        // then
        assertTrue(
            "消息应包含建议",
            insight.message.contains("高频") ||
            insight.message.contains("小额") ||
            insight.message.contains("减少")
        )
    }

    @Test
    fun `buildInsight - 不同笔数生成不同元数据`() {
        // when
        val insight1 = FrequencyRule.buildInsight(51)
        val insight2 = FrequencyRule.buildInsight(100)

        // then
        assertEquals(51, insight1.metadata["totalCount"])
        assertEquals(100, insight2.metadata["totalCount"])
        assertNotEquals(
            insight1.metadata["totalCount"],
            insight2.metadata["totalCount"]
        )
    }

    // ==================== 边界条件测试 ====================

    @Test
    fun `isHighFrequency - 临界值 51`() {
        assertTrue(FrequencyRule.isHighFrequency(51))
    }

    @Test
    fun `isHighFrequency - 临界值 50`() {
        assertFalse(FrequencyRule.isHighFrequency(50))
    }

    @Test
    fun `isHighFrequency - 临界值 49`() {
        assertFalse(FrequencyRule.isHighFrequency(49))
    }

    @Test
    fun `buildInsight - 边界值 51`() {
        // when
        val insight = FrequencyRule.buildInsight(51)

        // then
        assertEquals(InsightType.HIGH_FREQUENCY, insight.type)
        assertEquals(51, insight.metadata["totalCount"])
    }

    @Test
    fun `buildInsight - 大量笔数`() {
        // given
        val totalCount = 500

        // when
        val insight = FrequencyRule.buildInsight(totalCount)

        // then
        assertEquals(InsightType.HIGH_FREQUENCY, insight.type)
        assertEquals(500, insight.metadata["totalCount"])
    }
}
