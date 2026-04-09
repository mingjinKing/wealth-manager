package com.wealth.manager.ui.dashboard

import com.wealth.manager.data.entity.CategoryEntity
import com.wealth.manager.data.entity.ExpenseEntity

data class DashboardState(
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val hasMorePages: Boolean = true,
    val monthTotal: Double = 0.0,
    val monthIncome: Double = 0.0,
    val recent7DaysTotal: Double = 0.0,
    val dailyExpenses: List<DailyExpense> = emptyList(),
    val wowPreview: WowPreview? = null,
    val categories: List<CategoryEntity> = emptyList(),
    val customBackgroundImageUri: String? = null,
    // AI 洞察相关状态
    val isAnalyzingInsight: Boolean = false,
    val aiInsightText: String? = null
)

data class DailyExpense(
    val dateLabel: String,        // e.g. "今天", "昨天", "3月25日"
    val dateMillis: Long,
    val dayTotal: Double,
    val expenses: List<ExpenseItem>
)

data class ExpenseItem(
    val expense: ExpenseEntity,
    val category: CategoryEntity
)

data class WowPreview(
    val savedAmount: Double,
    val lastWeekAmount: Double, // 对标上周/平均周支出
    val isTriggered: Boolean,
    val reason: String
)
