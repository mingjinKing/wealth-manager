package com.wealth.manager.rules

import com.wealth.manager.data.entity.CategoryEntity
import org.junit.Assert.*
import org.junit.Test

/**
 * Calculator 单元测试
 *
 * 测试计算工具函数的各种场景
 */
class CalculatorTest {

    @Test
    fun `calculateNetWorth - 正资产和负负债应相加`() {
        // given
        val totalAssets = 100000.0
        val totalLiabilities = -30000.0

        // when
        val result = Calculator.calculateNetWorth(totalAssets, totalLiabilities)

        // then
        assertEquals(70000.0, result, 0.01)
    }

    @Test
    fun `calculateNetWorth - 只有资产`() {
        // given
        val totalAssets = 50000.0
        val totalLiabilities = 0.0

        // when
        val result = Calculator.calculateNetWorth(totalAssets, totalLiabilities)

        // then
        assertEquals(50000.0, result, 0.01)
    }

    @Test
    fun `calculateNetWorth - 资不抵债`() {
        // given
        val totalAssets = 20000.0
        val totalLiabilities = -50000.0

        // when
        val result = Calculator.calculateNetWorth(totalAssets, totalLiabilities)

        // then
        assertEquals(-30000.0, result, 0.01)
    }

    @Test
    fun `separateIncomeAndExpenseCategories - 应正确区分收入和支出分类`() {
        // given
        val categories = listOf(
            CategoryEntity(id = 1, name = "工资", icon = "💰", color = "", type = "INCOME", isDefault = true),
            CategoryEntity(id = 2, name = "奖金", icon = "🧧", color = "", type = "INCOME", isDefault = true),
            CategoryEntity(id = 3, name = "餐饮", icon = "🍗", color = "", type = "EXPENSE", isDefault = true),
            CategoryEntity(id = 4, name = "购物", icon = "🛒", color = "", type = "EXPENSE", isDefault = true)
        )

        // when
        val (incomeIds, expenseIds) = Calculator.separateIncomeAndExpenseCategories(categories)

        // then
        assertEquals(2, incomeIds.size)
        assertTrue(incomeIds.contains(1))
        assertTrue(incomeIds.contains(2))

        assertEquals(2, expenseIds.size)
        assertTrue(expenseIds.contains(3))
        assertTrue(expenseIds.contains(4))
    }

    @Test
    fun `determineBudgetType - 有月预算时应返回 MONTHLY`() {
        // given
        val monthlyBudget = 5000.0
        val weeklyBudget = 0.0

        // when
        val result = Calculator.determineBudgetType(monthlyBudget, weeklyBudget)

        // then
        assertEquals("MONTHLY", result)
    }

    @Test
    fun `determineBudgetType - 只有周预算时应返回 WEEKLY`() {
        // given
        val monthlyBudget = 0.0
        val weeklyBudget = 1000.0

        // when
        val result = Calculator.determineBudgetType(monthlyBudget, weeklyBudget)

        // then
        assertEquals("WEEKLY", result)
    }

    @Test
    fun `determineBudgetType - 两者都有时优先 MONTHLY`() {
        // given
        val monthlyBudget = 5000.0
        val weeklyBudget = 1000.0

        // when
        val result = Calculator.determineBudgetType(monthlyBudget, weeklyBudget)

        // then
        assertEquals("MONTHLY", result)
    }

    @Test
    fun `determineBudgetType - 两者都无时默认 MONTHLY`() {
        // given
        val monthlyBudget = 0.0
        val weeklyBudget = 0.0

        // when
        val result = Calculator.determineBudgetType(monthlyBudget, weeklyBudget)

        // then
        assertEquals("MONTHLY", result)
    }
}
