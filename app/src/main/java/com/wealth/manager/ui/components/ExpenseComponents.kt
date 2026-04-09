package com.wealth.manager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wealth.manager.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * 消费记录卡片
 * 统一记账列表项样式
 */
@Composable
fun ExpenseCard(
    categoryName: String,
    categoryIcon: String,
    amount: Double,
    date: Long,
    note: String?,
    isExpense: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(DesignTokens.Card.radius),
        color = Surface,
        tonalElevation = DesignTokens.Card.elevation
    ) {
        Row(
            modifier = Modifier
                .padding(DesignTokens.Card.padding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 分类图标
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Background),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = categoryIcon,
                    fontSize = 20.sp
                )
            }

            Spacer(modifier = Modifier.width(DesignTokens.Spacing.md))

            // 分类名和备注
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = categoryName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
                if (!note.isNullOrBlank()) {
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = formatDate(date),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }

            // 金额
            AmountText(
                amount = amount,
                isExpense = isExpense
            )
        }
    }
}

/**
 * 金额展示组件
 * 根据支出/收入显示不同颜色
 */
@Composable
fun AmountText(
    amount: Double,
    isExpense: Boolean = true,
    modifier: Modifier = Modifier
) {
    Text(
        text = if (isExpense) "-¥${formatAmount(amount)}" else "+¥${formatAmount(amount)}",
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Bold,
        color = if (isExpense) Warning else Income,
        modifier = modifier
    )
}

/**
 * 资产金额展示（无正负号）
 */
@Composable
fun AssetAmountText(
    amount: Double,
    modifier: Modifier = Modifier
) {
    Text(
        text = "¥${formatAmount(amount)}",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = TextPrimary,
        modifier = modifier
    )
}

/**
 * 辅助文字标签
 */
@Composable
fun LabelText(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = TextSecondary,
        modifier = modifier
    )
}

/**
 * 主标题
 */
@Composable
fun TitleText(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        color = TextPrimary,
        modifier = modifier
    )
}

/**
 * 副标题
 */
@Composable
fun SubtitleText(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = TextSecondary,
        modifier = modifier
    )
}

// ==================== 工具函数 ====================

fun formatAmount(amount: Double): String {
    return String.format("%.2f", kotlin.math.abs(amount))
}

fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

fun formatDateShort(timestamp: Long): String {
    val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
