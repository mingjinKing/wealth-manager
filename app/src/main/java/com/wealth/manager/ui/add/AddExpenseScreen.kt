package com.wealth.manager.ui.add

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wealth.manager.data.entity.CategoryEntity
import com.wealth.manager.ui.theme.Background
import com.wealth.manager.ui.theme.Primary
import com.wealth.manager.ui.theme.Surface
import com.wealth.manager.ui.theme.TextSecondary
import com.wealth.manager.ui.theme.Warning
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun AddExpenseScreen(
    expenseToEdit: Long? = null,
    onNavigateToDashboard: () -> Unit = {},
    onNavigateBack: () -> Unit = {},
    viewModel: AddExpenseViewModel = hiltViewModel()
) {
    val categories by viewModel.categories.collectAsState()
    val editingExpense by viewModel.editingExpense.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()

    var amount by remember { mutableStateOf("0") }
    var note by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf<Long?>(null) }
    var selectedDateMillis by remember { mutableLongStateOf(getTodayStartMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }

    // 删除分类相关状态
    var categoryToDelete by remember { mutableStateOf<CategoryEntity?>(null) }

    // 计算器逻辑状态
    var pendingValue by remember { mutableStateOf<Double?>(null) }
    var pendingOperator by remember { mutableStateOf<String?>(null) }
    var shouldResetInput by remember { mutableStateOf(false) }

    // 拦截系统滑动返回
    BackHandler { onNavigateBack() }

    // 日期选择弹窗
    if (showDatePicker) {
        val calendar = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
        val listener = android.app.DatePickerDialog.OnDateSetListener { _, year, month, dayOfMonth ->
            val cal = Calendar.getInstance().apply {
                set(year, month, dayOfMonth, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }
            selectedDateMillis = cal.timeInMillis
        }
        val dialog = android.app.DatePickerDialog(
            LocalContext.current,
            listener,
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        dialog.setOnDismissListener { showDatePicker = false }
        dialog.show()
        showDatePicker = false
    }

    // 删除分类确认弹窗
    categoryToDelete?.let { category ->
        AlertDialog(
            onDismissRequest = { categoryToDelete = null },
            title = { Text("删除分类") },
            text = { Text("确定要删除分类 \"${category.name}\" 吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteCategory(category.id)
                        if (selectedCategoryId == category.id) {
                            selectedCategoryId = null
                        }
                        categoryToDelete = null
                    }
                ) {
                    Text("确定", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { categoryToDelete = null }) {
                    Text("取消")
                }
            }
        )
    }

    LaunchedEffect(expenseToEdit) {
        if (expenseToEdit != null) {
            viewModel.loadExpense(expenseToEdit)
        }
    }

    LaunchedEffect(editingExpense) {
        editingExpense?.let {
            amount = formatDoubleToString(it.amount)
            note = it.note
            selectedCategoryId = it.categoryId
            selectedDateMillis = it.date
        }
    }

    val isEditMode = expenseToEdit != null
    val focusManager = LocalFocusManager.current

    // 计算逻辑函数
    val performCalculation = {
        if (pendingValue != null && pendingOperator != null) {
            val current = if (shouldResetInput) 0.0 else (amount.toDoubleOrNull() ?: 0.0)
            val result = when (pendingOperator) {
                "+" -> pendingValue!! + current
                "−" -> pendingValue!! - current
                else -> current
            }
            amount = formatDoubleToString(result)
            pendingValue = null
            pendingOperator = null
            shouldResetInput = false
        }
    }

    // 实时显示的金额（计算结果预览）
    val displayAmount = remember(amount, pendingValue, pendingOperator, shouldResetInput) {
        if (pendingOperator != null && pendingValue != null) {
            val current = if (shouldResetInput) 0.0 else (amount.toDoubleOrNull() ?: 0.0)
            val result = when (pendingOperator) {
                "+" -> pendingValue!! + current
                "−" -> pendingValue!! - current
                else -> current
            }
            formatDoubleToString(result)
        } else {
            amount
        }
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
                TopAppBar(
                    title = {
                        Text(
                            text = if (isEditMode) "修改账单" else "添加账单",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    },
                    actions = {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showDatePicker = true }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = formatDateLabel(selectedDateMillis),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Black,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
                
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = Color.Black,
                    divider = {},
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = Primary
                        )
                    }
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { viewModel.setTab(0); selectedCategoryId = null },
                        text = { Text("添加账单", fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal, color = Color.Black) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { viewModel.setTab(1); selectedCategoryId = null },
                        text = { Text("添加收入", fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal, color = Color.Black) }
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { focusManager.clearFocus() })
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
            ) {
                // 类别选择区域 - 升级为 4 列网格布局
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    items(categories.size) { index ->
                        val category = categories[index]
                        val isSelected = selectedCategoryId == category.id
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .combinedClickable(
                                    onClick = { selectedCategoryId = category.id },
                                    onLongClick = { categoryToDelete = category }
                                )
                                .padding(vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) Primary else Surface),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = category.icon, fontSize = 24.sp)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = category.name,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isSelected) Primary else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // 顶部区分线
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = TextSecondary.copy(alpha = 0.15f)
                )

                // 底部输入区域卡片
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RectangleShape,
                    colors = CardDefaults.cardColors(containerColor = Background),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Surface)
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                if (note.isEmpty()) {
                                    Text(
                                        text = "添加备注",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary.copy(alpha = 0.5f)
                                    )
                                }
                                BasicTextField(
                                    value = note,
                                    onValueChange = { if (it.length <= 50) note = it },
                                    textStyle = TextStyle(fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface),
                                    cursorBrush = SolidColor(Primary),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                            }

                            Text(
                                text = "¥ $displayAmount",
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (selectedTab == 1) Color(0xFF4CAF50) else Warning,
                                textAlign = TextAlign.End
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        NumericKeypadWithSign(
                            onNumberClick = { digit ->
                                if (shouldResetInput) {
                                    amount = digit
                                    shouldResetInput = false
                                } else {
                                    amount = if (amount == "0") digit else amount + digit
                                }
                            },
                            onDecimalClick = {
                                if (shouldResetInput) {
                                    amount = "0."
                                    shouldResetInput = false
                                } else if (!amount.contains(".")) {
                                    amount += "."
                                }
                            },
                            onPlusClick = {
                                performCalculation()
                                pendingValue = amount.toDoubleOrNull() ?: 0.0
                                pendingOperator = "+"
                                shouldResetInput = true
                            },
                            onMinusClick = {
                                performCalculation()
                                pendingValue = amount.toDoubleOrNull() ?: 0.0
                                pendingOperator = "−"
                                shouldResetInput = true
                            },
                            onDeleteClick = {
                                if (shouldResetInput) {
                                    amount = "0"
                                    shouldResetInput = false
                                } else {
                                    amount = if (amount.length > 1) amount.dropLast(1) else "0"
                                }
                            },
                            onConfirmClick = {
                                if (pendingOperator != null) {
                                    performCalculation()
                                }
                                
                                val finalAmount = amount.toDoubleOrNull() ?: 0.0
                                if (finalAmount > 0 && selectedCategoryId != null) {
                                    if (isEditMode && expenseToEdit != null) {
                                        viewModel.updateExpense(expenseToEdit, finalAmount, selectedCategoryId!!, note, selectedDateMillis) {
                                            onNavigateBack()
                                        }
                                    } else {
                                        viewModel.addExpense(finalAmount, selectedCategoryId!!, note, selectedDateMillis) {
                                            onNavigateToDashboard()
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun formatDoubleToString(d: Double): String {
    return if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()
}

private fun formatDateLabel(millis: Long): String {
    val today = getTodayStartMillis()
    val yesterday = today - 24 * 60 * 60 * 1000
    return when (millis) {
        in today..(today + 24 * 60 * 60 * 1000 - 1) -> "今天"
        in yesterday..(yesterday + 24 * 60 * 60 * 1000 - 1) -> "昨天"
        else -> {
            val cal = Calendar.getInstance().apply { timeInMillis = millis }
            "${cal.get(Calendar.MONTH) + 1}月${cal.get(Calendar.DAY_OF_MONTH)}日"
        }
    }
}

private fun getTodayStartMillis(): Long {
    return Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

@Composable
private fun NumericKeypadWithSign(
    onNumberClick: (String) -> Unit,
    onDecimalClick: () -> Unit,
    onPlusClick: () -> Unit,
    onMinusClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onConfirmClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KeypadButton("7", Modifier.weight(1f)) { onNumberClick("7") }
            KeypadButton("8", Modifier.weight(1f)) { onNumberClick("8") }
            KeypadButton("9", Modifier.weight(1f)) { onNumberClick("9") }
            KeypadButton("⌫", Modifier.weight(1f), isSpecial = true) { onDeleteClick() }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KeypadButton("4", Modifier.weight(1f)) { onNumberClick("4") }
            KeypadButton("5", Modifier.weight(1f)) { onNumberClick("5") }
            KeypadButton("6", Modifier.weight(1f)) { onNumberClick("6") }
            KeypadButton("+", Modifier.weight(1f), isSpecial = true) { onPlusClick() }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KeypadButton("1", Modifier.weight(1f)) { onNumberClick("1") }
            KeypadButton("2", Modifier.weight(1f)) { onNumberClick("2") }
            KeypadButton("3", Modifier.weight(1f)) { onNumberClick("3") }
            KeypadButton("−", Modifier.weight(1f), isSpecial = true) { onMinusClick() }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KeypadButton("0", Modifier.weight(2f)) { onNumberClick("0") }
            KeypadButton("·", Modifier.weight(1f), isSpecial = true) { onDecimalClick() }
            KeypadButton("✓", Modifier.weight(1f), isPrimary = true) { onConfirmClick() }
        }
    }
}

@Composable
private fun KeypadButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isSpecial: Boolean = false,
    isPrimary: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(49.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isPrimary) Primary else Surface)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        when (text) {
            "⌫" -> Icon(
                imageVector = Icons.AutoMirrored.Filled.Backspace,
                contentDescription = "删除",
                tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
            "✓" -> Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "确认",
                tint = MaterialTheme.colorScheme.onPrimary
            )
            else -> Text(
                text = text,
                style = if (text == "·") {
                    MaterialTheme.typography.headlineSmall.copy(fontSize = 28.sp, fontWeight = FontWeight.Bold)
                } else {
                    MaterialTheme.typography.headlineSmall
                },
                color = when {
                    isPrimary -> MaterialTheme.colorScheme.onPrimary
                    isSpecial -> MaterialTheme.colorScheme.onSurface
                    enabled -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                }
            )
        }
    }
}
