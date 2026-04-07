package com.wealth.manager.ui.navigation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.wealth.manager.R
import com.wealth.manager.ui.achievements.AchievementsScreen
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
        label = "首页",
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
        label = "进阶",
        selectedIcon = Icons.Filled.Star,
        unselectedIcon = Icons.Outlined.Star,
        route = Screen.Achievements.route
    )
)

data class DrawerMenuItem(
    val label: String,
    val icon: ImageVector,
    val route: String
)

val drawerMenuItems = listOf(
    DrawerMenuItem("资产管理", Icons.Default.AccountBalanceWallet, Screen.AssetManage.route),
    DrawerMenuItem("分类管理", Icons.Default.List, Screen.CategoryManage.route),
    DrawerMenuItem("导入数据", Icons.Default.Download, Screen.Import.route),
    DrawerMenuItem("版本信息", Icons.Default.NewReleases, Screen.VersionInfo.route)
)

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val themeViewModel: ThemeViewModel = hiltViewModel()
    val assetPasswordProtection by themeViewModel.assetPasswordProtection.collectAsState()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    // 密码验证弹窗状态
    var showAssetPasswordDialog by remember { mutableStateOf(false) }
    var pendingAssetRoute by remember { mutableStateOf<String?>(null) }
    var passwordInput by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf<String?>(null) }

    // 密码验证弹窗
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
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_logo),
                            contentDescription = "App Logo",
                            modifier = Modifier
                                .size(48.dp)
                                .padding(bottom = 8.dp)
                        )
                        Text(
                            text = "知其财，治其财",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    drawerMenuItems.forEach { item ->
                        val needsPassword = item.route == Screen.AssetManage.route && assetPasswordProtection
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (needsPassword) {
                                        scope.launch { drawerState.close() }
                                        pendingAssetRoute = item.route
                                        showAssetPasswordDialog = true
                                    } else {
                                        scope.launch { drawerState.close() }
                                        navController.navigate(item.route) {
                                            launchSingleTop = true
                                        }
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

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = Background)
                    Spacer(modifier = Modifier.height(12.dp))

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
                            .padding(horizontal = 20.dp, vertical = 14.dp),
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
                androidx.compose.foundation.layout.Box(
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
                                            launchSingleTop = true
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
                        onNavigateToDashboard = {
                            navController.navigate(Screen.Dashboard.route) {
                                popUpTo(Screen.Add.route) { inclusive = true }
                            }
                        },
                        onNavigateBack = {
                            navController.navigate(Screen.Dashboard.route) {
                                popUpTo(Screen.Add.route) { inclusive = true }
                            }
                        }
                    )
                }
                composable(Screen.AddWithId.route) { backStackEntry ->
                    val expenseId = backStackEntry.arguments?.getString("expenseId")?.toLongOrNull()
                    AddExpenseScreen(
                        expenseToEdit = expenseId,
                        onNavigateToDashboard = {
                            navController.navigate(Screen.Dashboard.route) {
                                popUpTo(Screen.AddWithId.route) { inclusive = true }
                            }
                        },
                        onNavigateBack = {
                            navController.navigate(Screen.Dashboard.route) {
                                popUpTo(Screen.AddWithId.route) { inclusive = true }
                            }
                        }
                    )
                }
                composable(Screen.Achievements.route) {
                    AchievementsScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToAssets = { navController.navigate(Screen.AssetManage.route) }
                    )
                }
                composable(Screen.CategoryManage.route) {
                    CategoryManageScreen()
                }
                composable(Screen.Import.route) {
                    ImportScreen()
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
