package com.wealth.manager.ui.navigation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.wealth.manager.R
import com.wealth.manager.ui.achievements.WealthGoalsScreen
import com.wealth.manager.ui.add.AddExpenseScreen
import com.wealth.manager.ui.assets.AssetManageScreen
import com.wealth.manager.ui.category.CategoryManageScreen
import com.wealth.manager.ui.dashboard.DashboardScreen
import com.wealth.manager.ui.importdata.ImportScreen
import com.wealth.manager.ui.insights.InsightsScreen
import com.wealth.manager.ui.how.HowToSpendScreen
import com.wealth.manager.ui.settings.SettingsScreen
import com.wealth.manager.ui.settings.MemoryManagementScreen
import com.wealth.manager.ui.version.VersionInfoScreen
import com.wealth.manager.ui.theme.Background
import com.wealth.manager.ui.theme.Surface
import com.wealth.manager.ui.theme.TextSecondary
import com.wealth.manager.ui.theme.ThemeViewModel
import kotlinx.coroutines.launch

data class BottomNavItem(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val route: String
)

val bottomNavItems = listOf(
    BottomNavItem(
        label = "记记账",
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
        route = Screen.Dashboard.route
    ),
    BottomNavItem(
        label = "算算账",
        selectedIcon = Icons.Filled.Insights,
        unselectedIcon = Icons.Outlined.Insights,
        route = Screen.Insights.route
    ),
    BottomNavItem(
        label = "怎么花",
        selectedIcon = Icons.Filled.Lightbulb,
        unselectedIcon = Icons.Outlined.Lightbulb,
        route = Screen.How.route
    ),
    BottomNavItem(
        label = "攒点钱",
        selectedIcon = Icons.Filled.Star,
        unselectedIcon = Icons.Outlined.Star,
        route = Screen.WealthGoals.route
    )
)

data class DrawerMenuItem(
    val label: String,
    val icon: ImageVector,
    val route: String
)

data class DrawerMenuGroup(
    val title: String,
    val items: List<DrawerMenuItem>
)

