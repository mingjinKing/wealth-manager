package com.wealth.manager.data.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wealth.manager.data.AppDatabase
import com.wealth.manager.data.entity.BudgetEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * BudgetDao 集成测试
 */
@RunWith(AndroidJUnit4::class)
class BudgetDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var budgetDao: BudgetDao

    private val testMonth = "2026-04"

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).build()
        budgetDao = database.budgetDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ==================== Insert 测试 ====================

    @Test
    fun `insertBudget - 基本插入返回 id`() = runBlocking {
        // given
        val budget = createBudgetEntity(month = testMonth)

        // when
        val id = budgetDao.insertBudget(budget)

        // then
        assertTrue(id > 0)
    }

    @Test
    fun `insertBudget - 全局预算插入成功`() = runBlocking {
        // given - categoryId = null 表示全局预算
        val budget = createBudgetEntity(month = testMonth, categoryId = null, amount = 5000.0)

        // when
        val id = budgetDao.insertBudget(budget)

        // then
        val result = budgetDao.getGlobalBudget(testMonth).first()
        assertNotNull(result)
        assertEquals(5000.0, result!!.amount, 0.001)
    }

    @Test
    fun `insertBudget - 分类预算插入成功`() = runBlocking {
        // given - categoryId 不为空表示分类预算
        val budget = createBudgetEntity(month = testMonth, categoryId = 1, amount = 1000.0)

        // when
        val id = budgetDao.insertBudget(budget)

        // then
        val result = budgetDao.getCategoryBudgets(testMonth).first()
        assertEquals(1, result.size)
        assertEquals(1000.0, result[0].amount, 0.001)
    }

    // ==================== Query 测试 ====================

    @Test
    fun `getAllBudgets - 空数据库返回空列表`() = runBlocking {
        // when
        val budgets = budgetDao.getAllBudgets().first()

        // then
        assertTrue(budgets.isEmpty())
    }

    @Test
    fun `getAllBudgets - 有数据返回所有预算`() = runBlocking {
        // given
        insertTestBudgets(5)

        // when
        val budgets = budgetDao.getAllBudgets().first()

        // then
        assertEquals(5, budgets.size)
    }

    @Test
    fun `getGlobalBudget - 存在返回预算`() = runBlocking {
        // given
        budgetDao.insertBudget(createBudgetEntity(month = testMonth, categoryId = null, amount = 5000.0))

        // when
        val result = budgetDao.getGlobalBudget(testMonth).first()

        // then
        assertNotNull(result)
        assertEquals(5000.0, result!!.amount, 0.001)
        assertNull(result.categoryId)
    }

    @Test
    fun `getGlobalBudget - 不存在返回 null`() = runBlocking {
        // when
        val result = budgetDao.getGlobalBudget("2099-01").first()

        // then
        assertNull(result)
    }

    @Test
    fun `getCategoryBudgets - 按月份查询分类预算`() = runBlocking {
        // given
        budgetDao.insertBudget(createBudgetEntity(month = testMonth, categoryId = 1, amount = 1000.0))
        budgetDao.insertBudget(createBudgetEntity(month = testMonth, categoryId = 2, amount = 2000.0))
        budgetDao.insertBudget(createBudgetEntity(month = "2026-05", categoryId = 1, amount = 3000.0)) // 不同月份

        // when
        val result = budgetDao.getCategoryBudgets(testMonth).first()

        // then - 只返回当月的分类预算
        assertEquals(2, result.size)
        assertTrue(result.all { it.month == testMonth })
    }

    @Test
    fun `getCategoryBudgets - 无分类预算返回空列表`() = runBlocking {
        // given - 只有全局预算
        budgetDao.insertBudget(createBudgetEntity(month = testMonth, categoryId = null))

        // when
        val result = budgetDao.getCategoryBudgets(testMonth).first()

        // then
        assertTrue(result.isEmpty())
    }

    // ==================== Update 测试 ====================

    @Test
    fun `updateBudget - 更新预算金额`() = runBlocking {
        // given
        val budget = createBudgetEntity(month = testMonth, amount = 1000.0)
        val id = budgetDao.insertBudget(budget)

        // 重新查询并更新
        val original = budgetDao.getGlobalBudget(testMonth).first()!!

        // when
        val updated = original.copy(amount = 9999.0)
        budgetDao.updateBudget(updated)

        // then
        val result = budgetDao.getGlobalBudget(testMonth).first()
        assertEquals(9999.0, result!!.amount, 0.001)
    }

    // ==================== Delete 测试 ====================

    @Test
    fun `deleteBudget - 按 ID 删除预算`() = runBlocking {
        // given
        val budget = createBudgetEntity(month = testMonth)
        val id = budgetDao.insertBudget(budget)

        // when
        budgetDao.deleteBudget(id)

        // then
        val result = budgetDao.getGlobalBudget(testMonth).first()
        assertNull(result)
    }

    @Test
    fun `deleteAllBudgets - 删除所有预算`() = runBlocking {
        // given
        insertTestBudgets(10)
        assertTrue(budgetDao.getAllBudgets().first().size > 0)

        // when
        budgetDao.deleteAllBudgets()

        // then
        assertTrue(budgetDao.getAllBudgets().first().isEmpty())
    }

    // ==================== 边界条件测试 ====================

    @Test
    fun `getGlobalBudget - 不同月份独立存储`() = runBlocking {
        // given
        budgetDao.insertBudget(createBudgetEntity(month = "2026-03", categoryId = null, amount = 3000.0))
        budgetDao.insertBudget(createBudgetEntity(month = "2026-04", categoryId = null, amount = 4000.0))
        budgetDao.insertBudget(createBudgetEntity(month = "2026-05", categoryId = null, amount = 5000.0))

        // when
        val marchBudget = budgetDao.getGlobalBudget("2026-03").first()
        val aprilBudget = budgetDao.getGlobalBudget("2026-04").first()

        // then
        assertEquals(3000.0, marchBudget!!.amount, 0.001)
        assertEquals(4000.0, aprilBudget!!.amount, 0.001)
    }

    @Test
    fun `insertBudget - 全局预算替换而非新增`() = runBlocking {
        // given - 同一个月份插入两个全局预算
        val budget1 = createBudgetEntity(month = testMonth, categoryId = null, amount = 1000.0)
        val budget2 = createBudgetEntity(month = testMonth, categoryId = null, amount = 2000.0)

        // when
        budgetDao.insertBudget(budget1)
        budgetDao.insertBudget(budget2)

        // then - 使用 REPLACE 策略，只会有一条
        val result = budgetDao.getAllBudgets().first()
        assertEquals(1, result.size)
        assertEquals(2000.0, result[0].amount, 0.001) // 最后插入的
    }

    // ==================== 辅助方法 ====================

    private suspend fun insertTestBudgets(count: Int) {
        repeat(count) {
            budgetDao.insertBudget(
                createBudgetEntity(
                    month = testMonth,
                    categoryId = if (it % 2 == 0) null else (it + 1).toLong(),
                    amount = (it + 1) * 1000.0
                )
            )
        }
    }

    private fun createBudgetEntity(
        id: Long = 0,
        month: String = testMonth,
        categoryId: Long? = null,
        amount: Double = 1000.0
    ): BudgetEntity {
        return BudgetEntity(
            id = id,
            month = month,
            categoryId = categoryId,
            amount = amount
        )
    }
}
