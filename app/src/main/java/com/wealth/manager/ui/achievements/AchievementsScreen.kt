package com.wealth.manager.ui.achievements

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wealth.manager.ui.theme.Primary
import com.wealth.manager.ui.theme.Surface
import com.wealth.manager.ui.theme.TextSecondary
import com.wealth.manager.ui.theme.Warning
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementsScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToAssets: () -> Unit = {},
    viewModel: AchievementsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val sheetState = rememberModalBottomSheetState()
    var showBudgetSheet by remember { mutableStateOf(false) }
    var showAssetGoalSheet by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var showTrendHelp by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "成长", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showHelpDialog = true }) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.HelpOutline, contentDescription = "帮助")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                item { Spacer(modifier = Modifier.height(16.dp)) }

                // 1. 资产卡片
                item {
                    NetWorthHeader(
                        netWorth = state.netWorth,
                        isVisible = state.isAssetVisible,
                        onToggleVisibility = { viewModel.toggleAssetVisibility() },
                        onClick = onNavigateToAssets
                    )
                }

                // 2. 资产目标卡片
                item {
                    val daysLeft = ((state.goalDate - System.currentTimeMillis()) / (24 * 60 * 60 * 1000)).toInt()
                    val totalDays = ((state.goalDate - state.goalStartDate) / (24 * 60 * 60 * 1000)).toInt().coerceAtLeast(1)
                    val timeProgress = 1f - (daysLeft.toFloat() / totalDays).coerceIn(0f, 1f)
                    
                    GoalCard(
                        title = "资产目标",
                        description = "目标：${if (state.isGoalVisible) "¥" + formatAmount(state.assetGoal) else "****"} (${if(daysLeft > 0) "${daysLeft}天后截止" else "已截止"})",
                        currentInfo = "当前净资产：${if (state.isGoalVisible) "¥" + formatAmount(state.netWorth) else "****"}",
                        progress = (state.netWorth / state.assetGoal).coerceAtMost(1.0).toFloat(),
                        timeProgress = timeProgress,
                        isVisible = state.isGoalVisible,
                        onToggleVisibility = { viewModel.toggleGoalVisibility() },
                        onClick = { showAssetGoalSheet = true }
                    )
                }

                // 3. 预算管理目标卡片
                item {
                    val isMonthly = state.budgetType == "MONTHLY"
                    val budget = if (isMonthly) state.monthlyBudget else state.weeklyBudget
                    val spent = if (isMonthly) state.monthlySpent else state.weeklySpent
                    val budgetProgress = if (budget > 0) (spent / budget).toFloat() else 0f
                    
                    GoalCard(
                        title = "预算管理 (${if (isMonthly) "月" else "周"})",
                        description = "${if (isMonthly) "本月" else "本周"}总预算：${if (state.isBudgetVisible) "¥" + formatAmount(budget) else "****"}",
                        currentInfo = "已支出：${if (state.isBudgetVisible) "¥" + formatAmount(spent) else "****"}",
                        subInfo = "可去算算账查看明细",
                        progress = budgetProgress.coerceAtMost(1f),
                        progressColor = if (budgetProgress > 0.9f) Warning else Primary,
                        isVisible = state.isBudgetVisible,
                        onToggleVisibility = { viewModel.toggleBudgetVisibility() },
                        onClick = { showBudgetSheet = true }
                    )
                }

                // 4. 增长趋势预测卡片 (小眼睛改为小问号)
                item {
                    TrendForecastCard(
                        points = state.trendPoints,
                        onShowHelp = { showTrendHelp = true }
                    )
                }

                item { Spacer(modifier = Modifier.height(32.dp)) }
            }
        }
    }

    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = { Text("成长说明") },
            text = {
                Text(
                    text = "本页面帮助你追踪长期财务目标和短期支出预算。\n\n• 点击资产卡片跳转管理详情\n• 点击目标卡片可重新设定金额和时间\n• 每个卡片可独立控制金额可见性",
                    lineHeight = 22.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = false }) { Text("知道了") }
            }
        )
    }

    if (showTrendHelp) {
        AlertDialog(
            onDismissRequest = { showTrendHelp = false },
            title = { Text("资产增长趋势说明") },
            text = {
                Text(
                    text = "该图表展示了你从设定目标之日起的资产增长情况：\n\n• 虚线：期望增长路径，代表达到目标所需的平均进度。\n• 实线：实际净资产轨迹，反映你真实的财富积累过程。\n• 若实线低于虚线，系统会提醒你偏离轨道，建议优化开支。",
                    lineHeight = 22.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { showTrendHelp = false }) { Text("知道了") }
            }
        )
    }

    if (showBudgetSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBudgetSheet = false },
            sheetState = sheetState
        ) {
            BudgetSettingSheet(
                initialType = state.budgetType,
                initialMonthly = state.monthlyBudget,
                initialWeekly = state.weeklyBudget,
                onConfirm = { type, amount ->
                    viewModel.updateBudget(type, amount)
                    showBudgetSheet = false
                }
            )
        }
    }

    if (showAssetGoalSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAssetGoalSheet = false },
            sheetState = sheetState
        ) {
            AssetGoalSettingSheet(
                initialAmount = state.assetGoal,
                initialDate = state.goalDate,
                onConfirm = { amount, date ->
                    viewModel.updateAssetGoal(amount, date)
                    showAssetGoalSheet = false
                }
            )
        }
    }
}

