package com.wealth.manager.ui.category

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wealth.manager.data.entity.CategoryEntity
import com.wealth.manager.ui.theme.Surface
import com.wealth.manager.ui.theme.TextSecondary
import com.wealth.manager.ui.theme.ThemeViewModel
import com.wealth.manager.ui.theme.Warning
import java.util.Locale

// 预设 emoji 列表
private val emojiList = listOf(
    "🍗", "🛒", "地铁", "🎮", "🏠", "💊", "📚", "📦",
    "💰", "🧧", "🚲", "📈", "🎁", "📝", "♻️", "💸",
    "📱", "☕", "📉", "🚗", "🏖️"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManageScreen(
    onNavigateBack: () -> Unit,
    viewModel: CategoryManageViewModel = hiltViewModel(),
    themeViewModel: ThemeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val refundOnDeletion by themeViewModel.refundOnDeletion.collectAsState()
    val showAssetSelection by themeViewModel.showAssetSelection.collectAsState()
    
    var selectedTabIndex by remember { mutableIntStateOf(0) } // 0 for Expense, 1 for Income

    var showDialog by remember { mutableStateOf(false) }
    var showDeleteActionDialog by remember { mutableStateOf<CategoryEntity?>(null) }
    var showMigrationDialog by remember { mutableStateOf<CategoryEntity?>(null) }
    var showRefundConfirmDialog by remember { mutableStateOf<CategoryEntity?>(null) }
    var editingCategory by remember { mutableStateOf<CategoryEntity?>(null) }
    
    var categoryName by remember { mutableStateOf("") }
    var categoryEmoji by remember { mutableStateOf(emojiList.first()) }
    var categoryType by remember { mutableStateOf("EXPENSE") }

    val context = LocalContext.current
    val tabs = listOf("支出", "收入")

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("分类管理", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            editingCategory = null
                            categoryName = ""
                            categoryEmoji = emojiList.first()
                            categoryType = if (selectedTabIndex == 0) "EXPENSE" else "INCOME"
                            showDialog = true
                        }) {
                            Icon(Icons.Default.Add, contentDescription = "新增")
                        }
                    }
                )
                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = MaterialTheme.colorScheme.background,
                    indicator = { tabPositions ->
                        if (selectedTabIndex < tabPositions.size) {
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = { Text(title) }
                        )
                    }
                }
            }
        }
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
                    onLongClick = { showDeleteActionDialog = category }
                )
            }
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }

    // 1. 编辑/新增弹窗
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(if (editingCategory == null) "新增分类" else "编辑分类") },
            text = {
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = categoryType == "EXPENSE",
                            onClick = { categoryType = "EXPENSE" },
                            label = { Text("支出") }
                        )
                        FilterChip(
                            selected = categoryType == "INCOME",
                            onClick = { categoryType = "INCOME" },
                            label = { Text("收入") }
                        )
                    }
                    OutlinedTextField(
                        value = categoryName,
                        onValueChange = { categoryName = it },
                        label = { Text("分类名称") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("选择图标", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        emojiList.forEach { emoji ->
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (emoji == categoryEmoji) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Surface)
                                    .clickable { categoryEmoji = emoji },
                                contentAlignment = Alignment.Center
                            ) { Text(emoji, fontSize = 20.sp) }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (categoryName.isNotBlank()) {
                        if (editingCategory == null) {
                            viewModel.addCategory(categoryName.trim(), categoryEmoji, categoryType)
                        } else {
                            viewModel.updateCategory(editingCategory!!, categoryName.trim(), categoryEmoji, categoryType)
                        }
                        showDialog = false
                    }
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("取消") } }
        )
    }

    // 2. 删除决策弹窗
    if (showDeleteActionDialog != null) {
        val category = showDeleteActionDialog!!
        var expenseCount by remember { mutableIntStateOf(0) }
        LaunchedEffect(category.id) {
            expenseCount = viewModel.getExpenseCount(category.id)
        }

        AlertDialog(
            onDismissRequest = { showDeleteActionDialog = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("确认删除分类")
                }
            },
            text = {
                Column {
                    Text(
                        text = "确定要删除“${category.name}”吗？",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (expenseCount > 0) {
                        Spacer(Modifier.height(12.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "提示：分类下有关联账单",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "共计 $expenseCount 条记录，请选择处理方式：",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (expenseCount > 0) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { showMigrationDialog = category; showDeleteActionDialog = null },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                        ) {
                            Icon(Icons.Default.SwapHoriz, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("迁移账单到其他分类")
                        }
                        
                        Button(
                            onClick = { 
                                if (refundOnDeletion && showAssetSelection) {
                                    viewModel.prepareDeleteRefundInfo(category.id, true)
                                    showRefundConfirmDialog = category
                                } else {
                                    viewModel.confirmDeleteAndRefund(category.id, false)
                                }
                                showDeleteActionDialog = null 
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("删除分类及所有账单")
                        }
                    }
                } else {
                    Button(
                        onClick = { viewModel.deleteCategoryDirectly(category.id); showDeleteActionDialog = null },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("直接删除")
                    }
                }
            }
        )
    }

    // 3. 退款预览确认卡片
    if (showRefundConfirmDialog != null) {
        val category = showRefundConfirmDialog!!
        val refundItems = state.deleteRefundItems

        AlertDialog(
            onDismissRequest = { viewModel.clearRefundInfo(); showRefundConfirmDialog = null },
            title = { Text("退还金额详情") },
            text = {
                Column {
                    Text("删除账单后，系统将自动执行以下退还：", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(16.dp))
                    
                    if (refundItems.isNullOrEmpty()) {
                        Text("无关联账户账单，无需退还。", color = TextSecondary)
                    } else {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                refundItems.forEach { item ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(item.assetName, style = MaterialTheme.typography.bodyLarge)
                                        Text(
                                            text = "${if (item.amount >= 0) "+" else ""}${String.format(Locale.getDefault(), "%.2f", item.amount)}",
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp
                                        )
                                    }
                                    if (item != refundItems.last()) {
                                        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "提示：此操作将永久移除相关流水记录。", 
                        style = MaterialTheme.typography.bodySmall, 
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.confirmDeleteAndRefund(category.id, refundOnDeletion && showAssetSelection)
                        showRefundConfirmDialog = null
                        Toast.makeText(context, "已成功执行删除与退还", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) { Text("确认并执行") }
            }
        )
    }

    // 4. 选择迁移目标
    if (showMigrationDialog != null) {
        val sourceCategory = showMigrationDialog!!
        val otherCategories = if (sourceCategory.type == "EXPENSE") state.expenseCategories else state.incomeCategories
        val targets = otherCategories.filter { it.id != sourceCategory.id }

        AlertDialog(
            onDismissRequest = { showMigrationDialog = null },
            title = { Text("选择迁移目标") },
            text = {
                if (targets.isEmpty()) {
                    Text("没有其他同类型的分类可供迁移。")
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(targets) { target ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.migrateExpenses(sourceCategory.id, target.id)
                                        showMigrationDialog = null
                                        Toast.makeText(context, "账单已迁移至 ${target.name}", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primary.copy(0.1f),
                                    shape = CircleShape,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) { Text(target.icon, fontSize = 16.sp) }
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(target.name, style = MaterialTheme.typography.bodyLarge)
                                Spacer(Modifier.weight(1f))
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                            }
                            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(0.5f))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showMigrationDialog = null }) { Text("取消") } }
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Surface)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(0.1f)),
                contentAlignment = Alignment.Center
            ) { Text(category.icon) }
            Spacer(modifier = Modifier.width(16.dp))
            Text(category.name, style = MaterialTheme.typography.bodyLarge)
            if (category.isDefault) {
                Spacer(Modifier.width(8.dp))
                Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                    Text("默认", modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }
            }
        }
    }
}
