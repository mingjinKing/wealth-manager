package com.wealth.manager.data.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wealth.manager.data.AppDatabase
import com.wealth.manager.data.entity.MessageEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * MessageDao 集成测试
 */
@RunWith(AndroidJUnit4::class)
class MessageDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var messageDao: MessageDao

    private val now = System.currentTimeMillis()
    private val sessionId = "test_session"

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).build()
        messageDao = database.messageDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ==================== Insert 测试 ====================

    @Test
    fun `insert - 基本插入成功`() = runBlocking {
        // given
        val message = createMessageEntity()

        // when
        messageDao.insert(message)

        // then
        val count = messageDao.getMessageCount()
        assertEquals(1, count)
    }

    @Test
    fun `insertAll - 批量插入成功`() = runBlocking {
        // given
        val messages = listOf(
            createMessageEntity(content = "消息1"),
            createMessageEntity(content = "消息2"),
            createMessageEntity(content = "消息3")
        )

        // when
        messageDao.insertAll(messages)

        // then
        val count = messageDao.getMessageCount()
        assertEquals(3, count)
    }

    // ==================== Query 测试 ====================

    @Test
    fun `getMessagesBySession - 按会话查询`() = runBlocking {
        // given
        messageDao.insert(createMessageEntity(sessionId = "session1", content = "消息1"))
        messageDao.insert(createMessageEntity(sessionId = "session2", content = "消息2"))
        messageDao.insert(createMessageEntity(sessionId = "session1", content = "消息3"))

        // when
        val result = messageDao.getMessagesBySession("session1").first()

        // then
        assertEquals(2, result.size)
        assertTrue(result.all { it.sessionId == "session1" })
    }

    @Test
    fun `getMessagesBySession - 按时间升序`() = runBlocking {
        // given
        messageDao.insert(createMessageEntity(sessionId = sessionId, content = "消息1", createdAt = now - 2000))
        messageDao.insert(createMessageEntity(sessionId = sessionId, content = "消息2", createdAt = now - 1000))
        messageDao.insert(createMessageEntity(sessionId = sessionId, content = "消息3", createdAt = now))

        // when
        val result = messageDao.getMessagesBySession(sessionId).first()

        // then - 按时间升序
        assertEquals("消息1", result[0].content)
        assertEquals("消息2", result[1].content)
        assertEquals("消息3", result[2].content)
    }

    @Test
    fun `getRecentMessages - 返回最近的 N 条消息`() = runBlocking {
        // given
        repeat(10) { i ->
            messageDao.insert(createMessageEntity(content = "消息$i", createdAt = now + i * 1000))
        }

        // when
        val result = messageDao.getRecentMessages(5)

        // then
        assertEquals(5, result.size)
    }

    @Test
    fun `getRecentUserMessages - 只返回用户消息`() = runBlocking {
        // given
        messageDao.insert(createMessageEntity(isUser = true, content = "用户消息"))
        messageDao.insert(createMessageEntity(isUser = false, content = "AI消息"))
        messageDao.insert(createMessageEntity(isUser = true, content = "用户消息2"))

        // when
        val result = messageDao.getRecentUserMessages(10)

        // then
        assertEquals(2, result.size)
        assertTrue(result.all { it.isUser })
    }

    @Test
    fun `getMessageCount - 返回总消息数`() = runBlocking {
        // given
        repeat(5) { i ->
            messageDao.insert(createMessageEntity())
        }

        // when
        val count = messageDao.getMessageCount()

        // then
        assertEquals(5, count)
    }

    @Test
    fun `getMessageCountBySession - 返回指定会话消息数`() = runBlocking {
        // given
        messageDao.insert(createMessageEntity(sessionId = "session1"))
        messageDao.insert(createMessageEntity(sessionId = "session1"))
        messageDao.insert(createMessageEntity(sessionId = "session2"))

        // when
        val count1 = messageDao.getMessageCountBySession("session1")
        val count2 = messageDao.getMessageCountBySession("session2")

        // then
        assertEquals(2, count1)
        assertEquals(1, count2)
    }

    // ==================== Update 测试 ====================

    @Test
    fun `update - 更新消息内容`() = runBlocking {
        // given
        messageDao.insert(createMessageEntity(content = "原始内容"))
        val original = messageDao.getRecentMessages(1)[0]

        // when
        val updated = original.copy(content = "更新后的内容")
        messageDao.update(updated)

        // then
        val result = messageDao.getRecentMessages(1)[0]
        assertEquals("更新后的内容", result.content)
    }

    // ==================== Delete 测试 ====================

    @Test
    fun `deleteMessagesBySession - 删除指定会话的所有消息`() = runBlocking {
        // given
        messageDao.insert(createMessageEntity(sessionId = "session1"))
        messageDao.insert(createMessageEntity(sessionId = "session1"))
        messageDao.insert(createMessageEntity(sessionId = "session2"))

        // when
        messageDao.deleteMessagesBySession("session1")

        // then
        assertEquals(1, messageDao.getMessageCount())
        assertEquals(0, messageDao.getMessageCountBySession("session1"))
    }

    @Test
    fun `deleteAllMessages - 删除所有消息`() = runBlocking {
        // given
        repeat(10) { i ->
            messageDao.insert(createMessageEntity())
        }
        assertTrue(messageDao.getMessageCount() > 0)

        // when
        messageDao.deleteAllMessages()

        // then
        assertEquals(0, messageDao.getMessageCount())
    }

    // ==================== 辅助方法 ====================

    private fun createMessageEntity(
        id: Long = 0,
        sessionId: String = this.sessionId,
        content: String = "测试消息",
        isUser: Boolean = true,
        createdAt: Long = now
    ): MessageEntity {
        return MessageEntity(
            id = id,
            sessionId = sessionId,
            content = content,
            isUser = isUser,
            createdAt = createdAt
        )
    }
}
