package com.wealth.manager.ui.category

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.wealth.manager.ui.theme.Surface
import com.wealth.manager.ui.theme.TextSecondary
import com.wealth.manager.ui.theme.Warning

// 预设 emoji 列表
private val emojiList = listOf(
    "\uD83C\uDF57", "\uD83D\uDECD", "\uD83D\uDE8C", "\uD83C\uDFAE",
    "\uD83C\uDFE0", "\uD83C\uDFE5", "\uD83D\uDCDA", "\uD83D\uDCCB",
    "\uD83D\uDCB0", "\u2764\uFE0F", "\u2708\uFE0F", "\uD83D\uDE34",
    "\uD83C\uDF4E", "\uD83C\uDF55", "\uD83D\uDE97", "\uD83D\uDECF",
    "\uD83D\uDCBC", "\uD83C\uDF93", "\uD83D\uDCB5", "\uD83D\uDE84",
    "\uD83E\uDDFC"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CategoryManageScreen(
    viewModel: CategoryManageViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var selectedTabIndex by remember { mutableIntStateOf(0) } // 0 for Expense, 1 for Income

    var showDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf<CategoryEntity?>(null) }
    var editingCategory by remember { mutableStateOf<CategoryEntity?>(null) }
    
    var categoryName by remember { mutableStateOf("") }
    var categoryEmoji by remember { mutableStateOf(emojiList.first()) }
    var categoryType by remember { mutableStateOf("EXPENSE") }

    val tabs = listOf("支出", "收入")
    val blackColor = Color.Black

    Scaffold(
        topBar = {
            Column {
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
                                categoryType = if (selectedTabIndex == 0) "EXPENSE" else "INCOME"
                                showDialog = true
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "新增分类",
                                tint = blackColor
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = blackColor,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    divider = {}
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedTabIndex == index) MaterialTheme.colorScheme.primary else blackColor
                                )
                            }
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        val currentCategories = if (selectedTabIndex == 0) state.expenseCategories else state.incomeCategories

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            if (currentCategories.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "暂无${tabs[selectedTabIndex]}分类",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            } else {
                items(currentCategories) { category ->
                    CategoryItem(
                        category = category,
                        onClick = {
                            editingCategory = category
                            categoryName = category.name
                            categoryEmoji = category.icon
                            categoryType = category.type
                            showDialog = true
                        },
                        onLongClick = { showDeleteConfirm = category }
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = categoryType == "EXPENSE",
                            onClick = { categoryType = "EXPENSE" },
                            label = { Text("支出") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                selectedLabelColor = MaterialTheme.colorScheme.primary,
                                labelColor = blackColor
                            )
                        )
                        FilterChip(
                            selected = categoryType == "INCOME",
                            onClick = { categoryType = "INCOME" },
                            label = { Text("收入") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                selectedLabelColor = MaterialTheme.colorScheme.primary,
                                labelColor = blackColor
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
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
                                        if (emoji == categoryEmoji) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
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
                                viewModel.addCategory(categoryName.trim(), categoryEmoji, categoryType)
                            } else {
                                viewModel.updateCategory(editingCategory!!, categoryName.trim(), categoryEmoji, categoryType)
                            }
                            showDialog = false
                        }
                    }
                ) {
                    Text(if (editingCategory == null) "添加" else "保存", color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("取消", color = TextSecondary)
                }
            }
        )
    }

    // 删除确认弹窗
    if (showDeleteConfirm != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("删除分类") },
            text = { Text("确定要删除“${showDeleteConfirm?.name}”吗？关联的账单可能需要手动调整。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm?.let { viewModel.deleteCategory(it.id) }
                        showDeleteConfirm = null
                    }
                ) {
                    Text("删除", color = Warning)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) {
                    Text("取消", color = TextSecondary)
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CategoryItem(
    category: CategoryEntity,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val color = Color(android.graphics.Color.parseColor(category.color))
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
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
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = category.icon, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = category.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
