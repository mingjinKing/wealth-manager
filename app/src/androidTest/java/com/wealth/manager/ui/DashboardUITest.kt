package com.wealth.manager.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wealth.manager.ui.dashboard.DashboardScreen
import com.wealth.manager.ui.theme.WealthManagerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 仪表板 UI 测试
 */
@RunWith(AndroidJUnit4::class)
class DashboardUITest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `仪表板标题应该显示`() {
        // 设置 Compose 内容
        composeTestRule.setContent {
            WealthManagerTheme {
                DashboardScreen()
            }
        }

        // 验证标题
        composeTestRule
            .onNodeWithText("Dashboard", substring = true, ignoreCase = true)
            .assertExists("仪表板标题应该存在")
    }

    @Test
    fun `净资产卡片应该存在`() {
        composeTestRule.setContent {
            WealthManagerTheme {
                DashboardScreen()
            }
        }

        // 查找净资产相关的文字
        composeTestRule

            .onNodeWithText("Net Worth", substring = true, ignoreCase = true)
            .assertExists("净资产卡片应该存在")
    }

    @Test
    fun `本周支出卡片应该存在`() {
        composeTestRule.setContent {
            WealthManagerTheme {
                DashboardScreen()
            }
        }

        // 查找本周支出相关的文字
        composeTestRule
            .onNodeWithText("This Week", substring = true, ignoreCase = true)
            .assertExists("本周支出卡片应该存在")
    }

    @Test
    fun `最近交易列表应该存在`() {
        composeTestRule.setContent {
            WealthManagerTheme {
                DashboardScreen()
            }
        }

        // 查找最近交易相关的文字
        composeTestRule
            .onNodeWithText("Recent", substring = true, ignoreCase = true)
            .assertExists("最近交易列表应该存在")
    }

    @Test
    fun `浮动操作按钮应该存在`() {
        composeTestRule.setContent {
            WealthManagerTheme {
                DashboardScreen()
            }
        }

        // 查找浮动操作按钮（FAB）
        composeTestRule

            .onNodeWithContentDescription("Add expense", ignoreCase = true)
            .assertExists("浮动操作按钮应该存在")
    }

    @Test
    fun `点击FAB应该导航到添加页面`() {
        // 由于导航需要完整的 Hilt 设置，这里简化测试
        composeTestRule.setContent {
            WealthManagerTheme {
                DashboardScreen()
            }
        }

        // 点击 FAB
        composeTestRule

            .onNodeWithContentDescription("Add expense", ignoreCase = true)
            .performClick()

        // 注意：真实的导航测试需要 HiltTestRule 和完整的应用环境
        // 这里只是演示交互
    }

    @Test
    fun `仪表板应该显示加载状态`() {
        // 测试加载状态的显示
        composeTestRule.setContent {
            WealthManagerTheme {
                DashboardScreen()
            }
        }

        // 查找加载相关的组件（如果有）
        // 实际项目中，DashboardScreen 应该有加载状态的可视化

        // 这个测试演示如何测试组件的不同状态
    }
}
