package com.wealth.manager.data.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wealth.manager.data.AppDatabase
import com.wealth.manager.data.entity.MemoryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * MemoryDao 集成测试
 */
@RunWith(AndroidJUnit4::class)
class MemoryDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var memoryDao: MemoryDao

    private val now = System.currentTimeMillis()

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).build()
        memoryDao = database.memoryDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ==================== Insert 测试 ====================

    @Test
    fun `insert - 基本插入成功`() = runBlocking {
        // given
        val memory = createMemoryEntity()

        // when
        memoryDao.insert(memory)

        // then
        val count = memoryDao.getMemoryCount()
        assertEquals(1, count)
    }

    @Test
    fun `insert - 相同 key REPLACE 更新`() = runBlocking {
        // given
        val memory1 = createMemoryEntity(key = "user_profile", content = "旧内容")
        val memory2 = createMemoryEntity(key = "user_profile", content = "新内容")

        // when
        memoryDao.insert(memory1)
        memoryDao.insert(memory2)

        // then - 只有一条
        assertEquals(1, memoryDao.getMemoryCount())
        assertEquals("新内容", memoryDao.getMemoryByKey("user_profile")!!.content)
    }

    @Test
    fun `insertAll - 批量插入成功`() = runBlocking {
        // given
        val memories = listOf(
            createMemoryEntity(key = "key1"),
            createMemoryEntity(key = "key2"),
            createMemoryEntity(key = "key3")
        )

        // when
        memoryDao.insertAll(memories)

        // then
        assertEquals(3, memoryDao.getMemoryCount())
    }

    // ==================== Query 测试 ====================

    @Test
    fun `getAllMemory - 按更新时间倒序`() = runBlocking {
        // given
        memoryDao.insert(createMemoryEntity(key = "key1", updatedAt = now - 1000))
        memoryDao.insert(createMemoryEntity(key = "key2", updatedAt = now))

        // when
        val result = memoryDao.getAllMemory().first()

        // then - 最新的在前面
        assertEquals("key2", result[0].key)
        assertEquals("key1", result[1].key)
    }

    @Test
    fun `getMemoryByKey - 存在返回记录`() = runBlocking {
        // given
        memoryDao.insert(createMemoryEntity(key = "user_profile", content = "用户信息"))

        // when
        val result = memoryDao.getMemoryByKey("user_profile")

        // then
        assertNotNull(result)
        assertEquals("用户信息", result!!.content)
    }

    @Test
    fun `getMemoryByKey - 不存在返回 null`() = runBlocking {
        // when
        val result = memoryDao.getMemoryByKey("nonexistent")

        // then
        assertNull(result)
    }

    @Test
    fun `getMemoryCount - 返回记忆数量`() = runBlocking {
        // given
        memoryDao.insert(createMemoryEntity(key = "key1"))
        memoryDao.insert(createMemoryEntity(key = "key2"))

        // when
        val count = memoryDao.getMemoryCount()

        // then
        assertEquals(2, count)
    }

    @Test
    fun `getAllMemoryKeys - 返回所有 key 列表`() = runBlocking {
        // given
        memoryDao.insert(createMemoryEntity(key = "profile"))
        memoryDao.insert(createMemoryEntity(key = "preferences"))
        memoryDao.insert(createMemoryEntity(key = "history"))

        // when
        val keys = memoryDao.getAllMemoryKeys()

        // then
        assertEquals(3, keys.size)
        assertTrue(keys.contains("profile"))
        assertTrue(keys.contains("preferences"))
        assertTrue(keys.contains("history"))
    }

    // ==================== Update 测试 ====================

    @Test
    fun `update - 更新记忆内容`() = runBlocking {
        // given
        memoryDao.insert(createMemoryEntity(key = "test", content = "旧内容"))
        val original = memoryDao.getMemoryByKey("test")!!

        // when
        val updated = original.copy(content = "新内容", updatedAt = now + 1000)
        memoryDao.update(updated)

        // then
        val result = memoryDao.getMemoryByKey("test")
        assertEquals("新内容", result!!.content)
    }

    @Test
    fun `update - 更新时间戳`() = runBlocking {
        // given
        memoryDao.insert(createMemoryEntity(key = "test", updatedAt = now))
        val original = memoryDao.getMemoryByKey("test")!!

        // when
        val newTime = now + 10000
        val updated = original.copy(updatedAt = newTime)
        memoryDao.update(updated)

        // then
        val result = memoryDao.getMemoryByKey("test")
        assertEquals(newTime, result!!.updatedAt)
    }

    // ==================== Delete 测试 ====================

    @Test
    fun `deleteMemory - 删除指定记忆`() = runBlocking {
        // given
        memoryDao.insert(createMemoryEntity(key = "to_delete"))
        assertNotNull(memoryDao.getMemoryByKey("to_delete"))

        // when
        val memory = memoryDao.getMemoryByKey("to_delete")!!
        memoryDao.deleteMemory(memory.id)

        // then
        assertNull(memoryDao.getMemoryByKey("to_delete"))
    }

    @Test
    fun `deleteAllMemory - 删除所有记忆`() = runBlocking {
        // given
        repeat(5) {
            memoryDao.insert(createMemoryEntity(key = "key$it"))
        }
        assertTrue(memoryDao.getMemoryCount() > 0)

        // when
        memoryDao.deleteAllMemory()

        // then
        assertEquals(0, memoryDao.getMemoryCount())
    }

    // ==================== 边界条件测试 ====================

    @Test
    fun `getMemoryByKey - 空数据库返回 null`() = runBlocking {
        // when
        val result = memoryDao.getMemoryByKey("any_key")

        // then
        assertNull(result)
    }

    @Test
    fun `getAllMemoryKeys - 空数据库返回空列表`() = runBlocking {
        // when
        val result = memoryDao.getAllMemoryKeys()

        // then
        assertTrue(result.isEmpty())
    }

    // ==================== 辅助方法 ====================

    private fun createMemoryEntity(
        id: String = "memory_${System.nanoTime()}",
        key: String = "test_key",
        content: String = "测试内容",
        createdAt: Long = now,
        updatedAt: Long = now
    ): MemoryEntity {
        return MemoryEntity(
            id = id,
            key = key,
            content = content,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}
