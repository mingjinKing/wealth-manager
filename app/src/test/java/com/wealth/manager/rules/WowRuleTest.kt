package com.wealth.manager.rules

import org.junit.Assert.*
import org.junit.Test

/**
 * WowRule 单元测试
 *
 * 测试哇时刻触发规则的各种场景
 */
class WowRuleTest {

    @Test
    fun `isTriggered - 节省金额超过阈值且绝对值大于100时应触发`() {
        // given
        val savedAmount = 200.0  // 节省了200
        val avgLast4Weeks = 800.0  // 过去4周均值800（200 > 800 * 0.2 = 160 ✓）

        // when
        val result = WowRule.isTriggered(savedAmount, avgLast4Weeks)

        // then
        // 200 > 100 ✓ AND 200 > 800 * 0.2 = 160 ✓
        assertTrue("节省200且均值800时应该触发", result)
    }

    @Test
    fun `isTriggered - 节省金额为100但比例不足20%时不触发`() {
        // given
        val savedAmount = 100.0
        val avgLast4Weeks = 600.0  // 100 > 600 * 0.2 = 120?  否，100 < 120

        // when
        val result = WowRule.isTriggered(savedAmount, avgLast4Weeks)

        // then
        // 100 > 100 ✓ BUT 100 > 120 ✗
        assertFalse("节省100但比例不足20%时不触发", result)
    }

    @Test
    fun `isTriggered - 节省金额超过比例但不足100元时不触发`() {
        // given
        val savedAmount = 80.0
        val avgLast4Weeks = 300.0  // 80 > 300 * 0.2 = 60 ✓ 但 80 < 100

        // when
        val result = WowRule.isTriggered(savedAmount, avgLast4Weeks)

        // then
        // 80 > 100 ✗
        assertFalse("节省不足100元时不触发", result)
    }

    @Test
    fun `isTriggered - 刚好等于临界值时不触发因为用大于号`() {
        // given - 刚好等于两个条件（使用 > 而非 >=）
        val savedAmount = 100.0
        val avgLast4Weeks = 500.0  // 100 > 500 * 0.2 = 100?  否，100 > 100 是 false

        // when
        val result = WowRule.isTriggered(savedAmount, avgLast4Weeks)

        // then
        // 因为使用 > 而非 >=，所以刚好等于时不触发
        assertFalse("刚好等于临界值因为用大于号所以不触发", result)
    }

    @Test
    fun `isTriggered - 略超临界值时应触发`() {
        // given - 略超临界值
        val savedAmount = 101.0
        val avgLast4Weeks = 500.0

        // when
        val result = WowRule.isTriggered(savedAmount, avgLast4Weeks)

        // then
        // 101 > 100 ✓ AND 101 > 100 ✓
        assertTrue("略超临界值应该触发", result)
    }

    @Test
    fun `isTriggered - 负数节省金额不触发`() {
        // given - 超支了
        val savedAmount = -100.0
        val avgLast4Weeks = 1000.0

        // when
        val result = WowRule.isTriggered(savedAmount, avgLast4Weeks)

        // then
        assertFalse("负数节省金额不应触发", result)
    }

    @Test
    fun `isTriggered - 零节省金额不触发`() {
        // given
        val savedAmount = 0.0
        val avgLast4Weeks = 1000.0

        // when
        val result = WowRule.isTriggered(savedAmount, avgLast4Weeks)

        // then
        assertFalse("零节省金额不应触发", result)
    }

    @Test
    fun `buildInsight - 应返回正确的洞察类型`() {
        // when
        val insight = WowRule.buildInsight(200.0)

        // then
        assertEquals(InsightType.WOW_MOMENT, insight.type)
        assertTrue(insight.message.isNotEmpty())
        assertEquals(200.0, insight.metadata["savedAmount"])
    }
}
