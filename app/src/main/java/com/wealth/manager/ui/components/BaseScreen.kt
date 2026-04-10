package com.wealth.manager.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wealth.manager.ui.theme.DesignTokens

/**
 * 基础Screen组件
 * 
 * 提供统一的页面结构，确保所有Screen遵循相同的设计规范：
 * - 标准的TopAppBar
 * - 统一的内容padding
 * - Loading/Error/Empty状态处理
 * 
 * 使用方式：
 * ```kotlin
 * BaseScreen(
 *     title = "页面标题",
 *     onNavigateBack = { ... },
 *     floatingActionButton = { ... }
 * ) {
 *     // 页面内容
 * }
 * ```
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BaseScreen(
    title: String,
    modifier: Modifier = Modifier,
    onNavigateBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    isLoading: Boolean = false,
    error: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回"
                            )
                        }
                    }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = floatingActionButton,
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = DesignTokens.Spacing.md),
            verticalArrangement = Arrangement.Top
        ) {
            when {
                isLoading -> {
                    LoadingContent()
                }
                error != null -> {
                    ErrorContent(message = error)
                }
                else -> {
                    content()
                }
            }
        }
    }
}

/**
 * Loading状态组件
 */
@Composable
fun LoadingContent(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * Error状态组件
 */
@Composable
fun ErrorContent(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(DesignTokens.Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "⚠️",
            style = MaterialTheme.typography.displayMedium
        )
        Spacer(modifier = Modifier.height(DesignTokens.Spacing.md))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error
        )
        if (onRetry != null) {
            Spacer(modifier = Modifier.height(DesignTokens.Spacing.lg))
            Button(onClick = onRetry) {
                Text("重试")
            }
        }
    }
}

/**
 * Empty状态组件
 */
@Composable
fun EmptyContent(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(DesignTokens.Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "📭",
            style = MaterialTheme.typography.displayLarge
        )
        Spacer(modifier = Modifier.height(DesignTokens.Spacing.md))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )
        if (subtitle != null) {
            Spacer(modifier = Modifier.height(DesignTokens.Spacing.sm))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (action != null) {
            Spacer(modifier = Modifier.height(DesignTokens.Spacing.lg))
            action()
        }
    }
}

/**
 * 标准页面Section标题
 */
@Composable
fun SectionTitle(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(vertical = DesignTokens.Spacing.sm)
    )
}

/**
 * 标准页面Section间距
 */
object PageDefaults {
    val ContentPadding = DesignTokens.Spacing.md
    val SectionSpacing = DesignTokens.Spacing.lg
    val ItemSpacing = DesignTokens.Spacing.sm
}
