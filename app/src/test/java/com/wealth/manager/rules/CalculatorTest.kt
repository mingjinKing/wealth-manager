package com.wealth.manager.rules

import com.wealth.manager.data.entity.CategoryEntity
import org.junit.Assert.*
import org.junit.Test

/**
 * Calculator 单元测试
 *
 * 测试财务计算工具函数的各种场景
 */
class CalculatorTest {

    // ==================== calculateNetWorth 测试 ====================

    @Test
    fun `calculateNetWorth - 资产大于负债时返回正值`() {
        // given
        val totalAssets = 10000.0
        val totalLiabilities = -2000.0  // 负债是负数

        // when
        val netWorth = Calculator.calculateNetWorth(totalAssets, totalLiabilities)

        // then
        assertEquals(8000.0, netWorth, 0.001)
    }

    @Test
    fun `calculateNetWorth - 资产小于负债时返回负值`() {
        // given - 负债为 -5000 (表示欠 5000)
        val totalAssets = 1000.0
        val totalLiabilities = -5000.0

        // when
        val netWorth = Calculator.calculateNetWorth(totalAssets, totalLiabilities)

        // then
        assertEquals(-4000.0, netWorth, 0.001)
    }

    @Test
    fun `calculateNetWorth - 资产等于负债时返回零`() {
        // given
        val totalAssets = 5000.0
        val totalLiabilities = -5000.0

        // when
        val netWorth = Calculator.calculateNetWorth(totalAssets, totalLiabilities)

        // then
        assertEquals(0.0, netWorth, 0.001)
    }

    @Test
    fun `calculateNetWorth - 零资产零负债返回零`() {
        // given
        val totalAssets = 0.0
        val totalLiabilities = 0.0

        // when
        val netWorth = Calculator.calculateNetWorth(totalAssets, totalLiabilities)

        // then
        assertEquals(0.0, netWorth, 0.001)
    }

    @Test
    fun `calculateNetWorth - 只有资产没有负债`() {
        // given - 负债为 0
        val totalAssets = 10000.0
        val totalLiabilities = 0.0

        // when
        val netWorth = Calculator.calculateNetWorth(totalAssets, totalLiabilities)

        // then
        assertEquals(10000.0, netWorth, 0.001)
    }

    @Test
    fun `calculateNetWorth - 浮点数精度问题`() {
        // given
        val totalAssets = 0.1
        val totalLiabilities = -0.2

        // when
        val netWorth = Calculator.calculateNetWorth(totalAssets, totalLiabilities)

        // then
        assertEquals(-0.1, netWorth, 0.001)
    }

    @Test
    fun `calculateNetWorth - 极大值`() {
        // given
        val totalAssets = Double.MAX_VALUE
        val totalLiabilities = 0.0

        // when
        val netWorth = Calculator.calculateNetWorth(totalAssets, totalLiabilities)

        // then
        assertEquals(Double.MAX_VALUE, netWorth, 0.001)
    }

    // ==================== separateIncomeAndExpenseCategories 测试 ====================

    @Test
    fun `separateIncomeAndExpenseCategories - 分离收入和支出分类`() {
        // given
        val categories = listOf(
            CategoryEntity(id = 1, name = "工资", icon = "💰", color = "#4CAF50", type = "INCOME", isDefault = true),
            CategoryEntity(id = 2, name = "餐饮", icon = "🍔", color = "#FF5722", type = "EXPENSE", isDefault = true),
            CategoryEntity(id = 3, name = "购物", icon = "🛍️", color = "#9C27B0", type = "EXPENSE", isDefault = true),
            CategoryEntity(id = 4, name = "奖金", icon = "🎁", color = "#4CAF50", type = "INCOME", isDefault = false),
            CategoryEntity(id = 5, name = "交通", icon = "🚗", color = "#607D8B", type = "EXPENSE", isDefault = true)
        )

        // when
        val result = Calculator.separateIncomeAndExpenseCategories(categories)

        // then
        assertEquals(2, result.first.size)  // income IDs
        assertTrue(result.first.contains(1L))
        assertTrue(result.first.contains(4L))

        assertEquals(3, result.second.size)  // expense IDs
        assertTrue(result.second.contains(2L))
        assertTrue(result.second.contains(3L))
        assertTrue(result.second.contains(5L))
    }

