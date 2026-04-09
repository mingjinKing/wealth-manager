package com.wealth.manager.rules

import org.junit.Assert.*
import org.junit.Test

/**
 * StructureRule 单元测试
 *
 * 测试结构分析规则的各种场景
 */
class StructureRuleTest {

    // ==================== isBiased 测试 ====================

    @Test
    fun `isBiased - 超过阈值返回 true`() {
        // given - 阈值是 0.4 (40%)
        val topPercentage = 0.5f

        // when
        val result = StructureRule.isBiased(topPercentage)

        // then
        assertTrue("0.5 > 0.4，应返回 true", result)
    }

    @Test
    fun `isBiased - 刚好等于阈值返回 false`() {
        // given - 使用 > 而非 >=
        val topPercentage = 0.4f

        // when
        val result = StructureRule.isBiased(topPercentage)

        // then
        assertFalse("0.4 不大于 0.4，应返回 false", result)
    }

    @Test
    fun `isBiased - 略低于阈值返回 false`() {
        // given
        val topPercentage = 0.39f

        // when
        val result = StructureRule.isBiased(topPercentage)

        // then
        assertFalse("0.39 < 0.4，应返回 false", result)
    }

    @Test
    fun `isBiased - 零返回 false`() {
        // given
        val topPercentage = 0.0f

        // when
        val result = StructureRule.isBiased(topPercentage)

        // then
        assertFalse("0 不是偏向", result)
    }

    @Test
    fun `isBiased - 负值返回 false`() {
        // given - 不正常情况
        val topPercentage = -0.1f

        // when
        val result = StructureRule.isBiased(topPercentage)

        // then
        assertFalse("负值不是偏向", result)
    }

    @Test
    fun `isBiased - 100%返回 true`() {
        // given
        val topPercentage = 1.0f

        // when
        val result = StructureRule.isBiased(topPercentage)

        // then
        assertTrue("1.0 > 0.4，应返回 true", result)
    }

    @Test
    fun `isBiased - 超过100%返回 true`() {
        // given - 不正常情况
        val topPercentage = 1.5f

        // when
        val result = StructureRule.isBiased(topPercentage)

        // then
        assertTrue(result)
    }

    // ==================== buildInsight 测试 ====================

    @Test
    fun `buildInsight - 返回正确类型`() {
        // when
        val insight = StructureRule.buildInsight("餐饮", 0.6f)

        // then
        assertEquals(InsightType.STRUCTURE_BIAS, insight.type)
    }

    @Test
    fun `buildInsight - 消息包含分类名称`() {
        // given
        val categoryName = "餐饮"

        // when
        val insight = StructureRule.buildInsight(categoryName, 0.6f)

        // then
        assertTrue("消息应包含分类名称", insight.message.contains("餐饮"))
    }

    @Test
    fun `buildInsight - 消息包含百分比`() {
        // given
        val topPercentage = 0.65f

        // when
        val insight = StructureRule.buildInsight("购物", topPercentage)

        // then
        assertTrue(
            "消息应包含百分比",
            insight.message.contains("65") || insight.message.contains("65%")
        )
    }

    @Test
    fun `buildInsight - 元数据包含分类名和百分比`() {
        // given
        val categoryName = "交通"
        val topPercentage = 0.55f

        // when
        val insight = StructureRule.buildInsight(categoryName, topPercentage)

        // then
        assertEquals("交通", insight.metadata["categoryName"])
        assertEquals(0.55f, insight.metadata["percentage"])
    }

    @Test
    fun `buildInsight - 消息包含优化建议`() {
        // when
        val insight = StructureRule.buildInsight("娱乐", 0.7f)

        // then
        assertTrue(
            "消息应包含建议",
            insight.message.contains("优化") ||
            insight.message.contains("建议") ||
            insight.message.contains("空间")
        )
    }

    @Test
    fun `buildInsight - 不同分类生成不同消息`() {
        // when
        val insight1 = StructureRule.buildInsight("餐饮", 0.5f)
        val insight2 = StructureRule.buildInsight("购物", 0.5f)

        // then
        assertNotEquals(insight1.message, insight2.message)
    }

    @Test
    fun `buildInsight - 不同百分比生成不同元数据`() {
        // when
        val insight1 = StructureRule.buildInsight("餐饮", 0.4f)
        val insight2 = StructureRule.buildInsight("餐饮", 0.8f)

        // then
        assertEquals(0.4f, insight1.metadata["percentage"])
        assertEquals(0.8f, insight2.metadata["percentage"])
        assertNotEquals(
            insight1.metadata["percentage"],
            insight2.metadata["percentage"]
        )
    }

    // ==================== 百分比格式测试 ====================

    @Test
    fun `buildInsight - 百分比显示正确`() {
        // given - 65%
        val topPercentage = 0.65f

        // when
        val insight = StructureRule.buildInsight("餐饮", topPercentage)

        // then
        assertTrue(insight.message.contains("65"))
    }

    @Test
    fun `buildInsight - 百分比取整`() {
        // given - 66.6%
        val topPercentage = 0.666f

        // when
        val insight = StructureRule.buildInsight("餐饮", topPercentage)

        // then - 应该是 66% 而不是 66.6%
        assertTrue(insight.message.contains("66"))
    }

    @Test
    fun `buildInsight - 低于阈值仍生成洞察但不限偏向`() {
        // given - 39% 低于 40% 阈值
        val topPercentage = 0.39f

        // when - 注意：这个方法不检查阈值，直接生成洞察
        val insight = StructureRule.buildInsight("餐饮", topPercentage)

        // then
        assertEquals(InsightType.STRUCTURE_BIAS, insight.type)
        // 业务上可能需要在调用前先用 isBiased 检查
    }

    // ==================== 边界条件测试 ====================

    @Test
    fun `isBiased - 临界值 041`() {
        assertTrue(StructureRule.isBiased(0.41f))
    }

    @Test
    fun `isBiased - 临界值 040`() {
        assertFalse(StructureRule.isBiased(0.40f))
    }

    @Test
    fun `isBiased - 临界值 039`() {
        assertFalse(StructureRule.isBiased(0.39f))
    }

    @Test
    fun `isBiased - 临界值 0401`() {
        assertTrue(StructureRule.isBiased(0.401f))
    }

    @Test
    fun `buildInsight - 临界阈值 41%`() {
        // when
        val insight = StructureRule.buildInsight("餐饮", 0.41f)

        // then
        assertTrue(insight.message.contains("41"))
    }
}
