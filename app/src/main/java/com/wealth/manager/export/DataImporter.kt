package com.wealth.manager.export

import android.content.Context
import com.wealth.manager.data.dao.AssetDao
import com.wealth.manager.data.dao.BudgetDao
import com.wealth.manager.data.dao.CategoryDao
import com.wealth.manager.data.dao.ExpenseDao
import com.wealth.manager.data.dao.WeekStatsDao
import com.wealth.manager.data.entity.AssetEntity
import com.wealth.manager.data.entity.AssetType
import com.wealth.manager.data.entity.BudgetEntity
import com.wealth.manager.data.entity.CategoryEntity
import com.wealth.manager.data.entity.ExpenseEntity
import com.wealth.manager.data.entity.WeekStatsEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 数据导入器（用于从备份恢复）
 * 支持从 JSON 文件导入全部数据
 */
class DataImporter(
    private val context: Context,
    private val expenseDao: ExpenseDao,
    private val assetDao: AssetDao,
    private val categoryDao: CategoryDao,
    private val budgetDao: BudgetDao,
    private val weekStatsDao: WeekStatsDao
) {
    /**
     * 从 JSON 内容导入全部数据（覆盖模式）
     * @param jsonContent JSON 文件内容
     * @return 导入的记录数，失败返回 null
     */
    suspend fun importAll(jsonContent: String): Int? = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject(jsonContent)
            var totalImported = 0

            // 1. 清空现有数据（覆盖模式）
            expenseDao.deleteAllExpenses()
            assetDao.deleteAllAssets()
            categoryDao.deleteAllCategories()
            budgetDao.deleteAllBudgets()
            weekStatsDao.deleteAllWeekStats()

            // 2. 导入分类，并记录旧ID到新ID的映射
            val categoryIdMapping = mutableMapOf<Long, Long>()
            val categories = json.optJSONArray("categories")
            if (categories != null) {
                for (i in 0 until categories.length()) {
                    val obj = categories.getJSONObject(i)
                    val oldId = obj.getLong("id")
                    val category = CategoryEntity(
                        id = 0, // 自增
                        name = obj.getString("name"),
                        icon = obj.optString("icon", ""),
                        color = obj.optString("color", "#4CAF50"),
                        type = obj.optString("type", "EXPENSE"),
                        isDefault = obj.optBoolean("isDefault", false)
                    )
                    val newId = categoryDao.insertCategory(category)
                    categoryIdMapping[oldId] = newId
                    totalImported++
                }
            }

            // 3. 导入资产，并记录旧ID到新ID的映射
            val assetIdMapping = mutableMapOf<Long, Long>()
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val obj = assets.getJSONObject(i)
                    val oldId = obj.getLong("id")
                    val assetTypeStr = obj.optString("type", "CASH")
                    val assetType = try {
                        AssetType.valueOf(assetTypeStr)
                    } catch (e: Exception) {
                        AssetType.OTHER
                    }
                    val asset = AssetEntity(
                        id = 0, // 自增
                        name = obj.getString("name"),
                        type = assetType,
                        customType = if (obj.has("customType") && !obj.isNull("customType")) obj.getString("customType") else null,
                        balance = obj.optDouble("balance", 0.0),
                        icon = obj.optString("icon", "💰"),
                        color = obj.optString("color", "#4CAF50"),
                        isHidden = obj.optBoolean("isHidden", false),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                    val newId = assetDao.insertAsset(asset)
                    assetIdMapping[oldId] = newId
                    totalImported++
                }
            }

            // 4. 导入支出，使用新的categoryId和assetId
            val expenses = json.optJSONArray("expenses")
            if (expenses != null) {
                for (i in 0 until expenses.length()) {
                    val obj = expenses.getJSONObject(i)
                    val oldCategoryId = obj.optLong("categoryId", 0)
                    val oldAssetId = if (obj.has("assetId") && !obj.isNull("assetId")) obj.getLong("assetId") else null
                    
                    // 映射到新的ID
                    val newCategoryId = categoryIdMapping[oldCategoryId] ?: 0L
                    val newAssetId = oldAssetId?.let { assetIdMapping[it] }
                    
                    val expense = ExpenseEntity(
                        id = 0, // 自增
                        amount = obj.getDouble("amount"),
                        categoryId = newCategoryId,
                        date = obj.getLong("date"),
                        note = obj.optString("note", ""),
                        assetId = newAssetId,
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                    expenseDao.insertExpense(expense)
                    totalImported++
                }
            }

            // 5. 导入预算，使用新的categoryId
            val budgets = json.optJSONArray("budgets")
            if (budgets != null) {
                for (i in 0 until budgets.length()) {
                    val obj = budgets.getJSONObject(i)
                    val oldCategoryId = if (obj.has("categoryId") && !obj.isNull("categoryId")) obj.getLong("categoryId") else null
                    val newCategoryId = oldCategoryId?.let { categoryIdMapping[it] }
                    
                    val budget = BudgetEntity(
                        id = 0, // 自增
                        amount = obj.getDouble("amount"),
                        month = obj.getString("month"),
                        categoryId = newCategoryId
                    )
                    budgetDao.insertBudget(budget)
                    totalImported++
                }
            }

            // 6. 导入周统计
            val weekStats = json.optJSONArray("weekStats")
            if (weekStats != null) {
                for (i in 0 until weekStats.length()) {
                    val obj = weekStats.getJSONObject(i)
                    val stats = WeekStatsEntity(
                        weekStartDate = obj.getLong("weekStartDate"),
                        totalAmount = obj.optDouble("totalAmount", 0.0),
                        categoryBreakdown = obj.optString("categoryBreakdown", "[]"),
                        wowTriggered = obj.optBoolean("wowTriggered", false),
                        savedAmount = obj.optDouble("savedAmount", 0.0)
                    )
                    weekStatsDao.insertWeekStats(stats)
                    totalImported++
                }
            }

            totalImported
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
