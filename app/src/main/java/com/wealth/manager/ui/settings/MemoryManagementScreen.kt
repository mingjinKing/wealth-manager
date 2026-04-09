package com.wealth.manager.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryManagementScreen(
    onNavigateBack: () -> Unit,
    viewModel: MemoryManagementViewModel = hiltViewModel<MemoryManagementViewModel>()
) {
    val state by viewModel.state.collectAsState()
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var showRebuildConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.snackbarMessage) {
        if (state.snackbarMessage != null) {
            kotlinx.coroutines.delay(4000)
            viewModel.clearSnackbar()
        }
    }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("清除所有记忆") },
            text = { Text("确定要清除所有记忆吗？该操作不可逆。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllMemories()
                        showClearConfirmDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("确认清除", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) { Text("取消") }
            }
        )
    }

    if (showRebuildConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showRebuildConfirmDialog = false },
            title = { Text("全量重建记忆") },
            text = { Text("系统将扫描所有历史对话，利用 AI 重新沉淀您的画像。这可能需要 1-2 分钟，确定开始吗？") },
            confirmButton = {
                TextButton(onClick = {
                    showRebuildConfirmDialog = false
                    viewModel.rebuildMemories()
                }) { Text("开始重建", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showRebuildConfirmDialog = false }) { Text("取消") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("记忆系统", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { showRebuildConfirmDialog = true },
                        enabled = !state.isLoading
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("重建画像", style = MaterialTheme.typography.labelLarge)
                    }
                    if (state.memories.isNotEmpty()) {
                        IconButton(onClick = { showClearConfirmDialog = true }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "清除", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        },
        snackbarHost = {
            state.snackbarMessage?.let { msg ->
                Card(
                    modifier = Modifier.padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(msg, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = { viewModel.clearSnackbar() }) { Text("知道了") }
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (state.isLoading && state.memories.isEmpty()) {
                Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(strokeWidth = 3.dp)
                    Spacer(Modifier.height(16.dp))
                    Text("分析中...", style = MaterialTheme.typography.bodySmall)
                }
            } else if (state.memories.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("记忆库空空如也", color = MaterialTheme.colorScheme.outline)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { showRebuildConfirmDialog = true }) {
                        Text("点击初始化画像")
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        Text(
                            text = "共提炼 ${state.memories.size} 条核心记忆",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(16.dp, 8.dp)
                        )
                    }
                    items(state.memories, key = { it.id }) { memory ->
                        MemoryListItem(
                            memory = memory,
                            onDelete = { viewModel.deleteMemory(memory) }
                        )
                    }
                    item { Spacer(Modifier.height(32.dp)) }
                }
            }

            if (state.isLoading && state.memories.isNotEmpty()) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
        }
    }
}

@Composable
private fun MemoryListItem(
    memory: MemoryItem,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("移除此项？") },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteConfirm = false }) { 
                    Text("确认移除", color = MaterialTheme.colorScheme.error) 
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { /* 可查看详情 */ }
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 紧凑标签 - 严格区分 长期 vs 短期
            Surface(
                color = if (memory.isLongTerm) Color(0xFFE3F2FD) else Color(0xFFF1F8E9),
                shape = MaterialTheme.shapes.extraSmall
            ) {
                Text(
                    text = if (memory.isLongTerm) "长期记忆" else "短期记忆",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                    color = if (memory.isLongTerm) Color(0xFF1976D2) else Color(0xFF388E3C)
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(
                text = memory.displayKey,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = formatTime(memory.updatedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
        
        Spacer(modifier = Modifier.height(6.dp))

        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = memory.summary,
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            
            IconButton(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.size(32.dp).padding(start = 8.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
}

private fun formatTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff < 60_000 -> "刚刚"
        diff < 3600_000 -> "${diff / 60_000}分钟前"
        diff < 86400_000 -> "${diff / 3600_000}小时前"
        diff < 172800_000 -> "昨天"
        else -> {
            val sdf = java.text.SimpleDateFormat("MM-dd", Locale.getDefault())
            sdf.format(java.util.Date(timestamp))
        }
    }
}
