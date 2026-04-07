package com.wealth.manager.export

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.wealth.manager.data.dao.AssetDao
import com.wealth.manager.data.dao.BudgetDao
import com.wealth.manager.data.dao.CategoryDao
import com.wealth.manager.data.dao.ExpenseDao
import com.wealth.manager.data.dao.WeekStatsDao
import com.wealth.manager.data.entity.AssetEntity
import com.wealth.manager.data.entity.BudgetEntity
import com.wealth.manager.data.entity.CategoryEntity
import com.wealth.manager.data.entity.ExpenseEntity
import com.wealth.manager.data.entity.WeekStatsEntity
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
 * 数据导出器
 * 导出所有数据到 JSON 文件
 */
class DataExporter(
    private val context: Context,
    private val expenseDao: ExpenseDao,
    private val assetDao: AssetDao,
    private val categoryDao: CategoryDao,
    private val budgetDao: BudgetDao,
    private val weekStatsDao: WeekStatsDao
) {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)

    /**
     * 导出全量数据到 Downloads 文件夹
     * @return 导出文件的路径，失败返回 null
     */
    suspend fun exportToDownloads(): String? = withContext(Dispatchers.IO) {
        try {
            val expenses = expenseDao.getAllExpenses().first()
            val assets = assetDao.getAllAssets().first()
            val categories = categoryDao.getAllCategories().first()
            val budgets = budgetDao.getAllBudgets().first()
            val weekStats = weekStatsDao.getAllWeekStats().first()

            val json = JSONObject().apply {
                put("version", 2) // 升级版本号，兼容旧备份
                put("exportTime", timestampFormat.format(Date()))
                put("expenses", JSONArray().apply {
                    expenses.forEach { expense ->
                        put(JSONObject().apply {
                            put("id", expense.id)
                            put("amount", expense.amount)
                            put("categoryId", expense.categoryId)
                            put("note", expense.note)
                            put("date", expense.date)
                            put("assetId", expense.assetId ?: JSONObject.NULL)
                            put("createdAt", expense.createdAt)
                        })
                    }
                })
                put("assets", JSONArray().apply {
                    assets.forEach { asset ->
                        put(JSONObject().apply {
                            put("id", asset.id)
                            put("name", asset.name)
                            put("type", asset.type.name)
                            put("customType", asset.customType ?: JSONObject.NULL)
                            put("balance", asset.balance)
                            put("icon", asset.icon)
                            put("color", asset.color)
                            put("isHidden", asset.isHidden)
                            put("createdAt", asset.createdAt)
                        })
                    }
                })
                put("categories", JSONArray().apply {
                    categories.forEach { category ->
                        put(JSONObject().apply {
                            put("id", category.id)
                            put("name", category.name)
                            put("icon", category.icon)
                            put("color", category.color)
                            put("type", category.type)
                            put("isDefault", category.isDefault)
                        })
                    }
                })
                put("budgets", JSONArray().apply {
                    budgets.forEach { budget ->
                        put(JSONObject().apply {
                            put("id", budget.id)
                            put("amount", budget.amount)
                            put("month", budget.month)
                            put("categoryId", budget.categoryId ?: JSONObject.NULL)
                        })
                    }
                })
                put("weekStats", JSONArray().apply {
                    weekStats.forEach { stats ->
                        put(JSONObject().apply {
                            put("weekStartDate", stats.weekStartDate)
                            put("totalAmount", stats.totalAmount)
                            put("categoryBreakdown", stats.categoryBreakdown)
                            put("wowTriggered", stats.wowTriggered)
                            put("savedAmount", stats.savedAmount)
                        })
                    }
                })
            }

            val fileName = "wealth_backup_${dateFormat.format(Date())}.json"
            val filePath = saveToDownloads(fileName, json.toString(2))
            filePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun saveToDownloads(fileName: String, content: String): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ 使用 MediaStore
            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/json")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }

            val uri = context.contentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                contentValues
            ) ?: return null

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(content.toByteArray())
            }

            contentValues.clear()
            contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
            context.contentResolver.update(uri, contentValues, null, null)

            "Downloads/$fileName"
        } else {
            // Android 9 及以下
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, fileName)
            FileOutputStream(file).use { outputStream ->
                outputStream.write(content.toByteArray())
            }
            file.absolutePath
        }
    }
}
