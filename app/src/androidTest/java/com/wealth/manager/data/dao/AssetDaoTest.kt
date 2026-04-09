package com.wealth.manager.data.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wealth.manager.data.AppDatabase
import com.wealth.manager.data.entity.AssetEntity
import com.wealth.manager.data.entity.AssetType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * AssetDao 集成测试
 */
@RunWith(AndroidJUnit4::class)
class AssetDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var assetDao: AssetDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).build()
        assetDao = database.assetDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ==================== Insert 测试 ====================

    @Test
    fun `insertAsset - 基本插入返回 id`() = runBlocking {
        // given
        val asset = createAssetEntity(name = "银行卡")

        // when
        val id = assetDao.insertAsset(asset)

        // then
        assertTrue(id > 0)
    }

    @Test
    fun `insertAsset - 多次插入返回不同 id`() = runBlocking {
        // given
        val asset1 = createAssetEntity(name = "银行卡")
        val asset2 = createAssetEntity(name = "余额宝")

        // when
        val id1 = assetDao.insertAsset(asset1)
        val id2 = assetDao.insertAsset(asset2)

        // then
        assertTrue(id1 != id2)
    }

    // ==================== Query 测试 ====================

    @Test
    fun `getAllAssets - 空数据库返回空列表`() = runBlocking {
        // when
        val assets = assetDao.getAllAssets().first()

        // then
        assertTrue(assets.isEmpty())
    }

    @Test
    fun `getAllAssets - 有数据返回所有资产`() = runBlocking {
        // given
        insertTestAssets(5)

        // when
        val assets = assetDao.getAllAssets().first()

        // then
        assertEquals(5, assets.size)
    }

    @Test
    fun `getAllAssets - 按类型排序`() = runBlocking {
        // given
        assetDao.insertAsset(createAssetEntity(name = "A资产", type = AssetType.CASH))
        assetDao.insertAsset(createAssetEntity(name = "B资产", type = AssetType.BANK_CARD))

        // when
        val assets = assetDao.getAllAssets().first()

        // then - CASH 在 BANK_CARD 前面
        assertEquals("A资产", assets[0].name)
        assertEquals("B资产", assets[1].name)
    }

    @Test
    fun `getAssetById - 存在返回记录`() = runBlocking {
        // given
        val asset = createAssetEntity(name = "测试资产")
        val id = assetDao.insertAsset(asset)

        // when
        val result = assetDao.getAssetById(id)

        // then
        assertNotNull(result)
        assertEquals("测试资产", result!!.name)
    }

    @Test
    fun `getAssetById - 不存在返回 null`() = runBlocking {
        // when
        val result = assetDao.getAssetById(9999)

        // then
        assertNull(result)
    }

    @Test
    fun `getAssetsByType - 按类型查询`() = runBlocking {
        // given
        assetDao.insertAsset(createAssetEntity(name = "现金", type = AssetType.CASH))
        assetDao.insertAsset(createAssetEntity(name = "银行卡", type = AssetType.BANK_CARD))
        assetDao.insertAsset(createAssetEntity(name = "另一个现金", type = AssetType.CASH))

        // when
        val cashAssets = assetDao.getAssetsByType(AssetType.CASH).first()

        // then
        assertEquals(2, cashAssets.size)
        assertTrue(cashAssets.all { it.type == AssetType.CASH })
    }

    // ==================== Update 测试 ====================

    @Test
    fun `updateAsset - 更新资产名称`() = runBlocking {
        // given
        val asset = createAssetEntity(name = "旧名称")
        val id = assetDao.insertAsset(asset)
        val original = assetDao.getAssetById(id)!!

        // when
        val updated = original.copy(name = "新名称")
        assetDao.updateAsset(updated)

        // then
        val result = assetDao.getAssetById(id)
        assertEquals("新名称", result!!.name)
    }

    @Test
    fun `updateAsset - 更新资产余额`() = runBlocking {
        // given
        val asset = createAssetEntity(balance = 1000.0)
        val id = assetDao.insertAsset(asset)
        val original = assetDao.getAssetById(id)!!

        // when
        val updated = original.copy(balance = 9999.0)
        assetDao.updateAsset(updated)

        // then
        val result = assetDao.getAssetById(id)
        assertEquals(9999.0, result!!.balance, 0.001)
    }

    // ==================== Delete 测试 ====================

    @Test
    fun `deleteAsset - 删除资产`() = runBlocking {
        // given
        val asset = createAssetEntity(name = "待删除")
        val id = assetDao.insertAsset(asset)
        assertNotNull(assetDao.getAssetById(id))

        // when
        assetDao.deleteAsset(asset)

        // then
        assertNull(assetDao.getAssetById(id))
    }

    @Test
    fun `deleteAllAssets - 删除所有资产`() = runBlocking {
        // given
        insertTestAssets(10)
        assertTrue(assetDao.getAllAssets().first().size > 0)

        // when
        assetDao.deleteAllAssets()

        // then
        assertTrue(assetDao.getAllAssets().first().isEmpty())
    }

    // ==================== 统计测试 ====================

    @Test
    fun `getTotalAssets - 有资产时返回总和`() = runBlocking {
        // given - 插入非负债类资产
        assetDao.insertAsset(createAssetEntity(type = AssetType.BANK_CARD, balance = 10000.0))
        assetDao.insertAsset(createAssetEntity(type = AssetType.BANK_CARD, balance = 20000.0))

        // when
        val total = assetDao.getTotalAssets().first()

        // then
        assertEquals(30000.0, total ?: 0.0, 0.001)
    }

    @Test
    fun `getTotalAssets - 无资产时返回 null`() = runBlocking {
        // when
        val total = assetDao.getTotalAssets().first()

        // then
        assertNull(total)
    }

    @Test
    fun `getTotalLiabilities - 有负债时返回负值总和`() = runBlocking {
        // given - 插入信用卡负债
        assetDao.insertAsset(createAssetEntity(type = AssetType.CREDIT_CARD, balance = -5000.0))
        assetDao.insertAsset(createAssetEntity(type = AssetType.CREDIT_CARD, balance = -3000.0))

        // when
        val total = assetDao.getTotalLiabilities().first()

        // then
        assertEquals(-8000.0, total ?: 0.0, 0.001)
    }

    @Test
    fun `getTotalLiabilities - 无负债时返回 null`() = runBlocking {
        // when
        val total = assetDao.getTotalLiabilities().first()

        // then
        assertNull(total)
    }

    @Test
    fun `getTotalAssets - 不计算隐藏资产`() = runBlocking {
        // given - 一个正常资产，一个隐藏资产
        assetDao.insertAsset(createAssetEntity(type = AssetType.CASH, balance = 10000.0, isHidden = false))
        assetDao.insertAsset(createAssetEntity(type = AssetType.CASH, balance = 50000.0, isHidden = true))

        // when
        val total = assetDao.getTotalAssets().first()

        // then - 只计算非隐藏的
        assertEquals(10000.0, total ?: 0.0, 0.001)
    }

    @Test
    fun `getTotalLiabilities - 不计算隐藏负债`() = runBlocking {
        // given - 一个正常负债，一个隐藏负债
        assetDao.insertAsset(createAssetEntity(type = AssetType.CREDIT_CARD, balance = -5000.0, isHidden = false))
        assetDao.insertAsset(createAssetEntity(type = AssetType.CREDIT_CARD, balance = -30000.0, isHidden = true))

        // when
        val total = assetDao.getTotalLiabilities().first()

        // then - 只计算非隐藏的
        assertEquals(-5000.0, total ?: 0.0, 0.001)
    }

    // ==================== 辅助方法 ====================

    private suspend fun insertTestAssets(count: Int) {
        repeat(count) {
            assetDao.insertAsset(
                createAssetEntity(name = "资产$it", type = AssetType.entries[it % AssetType.entries.size])
            )
        }
    }

    private fun createAssetEntity(
        id: Long = 0,
        name: String = "默认资产",
        type: AssetType = AssetType.BANK_CARD,
        balance: Double = 1000.0,
        isHidden: Boolean = false,
        icon: String = "💳",
        color: String = "#4CAF50"
    ): AssetEntity {
        return AssetEntity(
            id = id,
            name = name,
            type = type,
            balance = balance,
            isHidden = isHidden,
            icon = icon,
            color = color
        )
    }
}
