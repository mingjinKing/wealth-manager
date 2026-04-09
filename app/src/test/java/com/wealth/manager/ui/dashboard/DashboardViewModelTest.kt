package com.wealth.manager.ui.dashboard

import com.wealth.manager.rules.Calculator
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DashboardViewModelTest {

    @Before
    fun setup() {
        mockkObject(Calculator)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `Calculator - 计算净资产`() {
        // given
        val totalAssets = 15000.0
        val totalLiabilities = -3000.0

        // when
        val netWorth = Calculator.calculateNetWorth(totalAssets, totalLiabilities)

        // then
        assertEquals(12000.0, netWorth, 0.001)
    }

    @Test
    fun `Calculator - 区分收入和支出分类`() {
        // given
        val categories = listOf(
            com.wealth.manager.data.entity.CategoryEntity(id = 1, name = "工资", type = "INCOME", isDefault = true, icon = "💰", color = "#4CAF50"),
            com.wealth.manager.data.entity.CategoryEntity(id = 2, name = "奖金", type = "INCOME", isDefault = true, icon = "🎁", color = "#FF9800"),
            com.wealth.manager.data.entity.CategoryEntity(id = 3, name = "餐饮", type = "EXPENSE", isDefault = true, icon = "🍽️", color = "#F44336"),
            com.wealth.manager.data.entity.CategoryEntity(id = 4, name = "交通", type = "EXPENSE", isDefault = true, icon = "🚗", color = "#2196F3")
        )

        // when
        val (incomeIds, expenseIds) = Calculator.separateIncomeAndExpenseCategories(categories)

        // then
        assertEquals(2, incomeIds.size)
        assertEquals(2, expenseIds.size)
        assertTrue(incomeIds.contains(1))
        assertTrue(incomeIds.contains(2))
        assertTrue(expenseIds.contains(3))
        assertTrue(expenseIds.contains(4))
    }

    @Test
    fun `Calculator - 计算预算类型`() {
        // given
        val monthlyBudget = 2000.0
        val weeklyBudget = 500.0

        // when
        val budgetType = Calculator.determineBudgetType(monthlyBudget, weeklyBudget)

        // then
        assertEquals("MONTHLY", budgetType)
    }

    @Test
    fun `Calculator - 计算趋势点`() {
        // given
        val currentNetWorth = 10000.0
        val goalAmount = 20000.0
        val startDate = System.currentTimeMillis()
        val endDate = startDate + 30L * 24 * 60 * 60 * 1000 // 30天后

        // when
        val trendPoints = Calculator.calculateTrendPoints(currentNetWorth, goalAmount, startDate, endDate)

        // then
        assertTrue(trendPoints.isNotEmpty())
    }
}
