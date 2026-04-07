package com.wealth.manager.data.dao

import androidx.room.*
import com.wealth.manager.data.entity.AssetEntity
import com.wealth.manager.data.entity.AssetType
import kotlinx.coroutines.flow.Flow

@Dao
interface AssetDao {
    @Query("SELECT * FROM assets ORDER BY type ASC, name ASC")
    fun getAllAssets(): Flow<List<AssetEntity>>

    @Query("SELECT * FROM assets WHERE type = :type")
    fun getAssetsByType(type: AssetType): Flow<List<AssetEntity>>

    @Query("SELECT * FROM assets WHERE id = :id")
    suspend fun getAssetById(id: Long): AssetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAsset(asset: AssetEntity): Long

    @Update
    suspend fun updateAsset(asset: AssetEntity)

    @Delete
    suspend fun deleteAsset(asset: AssetEntity)

    @Query("DELETE FROM assets")
    suspend fun deleteAllAssets()

    // 修改：只计算未隐藏且 balance > 0 的资产（除信用卡和负债借款外）
    @Query("SELECT SUM(balance) FROM assets WHERE type != 'CREDIT_CARD' AND NOT (type = 'LOAN' AND balance < 0) AND isHidden = 0")
    fun getTotalAssets(): Flow<Double?>

    // 修改：只计算未隐藏且 balance < 0 的负债（信用卡或负债借款）
    @Query("SELECT SUM(balance) FROM assets WHERE (type = 'CREDIT_CARD' OR (type = 'LOAN' AND balance < 0)) AND isHidden = 0")
    fun getTotalLiabilities(): Flow<Double?>
}
