package com.wealth.manager.ui.navigation

sealed class Screen(val route: String) {
    data object Dashboard : Screen("dashboard")
    data object Add : Screen("add")
    data object Insights : Screen("insights")
    data object Achievements : Screen("achievements")
}
