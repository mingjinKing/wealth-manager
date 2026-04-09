package com.wealth.manager.export

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.wealth.manager.data.dao.*
import com.wealth.manager.data.entity.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 全量数据导出器 - 覆盖应用所有核心业务与配置数据
 */
class DataExporter(
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
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)

    suspend fun exportToDownloads(): String? = withContext(Dispatchers.IO) {
        try {
            val expenses = expenseDao.getAllExpenses().first()
            val assets = assetDao.getAllAssets().first()
            val categories = categoryDao.getAllCategories().first()
            val budgets = budgetDao.getAllBudgets().first()
            val weekStats = weekStatsDao.getAllWeekStats().first()
            val sessions = sessionDao.getAllSessionsOnce()
            val memories = memoryDao.getAllMemoryOnce()
            
            // 读取成就与目标配置 (SharedPreferences)
            val achPrefs = context.getSharedPreferences("achievements_prefs", Context.MODE_PRIVATE)
            val themePrefs = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)

            val json = JSONObject().apply {
                put("version", 4) // 升级全量备份版本
                put("exportTime", timestampFormat.format(Date()))
                
                // 1. 业务数据
                put("expenses", JSONArray().apply {
                    expenses.forEach { e ->
                        put(JSONObject().apply {
                            put("id", e.id); put("amount", e.amount); put("categoryId", e.categoryId)
                            put("note", e.note); put("date", e.date); put("assetId", e.assetId ?: JSONObject.NULL)
                            put("createdAt", e.createdAt)
                        })
                    }
                })
                put("assets", JSONArray().apply {
                    assets.forEach { a ->
                        put(JSONObject().apply {
                            put("id", a.id); put("name", a.name); put("type", a.type.name)
                            put("balance", a.balance); put("icon", a.icon); put("isHidden", a.isHidden)
                            put("customType", a.customType ?: JSONObject.NULL)
                            put("color", a.color)
                            put("createdAt", a.createdAt)
                        })
                    }
                })
                put("categories", JSONArray().apply {
                    categories.forEach { c ->
                        put(JSONObject().apply {
                            put("id", c.id); put("name", c.name); put("icon", c.icon)
                            put("type", c.type); put("isDefault", c.isDefault)
                            put("color", c.color)
                        })
                    }
                })
                
                // 2. AI 对话与记忆
                put("sessions", JSONArray().apply {
                    sessions.forEach { s ->
                        val msgs = messageDao.getMessagesBySessionOnce(s.id)
                        put(JSONObject().apply {
                            put("id", s.id); put("title", s.title)
                            put("createdAt", s.createdAt); put("updatedAt", s.updatedAt)
                            put("messages", JSONArray().apply {
                                msgs.forEach { m ->
                                    put(JSONObject().apply {
                                        put("id", m.id); put("isUser", m.isUser)
                                        put("content", m.content); put("createdAt", m.createdAt)
                                        put("isUseful", m.isUseful); put("isLiked", m.isLiked)
                                    })
                                }
                            })
                        })
                    }
                })
                put("memories", JSONArray().apply {
                    memories.forEach { m ->
                        put(JSONObject().apply {
                            put("id", m.id); put("key", m.key); put("summary", m.summary)
                            put("value", m.value); put("updatedAt", m.updatedAt)
                            put("source", m.source); put("confidence", m.confidence.toDouble())
                            put("createdAt", m.createdAt)
                        })
                    }
                })

                // 3. 应用配置 (攒点钱/偏好)
                put("config", JSONObject().apply {
                    put("asset_goal", achPrefs.getFloat("asset_goal", 0f).toDouble())
                    put("goal_date", achPrefs.getLong("goal_date", 0L))
                    put("goal_start_date", achPrefs.getLong("goal_start_date", 0L))
                    put("primary_color", themePrefs.getInt("primary_color", 0))
                    put("show_asset_selection", themePrefs.getBoolean("show_asset_selection", false))
                })
                
                put("budgets", JSONArray().apply {
                    budgets.forEach { b -> 
                        put(JSONObject().apply { 
                            put("amount", b.amount); put("month", b.month) 
                            put("categoryId", b.categoryId ?: JSONObject.NULL)
                        }) 
                    }
                })

                put("weekStats", JSONArray().apply {
                    weekStats.forEach { ws ->
                        put(JSONObject().apply {
                            put("weekStartDate", ws.weekStartDate)
                            put("totalAmount", ws.totalAmount)
                            put("categoryBreakdown", ws.categoryBreakdown)
                            put("wowTriggered", ws.wowTriggered)
                            put("savedAmount", ws.savedAmount)
                        })
                    }
                })
            }

            val fileName = "知财_全量备份_${dateFormat.format(Date())}.json"
            saveToDownloads(fileName, json.toString(2))
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun saveToDownloads(fileName: String, content: String): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/json")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues) ?: return null
            context.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
            contentValues.clear()
            contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
            context.contentResolver.update(uri, contentValues, null, null)
            "Downloads/$fileName"
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, fileName)
            FileOutputStream(file).use { it.write(content.toByteArray()) }
            file.absolutePath
        }
    }
}
