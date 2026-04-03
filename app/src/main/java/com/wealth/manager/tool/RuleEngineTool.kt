package com.wealth.manager.tool

import com.wealth.manager.data.dao.ExpenseDao
import com.wealth.manager.data.dao.CategoryDao
import com.wealth.manager.data.entity.ExpenseEntity
import com.wealth.manager.rules.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 规则引擎工具 - 增强版 (支持指定日期范围)
 */
@Singleton
class RuleEngineTool @Inject constructor(
    private val expenseDao: ExpenseDao,
    private val categoryDao: CategoryDao
) : Tool {

    override val name = "rule_engine"
    override val description = """
        核心财务数据工具。
        - get_summary: 获取指定范围（或默认最近30天）的账单统计。
        - scale_analysis: 消费规模分析。
        - structure_analysis: 消费结构分析。
    """.trimIndent()

    override val parametersSchema = """
        {
            "type": "object",
            "properties": {
                "operation": {
                    "type": "string",
                    "enum": ["get_summary", "scale_analysis", "structure_analysis", "frequency_analysis"],
                    "description": "操作类型"
                },
                "startTime": {
                    "type": "integer",
                    "description": "开始时间戳（毫秒），可选"
                },
                "endTime": {
                    "type": "integer",
                    "description": "结束时间戳（毫秒），可选"
                },
                "data": {
                    "type": "object",
                    "description": "可选数据对象"
                }
            },
            "required": ["operation"]
        }
    """.trimIndent()

    override fun execute(arguments: String): String {
        return try {
            val json = JSONObject(arguments)
            val operation = json.getString("operation")
            val data = json.optJSONObject("data")
            
            val startTime = if (json.has("startTime")) json.getLong("startTime") else null
            val endTime = if (json.has("endTime")) json.getLong("endTime") else null

            val result = when (operation) {
                "get_summary" -> getSummary(startTime, endTime)
                "scale_analysis" -> scaleAnalysis(data, startTime, endTime)
                "structure_analysis" -> structureAnalysis(data, startTime, endTime)
                "frequency_analysis" -> frequencyAnalysis(data, startTime, endTime)
                else -> errorResult("Unknown operation: $operation")
            }

            result.toString()
        } catch (e: Exception) {
            errorResult("RuleEngineTool error: ${e.message}").toString()
        }
    }

    private fun getSummary(customStart: Long?, customEnd: Long?): JSONObject {
        val start: Long
        val end: Long

        if (customStart != null && customEnd != null) {
            start = customStart
            end = customEnd
        } else {
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, 23)
            calendar.set(Calendar.MINUTE, 59)
            calendar.set(Calendar.SECOND, 59)
            end = calendar.timeInMillis

            calendar.add(Calendar.DAY_OF_YEAR, -29)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            start = calendar.timeInMillis
        }

        val (expenses, categories) = runBlocking {
            val ex = expenseDao.getExpensesByDateRange(start, end).first()
            val cat = categoryDao.getAllCategories().first()
            ex to cat
        }

        val catNameMap = categories.associate { it.id to "${it.icon} ${it.name}" }
        val totalRaw = expenses.sumOf { it.amount }
        val total = if (totalRaw.isNaN() || totalRaw.isInfinite()) 0.0 else totalRaw
        
        val categoryStats = expenses.groupBy { it.categoryId }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
            .toList()
            .sortedByDescending { it.second }

        val categoryArray = JSONArray()
        categoryStats.take(10).forEach { (catId, amount) ->
            val catObj = JSONObject()
            catObj.put("category", catNameMap[catId] ?: "未知($catId)")
            catObj.put("amount", amount)
            val percentage = if (total > 0) amount / total else 0.0
            catObj.put("percentage", if (percentage.isNaN()) 0.0 else percentage)
            categoryArray.put(catObj)
        }

        val res = JSONObject()
        res.put("period_desc", if (customStart != null) "custom_range" else "last_30_days")
        res.put("total_expense", total)
        res.put("transaction_count", expenses.size)
        res.put("top_categories", categoryArray)
        res.put("status", "success")
        return res
    }

    private fun scaleAnalysis(data: JSONObject?, start: Long?, end: Long?): JSONObject {
        val valIn = data?.optDouble("total", Double.NaN) ?: Double.NaN
        val total = if (valIn.isNaN()) getSummaryTotal(start, end) else valIn
        return insightToJson(ScaleRule.buildInsight(if (total.isNaN()) 0.0 else total))
    }

    private fun structureAnalysis(data: JSONObject?, start: Long?, end: Long?): JSONObject {
        var name = data?.optString("topCategoryName", "") ?: ""
        var percentage = data?.optDouble("topPercentage", Double.NaN) ?: Double.NaN

        if (name.isEmpty() || percentage.isNaN()) {
            val summary = getSummary(start, end)
            val tops = summary.optJSONArray("top_categories")
            if (tops != null && tops.length() > 0) {
                val first = tops.getJSONObject(0)
                name = first.optString("category", "未知")
                percentage = first.optDouble("percentage", 0.0)
            }
        }

        val safeP = if (percentage.isNaN() || percentage.isInfinite()) 0.0f else percentage.toFloat()
        val threshold = data?.optDouble("threshold", 0.4)?.toFloat() ?: 0.4f
        
        val insight = if (safeP > threshold) {
            StructureRule.buildInsight(name, safeP)
        } else {
            Insight(
                type = InsightType.STRUCTURE_BIAS,
                message = "$name 占比 ${(safeP * 100).toInt()}%，结构正常。",
                metadata = mapOf("categoryName" to name, "percentage" to safeP, "biased" to false)
            )
        }
        return insightToJson(insight)
    }

    private fun frequencyAnalysis(data: JSONObject?, start: Long?, end: Long?): JSONObject {
        val countIn = data?.optInt("totalCount", -1) ?: -1
        val count = if (countIn == -1) getSummaryCount(start, end) else countIn
        return insightToJson(FrequencyRule.buildInsight(count))
    }

    private fun getSummaryTotal(start: Long?, end: Long?): Double {
        val t = getSummary(start, end).optDouble("total_expense", 0.0)
        return if (t.isNaN()) 0.0 else t
    }

    private fun getSummaryCount(start: Long?, end: Long?) = getSummary(start, end).optInt("transaction_count", 0)

    private fun insightToJson(insight: Insight): JSONObject {
        val json = JSONObject()
        json.put("type", insight.type.name)
        json.put("message", insight.message)
        val metadata = JSONObject()
        insight.metadata.forEach { (key, value) -> 
            val safeValue = if (value is Number) {
                val d = value.toDouble()
                if (d.isNaN() || d.isInfinite()) 0.0 else value
            } else value
            metadata.put(key, safeValue) 
        }
        json.put("metadata", metadata)
        return json
    }

    private fun errorResult(message: String): JSONObject {
        val json = JSONObject()
        json.put("error", message)
        return json
    }
}
