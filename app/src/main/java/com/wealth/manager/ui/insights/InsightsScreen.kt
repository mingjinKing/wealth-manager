package com.wealth.manager.ui.insights

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDateRangePickerState
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
import com.wealth.manager.ui.theme.Income
import com.wealth.manager.ui.theme.Surface
import com.wealth.manager.ui.theme.TextSecondary
import com.wealth.manager.ui.theme.Warning
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: InsightsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var showDateRangePicker by remember { mutableStateOf(false) }
    val dateRangePickerState = rememberDateRangePickerState()

    val dateFormatter = remember { SimpleDateFormat("MM月dd日", Locale.CHINA) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "钱花哪了",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showDateRangePicker = true }) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "筛选日期",
                            tint = Color.Black
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
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "分析中...", color = TextSecondary)
            }
        } else if (state.summaryItems.isEmpty() && state.totalIncome <= 0) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "🔍", style = MaterialTheme.typography.displayLarge)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (state.isDefaultMonth) "本月暂无记录" else "选定期间暂无记录",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary
                    )
                    if (!state.isDefaultMonth) {
                        TextButton(onClick = { 
                            val calendar = Calendar.getInstance()
                            calendar.set(Calendar.DAY_OF_MONTH, 1)
                            calendar.set(Calendar.HOUR_OF_DAY, 0)
                            calendar.set(Calendar.MINUTE, 0)
                            calendar.set(Calendar.SECOND, 0)
                            calendar.set(Calendar.MILLISECOND, 0)
                            val start = calendar.timeInMillis
                            
                            calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
                            calendar.set(Calendar.HOUR_OF_DAY, 23)
                            calendar.set(Calendar.MINUTE, 59)
                            calendar.set(Calendar.SECOND, 59)
                            calendar.set(Calendar.MILLISECOND, 999)
                            val end = calendar.timeInMillis
                            
                            viewModel.loadInsights(start, end, true) 
                        }) {
                            Text("返回本月", color = Color.Black)
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!state.isDefaultMonth) {
                            Text(
                                text = "${dateFormatter.format(Date(state.startTime))} - ${dateFormatter.format(Date(state.endTime))}",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.Black,
                                modifier = Modifier.padding(bottom = 4.dp),
                                fontWeight = FontWeight.Medium
                            )
                        }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (state.isDefaultMonth) "本月总支出" else "期间总支出",
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextSecondary
                            )
                            Text(
                                text = "¥${String.format(Locale.CHINA, "%.2f", state.totalAmount)}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Warning
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (state.isDefaultMonth) "本月总收入" else "期间总收入",
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextSecondary
                            )
                            Text(
                                text = "¥${String.format(Locale.CHINA, "%.2f", state.totalIncome)}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Income
                            )
                        }
                    }
                }

                // 1. 分类占比
                item {
                    Text(
                        text = if (state.isDefaultMonth) "消费分类占比" else "期间分类占比",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextSecondary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                items(state.summaryItems) { item ->
                    MonthlyCategoryCard(item = item)
                }

                // 2. AI 洞察
                if (state.globalAnalysis.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (state.isDefaultMonth) "AI 全局洞察" else "AI 期间分析",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextSecondary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    item {
                        GlobalAnalysisCard(analysisPoints = state.globalAnalysis)
                    }
                }

                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }

    if (showDateRangePicker) {
        DatePickerDialog(
            onDismissRequest = { showDateRangePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val start = dateRangePickerState.selectedStartDateMillis
                        val end = dateRangePickerState.selectedEndDateMillis
                        if (start != null && end != null) {
                            // 修正结束时间为当天的 23:59:59
                            val calendar = Calendar.getInstance()
                            calendar.timeInMillis = end
                            calendar.set(Calendar.HOUR_OF_DAY, 23)
                            calendar.set(Calendar.MINUTE, 59)
                            calendar.set(Calendar.SECOND, 59)
                            calendar.set(Calendar.MILLISECOND, 999)
                            viewModel.loadInsights(start, calendar.timeInMillis, false)
                        }
                        showDateRangePicker = false
                    }
                ) {
                    Text("确定", color = Color.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDateRangePicker = false }) {
                    Text("取消", color = TextSecondary)
                }
            }
        ) {
            DateRangePicker(
                state = dateRangePickerState,
                modifier = Modifier.height(480.dp),
                title = { Text(text = "选择统计期间", modifier = Modifier.padding(16.dp)) },
                headline = { Text(text = "筛选任意时段", modifier = Modifier.padding(horizontal = 16.dp)) },
                showModeToggle = false
            )
        }
    }
}

@Composable
fun MonthlyCategoryCard(item: CategorySummary) {
    var expanded by remember { mutableStateOf(false) }
    val dateFormatter = remember { SimpleDateFormat("MM-dd", Locale.CHINA) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = item.categoryEmoji, fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = item.categoryName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "¥${String.format(Locale.CHINA, "%.2f", item.amount)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Warning
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(item.percentage)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "${(item.percentage * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.width(32.dp)
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(8.dp))
                    item.items.forEach { expense ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (expense.note.isNotBlank()) expense.note else "无备注",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = dateFormatter.format(Date(expense.date)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                            Text(
                                text = "¥${String.format(Locale.CHINA, "%.2f", expense.amount)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GlobalAnalysisCard(analysisPoints: List<String>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "✨", fontSize = 18.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "AI 消费分析报告",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            analysisPoints.forEach { point ->
                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = point,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 22.sp
                    )
                }
            }
        }
    }
}

fun formatAmount(amount: Double): String {
    return String.format(Locale.getDefault(), "%,.2f", amount)
}
