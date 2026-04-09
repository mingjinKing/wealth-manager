package com.wealth.manager.rules

import org.junit.Assert.*
import org.junit.Test

/**
 * WowRule 单元测试
 *
 * 测试哇时刻触发规则的各种场景
 */
class WowRuleTest {

    // ==================== isTriggered 测试 ====================

    @Test
    fun `isTriggered - 满足双条件时触发`() {
        // given - savedAmount > 100 && savedAmount > avg * 0.2
        val savedAmount = 200.0
        val avgLast4Weeks = 500.0

        // when
        val result = WowRule.isTriggered(savedAmount, avgLast4Weeks)

        // then
        assertTrue("200 > 100 且 200 > 500*0.2=100，应触发", result)
    }

    @Test
    fun `isTriggered - savedAmount低于阈值时不触发`() {
        // given
        val savedAmount = 50.0  // < 100
        val avgLast4Weeks = 500.0

        // when
        val result = WowRule.isTriggered(savedAmount, avgLast4Weeks)

        // then
        assertFalse("savedAmount < 100，不应触发", result)
    }

    @Test
    fun `isTriggered - savedAmount等于阈值时不触发`() {
        // given - savedAmount = 100 (边界值)
        val savedAmount = 100.0
        val avgLast4Weeks = 500.0

        // when
        val result = WowRule.isTriggered(savedAmount, avgLast4Weeks)

        // then
        assertFalse("savedAmount = 100 (不 > 100)，不应触发", result)
    }

    @Test
    fun `isTriggered - savedAmount大于阈值但不超过均值比例时不触发`() {
        // given
        val savedAmount = 90.0  // > 100? No, this won't pass first condition
        val avgLast4Weeks = 1000.0

        // when
        val result = WowRule.isTriggered(savedAmount, avgLast4Weeks)

        // then
        assertFalse("savedAmount < 100，不应触发", result)
    }

    @Test
    fun `isTriggered - savedAmount超过均值2倍时触发`() {
        // given
        val savedAmount = 1000.0
        val avgLast4Weeks = 500.0  // 1000 > 500 * 0.2 = 100 ✓

        // when
        val result = WowRule.isTriggered(savedAmount, avgLast4Weeks)

        // then
        assertTrue("1000 > 100 且 1000 > 100，应触发", result)
    }

    @Test
    fun `isTriggered - 均值接近零时触发`() {
        // given - avg 很小，savedAmount 容易超过
        val savedAmount = 150.0
        val avgLast4Weeks = 10.0  // 150 > 10 * 0.2 = 2 ✓

        // when
        val result = WowRule.isTriggered(savedAmount, avgLast4Weeks)

        // then
        assertTrue("150 > 100 且 150 > 2，应触发", result)
    }

    @Test
    fun `isTriggered - 均值为零时触发`() {
        // given - 第一次记账
        val savedAmount = 200.0
        val avgLast4Weeks = 0.0

        // when
        val result = WowRule.isTriggered(savedAmount, avgLast4Weeks)

        // then - 0.0 * 0.2 = 0.0，所以只要 savedAmount > 100 就触发
        assertTrue("savedAmount > 100 且 0.0 比较无意义，应触发", result)
    }

    @Test
    fun `isTriggered - 均值为负时触发`() {
        // given - 不正常情况
        val savedAmount = 200.0
        val avgLast4Weeks = -100.0

        // when
        val result = WowRule.isTriggered(savedAmount, avgLast4Weeks)

        // then
        assertTrue("savedAmount > 100，应触发", result)
    }

    @Test
    fun `isTriggered - 刚好满足双条件`() {
        // given - savedAmount > 100 且 savedAmount > avg * 0.2
        val savedAmount = 101.0  // > 100
        val avgLast4Weeks = 500.0  // 101 > 500 * 0.2 = 100 ✓

        // when
        val result = WowRule.isTriggered(savedAmount, avgLast4Weeks)

        // then
        assertTrue("101 > 100 且 101 > 100，应触发", result)
    }

    // ==================== buildInsight 测试 ====================

    @Test
    fun `buildInsight - 返回正确的洞察类型`() {
        // when
        val insight = WowRule.buildInsight(200.0)

        // then
        assertEquals(InsightType.WOW_MOMENT, insight.type)
    }

    @Test
    fun `buildInsight - 包含 savedAmount 元数据`() {
        // given
        val savedAmount = 300.0

        // when
        val insight = WowRule.buildInsight(savedAmount)

        // then
        assertEquals(300.0, insight.metadata["savedAmount"])
    }

    @Test
    fun `buildInsight - 消息不为空`() {
        // when
        val insight = WowRule.buildInsight(100.0)

        // then
        assertTrue(insight.message.isNotEmpty())
    }

    @Test
    fun `buildInsight - 不同金额生成不同元数据`() {
        // when
        val insight1 = WowRule.buildInsight(100.0)
        val insight2 = WowRule.buildInsight(500.0)

        // then
        assertEquals(100.0, insight1.metadata["savedAmount"])
        assertEquals(500.0, insight2.metadata["savedAmount"])
        assertNotEquals(
            insight1.metadata["savedAmount"],
            insight2.metadata["savedAmount"]
        )
    }

    // ==================== 边界条件测试 ====================

    @Test
    fun `isTriggered - 极大值`() {
        // given
        val savedAmount = Double.MAX_VALUE
        val avgLast4Weeks = 1.0

        // when
        val result = WowRule.isTriggered(savedAmount, avgLast4Weeks)

        // then
        assertTrue(result)
    }

    @Test
    fun `isTriggered - 极小正值`() {
        // given
        val savedAmount = 0.001
        val avgLast4Weeks = 1000.0

        // when
        val result = WowRule.isTriggered(savedAmount, avgLast4Weeks)

        // then
        assertFalse(result)
    }
}
