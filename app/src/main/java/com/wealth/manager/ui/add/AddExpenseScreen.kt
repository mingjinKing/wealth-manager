package com.wealth.manager.ui.add

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wealth.manager.ui.theme.Primary
import com.wealth.manager.ui.theme.Surface
import com.wealth.manager.ui.theme.TextSecondary
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddExpenseScreen(
    expenseToEdit: Long? = null,
    onNavigateToDashboard: () -> Unit = {},
    onNavigateBack: () -> Unit = {},
    viewModel: AddExpenseViewModel = hiltViewModel()
) {
    val categories by viewModel.categories.collectAsState()
    val editingExpense by viewModel.editingExpense.collectAsState()

    var amount by remember { mutableStateOf("0") }
    var note by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf<Long?>(null) }
    var selectedDateMillis by remember { mutableLongStateOf(getTodayStartMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }

    // 日期选择弹窗（使用Android原生DatePicker，支持中文月份）
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

    LaunchedEffect(expenseToEdit) {
        if (expenseToEdit != null) {
            viewModel.loadExpense(expenseToEdit)
        }
    }

    LaunchedEffect(editingExpense) {
        editingExpense?.let {
            amount = if (it.amount == it.amount.toLong().toDouble()) it.amount.toLong().toString()
            else it.amount.toString()
            note = it.note
            selectedCategoryId = it.categoryId
            selectedDateMillis = it.date
        }
    }

    val isEditMode = expenseToEdit != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isEditMode) "修改账单" else "添加账单",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
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
                    // 可点击的日期按钮
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showDatePicker = true }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = formatDateLabel(selectedDateMillis),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            // 顶部：类别 Chips（独立滚动区域）
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        categories.forEach { category ->
                            val isSelected = selectedCategoryId == category.id
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (isSelected) Primary else Surface)
                                    .clickable { selectedCategoryId = category.id }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(text = category.icon, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        text = category.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                                else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // 音符 + 金额 并排一行（固定在键盘上方）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        if (note.isEmpty()) {
                            Text(
                                text = "备注",
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
                }

                Text(
                    text = "¥ $amount",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.End
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 数字键盘（imePadding确保不被键盘遮挡）
            Column(
                modifier = Modifier
                    .imePadding()
                    .navigationBarsPadding()
            ) {
                NumericKeypadWithSign(
                    onNumberClick = { digit ->
                        amount = if (amount == "0") digit else amount + digit
                    },
                    onDecimalClick = {
                        if (!amount.contains(".")) amount += "."
                    },
                    onPlusClick = { },
                    onMinusClick = { },
                    onDeleteClick = {
                        amount = if (amount.length > 1) amount.dropLast(1) else "0"
                    },
                    onConfirmClick = {
                        val finalAmount = amount.toDoubleOrNull() ?: 0.0
                        if (finalAmount > 0 && selectedCategoryId != null) {
                            if (isEditMode && expenseToEdit != null) {
                                viewModel.updateExpense(expenseToEdit, finalAmount, selectedCategoryId!!, note)
                                onNavigateBack()
                            } else {
                                viewModel.addExpense(finalAmount, selectedCategoryId!!, note, selectedDateMillis)
                                onNavigateToDashboard()
                            }
                        }
                    }
                )
            }
        }

        // 日期选择弹窗（使用Android原生DatePicker，支持中文月份）
        if (showDatePicker) {
            val calendar = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
            val listener = android.app.DatePickerDialog.OnDateSetListener { _, year, month, dayOfMonth ->
                val cal = Calendar.getInstance().apply {
                    set(year, month, dayOfMonth, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                selectedDateMillis = cal.timeInMillis
                showDatePicker = false
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
            showDatePicker = false  // prevent recomposition
        }
    }
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
            KeypadButton("1", Modifier.weight(1f)) { onNumberClick("1") }
            KeypadButton("2", Modifier.weight(1f)) { onNumberClick("2") }
            KeypadButton("3", Modifier.weight(1f)) { onNumberClick("3") }
            KeypadButton("+", Modifier.weight(1f), isSpecial = true) { onPlusClick() }
            KeypadButton("−", Modifier.weight(1f), isSpecial = true) { onMinusClick() }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KeypadButton("4", Modifier.weight(1f)) { onNumberClick("4") }
            KeypadButton("5", Modifier.weight(1f)) { onNumberClick("5") }
            KeypadButton("6", Modifier.weight(1f)) { onNumberClick("6") }
            KeypadButton("⌫", Modifier.weight(1f), isSpecial = true) { onDeleteClick() }
            KeypadButton("✓", Modifier.weight(1f), isPrimary = true) { onConfirmClick() }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KeypadButton("7", Modifier.weight(1f)) { onNumberClick("7") }
            KeypadButton("8", Modifier.weight(1f)) { onNumberClick("8") }
            KeypadButton("9", Modifier.weight(1f)) { onNumberClick("9") }
            KeypadButton("·", Modifier.weight(1f), isSpecial = true) { onDecimalClick() }
            KeypadButton("00", Modifier.weight(1f), isSpecial = true) { onNumberClick("00") }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KeypadButton("0", Modifier.weight(1f)) { onNumberClick("0") }
            Spacer(modifier = Modifier.weight(4f))
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
            .height(56.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    isPrimary -> Primary
                    else -> Surface
                }
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        when (text) {
            "⌫" -> Icon(
                imageVector = Icons.AutoMirrored.Filled.Backspace,
                contentDescription = "删除",
                tint = if (enabled) TextSecondary else TextSecondary.copy(alpha = 0.3f)
            )
            "✓" -> Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "确认",
                tint = MaterialTheme.colorScheme.onPrimary
            )
            else -> Text(
                text = text,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                color = when {
                    !enabled -> TextSecondary.copy(alpha = 0.3f)
                    isPrimary || isSpecial -> MaterialTheme.colorScheme.onPrimary
                    else -> MaterialTheme.colorScheme.onSurface
                },
                textAlign = TextAlign.Center
            )
        }
    }
}
