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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
                    Text(
                        text = "今天",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(end = 16.dp)
                    )
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
                .imePadding()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 顶部：类别 Chips（5个/行）
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
                            Text(
                                text = category.icon,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = category.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // 底部：音符 + 金额 并排一行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧：备注输入
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
                            textStyle = TextStyle(
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(Primary),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }

                // 右侧：金额
                Text(
                    text = "¥ $amount",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.End
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 数字键盘
            NumericKeypadWithSign(
                onNumberClick = { digit ->
                    amount = if (amount == "0") digit else amount + digit
                },
                onDecimalClick = {
                    if (!amount.contains(".")) {
                        amount += "."
                    }
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
                            viewModel.addExpense(finalAmount, selectedCategoryId!!, note)
                            onNavigateToDashboard()
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun NumericKeypadWithSign(
    onNumberClick: (String) -> Unit,
    onDecimalClick: () -> Unit,
    onPlusClick: () -> Unit,
    onMinusClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onConfirmClick: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Row 1: 1 2 3 + −
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            KeypadButton("1", Modifier.weight(1f), onClick = { onNumberClick("1") })
            KeypadButton("2", Modifier.weight(1f), onClick = { onNumberClick("2") })
            KeypadButton("3", Modifier.weight(1f), onClick = { onNumberClick("3") })
            KeypadButton("+", Modifier.weight(1f), onClick = onPlusClick, isSpecial = true)
            KeypadButton("−", Modifier.weight(1f), onClick = onMinusClick, isSpecial = true)
        }

        // Row 2: 4 5 6 ⌫ ✓
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            KeypadButton("4", Modifier.weight(1f), onClick = { onNumberClick("4") })
            KeypadButton("5", Modifier.weight(1f), onClick = { onNumberClick("5") })
            KeypadButton("6", Modifier.weight(1f), onClick = { onNumberClick("6") })
            KeypadButton("⌫", Modifier.weight(1f), onClick = onDeleteClick, isSpecial = true)
            KeypadButton("✓", Modifier.weight(1f), onClick = onConfirmClick, isPrimary = true)
        }

        // Row 3: 7 8 9 .
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            KeypadButton("7", Modifier.weight(1f), onClick = { onNumberClick("7") })
            KeypadButton("8", Modifier.weight(1f), onClick = { onNumberClick("8") })
            KeypadButton("9", Modifier.weight(1f), onClick = { onNumberClick("9") })
            KeypadButton("·", Modifier.weight(1f), onClick = onDecimalClick, isSpecial = true)
            KeypadButton("00", Modifier.weight(1f), onClick = { onNumberClick("00") }, isSpecial = true)
        }

        // Row 4: 0
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            KeypadButton("0", Modifier.weight(1f), onClick = { onNumberClick("0") })
            Spacer(modifier = Modifier.weight(4f))
        }
    }
}

@Composable
fun KeypadButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    enabled: Boolean = true,
    isSpecial: Boolean = false,
    isPrimary: Boolean = false
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
                color = if (enabled) {
                    if (isPrimary) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface
                } else TextSecondary.copy(alpha = 0.3f),
                textAlign = TextAlign.Center
            )
        }
    }
}
