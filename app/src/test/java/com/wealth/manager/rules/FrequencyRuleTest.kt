package com.wealth.manager.rules

import org.junit.Assert.*
import org.junit.Test

/**
 * FrequencyRule 单元测试
 *
 * 测试频率分析规则的各种场景
 */
class FrequencyRuleTest {

    @Test
    fun `isHighFrequency - 消费笔数超过50时应返回true`() {
        // given
        val totalCount = 60

        // when
        val result = FrequencyRule.isHighFrequency(totalCount)

        // then
        assertTrue("60笔应判定为高频", result)
    }

    @Test
    fun `isHighFrequency - 刚好等于50时应返回false`() {
        // given
        val totalCount = 50

        // when
        val result = FrequencyRule.isHighFrequency(totalCount)

        // then
        // 使用 > 而非 >=，所以等于时不触发
        assertFalse("刚好50笔不是高频", result)
    }

    @Test
    fun `isHighFrequency - 略低于50时应返回false`() {
        // given
        val totalCount = 49

        // when
        val result = FrequencyRule.isHighFrequency(totalCount)

        // then
        assertFalse("略低于50笔不是高频", result)
    }

    @Test
    fun `isHighFrequency - 零笔数时应返回false`() {
        // given
        val totalCount = 0

        // when
        val result = FrequencyRule.isHighFrequency(totalCount)

        // then
        assertFalse("零笔数不是高频", result)
    }

    @Test
    fun `isHighFrequency - 100笔时应返回true`() {
        // given
        val totalCount = 100

        // when
        val result = FrequencyRule.isHighFrequency(totalCount)

        // then
        assertTrue("100笔应判定为高频", result)
    }

    @Test
    fun `buildInsight - 高频消费时应返回正确的洞察`() {
        // when
        val insight = FrequencyRule.buildInsight(80)

        // then
        assertEquals(InsightType.HIGH_FREQUENCY, insight.type)
        assertTrue("消息: " + insight.message, insight.message.contains("80"))
        assertEquals(80, insight.metadata["totalCount"])
    }

    @Test
    fun `buildInsight - 边界值51笔时应触发`() {
        // when
        val insight = FrequencyRule.buildInsight(51)

        // then
        assertEquals(InsightType.HIGH_FREQUENCY, insight.type)
        assertTrue("消息: " + insight.message, insight.message.contains("51"))
    }
}
