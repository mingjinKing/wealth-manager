package com.wealth.manager.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wealth.manager.ui.theme.DesignTokens
import kotlinx.coroutines.delay

/**
 * 增强版卡片组件
 * 带有点击动画效果
 */
@Composable
fun EnhancedCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    elevated: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "card_scale"
    )
    
    val elevation = if (elevated) DesignTokens.Elevation.Medium else DesignTokens.Elevation.Low
    
    Card(
        modifier = modifier
            .scale(scale)
            .shadow(
                elevation = if (isPressed) elevation * 1.5f else elevation,
                shape = RoundedCornerShape(DesignTokens.Radius.Normal)
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick
                    )
                } else Modifier
            ),
        shape = RoundedCornerShape(DesignTokens.Radius.Normal),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(DesignTokens.Card.padding),
            content = content
        )
    }
}

/**
 * 增强版FAB
 * 带有脉冲动画效果（可选择启用）
 */
@Composable
fun EnhancedFAB(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    pulse: Boolean = false,
    icon: @Composable () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    
    // 脉冲动画
    val infiniteTransition = rememberInfiniteTransition(label = "fab_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.9f
            isExpanded -> 1.1f
            else -> 1f
        },
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "fab_scale"
    )
    
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // 脉冲光环（可选）
        if (pulse) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .scale(pulseScale)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(DesignTokens.Radius.Circle)
                    )
            )
        }
        
        FloatingActionButton(
            onClick = onClick,
            modifier = Modifier
                .scale(scale)
                .size(DesignTokens.Fab.size),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            interactionSource = interactionSource,
            shape = RoundedCornerShape(DesignTokens.Radius.Circle)
        ) {
            icon()
        }
    }
}

/**
 * 骨架屏加载效果
 */
@Composable
fun SkeletonLoading(
    modifier: Modifier = Modifier,
    height: Int = 80,
    corners: androidx.compose.ui.unit.Dp = DesignTokens.Radius.Normal
) {
    val infiniteTransition = rememberInfiniteTransition(label = "skeleton")
    
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeleton_alpha"
    )
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(RoundedCornerShape(corners))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
    )
}

/**
 * 骨架屏卡片（用于列表项）
 */
@Composable
fun SkeletonCardList(
    itemCount: Int = 3,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.md)
    ) {
        repeat(itemCount) {
            SkeletonLoading(height = 80)
        }
    }
}

/**
 * 增强版空状态组件
 * 带有入场动画
 */
@Composable
fun EnhancedEmptyContent(
    title: String,
    subtitle: String? = null,
    icon: String = "📭",
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    var visible by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        visible = true
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(DesignTokens.Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 动画图标
        var emojiVisible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            delay(200)
            emojiVisible = true
        }
        
        AnimatedVisibility(
            visible = emojiVisible,
            enter = scaleIn(tween(300)) + fadeIn()
        ) {
            Text(
                text = icon,
                style = MaterialTheme.typography.displayLarge
            )
        }
        
        Spacer(modifier = Modifier.height(DesignTokens.Spacing.md))
        
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(500, delayMillis = 300)) + slideInVertically(tween(500, delayMillis = 300)) { it / 3 }
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
        }
        
        if (subtitle != null) {
            Spacer(modifier = Modifier.height(DesignTokens.Spacing.sm))
            
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(500, delayMillis = 500)) + slideInVertically(tween(500, delayMillis = 500)) { it / 3 }
            ) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        if (action != null) {
            Spacer(modifier = Modifier.height(DesignTokens.Spacing.lg))
            
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(500, delayMillis = 700)) + slideInVertically(tween(500, delayMillis = 700)) { it / 3 }
            ) {
                action()
            }
        }
    }
}

/**
 * 带动画的文本组件
 */
@Composable
fun AnimatedText(
    text: String,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    var visible by remember { mutableStateOf(false) }
    
    LaunchedEffect(text) {
        visible = true
    }
    
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(300)) + slideInHorizontally(tween(300)) { it / 4 }
    ) {
        Text(
            text = text,
            modifier = modifier,
            style = style,
            color = color
        )
    }
}

/**
 * 页面过渡动画
 */
val PageTransition = fadeIn(tween(300)) togetherWith fadeOut(tween(300))
