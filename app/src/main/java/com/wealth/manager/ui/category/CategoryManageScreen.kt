package com.wealth.manager.ui.category

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wealth.manager.data.entity.CategoryEntity
import com.wealth.manager.ui.theme.Primary
import com.wealth.manager.ui.theme.Surface
import com.wealth.manager.ui.theme.TextSecondary

// 预设 emoji 列表 - 已简化图标库
private val emojiList = listOf(
    "\uD83C\uDF57", "\uD83D\uDECD", "\uD83D\uDE8C", "\uD83C\uDFAE",
    "\uD83C\uDFE0", "\uD83C\uDFE5", "\uD83D\uDCDA", "\uD83D\uDCCB",
    "\uD83D\uDCB0", "\u2764\uFE0F", "\u2708\uFE0F", "\uD83D\uDE34",
    "\uD83C\uDF4E", "\uD83C\uDF55", "\uD83D\uDE97", "\uD83D\uDECF",
    "\uD83D\uDCBC", "\uD83C\uDF93", "\uD83D\uDCB5", "\uD83D\uDE84",
    "\uD83E\uDDFC" // 代表日用品的香皂图标
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CategoryManageScreen(
    viewModel: CategoryManageViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var showDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<CategoryEntity?>(null) }
    
    var categoryName by remember { mutableStateOf("") }
    var categoryEmoji by remember { mutableStateOf(emojiList.first()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "分类管理",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                },
                actions = {
                    IconButton(
                        onClick = {
                            editingCategory = null
                            categoryName = ""
                            categoryEmoji = emojiList.first()
                            showDialog = true
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "新增分类",
                            tint = Primary
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
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            // 默认分类分组
            if (state.defaultCategories.isNotEmpty()) {
                item {
                    Text(
                        text = "默认分类",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                items(state.defaultCategories) { category ->
                    CategoryItem(
                        name = category.name,
                        emoji = category.icon,
                        color = Color(android.graphics.Color.parseColor(category.color)),
                        onClick = {
                            editingCategory = category
                            categoryName = category.name
                            categoryEmoji = category.icon
                            showDialog = true
                        },
                        onDelete = { viewModel.deleteCategory(category.id) }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }

            // 自定义分类分组
            if (state.customCategories.isNotEmpty()) {
                item {
                    Text(
                        text = "自定义分类",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                items(state.customCategories) { category ->
                    CategoryItem(
                        name = category.name,
                        emoji = category.icon,
                        color = Color(android.graphics.Color.parseColor(category.color)),
                        onClick = {
                            editingCategory = category
                            categoryName = category.name
                            categoryEmoji = category.icon
                            showDialog = true
                        },
                        onDelete = { viewModel.deleteCategory(category.id) }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }

    // 新增/编辑分类弹窗
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(if (editingCategory == null) "新增分类" else "编辑分类") },
            text = {
                Column {
                    OutlinedTextField(
                        value = categoryName,
                        onValueChange = { categoryName = it },
                        label = { Text("分类名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "选择图标",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        emojiList.forEach { emoji ->
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (emoji == categoryEmoji) Primary.copy(alpha = 0.2f)
                                        else Surface
                                    )
                                    .clickable { categoryEmoji = emoji },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = emoji, fontSize = 24.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (categoryName.isNotBlank()) {
                            if (editingCategory == null) {
                                viewModel.addCategory(categoryName.trim(), categoryEmoji)
                            } else {
                                viewModel.updateCategory(editingCategory!!, categoryName.trim(), categoryEmoji)
                            }
                            showDialog = false
                        }
                    }
                ) {
                    Text(if (editingCategory == null) "添加" else "保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun CategoryItem(
    name: String,
    emoji: String,
    color: Color,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = emoji, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = TextSecondary.copy(alpha = 0.6f)
                )
            }
        }
    }
}
