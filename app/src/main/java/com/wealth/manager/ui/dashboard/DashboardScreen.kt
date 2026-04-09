package com.wealth.manager.ui.dashboard

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.wealth.manager.R
import com.wealth.manager.ui.theme.DesignTokens
import com.wealth.manager.ui.theme.Income
import com.wealth.manager.ui.theme.Surface
import com.wealth.manager.ui.theme.TextSecondary
import com.wealth.manager.ui.theme.Warning
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToAdd: (Long?) -> Unit,
    onOpenDrawer: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var selectedExpense by remember { mutableStateOf<ExpenseItem?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    val backgroundResources = listOf(
        R.drawable.background_1,
        R.drawable.background_2
    )
    var currentBgIndex by remember { mutableIntStateOf(0) }
    var showAiInsightsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadDashboardData(showLoading = false)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, "菜单", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { 
                        showAiInsightsDialog = true
                        viewModel.generateRealtimeInsight() 
                    }) {
                        Icon(Icons.Default.Lightbulb, "AI 洞察", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onNavigateToAdd(null) },
                containerColor = MaterialTheme.colorScheme.primary,
                shape = CircleShape
            ) { Icon(Icons.Default.Add, "记账") }
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { paddingValues ->
        val listState = rememberLazyListState()

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                MonthOverviewCard(
                    monthTotal = state.monthTotal,
                    monthIncome = state.monthIncome,
                    recent7DaysTotal = state.recent7DaysTotal,
                    bgResourceId = backgroundResources[currentBgIndex],
                    customBgUri = state.customBackgroundImageUri,
                    onBgClick = { currentBgIndex = (currentBgIndex + 1) % backgroundResources.size }
                )
            }

            state.wowPreview?.let { wow ->
                item {
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        WowPreviewCard(wowPreview = wow)
                    }
                }
            }

            if (state.dailyExpenses.isEmpty() && !state.isLoading) {
                item { EmptyListPlaceholder() }
            } else {
                state.dailyExpenses.forEach { daily ->
                    item(key = daily.dateMillis) {
                        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                            DailyExpenseCard(
                                dailyExpense = daily,
                                onExpenseClick = { onNavigateToAdd(it.expense.id) },
                                onExpenseLongClick = { item ->
                                    selectedExpense = item
                                    showDeleteDialog = true
                                }
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }

        if (showDeleteDialog && selectedExpense != null) {
            DeleteConfirmDialog(
                amount = selectedExpense!!.expense.amount,
                onConfirm = {
                    viewModel.deleteExpense(selectedExpense!!.expense.id)
                    showDeleteDialog = false
                },
                onDismiss = { showDeleteDialog = false }
            )
        }

        if (showAiInsightsDialog) {
            AiInsightDialog(
                isAnalyzing = state.isAnalyzingInsight,
                text = state.aiInsightText,
                onDismiss = { showAiInsightsDialog = false }
            )
        }
    }
}

@Composable
fun WowPreviewCard(wowPreview: WowPreview) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "✨", fontSize = 18.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "钱包守护成功！",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "本周少花 ¥${NumberFormat.getNumberInstance(Locale.CHINA).format(wowPreview.savedAmount)}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            
            Text(
                text = "平时周均支出 ¥${NumberFormat.getNumberInstance(Locale.CHINA).format(wowPreview.lastWeekAmount)}，省了不少呢！",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
fun MonthOverviewCard(
    monthTotal: Double,
    monthIncome: Double,
    recent7DaysTotal: Double,
    bgResourceId: Int,
    customBgUri: String?,
    onBgClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(4f / 3f)
            .clickable { onBgClick() }
    ) {
        if (customBgUri != null) {
            AsyncImage(model = customBgUri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Image(painter = painterResource(id = bgResourceId), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }

        Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.4f), Color.Transparent, Color.Black.copy(alpha = 0.3f)))))

        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = DesignTokens.Spacing.lg, vertical = DesignTokens.Spacing.lg), verticalArrangement = Arrangement.Bottom) {
            Text(text = "本月支出", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.9f))
            Text(text = "¥ ${NumberFormat.getNumberInstance(Locale.CHINA).format(monthTotal)}", style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold, fontSize = 42.sp), color = Color.White)
            Spacer(Modifier.height(DesignTokens.Spacing.xl))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("本月收入", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
                    Text("¥ ${NumberFormat.getNumberInstance(Locale.CHINA).format(monthIncome)}", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold), color = Color.White)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("当月近7日支出", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
                    Text("¥ ${NumberFormat.getNumberInstance(Locale.CHINA).format(recent7DaysTotal)}", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold), color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun EmptyListPlaceholder() {
    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Surface)) {
            Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("暂无记账记录", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    Text("点击 + 开始记账", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun DeleteConfirmDialog(amount: Double, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除账单") },
        text = { Text("确定删除这笔 $amount 元的账单吗？") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("删除", color = Warning) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun AiInsightDialog(isAnalyzing: Boolean, text: String?, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("💡", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.width(8.dp))
                Text("旺财智能洞察", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (isAnalyzing) {
                    Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                        Spacer(Modifier.height(12.dp))
                        Text("旺财正在复盘您的消费...", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                } else {
                    Text(text ?: "继续保持良好的记账习惯哦！", style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("知道了") } }
    )
}

@Composable
fun DailyExpenseCard(dailyExpense: DailyExpense, onExpenseClick: (ExpenseItem) -> Unit, onExpenseLongClick: (ExpenseItem) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Surface) ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(dailyExpense.dateLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                Text("¥ ${NumberFormat.getNumberInstance(Locale.CHINA).format(dailyExpense.dayTotal)}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            }
            Spacer(modifier = Modifier.height(12.dp))
            dailyExpense.expenses.forEach { item ->
                DailyExpenseItem(item = item, onClick = { onExpenseClick(item) }, onLongClick = { onExpenseLongClick(item) })
                if (item != dailyExpense.expenses.last()) Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DailyExpenseItem(item: ExpenseItem, onClick: () -> Unit, onLongClick: () -> Unit) {
    val isIncome = item.category.type == "INCOME"
    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) { Text(item.category.icon, style = MaterialTheme.typography.bodySmall) }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(item.category.name, style = MaterialTheme.typography.bodyMedium)
                if (item.expense.note.isNotBlank()) Text(item.expense.note, style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Text("${if (isIncome) "+" else "-"}¥ ${NumberFormat.getNumberInstance(Locale.CHINA).format(item.expense.amount)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = if (isIncome) Income else Warning)
    }
}
