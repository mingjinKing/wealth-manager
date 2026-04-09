package com.wealth.manager.export

import android.content.Context
import com.wealth.manager.data.dao.*
import com.wealth.manager.data.entity.*
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
    private val weekStatsDao: WeekStatsDao,
    private val sessionDao: SessionDao,
    private val messageDao: MessageDao,
    private val memoryDao: MemoryDao
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
            sessionDao.deleteAllSessions()
            messageDao.deleteAllMessages()
            memoryDao.deleteAllMemory()

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

            // 7. 导入 AI 对话与记忆
            val sessions = json.optJSONArray("sessions")
            if (sessions != null) {
                for (i in 0 until sessions.length()) {
                    val obj = sessions.getJSONObject(i)
                    val sessionId = obj.optString("id", java.util.UUID.randomUUID().toString())
                    val session = SessionEntity(
                        id = sessionId,
                        title = obj.optString("title", "新对话"),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                    )
                    sessionDao.insert(session)
                    
                    val messages = obj.optJSONArray("messages")
                    if (messages != null) {
                        for (j in 0 until messages.length()) {
                            val mObj = messages.getJSONObject(j)
                            val message = MessageEntity(
                                id = mObj.optString("id", java.util.UUID.randomUUID().toString()),
                                sessionId = sessionId,
                                isUser = mObj.getBoolean("isUser"),
                                content = mObj.getString("content"),
                                createdAt = mObj.optLong("createdAt", System.currentTimeMillis()),
                                isUseful = mObj.optBoolean("isUseful", false),
                                isLiked = mObj.optBoolean("isLiked", false)
                            )
                            messageDao.insert(message)
                        }
                    }
                    totalImported++
                }
            }

            val memories = json.optJSONArray("memories")
            if (memories != null) {
                for (i in 0 until memories.length()) {
                    val obj = memories.getJSONObject(i)
                    val memory = MemoryEntity(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        key = obj.getString("key"),
                        summary = obj.optString("summary", ""),
                        value = obj.getString("value"),
                        source = obj.optString("source", "user_input"),
                        confidence = obj.optDouble("confidence", 1.0).toFloat(),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                    )
                    memoryDao.insert(memory)
                    totalImported++
                }
            }

            // 8. 恢复应用配置 (SharedPreferences)
            val config = json.optJSONObject("config")
            if (config != null) {
                // 恢复攒钱目标配置
                val achPrefs = context.getSharedPreferences("achievements_prefs", Context.MODE_PRIVATE)
                achPrefs.edit().apply {
                    if (config.has("asset_goal")) putFloat("asset_goal", config.getDouble("asset_goal").toFloat())
                    if (config.has("goal_date")) putLong("goal_date", config.getLong("goal_date"))
                    if (config.has("goal_start_date")) putLong("goal_start_date", config.getLong("goal_start_date"))
                    apply()
                }

                // 恢复主题与显示配置
                val themePrefs = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
                themePrefs.edit().apply {
                    if (config.has("primary_color")) putInt("primary_color", config.getInt("primary_color"))
                    if (config.has("show_asset_selection")) putBoolean("show_asset_selection", config.getBoolean("show_asset_selection"))
                    apply()
                }

                // 恢复用户信息 (如果有)
                val profilePrefs = context.getSharedPreferences("user_profile_prefs", Context.MODE_PRIVATE)
                val profileConfig = config.optJSONObject("user_profile")
                if (profileConfig != null) {
                    profilePrefs.edit().apply {
                        profileConfig.keys().forEach { key ->
                            val value = profileConfig.get(key)
                            when (value) {
                                is String -> putString(key, value)
                                is Int -> putInt(key, value)
                                is Long -> putLong(key, value)
                                is Boolean -> putBoolean(key, value)
                                is Double -> putFloat(key, value.toFloat())
                            }
                        }
                        apply()
                    }
                }
            }

            totalImported
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
