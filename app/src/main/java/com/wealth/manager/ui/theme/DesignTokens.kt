package com.wealth.manager.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 设计令牌 (Design Tokens)
 * 统一管理间距、大小、圆角等设计规范值
 */
@Immutable
object DesignTokens {

    // ==================== 间距系统 (8pt Grid) ====================
    object Spacing {
        val xs = 4.dp   // 紧凑间距
        val sm = 8.dp    // 小间距
        val md = 16.dp  // 标准间距（默认）
        val lg = 24.dp  // 大间距
        val xl = 32.dp  // 超大间距
        val xxl = 48.dp // 极 大间距
    }

    // ==================== 卡片样式 ====================
    object Card {
        val padding = 16.dp
        val radius = 12.dp
        val elevation = 2.dp
    }

    // ==================== 按钮样式 ====================
    object Button {
        val height = 48.dp
        val minWidth = 88.dp
        val cornerRadius = 24.dp
        val paddingHorizontal = 16.dp
        val paddingVertical = 12.dp
    }

    // ==================== FAB 样式 ====================
    object Fab {
        val size = 56.dp
        val cornerRadius = 28.dp
    }

    // ==================== 底部导航 ====================
    object BottomNav {
        val height = 80
        val iconSize = 24.dp
        val labelFontSize = 12.sp
    }

    // ==================== 输入框 ====================
    object Input {
        val height = 56.dp
        val cornerRadius = 12.dp
        val paddingHorizontal = 16.dp
    }

    // ==================== 对话框 ====================
    object Dialog {
        val cornerRadius = 24.dp
        val padding = 24.dp
    }

    // ==================== 列表项 ====================
    object ListItem {
        val height = 72.dp
        val paddingHorizontal = 16.dp
        val paddingVertical = 12.dp
    }
}

/**
 * 本地组合式提供 DesignTokens
 */
val LocalDesignTokens = staticCompositionLocalOf { DesignTokens }
