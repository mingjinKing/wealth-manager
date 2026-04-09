package com.wealth.manager.ui

import com.wealth.manager.data.entity.AssetEntity
import com.wealth.manager.data.entity.AssetType
import com.wealth.manager.data.repository.AssetRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AssetViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: AssetViewModel

    private val mockAssetRepository: AssetRepository = mockk()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = AssetViewModel(mockAssetRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadAssets - 加载所有资产`() = runTest {
        // given
        val assets = listOf(
            AssetEntity(id = 1, name = "银行卡", type = AssetType.BANK_CARD, balance = 10000.0),
            AssetEntity(id = 2, name = "现金", type = AssetType.CASH, balance = 2000.0),
            AssetEntity(id = 3, name = "信用卡", type = AssetType.CREDIT_CARD, balance = -5000.0)
        )

        coEvery { mockAssetRepository.getAllAssets() } returns flowOf(assets)

        // when
        viewModel.loadAssets()
        testDispatcher.scheduler.advanceUntilIdle()

        // then
        coVerify { mockAssetRepository.getAllAssets() }
        val result = viewModel.assets.value
        assertEquals(3, result.size)
    }

    @Test
    fun `loadAssets - 空数据库返回空列表`() = runTest {
        // given
        coEvery { mockAssetRepository.getAllAssets() } returns flowOf(emptyList())

        // when
        viewModel.loadAssets()
        testDispatcher.scheduler.advanceUntilIdle()

        // then
        val result = viewModel.assets.value
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getTotalAssets - 计算总资产`() = runTest {
        // given
        val assets = listOf(
            AssetEntity(id = 1, name = "银行卡", type = AssetType.BANK_CARD, balance = 10000.0),
            AssetEntity(id = 2, name = "现金", type = AssetType.CASH, balance = 2000.0)
        )

        coEvery { mockAssetRepository.getAllAssets() } returns flowOf(assets)
        coEvery { mockAssetRepository.getTotalAssets() } returns flowOf(12000.0)

        // when
        viewModel.loadAssets()
        testDispatcher.scheduler.advanceUntilIdle()

        // then
        val total = viewModel.totalAssets.value
        assertEquals(12000.0, total, 0.001)
    }

    @Test
    fun `getTotalLiabilities - 计算总负债`() = runTest {
        // given
        val liabilities = listOf(
            AssetEntity(id = 3, name = "信用卡", type = AssetType.CREDIT_CARD, balance = -5000.0)
        )

        coEvery { mockAssetRepository.getAllAssets() } returns flowOf(liabilities)
        coEvery { mockAssetRepository.getTotalLiabilities() } returns flowOf(-5000.0)

        // when
        viewModel.loadAssets()
        testDispatcher.scheduler.advanceUntilIdle()

        // then
        val total = viewModel.totalLiabilities.value
        assertEquals(-5000.0, total, 0.001)
    }

    @Test
    fun `addAsset - 添加资产`() = runTest {
        // given
        val newAsset = AssetEntity(
            name = "余额宝",
            type = AssetType.INVESTMENT,
            balance = 5000.0,
            icon = "💰",
            color = "#4CAF50"
        )

        coEvery { mockAssetRepository.insertAsset(any()) } returns 1L

        // when
        viewModel.addAsset(newAsset)
        testDispatcher.scheduler.advanceUntilIdle()

        // then
        coVerify { mockAssetRepository.insertAsset(newAsset) }
    }

    @Test
    fun `updateAsset - 更新资产`() = runTest {
        // given
        val asset = AssetEntity(
            id = 1,
            name = "银行卡",
            type = AssetType.BANK_CARD,
            balance = 10000.0
        )

        coEvery { mockAssetRepository.updateAsset(any()) } returns Unit

        // when
        viewModel.updateAsset(asset)
        testDispatcher.scheduler.advanceUntilIdle()

        // then
        coVerify { mockAssetRepository.updateAsset(asset) }
    }

    @Test
    fun `deleteAsset - 删除资产`() = runTest {
        // given
        val asset = AssetEntity(
            id = 1,
            name = "待删除资产",
            type = AssetType.BANK_CARD,
            balance = 1000.0
        )

        coEvery { mockAssetRepository.deleteAsset(any()) } returns Unit

        // when
        viewModel.deleteAsset(asset)
        testDispatcher.scheduler.advanceUntilIdle()

        // then
        coVerify { mockAssetRepository.deleteAsset(asset) }
    }

    @Test
    fun `getAssetById - 按 ID 获取资产`() {
        // given
        val assets = listOf(
            AssetEntity(id = 1, name = "资产1", type = AssetType.BANK_CARD, balance = 1000.0),
            AssetEntity(id = 2, name = "资产2", type = AssetType.CASH, balance = 2000.0)
        )

        // when
        val asset = viewModel.getAssetById(assets, 2)

        // then
        assertEquals("资产2", asset?.name)
    }

    @Test
    fun `getAssetById - 不存在的 ID 返回 null`() {
        // given
        val assets = listOf(
            AssetEntity(id = 1, name = "资产1", type = AssetType.BANK_CARD, balance = 1000.0)
        )

        // when
        val asset = viewModel.getAssetById(assets, 999)

        // then
        assertNull(asset)
    }

    @Test
    fun `filterAssetsByType - 按类型过滤`() {
        // given
        val assets = listOf(
            AssetEntity(id = 1, name = "银行卡", type = AssetType.BANK_CARD, balance = 10000.0),
            AssetEntity(id = 2, name = "现金", type = AssetType.CASH, balance = 2000.0),
            AssetEntity(id = 3, name = "信用卡", type = AssetType.CREDIT_CARD, balance = -5000.0)
        )

        // when
        val cashAssets = viewModel.filterAssetsByType(assets, AssetType.CASH)
        val cardAssets = viewModel.filterAssetsByType(assets, AssetType.BANK_CARD)
        val creditAssets = viewModel.filterAssetsByType(assets, AssetType.CREDIT_CARD)

        // then
        assertEquals(1, cashAssets.size)
        assertEquals("现金", cashAssets[0].name)
        
        assertEquals(1, cardAssets.size)
        assertEquals("银行卡", cardAssets[0].name)
        
        assertEquals(1, creditAssets.size)
        assertEquals("信用卡", creditAssets[0].name)
    }

    @Test
    fun `calculateNetWorth - 计算净资产`() = runTest {
        // given
        coEvery { mockAssetRepository.getTotalAssets() } returns flowOf(15000.0)
        coEvery { mockAssetRepository.getTotalLiabilities() } returns flowOf(-3000.0)

        // when
        viewModel.loadAssets()
        testDispatcher.scheduler.advanceUntilIdle()

        // then
        val netWorth = viewModel.netWorth.value
        assertEquals(12000.0, netWorth, 0.001)
    }

    @Test
    fun `isValidAsset - 有效资产返回 true`() {
        // given
        val asset = AssetEntity(
            name = "测试资产",
            type = AssetType.BANK_CARD,
            balance = 1000.0
        )

        // when
        val isValid = viewModel.isValidAsset(asset)

        // then
        assertTrue(isValid)
    }

    @Test
    fun `isValidAsset - 名称为空返回 false`() {
        // given
        val asset = AssetEntity(
            name = "",
            type = AssetType.BANK_CARD,
            balance = 1000.0
        )

        // when
        val isValid = viewModel.isValidAsset(asset)

        // then
        assertFalse(isValid)
    }

    @Test
    fun `isValidAsset - 余额为零返回 true`() {
        // given
        val asset = AssetEntity(
            name = "零余额资产",
            type = AssetType.BANK_CARD,
            balance = 0.0
        )

        // when
        val isValid = viewModel.isValidAsset(asset)

        // then
        assertTrue(isValid)
    }

    @Test
    fun `refresh - 刷新数据`() = runTest {
        // given
        coEvery { mockAssetRepository.getAllAssets() } returns flowOf(emptyList())
        coEvery { mockAssetRepository.getTotalAssets() } returns flowOf(null)
        coEvery { mockAssetRepository.getTotalLiabilities() } returns flowOf(null)

        // when
        viewModel.refresh()
        testDispatcher.scheduler.advanceUntilIdle()

        // then
        coVerify { mockAssetRepository.getAllAssets() }
        coVerify { mockAssetRepository.getTotalAssets() }
        coVerify { mockAssetRepository.getTotalLiabilities() }
    }

    @Test
    fun `clearError - 清除错误`() {
        // given
        viewModel.setError("测试错误")

        // when
        viewModel.clearError()

        // then
        assertNull(viewModel.error.value)
    }

    @Test
    fun `setError - 设置错误信息`() {
        // when
        viewModel.setError("保存失败")

        // then
        assertEquals("保存失败", viewModel.error.value)
    }

    @Test
    fun `isLoading - 初始状态为 false`() {
        // when
        val isLoading = viewModel.isLoading.value

        // then
        assertFalse(isLoading)
    }

    @Test
    fun `error - 初始状态为 null`() {
        // when
        val error = viewModel.error.value

        // then
        assertNull(error)
    }

    @Test
    fun `getAssetTypeDisplayName - 获取类型显示名称`() {
        // when
        val bankCardName = viewModel.getAssetTypeDisplayName(AssetType.BANK_CARD)
        val cashName = viewModel.getAssetTypeDisplayName(AssetType.CASH)
        val creditCardName = viewModel.getAssetTypeDisplayName(AssetType.CREDIT_CARD)

        // then
        assertTrue(bankCardName.isNotEmpty())
        assertTrue(cashName.isNotEmpty())
        assertTrue(creditCardName.isNotEmpty())
    }

    @Test
    fun `getAssetTypeIcon - 获取类型图标`() {
        // when
        val bankCardIcon = viewModel.getAssetTypeIcon(AssetType.BANK_CARD)
        val cashIcon = viewModel.getAssetTypeIcon(AssetType.CASH)
        val creditCardIcon = viewModel.getAssetTypeIcon(AssetType.CREDIT_CARD)

        // then
        assertTrue(bankCardIcon.isNotEmpty())
        assertTrue(cashIcon.isNotEmpty())
        assertTrue(creditCardIcon.isNotEmpty())
    }
}
