package com.wealth.manager.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wealth.manager.ui.add.AddExpenseScreen
import com.wealth.manager.ui.dashboard.DashboardScreen
import com.wealth.manager.ui.navigation.AppNavigation
import com.wealth.manager.ui.theme.WealthManagerTheme
import io.mockk.mockk
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 添加支出 UI 测试
 * 测试核心流程：从仪表板导航到添加页面
 */
@RunWith(AndroidJUnit4::class)
class AddExpenseUITest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun setup() {
        // 设置 Compose 主题和内容
        composeTestRule.setContent {
            WealthManagerTheme {
                // 使用模拟的导航控制器
                val navController = rememberNavController()
                AppNavigation(navController = navController)
            }
        }
    }

    @Test
    fun `添加支出按钮应该存在`() {
        // 查找添加按钮（FAB）
        composeTestRule
            .onNodeWithText("添加", substring = true)
            .assertExists("添加按钮应该存在")
    }

    @Test
    fun `点击添加按钮应该打开添加页面`() {
        // 查找并点击添加按钮
        composeTestRule
            .onNodeWithText("添加", substring = true)
            .performClick()

        // 验证添加页面标题
        composeTestRule
            .onNodeWithText("记一笔", substring = true)
            .assertExists("添加页面标题应该显示")
    }

    @Test
    fun `添加页面应该有金额输入框`() {
        // 导航到添加页面
        composeTestRule
            .onNodeWithText("添加", substring = true)
            .performClick()

        // 查找金额标签
        composeTestRule
            .onNodeWithText("金额", substring = true)
            .assertExists("金额输入框应该存在")
    }

    @Test
    fun `添加页面应该有分类选择`() {
        // 导航到添加页面
        composeTestRule
            .onNodeWithText("添加", substring = true)
            .performClick()

        // 查找分类标签
        composeTestRule
            .onNodeWithText("分类", substring = true)
            .assertExists("分类选择应该存在")
    }

    @Test
    fun `添加页面应该有保存按钮`() {
        // 导航到添加页面
        composeTestRule
            .onNodeWithText("添加", substring = true)
            .performClick()

        // 查找保存按钮
        composeTestRule
            .onNodeWithText("保存", substring = true)
            .assertExists("保存按钮应该存在")
    }
}
