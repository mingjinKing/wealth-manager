package com.wealth.manager.ui.insights

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wealth.manager.data.entity.ExpenseEntity
import com.wealth.manager.rules.Insight
import com.wealth.manager.ui.theme.Income
import com.wealth.manager.ui.theme.Surface
import com.wealth.manager.ui.theme.TextSecondary
import com.wealth.manager.ui.theme.Warning
import java.text.SimpleDateFormat
import java.util.*

/**
 * AI 区域显示模式，用于稳定渲染，防止文字流引起的闪烁
 */
private enum class AiDisplayMode {
    IDLE, LOADING, STREAMING, ERROR
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: InsightsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var showDateRangePicker by remember { mutableStateOf(false) }
    val dateRangePickerState = rememberDateRangePickerState()
    val scrollState = rememberLazyListState()

    // 自动跟随文字流滚动到底部
    LaunchedEffect(state.aiAnalysisResult) {
        if (state.isAiAnalyzing && !state.aiAnalysisResult.isNullOrEmpty()) {
            scrollState.animateScrollToItem(scrollState.layoutInfo.totalItemsCount - 1)
        }
    }

    // 关键：确定显示模式。AnimatedContent 只监听 Mode 变化，不监听文字内容变化。
    val aiMode = remember(state.isAiAnalyzing, state.aiAnalysisResult, state.aiAnalysisError) {
        when {
            state.aiAnalysisError != null -> AiDisplayMode.ERROR
            state.isAiAnalyzing && (state.aiAnalysisResult ?: "").isEmpty() -> AiDisplayMode.LOADING
            !state.aiAnalysisResult.isNullOrEmpty() -> AiDisplayMode.STREAMING
            else -> AiDisplayMode.IDLE
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "钱花哪了", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (!state.isAiAnalyzing) {
                        TextButton(onClick = { viewModel.triggerAiAnalysis() }) {
                            Icon(imageVector = Icons.Default.AutoAwesome, modifier = Modifier.size(18.dp), contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text(text = "AI 复盘", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                    IconButton(onClick = { showDateRangePicker = true }) {
                        Icon(imageVector = Icons.Default.DateRange, contentDescription = "筛选日期", tint = Color.Black)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { paddingValues ->
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(strokeWidth = 3.dp)
            }
        } else {
            LazyColumn(
                state = scrollState,
                modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. 顶部总览卡片
                item {
                    val period = if (state.isDefaultMonth) "本月" else "期间"
                    SummaryOverviewCard(
                        period = period,
                        totalExpense = state.totalAmount,
                        totalIncome = state.totalIncome
                    )
                }

                item { Text(text = "消费分类占比", style = MaterialTheme.typography.labelLarge, color = TextSecondary) }
                items(state.summaryItems) { item -> MonthlyCategoryCard(item = item) }

                // 2. AI 洞察区域
                item {
                    val title = when (aiMode) {
                        AiDisplayMode.LOADING -> "旺财深度分析中"
                        AiDisplayMode.STREAMING -> "旺财复盘报告"
                        else -> "AI 全局洞察"
                    }
                    Text(text = title, style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                }

                item {
                    AnimatedContent(
                        targetState = aiMode,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "AiContentAnimation"
                    ) { mode ->
                        when (mode) {
                            AiDisplayMode.LOADING -> AiAnalysisLoadingCard(status = state.aiThoughtStatus)
                            AiDisplayMode.STREAMING -> AiAnalysisResultCard(result = state.aiAnalysisResult ?: "")
                            AiDisplayMode.ERROR -> AiAnalysisErrorCard(error = state.aiAnalysisError ?: "分析失败")
                            AiDisplayMode.IDLE -> if (state.globalAnalysis.isNotEmpty()) GlobalAnalysisCard(insights = state.globalAnalysis) else Spacer(Modifier.height(1.dp))
                        }
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
                TextButton(onClick = {
                    dateRangePickerState.selectedEndDateMillis?.let { end ->
                        val cal = Calendar.getInstance().apply { timeInMillis = end; set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59) }
                        dateRangePickerState.selectedStartDateMillis?.let { start ->
                            viewModel.loadInsights(start, cal.timeInMillis, false)
                        }
                    }
                    showDateRangePicker = false
                }) { Text("确定", color = Color.Black) }
            }
        ) { DateRangePicker(state = dateRangePickerState, modifier = Modifier.height(480.dp), showModeToggle = false) }
    }
}

@Composable
fun SummaryOverviewCard(period: String, totalExpense: Double, totalIncome: Double) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "${period}总支出", color = TextSecondary, style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "¥${String.format(Locale.CHINA, "%.2f", totalExpense)}",
                    color = Warning,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            
            // 分割线
            VerticalDivider(
                modifier = Modifier.height(32.dp).width(1.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "${period}总收入", color = TextSecondary, style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "¥${String.format(Locale.CHINA, "%.2f", totalIncome)}",
                    color = Income,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun MonthlyCategoryCard(item: CategorySummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = item.categoryEmoji, fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = item.categoryName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Text(text = "¥${String.format(Locale.CHINA, "%.2f", item.amount)}", color = Warning, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { item.percentage },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outlineVariant
            )
        }
    }
}

@Composable
fun GlobalAnalysisCard(insights: List<Insight>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.AutoAwesome, tint = MaterialTheme.colorScheme.primary, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(text = "AI 消费分析报告", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            insights.forEach { insight ->
                Row(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(text = "•", modifier = Modifier.padding(end = 8.dp), color = MaterialTheme.colorScheme.primary)
                    Text(text = insight.message, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun AiAnalysisResultCard(result: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "✨", fontSize = 18.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "旺财复盘报告", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = renderMarkdown(result), style = MaterialTheme.typography.bodyMedium, lineHeight = 24.sp)
        }
    }
}

@Composable
private fun AiAnalysisLoadingCard(status: String) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f))) {
        Column(modifier = Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = status.ifEmpty { "旺财正在通过数据库复盘您的消费..." }, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun AiAnalysisErrorCard(error: String) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.05f))) {
        Text(text = "⚠️ $error", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun renderMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    text.split("\n").forEach { line ->
        val trimmed = line.trim()
        when {
            trimmed.startsWith("## ") -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 19.sp, color = Color.Black)) { append(trimmed.substring(3) + "\n") }
            }
            trimmed.startsWith("### ") -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 17.sp)) { append(trimmed.substring(4) + "\n") }
            }
            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                append("  •  ")
                append(parseInlineStyles(trimmed.substring(2)))
                append("\n")
            }
            else -> {
                append(parseInlineStyles(line))
                append("\n")
            }
        }
    }
}

private fun parseInlineStyles(line: String): AnnotatedString = buildAnnotatedString {
    val boldRegex = Regex("\\*\\*(.*?)\\*\\*")
    var last = 0
    boldRegex.findAll(line).forEach { m ->
        append(line.substring(last, m.range.first))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color.Black)) { append(m.groupValues[1]) }
        last = m.range.last + 1
    }
    append(line.substring(last))
}