val drawerMenuGroups = listOf(
    DrawerMenuGroup(
        title = "账本管理",
        items = listOf(
            DrawerMenuItem("资产管理", Icons.Default.AccountBalanceWallet, Screen.AssetManage.route),
            DrawerMenuItem("分类管理", Icons.Default.List, Screen.CategoryManage.route)
        )
    ),
    DrawerMenuGroup(
        title = "数据工具",
        items = listOf(
            DrawerMenuItem("导入数据", Icons.Default.Download, Screen.Import.route)
        )
    )
)

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val themeViewModel: ThemeViewModel = hiltViewModel()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    // 资产保护密码弹窗逻辑
    var showAssetPasswordDialog by remember { mutableStateOf(false) }
    var pendingAssetRoute by remember { mutableStateOf<String?>(null) }
    var passwordInput by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf<String?>(null) }

    if (showAssetPasswordDialog) {
        AlertDialog(
            onDismissRequest = {
                showAssetPasswordDialog = false
                pendingAssetRoute = null
                passwordInput = ""
                passwordError = null
            },
            title = { Text("请输入密码") },
            text = {
                Column {
                    Text("访问资产管理需要密码验证", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(12.dp))
                    TextField(
                        value = passwordInput,
                        onValueChange = { if (it.length <= 6) passwordInput = it },
                        label = { Text("密码") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        isError = passwordError != null,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (passwordError != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = passwordError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (themeViewModel.verifyAssetPassword(passwordInput)) {
                            showAssetPasswordDialog = false
                            passwordInput = ""
                            passwordError = null
                            pendingAssetRoute?.let { route ->
                                navController.navigate(route) {
                                    launchSingleTop = true
                                }
                            }
                            pendingAssetRoute = null
                        } else {
                            passwordError = "密码错误"
                        }
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAssetPasswordDialog = false
                        pendingAssetRoute = null
                        passwordInput = ""
                        passwordError = null
                    }
                ) {
                    Text("取消")
                }
            }
        )
    }

    val scope = rememberCoroutineScope()

    fun isRouteSelected(route: String): Boolean {
        return currentDestination?.hierarchy?.any { it.route == route } == true
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(280.dp),
                drawerContainerColor = Surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    // Drawer Header
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 20.dp, vertical = 24.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_logo),
                            contentDescription = "App Logo",
                            modifier = Modifier
                                .size(48.dp)
                                .padding(bottom = 12.dp)
                        )
                        Text(
                            text = "知其财，治其财",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    drawerMenuGroups.forEachIndexed { index, group ->
                        Text(
                            text = group.title,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )

                        group.items.forEach { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        scope.launch { drawerState.close() }
                                        navController.navigate(item.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                    .background(
                                        if (isRouteSelected(item.route)) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                        else Surface
                                    )
                                    .padding(horizontal = 20.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.label,
                                    tint = if (isRouteSelected(item.route)) MaterialTheme.colorScheme.primary else TextSecondary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = item.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (isRouteSelected(item.route)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (isRouteSelected(item.route)) FontWeight.Medium else FontWeight.Normal
                                )
                            }
                        }
                        
                        if (index < drawerMenuGroups.size - 1) {
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))
                    HorizontalDivider(color = Background)
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                scope.launch { drawerState.close() }
                                navController.navigate(Screen.Settings.route) {
                                    launchSingleTop = true
                                }
                            }
                            .background(
                                if (isRouteSelected(Screen.Settings.route)) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                else Surface
                            )
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "设置",
                            tint = if (isRouteSelected(Screen.Settings.route)) MaterialTheme.colorScheme.primary else TextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "设置",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isRouteSelected(Screen.Settings.route)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (isRouteSelected(Screen.Settings.route)) FontWeight.Medium else FontWeight.Normal
                        )
                    }
                }
            }
        }
    ) {
        Scaffold(
            bottomBar = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Background)
                        .navigationBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        bottomNavItems.forEach { item ->
                            val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        navController.navigate(item.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                    .padding(top = 4.dp, bottom = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.label,
                                    tint = if (selected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.6f),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = item.label,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontSize = 12.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (selected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            }
        ) { paddingValues ->
            NavHost(
                navController = navController,
                startDestination = Screen.Dashboard.route,
                modifier = Modifier.padding(paddingValues)
            ) {
                composable(Screen.Dashboard.route) {
                    DashboardScreen(
                        onNavigateToAdd = { expenseId ->
                            if (expenseId != null) {
                                navController.navigate(Screen.AddWithId.createRoute(expenseId))
                            } else {
                                navController.navigate(Screen.Add.route)
                            }
                        },
                        onOpenDrawer = {
                            scope.launch { drawerState.open() }
                        }
                    )
                }
                composable(Screen.Insights.route) {
                    InsightsScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
                composable(Screen.How.route) {
                    HowToSpendScreen()
                }
                composable(Screen.Add.route) {
                    AddExpenseScreen(
                        expenseToEdit = null,
                        onNavigateToDashboard = { navController.popBackStack() },
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
                composable(Screen.AddWithId.route) { backStackEntry ->
                    val expenseId = backStackEntry.arguments?.getString("expenseId")?.toLongOrNull()
                    AddExpenseScreen(
                        expenseToEdit = expenseId,
                        onNavigateToDashboard = { navController.popBackStack() },
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
                composable(Screen.WealthGoals.route) {
                    WealthGoalsScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToAssets = { navController.navigate(Screen.AssetManage.route) }
                    )
                }
                composable(Screen.CategoryManage.route) {
                    CategoryManageScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
                composable(Screen.Import.route) {
                    ImportScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
                composable(Screen.AssetManage.route) {
                    AssetManageScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
                composable(Screen.Settings.route) {
                    SettingsScreen(
                        onNavigateBack = { navController.popBackStack() },
                        navController = navController
                    )
                }
                composable(Screen.VersionInfo.route) {
                    VersionInfoScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
                composable(Screen.MemoryManagement.route) {
                    MemoryManagementScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}