@Composable
fun NetWorthHeader(netWorth: Double, isVisible: Boolean, onToggleVisibility: () -> Unit, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Primary)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "当前净资产", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
                IconButton(onClick = { onToggleVisibility() }, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = if (isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Text(
                text = if (isVisible) "¥${formatAmount(netWorth)}" else "****",
                color = Color.White,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black
            )
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                color = Color.White.copy(alpha = 0.2f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "查看资产详情 >",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
fun TrendForecastCard(points: List<TrendPoint>, onShowHelp: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "资产增长趋势", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                IconButton(onClick = onShowHelp, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                        contentDescription = "说明",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            val lastActualPoint = points.lastOrNull { it.actual != null }
            val deviation = if (lastActualPoint != null) {
                val actualVal = lastActualPoint.actual ?: 0.0
                val expectedAtThatTime = lastActualPoint.expected
                (actualVal - expectedAtThatTime) / expectedAtThatTime.coerceAtLeast(1.0)
            } else 0.0

            if (deviation < -0.1) {
                Surface(
                    color = Warning.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Warning, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "资产增长偏离期望轨道，请注意开支！", color = Warning, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // 折线图绘制 (带坐标轴)
            Box(modifier = Modifier.fillMaxWidth().height(200.dp).padding(start = 40.dp, bottom = 20.dp)) {
                TrendLineChart(points = points)
            }
            
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(Color.Gray.copy(alpha = 0.5f), CircleShape))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("期望", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(Primary, CircleShape))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("现状", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }
            }
        }
    }
}

@Composable
fun TrendLineChart(points: List<TrendPoint>) {
    if (points.isEmpty()) return
    
    val maxVal = (points.maxOf { it.expected + (it.actual ?: 0.0) } * 1.2).toFloat().coerceAtLeast(1f)
    val textSecondaryColor = TextSecondary.toArgb()

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val spacing = width / (points.size - 1).coerceAtLeast(1)

        // 1. 绘制纵轴刻度 (金额)
        val ySteps = 4
        for (i in 0..ySteps) {
            val y = height - (i.toFloat() / ySteps * height)
            val value = (i.toFloat() / ySteps * maxVal).toInt()
            
            // 绘制横向背景参考线
            drawLine(
                color = Color.LightGray.copy(alpha = 0.3f),
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 1.dp.toPx()
            )
            
            // 绘制刻度数值
            drawContext.canvas.nativeCanvas.drawText(
                if (value >= 10000) "${value/1000}k" else "$value",
                -35.dp.toPx(),
                y + 4.dp.toPx(),
                android.graphics.Paint().apply {
                    color = textSecondaryColor
                    textSize = 10.sp.toPx()
                    textAlign = android.graphics.Paint.Align.RIGHT
                }
            )
        }

        // 2. 绘制横轴刻度 (日期)
        points.forEachIndexed { index, point ->
            val x = index * spacing
            drawContext.canvas.nativeCanvas.drawText(
                point.label,
                x,
                height + 15.dp.toPx(),
                android.graphics.Paint().apply {
                    color = textSecondaryColor
                    textSize = 10.sp.toPx()
                    textAlign = android.graphics.Paint.Align.CENTER
                }
            )
        }

        // 3. 绘制期望路径 (虚线)
        val expectedPath = Path()
        points.forEachIndexed { index, point ->
            val x = index * spacing
            val y = height - (point.expected.toFloat() / maxVal * height)
            if (index == 0) expectedPath.moveTo(x, y) else expectedPath.lineTo(x, y)
        }
        drawPath(
            path = expectedPath,
            color = Color.Gray.copy(alpha = 0.5f),
            style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f))
        )

        // 4. 绘制现状路径 (实线)
        val actualPath = Path()
        var firstActual = true
        points.forEachIndexed { index, point ->
            val actualVal = point.actual
            if (actualVal != null) {
                val x = index * spacing
                val y = height - (actualVal.toFloat() / maxVal * height)
                if (firstActual) {
                    actualPath.moveTo(x, y)
                    firstActual = false
                } else {
                    actualPath.lineTo(x, y)
                }
                drawCircle(color = Primary, radius = 4.dp.toPx(), center = Offset(x, y))
            }
        }
        drawPath(
            path = actualPath,
            color = Primary,
            style = Stroke(width = 3.dp.toPx())
        )
    }
}

