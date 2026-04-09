package com.wealth.manager.data.entity

import org.junit.Assert.*
import org.junit.Test

/**
 * CategoryEntity 单元测试
 *
 * 测试分类实体的创建和属性
 */
class CategoryEntityTest {

    @Test
    fun `CategoryEntity - 默认值测试`() {
        // when
        val category = CategoryEntity(
            name = "餐饮",
            icon = "🍗",
            color = "#ffc880"
        )

        // then
        assertEquals(0L, category.id)  // 默认 autoGenerate = true，id 为 0
        assertEquals("餐饮", category.name)
        assertEquals("🍗", category.icon)
        assertEquals("#ffc880", category.color)
        assertEquals("EXPENSE", category.type)  // 默认值
        assertTrue(category.isDefault)  // 默认值
    }

    @Test
    fun `CategoryEntity - 收入分类`() {
        // when
        val category = CategoryEntity(
            name = "工资",
            icon = "💰",
            color = "#4CAF50",
            type = "INCOME",
            isDefault = true
        )

        // then
        assertEquals("INCOME", category.type)
        assertTrue(category.isDefault)
    }

    @Test
    fun `CategoryEntity - 自定义分类`() {
        // when
        val category = CategoryEntity(
            id = 100L,
            name = "我的分类",
            icon = "⭐",
            color = "#FF5722",
            type = "EXPENSE",
            isDefault = false
        )

        // then
        assertEquals(100L, category.id)
        assertEquals("我的分类", category.name)
        assertEquals("⭐", category.icon)
        assertEquals("#FF5722", category.color)
        assertEquals("EXPENSE", category.type)
        assertFalse(category.isDefault)
    }

    @Test
    fun `CategoryEntity - type 只能是 EXPENSE 或 INCOME`() {
        // given
        val expenseCategory = CategoryEntity(
            name = "支出",
            icon = "💸",
            color = "#000",
            type = "EXPENSE"
        )

        val incomeCategory = CategoryEntity(
            name = "收入",
            icon = "💵",
            color = "#000",
            type = "INCOME"
        )

        // then
        assertEquals("EXPENSE", expenseCategory.type)
        assertEquals("INCOME", incomeCategory.type)
    }
}
