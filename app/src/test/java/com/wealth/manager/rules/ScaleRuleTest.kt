package com.wealth.manager.rules

import org.junit.Assert.*
import org.junit.Test

/**
 * ScaleRule 单元测试
 *
 * 测试规模分析规则的各种场景
 */
class ScaleRuleTest {

    @Test
    fun `isLargeScale - 支出超过5000时应返回true`() {
        // given
        val total = 6000.0

        // when
        val result = ScaleRule.isLargeScale(total)

        // then
        assertTrue("支出6000应判定为大规模", result)
    }

    @Test
    fun `isLargeScale - 刚好等于5000时应返回false`() {
        // given
        val total = 5000.0

        // when
        val result = ScaleRule.isLargeScale(total)

        // then
        // 使用 > 而非 >=，所以等于时不触发
        assertFalse("刚好等于5000不是大规模", result)
    }

    @Test
    fun `isLargeScale - 略低于5000时应返回false`() {
        // given
        val total = 4999.0

        // when
        val result = ScaleRule.isLargeScale(total)

        // then
        assertFalse("略低于5000不是大规模", result)
    }

    @Test
    fun `isLargeScale - 零支出时应返回false`() {
        // given
        val total = 0.0

        // when
        val result = ScaleRule.isLargeScale(total)

        // then
        assertFalse("零支出不是大规模", result)
    }

    @Test
    fun `isLargeScale - 负数支出时应返回false`() {
        // given
        val total = -100.0

        // when
        val result = ScaleRule.isLargeScale(total)

        // then
        assertFalse("负数支出不是大规模", result)
    }

    @Test
    fun `isWellControlled - 支出在0到5000之间时应返回true`() {
        // given
        val total = 3000.0

        // when
        val result = ScaleRule.isWellControlled(total)

        // then
        assertTrue("支出3000应在可控范围", result)
    }

    @Test
    fun `isWellControlled - 刚好等于5000时应返回true`() {
        // given
        val total = 5000.0

        // when
        val result = ScaleRule.isWellControlled(total)

        // then
        assertTrue("等于5000边界值应在可控范围", result)
    }

    @Test
    fun `isWellControlled - 超过5000时应返回false`() {
        // given
        val total = 6000.0

        // when
        val result = ScaleRule.isWellControlled(total)

        // then
        assertFalse("超过5000不是可控范围", result)
    }

    @Test
    fun `isWellControlled - 零支出时应返回true`() {
        // given
        val total = 0.0

        // when
        val result = ScaleRule.isWellControlled(total)

        // then
        assertTrue("零支出是可控的", result)
    }

    @Test
    fun `buildInsight - 大规模支出时应返回LARGE级别的洞察`() {
        // when
        val insight = ScaleRule.buildInsight(8000.0)

        // then
        assertEquals(InsightType.SCALE_ANALYSIS, insight.type)
        assertTrue(insight.message.contains("规模较大"))
        assertEquals(8000.0, insight.metadata["total"])
        assertEquals(5000.0, insight.metadata["threshold"])
        assertEquals("LARGE", insight.metadata["level"])
    }

    @Test
    fun `buildInsight - 正常支出时应返回NORMAL级别的洞察`() {
        // when
        val insight = ScaleRule.buildInsight(3000.0)

        // then
        assertEquals(InsightType.SCALE_ANALYSIS, insight.type)
        assertTrue(insight.message.contains("预算管理良好"))
        assertEquals(3000.0, insight.metadata["total"])
        assertEquals("NORMAL", insight.metadata["level"])
    }
}