@Composable
fun GoalCard(
    title: String,
    description: String,
    currentInfo: String,
    subInfo: String? = null,
    progress: Float,
    timeProgress: Float? = null,
    progressColor: Color = Primary,
    isVisible: Boolean,
    onToggleVisibility: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                IconButton(onClick = onToggleVisibility, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = if (isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description, 
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp), 
                color = TextSecondary
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = currentInfo, 
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, lineHeight = 24.sp)
            )
            
            if (subInfo != null) {
                Text(
                    text = subInfo,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            
            // 金额进度
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(CircleShape),
                color = progressColor,
                trackColor = progressColor.copy(alpha = 0.1f)
            )
            
            if (timeProgress != null) {
                Spacer(modifier = Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "剩余时间", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Spacer(modifier = Modifier.width(8.dp))
                    LinearProgressIndicator(
                        progress = { timeProgress },
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(CircleShape),
                        color = Color(0xFF2196F3),
                        trackColor = Color(0xFF2196F3).copy(alpha = 0.15f)
                    )
                }
            }
        }
    }
}

@Composable
fun BudgetSettingSheet(
    initialType: String,
    initialMonthly: Double,
    initialWeekly: Double,
    onConfirm: (String, Double) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(if (initialType == "MONTHLY") 0 else 1) }
    var monthlyAmount by remember { mutableStateOf(if(initialMonthly > 0) initialMonthly.toString() else "") }
    var weeklyAmount by remember { mutableStateOf(if(initialWeekly > 0) initialWeekly.toString() else "") }

    Column(
        modifier = Modifier
            .padding(24.dp)
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        Text("预算管理设定", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        
        TabRow(selectedTabIndex = selectedTab, containerColor = Color.Transparent) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                Text("月预算", modifier = Modifier.padding(12.dp))
            }
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                Text("周预算", modifier = Modifier.padding(12.dp))
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        OutlinedTextField(
            value = if (selectedTab == 0) monthlyAmount else weeklyAmount,
            onValueChange = { if (selectedTab == 0) monthlyAmount = it else weeklyAmount = it },
            label = { Text(if (selectedTab == 0) "本月预算总额" else "本周预算总额") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
            prefix = { Text("¥") }
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = { 
                val amount = (if (selectedTab == 0) monthlyAmount else weeklyAmount).toDoubleOrNull() ?: 0.0
                onConfirm(if (selectedTab == 0) "MONTHLY" else "WEEKLY", amount)
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("保存设定")
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetGoalSettingSheet(
    initialAmount: Double,
    initialDate: Long,
    onConfirm: (Double, Long) -> Unit
) {
    var amount by remember { mutableStateOf(if(initialAmount > 0) initialAmount.toString() else "") }
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialDate
    )

    Column(
        modifier = Modifier
            .padding(24.dp)
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        Text("资产目标设定", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(24.dp))
        
        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it },
            label = { Text("目标净资产金额") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
            prefix = { Text("¥") }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedCard(
            onClick = { showDatePicker = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = Primary)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("达成日期目标", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Text(
                        text = SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault()).format(Date(datePickerState.selectedDateMillis ?: initialDate)),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = { 
                onConfirm(
                    amount.toDoubleOrNull() ?: 0.0, 
                    datePickerState.selectedDateMillis ?: initialDate
                ) 
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("确认并开启挑战")
        }
        Spacer(modifier = Modifier.height(16.dp))
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("确定") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

fun formatAmount(amount: Double): String {
    return String.format(Locale.getDefault(), "%,.2f", amount)
}