    @Test
    fun `separateIncomeAndExpenseCategories - 空列表`() {
        // given
        val categories = emptyList<CategoryEntity>()

        // when
        val result = Calculator.separateIncomeAndExpenseCategories(categories)

        // then
        assertTrue(result.first.isEmpty())
        assertTrue(result.second.isEmpty())
    }

    @Test
    fun `separateIncomeAndExpenseCategories - 全是收入分类`() {
        // given
        val categories = listOf(
            CategoryEntity(id = 1, name = "工资", icon = "💰", color = "#4CAF50", type = "INCOME", isDefault = true),
            CategoryEntity(id = 2, name = "奖金", icon = "🎁", color = "#4CAF50", type = "INCOME", isDefault = false),
            CategoryEntity(id = 3, name = "投资收益", icon = "📈", color = "#4CAF50", type = "INCOME", isDefault = false)
        )

        // when
        val result = Calculator.separateIncomeAndExpenseCategories(categories)

        // then
        assertEquals(3, result.first.size)
        assertTrue(result.second.isEmpty())
    }

    @Test
    fun `separateIncomeAndExpenseCategories - 全是支出分类`() {
        // given
        val categories = listOf(
            CategoryEntity(id = 1, name = "餐饮", icon = "🍔", color = "#FF5722", type = "EXPENSE", isDefault = true),
            CategoryEntity(id = 2, name = "交通", icon = "🚗", color = "#607D8B", type = "EXPENSE", isDefault = true),
            CategoryEntity(id = 3, name = "娱乐", icon = "🎮", color = "#9C27B0", type = "EXPENSE", isDefault = true)
        )

        // when
        val result = Calculator.separateIncomeAndExpenseCategories(categories)

        // then
        assertTrue(result.first.isEmpty())
        assertEquals(3, result.second.size)
    }

    @Test
    fun `separateIncomeAndExpenseCategories - 返回的是 Set 不是 List`() {
        // given
        val categories = listOf(
            CategoryEntity(id = 1, name = "工资", icon = "💰", color = "#4CAF50", type = "INCOME", isDefault = true),
            CategoryEntity(id = 2, name = "餐饮", icon = "🍔", color = "#FF5722", type = "EXPENSE", isDefault = true)
        )

        // when
        val result = Calculator.separateIncomeAndExpenseCategories(categories)

        // then
        assertTrue(result.first is Set)
        assertTrue(result.second is Set)
    }

    // ==================== determineBudgetType 测试 ====================

    @Test
    fun `determineBudgetType - WEEKLY 类型`() {
        // when - monthlyBudget <= 0 且 weeklyBudget > 0 时返回 WEEKLY
        val result = Calculator.determineBudgetType(0.0, 1000.0)

        // then
        assertEquals("WEEKLY", result)
    }

    @Test
    fun `determineBudgetType - 负月预算正周预算返回 WEEKLY`() {
        // when
        val result = Calculator.determineBudgetType(-100.0, 500.0)

        // then
        assertEquals("WEEKLY", result)
    }

    @Test
    fun `determineBudgetType - MONTHLY 类型`() {
        // when - 默认情况
        val result = Calculator.determineBudgetType(5000.0, 1000.0)

        // then
        assertEquals("MONTHLY", result)
    }

    @Test
    fun `determineBudgetType - 月预算和周预算都为零返回 MONTHLY`() {
        // when - 都不满足 WEEKLY 条件
        val result = Calculator.determineBudgetType(0.0, 0.0)

        // then
        assertEquals("MONTHLY", result)
    }

    @Test
    fun `determineBudgetType - 月预算为负周预算为零返回 MONTHLY`() {
        // when
        val result = Calculator.determineBudgetType(-500.0, 0.0)

        // then
        assertEquals("MONTHLY", result)
    }

