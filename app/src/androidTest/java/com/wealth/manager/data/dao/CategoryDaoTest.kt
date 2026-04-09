package com.wealth.manager.data.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wealth.manager.data.AppDatabase
import com.wealth.manager.data.entity.CategoryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * CategoryDao 集成测试
 *
 * 使用 Room in-memory 数据库进行 DAO 层测试
 */
@RunWith(AndroidJUnit4::class)
class CategoryDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var categoryDao: CategoryDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).build()
        categoryDao = database.categoryDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ==================== Insert 测试 ====================

    @Test
    fun `insertCategory - 基本插入返回 id`() = runBlocking {
        // given
        val category = createCategoryEntity(name = "餐饮")

        // when
        val id = categoryDao.insertCategory(category)

        // then
        assertTrue(id > 0)
    }

    @Test
    fun `insertCategory - 收入分类插入成功`() = runBlocking {
        // given
        val category = createCategoryEntity(name = "工资", type = "INCOME")

        // when
        val id = categoryDao.insertCategory(category)
        val result = categoryDao.getCategoryById(id)

        // then
        assertNotNull(result)
        assertEquals("INCOME", result!!.type)
    }

    @Test
    fun `insertCategory - 支出分类插入成功`() = runBlocking {
        // given
        val category = createCategoryEntity(name = "购物", type = "EXPENSE")

        // when
        val id = categoryDao.insertCategory(category)
        val result = categoryDao.getCategoryById(id)

        // then
        assertNotNull(result)
        assertEquals("EXPENSE", result!!.type)
    }

    @Test
    fun `insertCategories - 批量插入`() = runBlocking {
        // given
        val categories = listOf(
            createCategoryEntity(name = "餐饮", type = "EXPENSE"),
            createCategoryEntity(name = "交通", type = "EXPENSE"),
            createCategoryEntity(name = "工资", type = "INCOME")
        )

        // when
        categoryDao.insertCategories(categories)

        // then
        val all = categoryDao.getAllCategories().first()
        assertEquals(3, all.size)
    }

    // ==================== Query 测试 ====================

    @Test
    fun `getAllCategories - 空数据库返回空列表`() = runBlocking {
        // when
        val categories = categoryDao.getAllCategories().first()

        // then
        assertTrue(categories.isEmpty())
    }

    @Test
    fun `getAllCategories - 有数据时返回所有分类`() = runBlocking {
        // given
        insertTestCategories(5)

        // when
        val categories = categoryDao.getAllCategories().first()

        // then
        assertEquals(5, categories.size)
    }

    @Test
    fun `getCategoryById - 存在返回记录`() = runBlocking {
        // given
        val category = createCategoryEntity(name = "测试分类")
        val id = categoryDao.insertCategory(category)

        // when
        val result = categoryDao.getCategoryById(id)

        // then
        assertNotNull(result)
        assertEquals("测试分类", result!!.name)
    }

    @Test
    fun `getCategoryById - 不存在返回 null`() = runBlocking {
        // when
        val result = categoryDao.getCategoryById(9999)

        // then
        assertNull(result)
    }

    @Test
    fun `getCategoriesByType - 按类型查询`() = runBlocking {
        // given
        categoryDao.insertCategory(createCategoryEntity(name = "工资", type = "INCOME"))
        categoryDao.insertCategory(createCategoryEntity(name = "奖金", type = "INCOME"))
        categoryDao.insertCategory(createCategoryEntity(name = "餐饮", type = "EXPENSE"))

        // when
        val incomeCategories = categoryDao.getCategoriesByType("INCOME").first()
        val expenseCategories = categoryDao.getCategoriesByType("EXPENSE").first()

        // then
        assertEquals(2, incomeCategories.size)
        assertEquals(1, expenseCategories.size)
    }

    @Test
    fun `getCategoriesByType - 按类型查询 EXPENSE`() = runBlocking {
        // given
        categoryDao.insertCategory(createCategoryEntity(name = "工资", type = "INCOME"))
        categoryDao.insertCategory(createCategoryEntity(name = "奖金", type = "INCOME"))
        categoryDao.insertCategory(createCategoryEntity(name = "餐饮", type = "EXPENSE"))

        // when
        val expenseCategories = categoryDao.getCategoriesByType("EXPENSE").first()

        // then
        assertEquals(1, expenseCategories.size)
        assertEquals("餐饮", expenseCategories.first().name)
    }

    // ==================== Update 测试 ====================

    @Test
    fun `updateCategory - 更新分类名称`() = runBlocking {
        // given
        val category = createCategoryEntity(name = "旧名称")
        val id = categoryDao.insertCategory(category)
        val original = categoryDao.getCategoryById(id)!!

        // when
        val updated = original.copy(name = "新名称")
        categoryDao.updateCategory(updated)

        // then
        val result = categoryDao.getCategoryById(id)
        assertEquals("新名称", result!!.name)
    }

    @Test
    fun `updateCategory - 更新分类类型`() = runBlocking {
        // given
        val category = createCategoryEntity(name = "测试", type = "EXPENSE")
        val id = categoryDao.insertCategory(category)
        val original = categoryDao.getCategoryById(id)!!

        // when
        val updated = original.copy(type = "INCOME")
        categoryDao.updateCategory(updated)

        // then
        val result = categoryDao.getCategoryById(id)
        assertEquals("INCOME", result!!.type)
    }

    // ==================== Delete 测试 ====================

    @Test
    fun `deleteCategoryById - 按 ID 删除`() = runBlocking {
        // given
        val category = createCategoryEntity(name = "待删除")
        val id = categoryDao.insertCategory(category)
        assertNotNull(categoryDao.getCategoryById(id))

        // when
        categoryDao.deleteCategoryById(id)

        // then
        assertNull(categoryDao.getCategoryById(id))
    }

    @Test
    fun `deleteAllCategories - 删除所有分类`() = runBlocking {
        // given
        insertTestCategories(10)
        val countBefore = categoryDao.getCategoryCount()
        assertTrue(countBefore > 0)

        // when
        categoryDao.deleteAllCategories()

        // then
        val countAfter = categoryDao.getCategoryCount()
        assertEquals(0, countAfter)
    }

    // ==================== 边界条件测试 ====================

    @Test
    fun `insertCategory - 相同名称可以插入`() = runBlocking {
        // given - 允许重名
        val category1 = createCategoryEntity(name = "重复名称")
        val category2 = createCategoryEntity(name = "重复名称")

        // when
        val id1 = categoryDao.insertCategory(category1)
        val id2 = categoryDao.insertCategory(category2)

        // then - 允许重名，id 不同
        assertTrue(id1 != id2)
    }

    @Test
    fun `getCategoriesByType - 无该类型返回空列表`() = runBlocking {
        // given
        categoryDao.insertCategory(createCategoryEntity(name = "餐饮", type = "EXPENSE"))

        // when
        val result = categoryDao.getCategoriesByType("INCOME").first()

        // then
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getCategoryCount - 统计分类数量`() = runBlocking {
        // given
        insertTestCategories(5)

        // when
        val count = categoryDao.getCategoryCount()

        // then
        assertEquals(5, count)
    }

    // ==================== 辅助方法 ====================

    private suspend fun insertTestCategories(count: Int) {
        repeat(count) {
            categoryDao.insertCategory(
                createCategoryEntity(name = "分类$it", type = if (it % 2 == 0) "EXPENSE" else "INCOME")
            )
        }
    }

    private fun createCategoryEntity(
        id: Long = 0,
        name: String = "默认分类",
        icon: String = "💰",
        color: String = "#4CAF50",
        type: String = "EXPENSE",
        isDefault: Boolean = false
    ): CategoryEntity {
        return CategoryEntity(
            id = id,
            name = name,
            icon = icon,
            color = color,
            type = type,
            isDefault = isDefault
        )
    }
}
