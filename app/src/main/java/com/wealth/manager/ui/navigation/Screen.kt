package com.wealth.manager.ui.navigation

sealed class Screen(val route: String) {
    data object Dashboard : Screen("dashboard")
    data object Add : Screen("add?expenseId={expenseId}") {
        fun createRoute(expenseId: Long? = null): String {
            return if (expenseId != null) "add?expenseId=$expenseId" else "add"
        }
    }
    data object Insights : Screen("insights")
    data object Achievements : Screen("achievements")
}
