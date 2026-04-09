package com.wealth.manager.data.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wealth.manager.data.AppDatabase
import com.wealth.manager.data.entity.ExpenseEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * ExpenseDao 集成测试
 *
 * 使用 Room in-memory 数据库进行 DAO 层测试
 */
@RunWith(AndroidJUnit4::class)
class ExpenseDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var expenseDao: ExpenseDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).build()
        expenseDao = database.expenseDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ==================== Insert 测试 ====================

    @Test
    fun `insertExpense - 基本插入返回 id`() = runBlocking {
        // given
        val expense = createExpenseEntity(amount = 100.0)

        // when
        val id = expenseDao.insertExpense(expense)

        // then
        assertTrue("插入后返回的 ID 应大于 0", id > 0)
    }

    @Test
    fun `insertExpense - 多次插入返回不同 id`() = runBlocking {
        // given
        val expense1 = createExpenseEntity(amount = 100.0)
        val expense2 = createExpenseEntity(amount = 200.0)
        val expense3 = createExpenseEntity(amount = 300.0)

        // when
        val id1 = expenseDao.insertExpense(expense1)
        val id2 = expenseDao.insertExpense(expense2)
        val id3 = expenseDao.insertExpense(expense3)

        // then
        assertTrue(id1 != id2)
        assertTrue(id2 != id3)
        assertTrue(id1 != id3)
    }

    // ==================== Query 测试 ====================

    @Test
    fun `getAllExpenses - 空数据库返回空列表`() = runBlocking {
        // when
        val expenses = expenseDao.getAllExpenses().first()

        // then
        assertTrue(expenses.isEmpty())
    }

    @Test
    fun `getAllExpenses - 有数据时返回所有记录`() = runBlocking {
        // given
        insertTestExpenses(5)

        // when
        val expenses = expenseDao.getAllExpenses().first()

        // then
        assertEquals(5, expenses.size)
    }

    @Test
    fun `getAllExpenses - 按日期倒序`() = runBlocking {
        // given
        val now = System.currentTimeMillis()
        val expense1 = createExpenseEntity(date = now - 1000)  // 较旧
        val expense2 = createExpenseEntity(date = now)         // 较新
        expenseDao.insertExpense(expense1)
        expenseDao.insertExpense(expense2)

        // when
        val expenses = expenseDao.getAllExpenses().first()

        // then - 较新的在前面
        assertTrue(expenses[0].date >= expenses[1].date)
    }

    @Test
    fun `getExpenseById - 存在返回记录`() = runBlocking {
        // given
        val expense = createExpenseEntity(amount = 500.0)
        val id = expenseDao.insertExpense(expense)

        // when
        val result = expenseDao.getExpenseById(id)

        // then
        assertNotNull(result)
        assertEquals(500.0, result!!.amount, 0.001)
    }

    @Test
    fun `getExpenseById - 不存在返回 null`() = runBlocking {
        // when
        val result = expenseDao.getExpenseById(9999)

        // then
        assertNull(result)
    }

    @Test
    fun `getExpensesByDateRange - 在范围内返回记录`() = runBlocking {
        // given
        val now = System.currentTimeMillis()
        val expense1 = createExpenseEntity(date = now - 86400000)  // 1天前
        val expense2 = createExpenseEntity(date = now)              // 今天
        val expense3 = createExpenseEntity(date = now + 86400000)  // 明天（不在范围内）
        expenseDao.insertExpense(expense1)
        expenseDao.insertExpense(expense2)
        expenseDao.insertExpense(expense3)

        // when - 查今天
        val startOfToday = now - (now % 86400000)
        val endOfToday = startOfToday + 86400000 - 1
        val expenses = expenseDao.getExpensesByDateRange(startOfToday, endOfToday).first()

        // then - 只有今天的记录
        assertEquals(1, expenses.size)
    }

    @Test
    fun `getExpensesByCategory - 按分类查询`() = runBlocking {
        // given
        val expense1 = createExpenseEntity(categoryId = 1)
        val expense2 = createExpenseEntity(categoryId = 2)
        val expense3 = createExpenseEntity(categoryId = 1)
        expenseDao.insertExpense(expense1)
        expenseDao.insertExpense(expense2)
        expenseDao.insertExpense(expense3)

        // when
        val expenses = expenseDao.getExpensesByCategory(1).first()

        // then
        assertEquals(2, expenses.size)
    }

    // ==================== Aggregate 测试 ====================

    @Test
    fun `getTotalAmountByDateRange - 有数据时返回总和`() = runBlocking {
        // given
        val now = System.currentTimeMillis()
        val expense1 = createExpenseEntity(amount = 100.0, date = now)
        val expense2 = createExpenseEntity(amount = 200.0, date = now)
        val expense3 = createExpenseEntity(amount = 300.0, date = now)
        expenseDao.insertExpense(expense1)
        expenseDao.insertExpense(expense2)
        expenseDao.insertExpense(expense3)

        // when
        val startOfToday = now - (now % 86400000)
        val endOfToday = startOfToday + 86400000 - 1
        val total = expenseDao.getTotalAmountByDateRange(startOfToday, endOfToday).first()

        // then
        assertEquals(600.0, total ?: 0.0, 0.001)
    }

    @Test
    fun `getTotalAmountByDateRange - 无数据时返回 null`() = runBlocking {
        // when
        val total = expenseDao.getTotalAmountByDateRange(0, 1000).first()

        // then
        assertNull(total)
    }

    @Test
    fun `getAmountByCategoryAndDateRange - 按分类和日期范围`() = runBlocking {
        // given
        val now = System.currentTimeMillis()
        val expense1 = createExpenseEntity(categoryId = 1, amount = 100.0, date = now)
        val expense2 = createExpenseEntity(categoryId = 1, amount = 200.0, date = now)
        val expense3 = createExpenseEntity(categoryId = 2, amount = 300.0, date = now)
        expenseDao.insertExpense(expense1)
        expenseDao.insertExpense(expense2)
        expenseDao.insertExpense(expense3)

        // when
        val startOfToday = now - (now % 86400000)
        val endOfToday = startOfToday + 86400000 - 1
        val total = expenseDao.getAmountByCategoryAndDateRange(1, startOfToday, endOfToday).first()

        // then
        assertEquals(300.0, total ?: 0.0, 0.001)
    }

    // ==================== Update 测试 ====================

    @Test
    fun `updateExpense - 更新记录`() = runBlocking {
        // given
        val expense = createExpenseEntity(amount = 100.0)
        val id = expenseDao.insertExpense(expense)
        val original = expenseDao.getExpenseById(id)!!

        // when
        val updated = original.copy(amount = 999.0)
        expenseDao.updateExpense(updated)

        // then
        val result = expenseDao.getExpenseById(id)
        assertEquals(999.0, result!!.amount, 0.001)
    }

    // ==================== Delete 测试 ====================

    @Test
    fun `deleteExpense - 删除记录`() = runBlocking {
        // given
        val expense = createExpenseEntity()
        val id = expenseDao.insertExpense(expense)
        assertNotNull(expenseDao.getExpenseById(id))

        // when
        expenseDao.deleteExpense(expense)

        // then
        assertNull(expenseDao.getExpenseById(id))
    }

    @Test
    fun `deleteExpenseById - 按 ID 删除`() = runBlocking {
        // given
        val expense = createExpenseEntity()
        val id = expenseDao.insertExpense(expense)
        assertNotNull(expenseDao.getExpenseById(id))

        // when
        expenseDao.deleteExpenseById(id)

        // then
        assertNull(expenseDao.getExpenseById(id))
    }

    @Test
    fun `deleteAllExpenses - 删除所有记录`() = runBlocking {
        // given
        insertTestExpenses(10)
        assertEquals(10, expenseDao.getAllExpenses().first().size)

        // when
        expenseDao.deleteAllExpenses()

        // then
        assertTrue(expenseDao.getAllExpenses().first().isEmpty())
    }

    // ==================== 分页测试 ====================

    @Test
    fun `getExpensesPaginated - 分页返回`() = runBlocking {
        // given
        insertTestExpenses(20)

        // when
        val page1 = expenseDao.getExpensesPaginated(Long.MAX_VALUE, 5)
        val page2 = expenseDao.getExpensesPaginated(page1.last().date, 5)

        // then
        assertEquals(5, page1.size)
        assertEquals(5, page2.size)
        assertTrue(page1 != page2)
    }

    @Test
    fun `getExpensesPaginated - 数据不足返回全部`() = runBlocking {
        // given
        insertTestExpenses(3)

        // when
        val result = expenseDao.getExpensesPaginated(Long.MAX_VALUE, 10)

        // then
        assertEquals(3, result.size)
    }

    // ==================== 辅助方法 ====================

    private suspend fun insertTestExpenses(count: Int) {
        repeat(count) {
            expenseDao.insertExpense(createExpenseEntity(amount = (it + 1) * 100.0))
        }
    }

    private fun createExpenseEntity(
        id: Long = 0,
        amount: Double = 100.0,
        categoryId: Long = 1,
        date: Long = System.currentTimeMillis(),
        note: String = "",
        assetId: Long? = null,
        createdAt: Long = System.currentTimeMillis()
    ): ExpenseEntity {
        return ExpenseEntity(
            id = id,
            amount = amount,
            categoryId = categoryId,
            date = date,
            note = note,
            assetId = assetId,
            createdAt = createdAt
        )
    }
}
