package com.wealth.manager.ui

import com.wealth.manager.data.entity.CategoryEntity
import com.wealth.manager.data.entity.ExpenseEntity
import com.wealth.manager.data.repository.CategoryRepository
import com.wealth.manager.data.repository.ExpenseRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AddExpenseViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: AddExpenseViewModel

    private val mockExpenseRepository: ExpenseRepository = mockk()
    private val mockCategoryRepository: CategoryRepository = mockk()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = AddExpenseViewModel(
            expenseRepository = mockExpenseRepository,
            categoryRepository = mockCategoryRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state - 金额为空，无分类`() {
        // when
        val amount = viewModel.amount.value
        val category = viewModel.selectedCategory.value
        val categories = viewModel.categories.value
        val isLoading = viewModel.isLoading.value
        val error = viewModel.error.value

        // then
        assertEquals("", amount)
        assertNull(category)
        assertTrue(categories.isEmpty())
        assertFalse(isLoading)
        assertNull(error)
    }

    @Test
    fun `loadCategories - 加载分类列表`() = runTest {
        // given
        val categories = listOf(
            CategoryEntity(id = 1, name = "餐饮", type = "EXPENSE", isDefault = true, icon = "🍽️"),
            CategoryEntity(id = 2, name = "交通", type = "EXPENSE", isDefault = true, icon = "🚗")
        )

        coEvery { mockCategoryRepository.getCategoriesByType("EXPENSE") } returns flowOf(categories)

        // when
        viewModel.loadCategories()
        testDispatcher.scheduler.advanceUntilIdle()

        // then
        coVerify { mockCategoryRepository.getCategoriesByType("EXPENSE") }
        val result = viewModel.categories.value
        assertEquals(2, result.size)
    }

    @Test
    fun `setAmount - 设置金额`() {
        // when
        viewModel.setAmount("100.5")

        // then
        assertEquals("100.5", viewModel.amount.value)
    }

    @Test
    fun `setAmount - 清除金额`() {
        // given
        viewModel.setAmount("100.5")

        // when
        viewModel.setAmount("")

        // then
        assertEquals("", viewModel.amount.value)
    }

    @Test
    fun `selectCategory - 选择分类`() {
        // given
        val category = CategoryEntity(id = 1, name = "餐饮", type = "EXPENSE")

        // when
        viewModel.selectCategory(category)

        // then
        val selected = viewModel.selectedCategory.value
        assertEquals(category.id, selected?.id)
        assertEquals(category.name, selected?.name)
    }

    @Test
    fun `clearCategory - 清除分类选择`() {
        // given
        val category = CategoryEntity(id = 1, name = "餐饮", type = "EXPENSE")
        viewModel.selectCategory(category)

        // when
        viewModel.clearCategory()

        // then
        assertNull(viewModel.selectedCategory.value)
    }

    @Test
    fun `isValid - 金额为空返回 false`() {
        // given
        viewModel.setAmount("")

        // when
        val isValid = viewModel.isValid()

        // then
        assertFalse(isValid)
    }

    @Test
    fun `isValid - 金额为零返回 false`() {
        // given
        viewModel.setAmount("0")

        // when
        val isValid = viewModel.isValid()

        // then
        assertFalse(isValid)
    }

    @Test
    fun `isValid - 负数金额返回 false`() {
        // given
        viewModel.setAmount("-100")

        // when
        val isValid = viewModel.isValid()

        // then
        assertFalse(isValid)
    }

    @Test
    fun `isValid - 有效金额无分类返回 true`() {
        // given
        viewModel.setAmount("100.5")

        // when
        val isValid = viewModel.isValid()

        // then
        assertTrue(isValid)
    }

    @Test
    fun `isValid - 有效金额有分类返回 true`() {
        // given
        viewModel.setAmount("100.5")
        viewModel.selectCategory(CategoryEntity(id = 1, name = "餐饮", type = "EXPENSE"))

        // when
        val isValid = viewModel.isValid()

        // then
        assertTrue(isValid)
    }

    @Test
    fun `saveExpense - 保存支出`() = runTest {
        // given
        viewModel.setAmount("100.5")
        val category = CategoryEntity(id = 1, name = "餐饮", type = "EXPENSE")
        viewModel.selectCategory(category)

        coEvery { mockExpenseRepository.insertExpense(any()) } returns 1L

        // when
        viewModel.saveExpense()
        testDispatcher.scheduler.advanceUntilIdle()

        // then
        coVerify { mockExpenseRepository.insertExpense(any()) }
    }

    @Test
    fun `saveExpense - 金额无效时抛出错误`() = runTest {
        // given
        viewModel.setAmount("") // 无效金额

        // when
        viewModel.saveExpense()
        testDispatcher.scheduler.advanceUntilIdle()

        // then - 应该捕获到异常
        assertTrue(viewModel.error.value?.contains("金额") == true)
    }

    @Test
    fun `saveExpense - 金额为零时抛出错误`() = runTest {
        // given
        viewModel.setAmount("0")

        // when
        viewModel.saveExpense()
        testDispatcher.scheduler.advanceUntilIdle()

        // then
        assertTrue(viewModel.error.value?.contains("金额") == true)
    }

    @Test
    fun `reset - 重置所有字段`() {
        // given
        viewModel.setAmount("100.5")
        viewModel.selectCategory(CategoryEntity(id = 1, name = "餐饮", type = "EXPENSE"))
        viewModel.showSuccessMessage()

        // when
        viewModel.reset()

        // then
        assertEquals("", viewModel.amount.value)
        assertNull(viewModel.selectedCategory.value)
        assertFalse(viewModel.showSuccess.value)
    }

    @Test
    fun `showSuccessMessage - 显示成功消息`() {
        // when
        viewModel.showSuccessMessage()

        // then
        assertTrue(viewModel.showSuccess.value)
    }

    @Test
    fun `clearError - 清除错误`() {
        // given
        viewModel.setError("测试错误")

        // when
        viewModel.clearError()

        // then
        assertNull(viewModel.error.value)
    }

    @Test
    fun `setError - 设置错误信息`() {
        // when
        viewModel.setError("保存失败")

        // then
        assertEquals("保存失败", viewModel.error.value)
    }

    @Test
    fun `getCategoryById - 按 ID 获取分类`() {
        // given
        val categories = listOf(
            CategoryEntity(id = 1, name = "餐饮", type = "EXPENSE"),
            CategoryEntity(id = 2, name = "交通", type = "EXPENSE")
        )

        // when
        val category = viewModel.getCategoryById(categories, 2)

        // then
        assertEquals("交通", category?.name)
    }

    @Test
    fun `getCategoryById - 不存在的 ID 返回 null`() {
        // given
        val categories = listOf(
            CategoryEntity(id = 1, name = "餐饮", type = "EXPENSE")
        )

        // when
        val category = viewModel.getCategoryById(categories, 999)

        // then
        assertNull(category)
    }

    @Test
    fun `buildExpense - 构建支出实体`() {
        // given
        val amount = "150.75"
        val category = CategoryEntity(id = 3, name = "购物", type = "EXPENSE")
        val note = "购买衣服"
        val timestamp = 1744329600000L // 2026-04-10

        // when
        val expense = viewModel.buildExpense(amount, category, note, timestamp)

        // then
        assertEquals(150.75, expense.amount, 0.001)
        assertEquals(3, expense.categoryId)
        assertEquals("购买衣服", expense.note)
        assertEquals(timestamp, expense.date)
    }

    @Test
    fun `buildExpense - 金额转换错误返回 null`() {
        // given
        val invalidAmount = "invalid"
        val category = CategoryEntity(id = 1, name = "餐饮", type = "EXPENSE")

        // when
        val expense = viewModel.buildExpense(invalidAmount, category, "test", System.currentTimeMillis())

        // then
        assertNull(expense)
    }

    @Test
    fun `parseAmount - 转换金额`() {
        // when
        val result = viewModel.parseAmount("99.99")

        // then
        assertEquals(99.99, result, 0.001)
    }

    @Test
    fun `parseAmount - 无效金额返回 0`() {
        // when
        val result = viewModel.parseAmount("invalid")

        // then
        assertEquals(0.0, result, 0.001)
    }
}
