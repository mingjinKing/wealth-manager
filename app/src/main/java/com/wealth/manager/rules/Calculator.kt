package com.wealth.manager.rules

import com.wealth.manager.data.entity.CategoryEntity
import java.text.SimpleDateFormat
import java.util.*

/**
 * 计算工具函数
 *
 * 非规则的计算逻辑，作为 Calculator 维护，不放入 rules/ 的规则判定部分。
 */
object Calculator {

    /**
     * 计算净资产
     *
     * @param totalAssets 总资产
     * @param totalLiabilities 总负债
     * @return 净资产
     */
    fun calculateNetWorth(totalAssets: Double, totalLiabilities: Double): Double {
        return totalAssets + totalLiabilities
    }

    /**
     * 区分收入和支出分类 ID
     *
     * @param categories 所有分类
     * @return Pair(收入分类ID集合, 支出分类ID集合)
     */
    fun separateIncomeAndExpenseCategories(categories: List<CategoryEntity>): Pair<Set<Long>, Set<Long>> {
        val incomeIds = categories.filter { it.type == "INCOME" }.map { it.id }.toSet()
        val expenseIds = categories.filter { it.type == "EXPENSE" }.map { it.id }.toSet()
        return Pair(incomeIds, expenseIds)
    }

    /**
     * 判断预算类型
     *
     * @param monthlyBudget 月预算金额
     * @param weeklyBudget 周预算金额
     * @return 预算类型 ("MONTHLY" 或 "WEEKLY")
     */
    fun determineBudgetType(monthlyBudget: Double, weeklyBudget: Double): String {
        return if (monthlyBudget <= 0.0 && weeklyBudget > 0.0) "WEEKLY" else "MONTHLY"
    }

    /**
     * Trend 进度分段（等分5段）
     *
     * @param currentNetWorth 当前净资产
     * @param goalAmount 目标金额
     * @param startDate 开始时间戳
     * @param endDate 结束时间戳
     * @return 趋势点列表
     */
    fun calculateTrendPoints(
        currentNetWorth: Double,
        goalAmount: Double,
        startDate: Long,
        endDate: Long
    ): List<TrendPoint> {
        val totalDuration = endDate - startDate
        if (totalDuration <= 0) return emptyList()

        val points = mutableListOf<TrendPoint>()
        val step = totalDuration / 5

        for (i in 0..5) {
            val time = startDate + i * step
            val expected = (goalAmount / 5) * i

            val actual = if (time <= System.currentTimeMillis()) {
                val progress = (System.currentTimeMillis() - startDate).toDouble() / totalDuration
                val currentProgressInPath = i.toDouble() / 5
                if (currentProgressInPath <= progress) {
                    (currentNetWorth / progress.coerceAtLeast(0.01)) * currentProgressInPath
                } else null
            } else null

            points.add(
                TrendPoint(
                    label = SimpleDateFormat("MM/dd", Locale.getDefault()).format(Date(time)),
                    expected = expected,
                    actual = actual
                )
            )
        }
        return points
    }
}

data class TrendPoint(
    val label: String,
    val expected: Double,
    val actual: Double?
)