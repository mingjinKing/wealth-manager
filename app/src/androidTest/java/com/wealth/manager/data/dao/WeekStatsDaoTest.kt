package com.wealth.manager.data.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wealth.manager.data.AppDatabase
import com.wealth.manager.data.entity.WeekStatsEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * WeekStatsDao 集成测试
 */
@RunWith(AndroidJUnit4::class)
class WeekStatsDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var weekStatsDao: WeekStatsDao

    private val now = System.currentTimeMillis()
    private val oneWeek = 7L * 24 * 60 * 60 * 1000

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).build()
        weekStatsDao = database.weekStatsDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ==================== Insert 测试 ====================

    @Test
    fun `insertWeekStats - 基本插入返回成功`() = runBlocking {
        // given
        val weekStats = createWeekStatsEntity(weekStartDate = now)

        // when
        weekStatsDao.insertWeekStats(weekStats)

        // then
        val result = weekStatsDao.getWeekStatsByStartDate(now)
        assertNotNull(result)
    }

    @Test
    fun `insertWeekStats - REPLACE 策略更新已有数据`() = runBlocking {
        // given
        val weekStats1 = createWeekStatsEntity(weekStartDate = now, totalAmount = 1000.0)
        val weekStats2 = createWeekStatsEntity(weekStartDate = now, totalAmount = 2000.0)

        // when
        weekStatsDao.insertWeekStats(weekStats1)
        weekStatsDao.insertWeekStats(weekStats2)

        // then - 应该只有一条，且金额是最后插入的
        val result = weekStatsDao.getAllWeekStats().first()
        assertEquals(1, result.size)
        assertEquals(2000.0, result[0].totalAmount, 0.001)
    }

    // ==================== Query 测试 ====================

    @Test
    fun `getAllWeekStats - 空数据库返回空列表`() = runBlocking {
        // when
        val result = weekStatsDao.getAllWeekStats().first()

        // then
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getAllWeekStats - 按日期倒序`() = runBlocking {
        // given - 插入多周数据
        weekStatsDao.insertWeekStats(createWeekStatsEntity(weekStartDate = now - 3 * oneWeek))
        weekStatsDao.insertWeekStats(createWeekStatsEntity(weekStartDate = now - oneWeek))
        weekStatsDao.insertWeekStats(createWeekStatsEntity(weekStartDate = now))

        // when
        val result = weekStatsDao.getAllWeekStats().first()

        // then - 最新在前面
        assertTrue(result[0].weekStartDate > result[1].weekStartDate)
        assertTrue(result[1].weekStartDate > result[2].weekStartDate)
    }

    @Test
    fun `getWeekStatsByStartDate - 存在返回记录`() = runBlocking {
        // given
        weekStatsDao.insertWeekStats(createWeekStatsEntity(weekStartDate = now))

        // when
        val result = weekStatsDao.getWeekStatsByStartDate(now)

        // then
        assertNotNull(result)
        assertEquals(now, result!!.weekStartDate)
    }

    @Test
    fun `getWeekStatsByStartDate - 不存在返回 null`() = runBlocking {
        // when
        val result = weekStatsDao.getWeekStatsByStartDate(now)

        // then
        assertNull(result)
    }

    @Test
    fun `getRecentWeekStats - 返回最近的 N 周`() = runBlocking {
        // given - 插入 10 周数据
        repeat(10) { i ->
            weekStatsDao.insertWeekStats(createWeekStatsEntity(weekStartDate = now - i * oneWeek))
        }

        // when
        val result = weekStatsDao.getRecentWeekStats(5).first()

        // then
        assertEquals(5, result.size)
    }

    @Test
    fun `getRecentWeekStats - 数据不足返回全部`() = runBlocking {
        // given - 只插入 3 周
        repeat(3) { i ->
            weekStatsDao.insertWeekStats(createWeekStatsEntity(weekStartDate = now - i * oneWeek))
        }

        // when - 请求 10 周
        val result = weekStatsDao.getRecentWeekStats(10).first()

        // then
        assertEquals(3, result.size)
    }

    // ==================== Aggregate 测试 ====================

    @Test
    fun `getAverageLastWeeks - 计算最近 N 周平均值`() = runBlocking {
        // given - 插入 4 周数据
        weekStatsDao.insertWeekStats(createWeekStatsEntity(weekStartDate = now, totalAmount = 1000.0))
        weekStatsDao.insertWeekStats(createWeekStatsEntity(weekStartDate = now - oneWeek, totalAmount = 2000.0))
        weekStatsDao.insertWeekStats(createWeekStatsEntity(weekStartDate = now - 2 * oneWeek, totalAmount = 3000.0))
        weekStatsDao.insertWeekStats(createWeekStatsEntity(weekStartDate = now - 3 * oneWeek, totalAmount = 4000.0))

        // when
        val average = weekStatsDao.getAverageLastWeeks(4)

        // then - (1000+2000+3000+4000)/4 = 2500
        assertEquals(2500.0, average!!, 0.001)
    }

    @Test
    fun `getAverageLastWeeks - 无数据返回 null`() = runBlocking {
        // when
        val average = weekStatsDao.getAverageLastWeeks(4)

        // then
        assertNull(average)
    }

    @Test
    fun `getAverageLastWeeks - 数据不足 N 周按实际计算`() = runBlocking {
        // given - 只有 2 周数据
        weekStatsDao.insertWeekStats(createWeekStatsEntity(weekStartDate = now, totalAmount = 1000.0))
        weekStatsDao.insertWeekStats(createWeekStatsEntity(weekStartDate = now - oneWeek, totalAmount = 3000.0))

        // when - 请求 4 周平均值
        val average = weekStatsDao.getAverageLastWeeks(4)

        // then - (1000+3000)/2 = 2000
        assertEquals(2000.0, average!!, 0.001)
    }

    // ==================== Update 测试 ====================

    @Test
    fun `updateWeekStats - 更新周统计`() = runBlocking {
        // given
        weekStatsDao.insertWeekStats(createWeekStatsEntity(weekStartDate = now, totalAmount = 1000.0))
        val original = weekStatsDao.getWeekStatsByStartDate(now)!!

        // when
        val updated = original.copy(totalAmount = 9999.0)
        weekStatsDao.updateWeekStats(updated)

        // then
        val result = weekStatsDao.getWeekStatsByStartDate(now)
        assertEquals(9999.0, result!!.totalAmount, 0.001)
    }

    // ==================== Delete 测试 ====================

    @Test
    fun `deleteAllWeekStats - 删除所有周统计`() = runBlocking {
        // given
        repeat(5) { i ->
            weekStatsDao.insertWeekStats(createWeekStatsEntity(weekStartDate = now - i * oneWeek))
        }
        assertTrue(weekStatsDao.getAllWeekStats().first().size > 0)

        // when
        weekStatsDao.deleteAllWeekStats()

        // then
        assertTrue(weekStatsDao.getAllWeekStats().first().isEmpty())
    }

    // ==================== 辅助方法 ====================

    private fun createWeekStatsEntity(
        id: Long = 0,
        weekStartDate: Long = now,
        totalAmount: Double = 1000.0,
        expenseCount: Int = 10,
        categoryBreakdown: String = "{}"
    ): WeekStatsEntity {
        return WeekStatsEntity(
            id = id,
            weekStartDate = weekStartDate,
            totalAmount = totalAmount,
            expenseCount = expenseCount,
            categoryBreakdown = categoryBreakdown
        )
    }
}
