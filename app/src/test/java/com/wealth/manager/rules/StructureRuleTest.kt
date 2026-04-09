package com.wealth.manager.rules

import org.junit.Assert.*
import org.junit.Test

/**
 * StructureRule 单元测试
 *
 * 测试结构偏向规则的各种场景
 */
class StructureRuleTest {

    @Test
    fun `isBiased - 占比超过40%时应返回true`() {
        // given
        val topPercentage = 0.5f  // 50%

        // when
        val result = StructureRule.isBiased(topPercentage)

        // then
        assertTrue("50%占比应判定为偏向", result)
    }

    @Test
    fun `isBiased - 刚好等于40%时应返回false`() {
        // given
        val topPercentage = 0.4f  // 40%

        // when
        val result = StructureRule.isBiased(topPercentage)

        // then
        // 使用 > 而非 >=，所以等于时不触发
        assertFalse("刚好40%不是偏向", result)
    }

    @Test
    fun `isBiased - 略低于40%时应返回false`() {
        // given
        val topPercentage = 0.39f  // 39%

        // when
        val result = StructureRule.isBiased(topPercentage)

        // then
        assertFalse("略低于40%不是偏向", result)
    }

    @Test
    fun `isBiased - 零占比时应返回false`() {
        // given
        val topPercentage = 0.0f

        // when
        val result = StructureRule.isBiased(topPercentage)

        // then
        assertFalse("零占比不是偏向", result)
    }

    @Test
    fun `isBiased - 100%占比时应返回true`() {
        // given
        val topPercentage = 1.0f  // 100%

        // when
        val result = StructureRule.isBiased(topPercentage)

        // then
        assertTrue("100%占比应判定为偏向", result)
    }

    @Test
    fun `isBiased - 41%占比时应返回true`() {
        // given
        val topPercentage = 0.41f  // 41%

        // when
        val result = StructureRule.isBiased(topPercentage)

        // then
        assertTrue("41%占比应判定为偏向", result)
    }

    @Test
    fun `buildInsight - 存在偏向时应返回正确的洞察`() {
        // when
        val insight = StructureRule.buildInsight("餐饮", 0.6f)

        // then
        assertEquals(InsightType.STRUCTURE_BIAS, insight.type)
        assertTrue(insight.message.contains("餐饮"))
        assertTrue(insight.message.contains("60%"))
        assertEquals("餐饮", insight.metadata["categoryName"])
        assertEquals(0.6f, insight.metadata["percentage"])
    }

    @Test
    fun `buildInsight - 边界值40%`() {
        // when
        val insight = StructureRule.buildInsight("购物", 0.4f)

        // then
        // 注意：isBiased 不会对40%触发，但 buildInsight 不做判断
        assertEquals(InsightType.STRUCTURE_BIAS, insight.type)
        assertTrue(insight.message.contains("购物"))
        assertTrue(insight.message.contains("40%"))
    }
}
