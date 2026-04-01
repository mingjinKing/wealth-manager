package com.wealth.manager.ui.achievements

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wealth.manager.ui.theme.Primary
import com.wealth.manager.ui.theme.Surface
import com.wealth.manager.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementsScreen(
    onNavigateToInsights: () -> Unit = {},
    viewModel: AchievementsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var showHelpDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "成长成就",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = { showHelpDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.HelpOutline,
                            contentDescription = "成就说明",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. 本周核心 WOW 卡片
            if (state.currentWow != null) {
                item {
                    CurrentWowSection(state.currentWow!!)
                }
            } else {
                item {
                    EmptyWowSection()
                }
            }

            // 2. 操作按钮
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("分享喜悦")
                    }

                    OutlinedButton(
                        onClick = onNavigateToInsights,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Primary),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Primary)
                    ) {
                        Text("分析报告")
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }

            // 3. 成就勋章墙 (过滤逻辑：只展示已解锁或有进度的)
            val displayBadges = state.allBadges.filter { it.isUnlocked || it.progress > 0 }
            
            if (displayBadges.isNotEmpty()) {
                item {
                    Text(
                        text = "我的勋章",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                val chunks = displayBadges.chunked(3)
                chunks.forEach { rowBadges ->
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            rowBadges.forEach { badge ->
                                BadgeItem(
                                    badge = badge,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            repeat(3 - rowBadges.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(40.dp)) }
        }
    }

    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = { Text("成就体系说明") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("• WOW 奖励：每周日系统会根据你的消费预算达成情况，判定是否触发 WOW。节省越多，惊喜越大！", fontSize = 14.sp)
                    Text("• 勋章体系：完成特定任务（如连续达标、累计节省、首笔记账）可解锁专属勋章。", fontSize = 14.sp)
                    Text("• 进度条：勋章下方的进度条代表你距离达成该成就的距离，加油！", fontSize = 14.sp)
                    Text("• 展示逻辑：主页仅展示已解锁或已产生进度的勋章，保持页面整洁。", fontSize = 14.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = false }) {
                    Text("知道了", color = Primary)
                }
            }
        )
    }
}

@Composable
fun CurrentWowSection(wow: CurrentWow) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(modifier = Modifier.height(24.dp))
        Text(text = "\uD83C\uDF89", fontSize = 48.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "本周节省金额", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Text(
            text = "¥${wow.savedAmount}",
            style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Black),
            color = Primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        
        Card(
            colors = CardDefaults.cardColors(containerColor = Primary.copy(alpha = 0.05f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = wow.reason,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = Primary,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Surface)
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "\uD83D\uDCAA", fontSize = 16.sp)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(text = "升级进度", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    Text(text = wow.progressHint ?: "", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { wow.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = Primary,
                trackColor = Primary.copy(alpha = 0.1f)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f).height(1.dp).background(Primary.copy(alpha = 0.2f)))
            Text(
                text = " 相当于：${wow.equivalent} ",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            Box(modifier = Modifier.weight(1f).height(1.dp).background(Primary.copy(alpha = 0.2f)))
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun EmptyWowSection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Primary.copy(alpha = 0.05f))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "🌱", fontSize = 32.sp)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "理财之种正在萌芽",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "本周你还没有成就数据。通过每日记账和控制预算，你可以在周日解锁首个“WOW”时刻！",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "目标：完成本周 7 天连续记账",
            style = MaterialTheme.typography.labelLarge,
            color = Primary,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun BadgeItem(badge: BadgeMetadata, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(if (badge.isUnlocked) Primary.copy(alpha = 0.1f) else Surface),
            contentAlignment = Alignment.Center
        ) {
            if (badge.isUnlocked) {
                Text(text = badge.emoji, fontSize = 32.sp)
            } else {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp).alpha(0.3f),
                    tint = TextSecondary
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = badge.name,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (badge.isUnlocked) FontWeight.Bold else FontWeight.Normal,
            color = if (badge.isUnlocked) Color.Unspecified else TextSecondary,
            maxLines = 1
        )
        Text(
            text = badge.criteria,
            style = MaterialTheme.typography.bodySmall,
            fontSize = 10.sp,
            color = TextSecondary,
            maxLines = 1
        )
        if (badge.isUnlocked) {
            Text(
                text = "已解锁",
                style = MaterialTheme.typography.bodySmall,
                fontSize = 10.sp,
                color = Primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
