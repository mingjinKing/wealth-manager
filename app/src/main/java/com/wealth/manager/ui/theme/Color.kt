package com.wealth.manager.ui.theme

import androidx.compose.ui.graphics.Color

// ==================== 主题色 ====================
val ThemeGold = Color(0xFFffc880)
val ThemeBlue = Color(0xFF4A90D9)
val ThemePink = Color(0xFFF48FB1)
val ThemePurple = Color(0xFF9575CD)

// 主色调（默认金色）
val Primary = ThemeGold
val PrimaryDark = Color(0xFF0c0c1f)

// ==================== 背景色 ====================
val Background = Color(0xFFF0F0F0)   // 浅灰底色
val Surface = Color(0xFFFFFFFF)       // 纯白卡片
val SurfaceElevated = Color(0xFFFFFFFF)

// ==================== 语义色 ====================
val Safe = Color(0xFF4A90D9)         // 安全/收入
val Warning = Color(0xFFE55B5B)      // 警告/支出
val Income = Color(0xFF4CAF50)       // 收入

// ==================== 文字色 ====================
val TextPrimary = Color(0xFF1A1A2E)  // 主要文字
val TextSecondary = Color(0xFF9E9E9E) // 次要文字
val TextHint = Color(0xFFBDBDBD)      // 提示文字

// ==================== 边框/分割线 ====================
val Divider = Color(0xFFE8E8E8)
val Outline = Color(0xFFE0E0E0)

// ==================== 状态色 ====================
val Success = Color(0xFF4CAF50)
val Error = Color(0xFFE55B5B)
val Info = Color(0xFF4A90D9)
val WarningLight = Color(0xFFFFF3E0)

// ==================== 分类色（用于图表）====================
val CategoryColors = listOf(
    Color(0xFFFF7043),  // 餐饮 - 橙红
    Color(0xFF42A5F5),  // 购物 - 蓝
    Color(0xFF66BB6A),  // 交通 - 绿
    Color(0xFFAB47BC),  // 娱乐 - 紫
    Color(0xFFFFCA28),  // 居住 - 黄
    Color(0xFFEF5350),  // 医疗 - 红
    Color(0xFF26A69A),  // 学习 - 青
    Color(0xFF78909C),  // 其他 - 灰蓝
)
