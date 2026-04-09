package com.wealth.manager.rules

import org.junit.Assert.*
import org.junit.Test

/**
 * ScaleRule 单元测试
 *
 * 测试规模分析规则的各种场景
 */
class ScaleRuleTest {

    // ==================== isLargeScale 测试 ====================

    @Test
    fun `isLargeScale - 超过阈值返回 true`() {
        // given - 阈值是 5000
        val total = 6000.0

        // when
        val result = ScaleRule.isLargeScale(total)

        // then
        assertTrue("6000 > 5000，应返回 true", result)
    }

    @Test
    fun `isLargeScale - 刚好等于阈值返回 false`() {
        // given - 阈值是 5000（使用 > 而非 >=）
        val total = 5000.0

        // when
        val result = ScaleRule.isLargeScale(total)

        // then
        assertFalse("5000 不大于 5000，应返回 false", result)
    }

    @Test
    fun `isLargeScale - 略低于阈值返回 false`() {
        // given
        val total = 4999.99

        // when
        val result = ScaleRule.isLargeScale(total)

        // then
        assertFalse("4999.99 < 5000，应返回 false", result)
    }

    @Test
    fun `isLargeScale - 零值返回 false`() {
        // given
        val total = 0.0

        // when
        val result = ScaleRule.isLargeScale(total)

        // then
        assertFalse("0 不大于 5000，应返回 false", result)
    }

    @Test
    fun `isLargeScale - 负值返回 false`() {
        // given - 不正常情况
        val total = -100.0

        // when
        val result = ScaleRule.isLargeScale(total)

        // then
        assertFalse("负值不应算大规模", result)
    }

    @Test
    fun `isLargeScale - 极大值`() {
        // given
        val total = Double.MAX_VALUE

        // when
        val result = ScaleRule.isLargeScale(total)

        // then
        assertTrue(result)
    }

    // ==================== isWellControlled 测试 ====================

    @Test
    fun `isWellControlled - 零值返回 true`() {
        // given
        val total = 0.0

        // when
        val result = ScaleRule.isWellControlled(total)

        // then
        assertTrue("0 在范围内 [0, 5000]，应返回 true", result)
    }

    @Test
    fun `isWellControlled - 正常范围返回 true`() {
        // given
        val total = 3000.0

        // when
        val result = ScaleRule.isWellControlled(total)

        // then
        assertTrue("3000 在 [0, 5000] 范围内，应返回 true", result)
    }

    @Test
    fun `isWellControlled - 刚好等于上限返回 true`() {
        // given
        val total = 5000.0

        // when
        val result = ScaleRule.isWellControlled(total)

        // then
        assertTrue("5000 在 [0, 5000] 范围内，应返回 true", result)
    }

    @Test
    fun `isWellControlled - 超过上限返回 false`() {
        // given
        val total = 5001.0

        // when
        val result = ScaleRule.isWellControlled(total)

        // then
        assertFalse("5001 > 5000，超出范围，应返回 false", result)
    }

    @Test
    fun `isWellControlled - 负值返回 false`() {
        // given
        val total = -100.0

        // when
        val result = ScaleRule.isWellControlled(total)

        // then
        assertFalse("负值不在范围内，应返回 false", result)
    }

    // ==================== buildInsight 测试 ====================

    @Test
    fun `buildInsight - 大规模支出返回正确类型`() {
        // given
        val total = 8000.0

        // when
        val insight = ScaleRule.buildInsight(total)

        // then
        assertEquals(InsightType.SCALE_ANALYSIS, insight.type)
        assertEquals("LARGE", insight.metadata["level"])
    }

    @Test
    fun `buildInsight - 正常规模返回正确类型`() {
        // given
        val total = 3000.0

        // when
        val insight = ScaleRule.buildInsight(total)

        // then
        assertEquals(InsightType.SCALE_ANALYSIS, insight.type)
        assertEquals("NORMAL", insight.metadata["level"])
    }

    @Test
    fun `buildInsight - 大规模支出消息包含金额`() {
        // given
        val total = 8000.0

        // when
        val insight = ScaleRule.buildInsight(total)

        // then
        assertTrue(insight.message.contains("8000") || insight.message.contains("8000"))
        assertTrue(insight.message.contains("大") || insight.message.contains("审查"))
    }

    @Test
    fun `buildInsight - 正常规模消息积极`() {
        // given
        val total = 3000.0

        // when
        val insight = ScaleRule.buildInsight(total)

        // then
        assertTrue(
            insight.message.contains("良好") ||
            insight.message.contains("正常") ||
            insight.message.contains("控制")
        )
    }

    @Test
    fun `buildInsight - 包含元数据`() {
        // given
        val total = 6000.0

        // when
        val insight = ScaleRule.buildInsight(total)

        // then
        assertEquals(6000.0, insight.metadata["total"])
        assertEquals(5000.0, insight.metadata["threshold"])
    }

    @Test
    fun `buildInsight - 零值正常处理`() {
        // given
        val total = 0.0

        // when
        val insight = ScaleRule.buildInsight(total)

        // then
        assertEquals(InsightType.SCALE_ANALYSIS, insight.type)
        assertEquals("NORMAL", insight.metadata["level"])
    }

    // ==================== 边界条件测试 ====================

    @Test
    fun `isLargeScale - 临界值 5001`() {
        assertTrue(ScaleRule.isLargeScale(5001.0))
    }

    @Test
    fun `isLargeScale - 临界值 4999`() {
        assertFalse(ScaleRule.isLargeScale(4999.0))
    }

    @Test
    fun `buildInsight - 精确到分的金额`() {
        // given
        val total = 5000.01

        // when
        val insight = ScaleRule.buildInsight(total)

        // then
        assertEquals("LARGE", insight.metadata["level"])
    }

    @Test
    fun `isLargeScale - 科学计数法表示的临界值`() {
        // given - 5000.0000001
        val total = 5000.0000001

        // when
        val result = ScaleRule.isLargeScale(total)

        // then
        assertTrue(result)
    }
}
