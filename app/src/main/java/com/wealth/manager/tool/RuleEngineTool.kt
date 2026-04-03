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
 * 规则引擎工具 - 终极稳定版 (对齐 UI，彻底杜绝 NaN)
 */
@Singleton
class RuleEngineTool @Inject constructor(
    private val expenseDao: ExpenseDao,
    private val categoryDao: CategoryDao
) : Tool {

    override val name = "rule_engine"
    override val description = """
        核心财务数据工具。
        - get_summary: 获取最近30个自然日的账单统计。
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

            val result = when (operation) {
                "get_summary" -> getRecentSummary()
                "scale_analysis" -> scaleAnalysis(data)
                "structure_analysis" -> structureAnalysis(data)
                "frequency_analysis" -> frequencyAnalysis(data)
                else -> errorResult("Unknown operation: $operation")
            }

            result.toString()
        } catch (e: Exception) {
            errorResult("RuleEngineTool error: ${e.message}").toString()
        }
    }

    private fun getRecentSummary(): JSONObject {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        val end = calendar.timeInMillis

        calendar.add(Calendar.DAY_OF_YEAR, -29)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val start = calendar.timeInMillis

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
        categoryStats.take(8).forEach { (catId, amount) ->
            val catObj = JSONObject()
            catObj.put("category", catNameMap[catId] ?: "未知($catId)")
            catObj.put("amount", amount)
            val percentage = if (total > 0) amount / total else 0.0
            catObj.put("percentage", if (percentage.isNaN()) 0.0 else percentage)
            categoryArray.put(catObj)
        }

        val res = JSONObject()
        res.put("period", "last_30_days_aligned")
        res.put("total_expense", total)
        res.put("transaction_count", expenses.size)
        res.put("top_categories", categoryArray)
        res.put("status", "success")
        return res
    }

    private fun scaleAnalysis(data: JSONObject?): JSONObject {
        val valIn = data?.optDouble("total", Double.NaN) ?: Double.NaN
        val total = if (valIn.isNaN()) getSummaryTotal() else valIn
        return insightToJson(ScaleRule.buildInsight(if (total.isNaN()) 0.0 else total))
    }

    private fun structureAnalysis(data: JSONObject?): JSONObject {
        var name = data?.optString("topCategoryName", "") ?: ""
        var percentage = data?.optDouble("topPercentage", Double.NaN) ?: Double.NaN

        if (name.isEmpty() || percentage.isNaN()) {
            val summary = getRecentSummary()
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

    private fun frequencyAnalysis(data: JSONObject?): JSONObject {
        val countIn = data?.optInt("totalCount", -1) ?: -1
        val count = if (countIn == -1) getSummaryCount() else countIn
        return insightToJson(FrequencyRule.buildInsight(count))
    }

    private fun getSummaryTotal(): Double {
        val t = getRecentSummary().optDouble("total_expense", 0.0)
        return if (t.isNaN()) 0.0 else t
    }

    private fun getSummaryCount() = getRecentSummary().optInt("transaction_count", 0)

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
