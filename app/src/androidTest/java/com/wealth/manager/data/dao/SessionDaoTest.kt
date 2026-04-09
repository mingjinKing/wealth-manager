package com.wealth.manager.data.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wealth.manager.data.AppDatabase
import com.wealth.manager.data.entity.SessionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * SessionDao 集成测试
 */
@RunWith(AndroidJUnit4::class)
class SessionDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var sessionDao: SessionDao

    private val now = System.currentTimeMillis()

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).build()
        sessionDao = database.sessionDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ==================== Insert 测试 ====================

    @Test
    fun `insertSession - 基本插入返回 id`() = runBlocking {
        // given
        val session = createSessionEntity()

        // when
        val id = sessionDao.insertSession(session)

        // then
        assertTrue(id.isNotEmpty())
    }

    @Test
    fun `insertSession - 多次插入不同 ID`() = runBlocking {
        // given
        val session1 = createSessionEntity()
        val session2 = createSessionEntity()

        // when
        val id1 = sessionDao.insertSession(session1)
        val id2 = sessionDao.insertSession(session2)

        // then
        assertTrue(id1 != id2)
    }

    @Test
    fun `insertSession - REPLACE 策略更新已有会话`() = runBlocking {
        // given
        val session1 = createSessionEntity()
        val id = sessionDao.insertSession(session1)

        // when - 用相同 ID 插入
        val session2 = sessionDao.getSessionById(id)!!.copy(updatedAt = now + 1000)
        sessionDao.insertSession(session2)

        // then - 只有一条记录
        val count = sessionDao.getSessionCount()
        assertEquals(1, count)
    }

    // ==================== Query 测试 ====================

    @Test
    fun `getAllSessions - 空数据库返回空列表`() = runBlocking {
        // when
        val result = sessionDao.getAllSessions().first()

        // then
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getAllSessions - 按更新时间倒序`() = runBlocking {
        // given
        val session1 = createSessionEntity(id = "session1", updatedAt = now - 1000)
        val session2 = createSessionEntity(id = "session2", updatedAt = now)
        sessionDao.insertSession(session1)
        sessionDao.insertSession(session2)

        // when
        val result = sessionDao.getAllSessions().first()

        // then - 最新的在前面
        assertEquals("session2", result[0].id)
        assertEquals("session1", result[1].id)
    }

    @Test
    fun `getSessionById - 存在返回记录`() = runBlocking {
        // given
        val session = createSessionEntity()
        val id = sessionDao.insertSession(session)

        // when
        val result = sessionDao.getSessionById(id)

        // then
        assertNotNull(result)
        assertEquals(id, result!!.id)
    }

    @Test
    fun `getSessionById - 不存在返回 null`() = runBlocking {
        // when
        val result = sessionDao.getSessionById("nonexistent")

        // then
        assertNull(result)
    }

    @Test
    fun `getSessionCount - 返回会话数量`() = runBlocking {
        // given
        repeat(5) {
            sessionDao.insertSession(createSessionEntity())
        }

        // when
        val count = sessionDao.getSessionCount()

        // then
        assertEquals(5, count)
    }

    // ==================== Update 测试 ====================

    @Test
    fun `update - 更新会话`() = runBlocking {
        // given
        val session = createSessionEntity()
        val id = sessionDao.insertSession(session)
        val original = sessionDao.getSessionById(id)!!

        // when
        val updated = original.copy(title = "新标题")
        sessionDao.update(updated)

        // then
        val result = sessionDao.getSessionById(id)
        assertEquals("新标题", result!!.title)
    }

    @Test
    fun `update - 更新时间戳`() = runBlocking {
        // given
        val session = createSessionEntity(updatedAt = now)
        val id = sessionDao.insertSession(session)
        val original = sessionDao.getSessionById(id)!!

        // when
        val newTime = now + 10000
        val updated = original.copy(updatedAt = newTime)
        sessionDao.update(updated)

        // then
        val result = sessionDao.getSessionById(id)
        assertEquals(newTime, result!!.updatedAt)
    }

    // ==================== Delete 测试 ====================

    @Test
    fun `deleteSession - 删除指定会话`() = runBlocking {
        // given
        val session = createSessionEntity()
        val id = sessionDao.insertSession(session)
        assertNotNull(sessionDao.getSessionById(id))

        // when
        sessionDao.deleteSession(id)

        // then
        assertNull(sessionDao.getSessionById(id))
    }

    @Test
    fun `deleteAllSessions - 删除所有会话`() = runBlocking {
        // given
        repeat(5) {
            sessionDao.insertSession(createSessionEntity())
        }
        assertTrue(sessionDao.getSessionCount() > 0)

        // when
        sessionDao.deleteAllSessions()

        // then
        assertEquals(0, sessionDao.getSessionCount())
    }

    // ==================== 辅助方法 ====================

    private fun createSessionEntity(
        id: String = "session_${System.nanoTime()}",
        title: String = "会话标题",
        createdAt: Long = now,
        updatedAt: Long = now
    ): SessionEntity {
        return SessionEntity(
            id = id,
            title = title,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}
