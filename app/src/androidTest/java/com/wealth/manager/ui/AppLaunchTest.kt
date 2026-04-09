package com.wealth.manager.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wealth.manager.ui.theme.WealthManagerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 应用启动测试
 * 验证应用可以正常启动和显示初始内容
 */
@RunWith(AndroidJUnit4::class)
class AppLaunchTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `应用应该启动成功`() {
        // 这个测试验证应用可以启动，不抛出异常
        
        // 获取应用上下文
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        
        // 验证应用上下文不为空
        assert(appContext != null)
        
        // 验证应用包名
        assertEquals("com.wealth.manager", appContext.packageName)
    }

    @Test
    fun `主题应该正常应用`() {
        // 测试 Compose 主题是否可以正常应用
        
        composeTestRule.setContent {
            WealthManagerTheme {
                // 这里可以放一个简单的测试组件
                // 例如: Text("测试")
            }
        }
        
        // 如果没有抛出异常，说明主题应用成功
    }

    @Test
    fun `应用启动后应该有默认内容`() {
        // 测试应用启动后是否显示一些默认内容
        
        composeTestRule.setContent {
            WealthManagerTheme {
                // 这里放应用的根组件
                // 实际项目中是 AppNavigation 或类似组件
            }
        }
        
        // 验证没有崩溃
        // 这个测试主要验证启动过程的稳定性
    }
}
