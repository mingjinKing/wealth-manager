package com.wealth.manager.ui.navigation

sealed class Screen(val route: String) {
    data object Dashboard : Screen("dashboard")
    data object Add : Screen("add")
    data object AddWithId : Screen("add/{expenseId}") {
        fun createRoute(expenseId: Long): String = "add/$expenseId"
    }
    data object Insights : Screen("insights")
    data object Achievements : Screen("achievements")
    data object How : Screen("how-to-spend")
    data object CategoryManage : Screen("category-manage")
    data object Import : Screen("import")
    data object AssetManage : Screen("asset-manage")
    data object Settings : Screen("settings")
    data object VersionInfo : Screen("version-info")
    data object MemoryManagement : Screen("memory-management")
}
