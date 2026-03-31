package com.wealth.manager.ui.dashboard

import com.wealth.manager.data.entity.CategoryEntity
import com.wealth.manager.data.entity.ExpenseEntity

data class DashboardState(
    val isLoading: Boolean = true,
    val weeklyTotal: Double = 0.0,
    val weeklyChange: Float = 0f,
    val categoryBreakdown: List<CategorySpending> = emptyList(),
    val aiSuggestions: List<String> = emptyList(),
    val wowPreview: WowPreview? = null,
    val recentExpenses: List<ExpenseEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList()
)

data class CategorySpending(
    val category: CategoryEntity,
    val amount: Double,
    val percentage: Float,
    val isOverBaseline: Boolean
)

data class WowPreview(
    val savedAmount: Double,
    val isTriggered: Boolean,
    val reason: String
)