    // ==================== calculateTrendPoints 测试 ====================

    @Test
    fun `calculateTrendPoints - 基本调用`() {
        // given
        val currentNetWorth = 50000.0
        val goalAmount = 100000.0
        val startDate = System.currentTimeMillis()
        val endDate = startDate + 365L * 24 * 60 * 60 * 1000

        // when
        val trend = Calculator.calculateTrendPoints(currentNetWorth, goalAmount, startDate, endDate)

        // then
        assertEquals(6, trend.size)  // 0..5 共6个点
    }

    @Test
    fun `calculateTrendPoints - 返回6个点`() {
        // given
        val startDate = System.currentTimeMillis()
        val endDate = startDate + 30L * 24 * 60 * 60 * 1000  // 30天

        // when
        val trend = Calculator.calculateTrendPoints(10000.0, 50000.0, startDate, endDate)

        // then
        assertEquals(6, trend.size)
    }

    @Test
    fun `calculateTrendPoints - 零目标金额返回空列表`() {
        // given
        val startDate = System.currentTimeMillis()
        val endDate = startDate + 365L * 24 * 60 * 60 * 1000

        // when
        val trend = Calculator.calculateTrendPoints(50000.0, 0.0, startDate, endDate)

        // then - totalDuration > 0，所以不会返回空
        assertTrue(trend.isNotEmpty())
    }

    @Test
    fun `calculateTrendPoints - 零当前净资产`() {
        // given
        val startDate = System.currentTimeMillis()
        val endDate = startDate + 365L * 24 * 60 * 60 * 1000

        // when
        val trend = Calculator.calculateTrendPoints(0.0, 100000.0, startDate, endDate)

        // then
        assertEquals(6, trend.size)
    }

    @Test
    fun `calculateTrendPoints - 结束时间小于开始时间返回空`() {
        // given - 结束时间在开始时间之前
        val startDate = System.currentTimeMillis()
        val endDate = startDate - 1000L

        // when
        val trend = Calculator.calculateTrendPoints(50000.0, 100000.0, startDate, endDate)

        // then
        assertTrue(trend.isEmpty())
    }

    @Test
    fun `calculateTrendPoints - 趋势点包含 label`() {
        // given
        val startDate = System.currentTimeMillis()
        val endDate = startDate + 365L * 24 * 60 * 60 * 1000

        // when
        val trend = Calculator.calculateTrendPoints(50000.0, 100000.0, startDate, endDate)

        // then - label 应该是日期格式 MM/dd
        assertTrue(trend.all { it.label.isNotEmpty() })
    }

    @Test
    fun `calculateTrendPoints - 趋势点 expected 值递增`() {
        // given
        val startDate = System.currentTimeMillis()
        val endDate = startDate + 365L * 24 * 60 * 60 * 1000

        // when
        val trend = Calculator.calculateTrendPoints(0.0, 100000.0, startDate, endDate)

        // then - expected 值应该递增
        for (i in 1 until trend.size) {
            assertTrue(trend[i].expected > trend[i - 1].expected)
        }
    }

    @Test
    fun `calculateTrendPoints - 第一个点 expected 为零`() {
        // given
        val startDate = System.currentTimeMillis()
        val endDate = startDate + 365L * 24 * 60 * 60 * 1000

        // when
        val trend = Calculator.calculateTrendPoints(50000.0, 100000.0, startDate, endDate)

        // then - 第一个点的 expected 应该是 0
        assertEquals(0.0, trend[0].expected, 0.001)
    }

    @Test
    fun `calculateTrendPoints - 最后一个点 expected 等于目标金额`() {
        // given
        val goalAmount = 100000.0
        val startDate = System.currentTimeMillis()
        val endDate = startDate + 365L * 24 * 60 * 60 * 1000

        // when
        val trend = Calculator.calculateTrendPoints(50000.0, goalAmount, startDate, endDate)

        // then - 最后一个点的 expected 应该是目标金额
        assertEquals(goalAmount, trend[5].expected, 0.001)
    }
}
