package com.wealth.manager.data.entity

import org.junit.Assert.*
import org.junit.Test

/**
 * ExpenseEntity 单元测试
 *
 * 测试消费记录实体的创建、属性和业务逻辑
 */
class ExpenseEntityTest {

    @Test
    fun `ExpenseEntity - 基本创建`() {
        // given
        val now = System.currentTimeMillis()

        // when
        val expense = ExpenseEntity(
            amount = 128.5,
            categoryId = 1L,
            date = now
        )

        // then
        assertEquals(0L, expense.id)  // autoGenerate
        assertEquals(128.5, expense.amount, 0.001)
        assertEquals(1L, expense.categoryId)
        assertEquals(now, expense.date)
        assertEquals("", expense.note)
        assertNull(expense.assetId)
        assertTrue(expense.createdAt > 0)
    }

    @Test
    fun `ExpenseEntity - 带备注`() {
        // when
        val expense = ExpenseEntity(
            amount = 42.0,
            categoryId = 1L,
            date = System.currentTimeMillis(),
            note = "午餐"
        )

        // then
        assertEquals("午餐", expense.note)
    }

    @Test
    fun `ExpenseEntity - 关联资产`() {
        // when
        val expense = ExpenseEntity(
            amount = 1000.0,
            categoryId = 2L,
            date = System.currentTimeMillis(),
            assetId = 5L
        )

        // then
        assertEquals(5L, expense.assetId)
    }

    @Test
    fun `ExpenseEntity - 金额为正数`() {
        // when
        val expense = ExpenseEntity(
            amount = 999999.99,
            categoryId = 1L,
            date = System.currentTimeMillis()
        )

        // then
        assertEquals(999999.99, expense.amount, 0.001)
    }

    @Test
    fun `ExpenseEntity - 金额可以很小`() {
        // when
        val expense = ExpenseEntity(
            amount = 0.01,
            categoryId = 1L,
            date = System.currentTimeMillis()
        )

        // then
        assertEquals(0.01, expense.amount, 0.001)
    }

    @Test
    fun `ExpenseEntity - 创建时间默认`() {
        // when
        val before = System.currentTimeMillis()
        val expense = ExpenseEntity(
            amount = 10.0,
            categoryId = 1L,
            date = System.currentTimeMillis()
        )
        val after = System.currentTimeMillis()

        // then
        assertTrue(expense.createdAt >= before)
        assertTrue(expense.createdAt <= after)
    }

    @Test
    fun `ExpenseEntity - ForeignKey 关系验证`() {
        // given - 模拟外键关系
        val categoryId = 1L
        val assetId: Long? = null

        // when
        val expense = ExpenseEntity(
            amount = 50.0,
            categoryId = categoryId,
            date = System.currentTimeMillis(),
            assetId = assetId
        )

        // then - 验证外键字段
        assertEquals(categoryId, expense.categoryId)
        assertNull(expense.assetId)  // 没有关联资产
    }
}
