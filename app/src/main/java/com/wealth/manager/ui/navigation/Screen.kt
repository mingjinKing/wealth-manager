package com.wealth.manager.ui.navigation

sealed class Screen(val route: String) {
    data object Dashboard : Screen("dashboard")
    data object Add : Screen("add")
    data object AddWithId : Screen("add/{expenseId}") {
        fun createRoute(expenseId: Long): String = "add/$expenseId"
    }
    data object Insights : Screen("insights")
    data object Achievements : Screen("achievements")
    data object CategoryManage : Screen("category-manage")
    data object Import : Screen("import")
}
