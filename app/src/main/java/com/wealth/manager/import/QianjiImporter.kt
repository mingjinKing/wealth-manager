package com.wealth.manager.import

import android.content.Context
import android.net.Uri
import com.wealth.manager.data.dao.CategoryDao
import com.wealth.manager.data.dao.ExpenseDao
import com.wealth.manager.data.entity.CategoryEntity
import com.wealth.manager.data.entity.ExpenseEntity
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 钱迹 CSV 导入器
 *
 * CSV 列结构（18列）:
 * 0=ID, 1=时间, 2=分类, 3=二级分类, 4=类型, 5=金额, 6=币种,
 * 7=账户1, 8=账户2, 9=备注, ...
 *
 * 导入规则:
 * - 只导入"支出"，跳过"收入"
 * - 分类不存在则自动创建
 * - 备注最多50字
 */
class QianjiImporter(
    private val context: Context,
    private val categoryDao: CategoryDao,
    private val expenseDao: ExpenseDao
) {
    // 钱迹分类名 → 透视镜分类名（null表示跳过该记录）
    private val categoryNameMapping = mapOf(
        "餐饮" to "餐饮",
        "日用品" to "日用品",
        "其它" to "其他",
        "工资" to null,   // 收入，跳过
        "外快" to null,    // 收入，跳过
        "收红包" to null,  // 收入，跳过
        "报销" to null,    // 收入，跳过
        "公积金" to null,  // 收入，跳过
        "激励性资金" to null  // 收入，跳过
    )

    // 默认分类（用于精确匹配已存在的分类）
    private val defaultCategoryNames = setOf(
        "餐饮", "购物", "交通", "娱乐", "居住", "医疗", "学习", "其他"
    )

    private val dateFormats = listOf(
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA),
        SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
    )

    data class ImportRecord(
        val date: Long,
        val categoryName: String,
        val categoryIcon: String,
        val categoryColor: String,
        val amount: Double,
        val note: String,
        val willSkip: Boolean,
        val skipReason: String?
    )

    data class ImportResult(
        val totalRows: Int,
        val expenseRecords: List<ImportRecord>,
        val skippedRecords: List<ImportRecord>,
        val newCategoriesWillBeCreated: List<String>
    )

    suspend fun parse(uri: Uri): ImportResult {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("无法打开文件")

        val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
        val lines = reader.readLines()
        reader.close()

        if (lines.isEmpty()) {
            return ImportResult(0, emptyList(), emptyList(), emptyList())
        }

        // 跳过表头
        val dataLines = lines.drop(1)
        val allRecords = mutableListOf<ImportRecord>()
        val newCategoryNames = mutableSetOf<String>()

        for (line in dataLines) {
            if (line.isBlank()) continue
            val record = parseLine(line, newCategoryNames)
            allRecords.add(record)
        }

        val expenseRecords = allRecords.filter { !it.willSkip }
        val skippedRecords = allRecords.filter { it.willSkip }

        return ImportResult(
            totalRows = dataLines.size,
            expenseRecords = expenseRecords,
            skippedRecords = skippedRecords,
            newCategoriesWillBeCreated = newCategoryNames.toList()
        )
    }

    private fun parseLine(line: String, newCategoryNames: MutableSet<String>): ImportRecord {
        val fields = parseCsvLine(line)
        if (fields.size < 6) {
            return ImportRecord(
                date = System.currentTimeMillis(),
                categoryName = "",
                categoryIcon = "",
                categoryColor = "",
                amount = 0.0,
                note = "",
                willSkip = true,
                skipReason = "列数不足"
            )
        }

        val type = fields.getOrNull(4) ?: ""
        val amountStr = fields.getOrNull(5) ?: "0"
        val amount = amountStr.toDoubleOrNull() ?: 0.0

        // 只导入支出，跳过收入
        if (type != "支出") {
            return ImportRecord(
                date = 0,
                categoryName = fields.getOrNull(2) ?: "",
                categoryIcon = "",
                categoryColor = "",
                amount = amount,
                note = "",
                willSkip = true,
                skipReason = "非支出类型（$type）"
            )
        }

        val categoryName = fields.getOrNull(2) ?: "其他"
        val note = fields.getOrNull(9)?.take(50) ?: ""
        val dateStr = fields.getOrNull(1) ?: ""

        val date = parseDate(dateStr)

        // 确定分类
        val mappedName = categoryNameMapping[categoryName]
        val finalName: String
        val isNew: Boolean

        if (mappedName != null) {
            // 有显式映射（可能是null=跳过，或重映射）
            if (mappedName == "其他") {
                finalName = "其他"
                isNew = false
            } else {
                finalName = mappedName
                isNew = false
            }
        } else if (categoryNameMapping[categoryName] == null && categoryName !in defaultCategoryNames) {
            // 未知分类 → 自动创建（收入类型已在上面过滤）
            finalName = categoryName
            isNew = true
            newCategoryNames.add(categoryName)
        } else {
            finalName = categoryName
            isNew = false
        }

        // 生成图标和颜色
        val icon = getCategoryIcon(finalName)
        val color = getCategoryColor(finalName)

        return ImportRecord(
            date = date,
            categoryName = finalName,
            categoryIcon = icon,
            categoryColor = color,
            amount = amount,
            note = note,
            willSkip = false,
            skipReason = null
        )
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var inQuotes = false
        val current = StringBuilder()

        for (char in line) {
            when {
                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    result.add(current.toString().trim())
                    current.clear()
                }
                else -> current.append(char)
            }
        }
        result.add(current.toString().trim())
        return result
    }

    private fun parseDate(dateStr: String): Long {
        for (format in dateFormats) {
            try {
                return format.parse(dateStr)?.time ?: continue
            } catch (_: Exception) {
                continue
            }
        }
        return System.currentTimeMillis()
    }

    private fun getCategoryIcon(name: String): String {
        return when (name) {
            "餐饮" -> "\uD83C\uDF57"
            "购物" -> "\uD83D\uDECD"
            "交通" -> "\uD83D\uDE8C"
            "交通通讯" -> "\uD83D\uDE8C"
            "娱乐" -> "\uD83C\uDFAE"
            "居住" -> "\uD83C\uDFE0"
            "医疗" -> "\uD83C\uDFE5"
            "学习" -> "\uD83D\uDCDA"
            "学习成长" -> "\uD83D\uDCDA"
            "其他" -> "\uD83D\uDCCB"
            "日用品" -> "\uD83D\uDED8"
            "形象建设" -> "\uD83D\uDC84"
            "兴趣爱好" -> "\uD83C\uDFB8"
            "人情人脉" -> "\u2764\uFE0F"
            "应急备用" -> "\uD83D\uDCB0"
            "身心健康" -> "\uD83D\uDCAA"
            "基金" -> "\uD83D\uDCC8"
            "资金蓄水池" -> "\uD83C\uDFE6"
            else -> "\uD83D\uDCCB"
        }
    }

    private fun getCategoryColor(name: String): String {
        val colors = mapOf(
            "餐饮" to "#ffc880",
            "购物" to "#4A90D9",
            "交通" to "#E55B5B",
            "交通通讯" to "#E55B5B",
            "娱乐" to "#9C27B0",
            "居住" to "#4CAF50",
            "医疗" to "#F44336",
            "学习" to "#2196F3",
            "学习成长" to "#2196F3",
            "其他" to "#9E9E9E",
            "日用品" to "#795548",
            "形象建设" to "#E91E63",
            "兴趣爱好" to "#FF5722",
            "人情人脉" to "#F44336",
            "应急备用" to "#FF9800",
            "身心健康" to "#00BCD4",
            "基金" to "#3F51B5",
            "资金蓄水池" to "#009688"
        )
        return colors[name] ?: "#9E9E9E"
    }

    /**
     * 执行导入：先创建新分类，再批量插入账单
     */
    suspend fun executeImport(records: List<ImportRecord>): Int {
        // 先创建所有新分类
        for (name in records.map { it.categoryName }.distinct()) {
            val existing = categoryDao.getCategoryByName(name)
            if (existing == null) {
                val icon = getCategoryIcon(name)
                val color = getCategoryColor(name)
                categoryDao.insertCategory(
                    CategoryEntity(
                        name = name,
                        icon = icon,
                        color = color,
                        isDefault = false
                    )
                )
            }
        }

        // 插入所有账单
        var count = 0
        for (record in records) {
            val category = categoryDao.getCategoryByName(record.categoryName) ?: continue
            val expense = ExpenseEntity(
                amount = record.amount,
                categoryId = category.id,
                date = record.date,
                note = record.note
            )
            expenseDao.insertExpense(expense)
            count++
        }
        return count
    }
}
