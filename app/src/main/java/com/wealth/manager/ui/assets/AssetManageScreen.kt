package com.wealth.manager.ui.assets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wealth.manager.data.entity.AssetEntity
import com.wealth.manager.data.entity.AssetType
import com.wealth.manager.ui.achievements.WealthGoalsViewModel
import com.wealth.manager.ui.theme.Surface
import com.wealth.manager.ui.theme.TextSecondary
import com.wealth.manager.ui.theme.ThemeViewModel
import com.wealth.manager.ui.theme.Warning
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AssetManageScreen(
    onNavigateBack: () -> Unit,
    viewModel: AssetViewModel = hiltViewModel(),
    achievementsViewModel: WealthGoalsViewModel = hiltViewModel(),
    themeViewModel: ThemeViewModel = hiltViewModel()
) {
    val assets by viewModel.assets.collectAsState()
    val totalAssets by viewModel.totalAssets.collectAsState()
    val totalLiabilities by viewModel.totalLiabilities.collectAsState()
    val state by achievementsViewModel.state.collectAsState()
    val assetPasswordProtection by themeViewModel.assetPasswordProtection.collectAsState()
    val isAmountVisible = state.isAssetVisible // 资产页同步成就页的资产隐私开关
    
    var showAddDialog by remember { mutableStateOf(false) }
    var editingAsset by remember { mutableStateOf<AssetEntity?>(null) }

    // 密码验证弹窗状态
    var showAssetPasswordDialog by remember { mutableStateOf(false) }
    var assetPasswordInput by remember { mutableStateOf("") }
    var assetPasswordError by remember { mutableStateOf<String?>(null) }

    if (showAssetPasswordDialog) {
        AlertDialog(
            onDismissRequest = {
                showAssetPasswordDialog = false
                assetPasswordInput = ""
                assetPasswordError = null
            },
            title = { Text("请输入密码") },
            text = {
                Column {
                    Text("查看资产金额需要密码验证", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(12.dp))
                    TextField(
                        value = assetPasswordInput,
                        onValueChange = { if (it.length <= 6) assetPasswordInput = it },
                        label = { Text("密码") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        isError = assetPasswordError != null,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (assetPasswordError != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = assetPasswordError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (themeViewModel.verifyAssetPassword(assetPasswordInput)) {
                            showAssetPasswordDialog = false
                            assetPasswordInput = ""
                            assetPasswordError = null
                            achievementsViewModel.toggleAssetVisibility()
                        } else {
                            assetPasswordError = "密码错误"
                        }
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAssetPasswordDialog = false
                        assetPasswordInput = ""
                        assetPasswordError = null
                    }
                ) {
                    Text("取消")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("资产管理", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { 
                        if (!isAmountVisible && assetPasswordProtection) {
                            showAssetPasswordDialog = true
                        } else {
                            achievementsViewModel.toggleAssetVisibility()
                        }
                    }) {
                        Icon(
                            imageVector = if (isAmountVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = "切换可见性"
                        )
                    }
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "添加资产")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }
            
            // 资产概览卡片
            item {
                AssetSummaryCard(totalAssets, totalLiabilities, isAmountVisible)
            }

            // 资产列表按类型分组
            val groups = assets.groupBy { it.type }
            AssetType.entries.forEach { type ->
                val typeAssets = groups[type] ?: emptyList()
                if (typeAssets.isNotEmpty()) {
                    item {
                        Text(
                            text = getTypeNameLabel(type),
                            style = MaterialTheme.typography.titleSmall,
                            color = TextSecondary,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    items(typeAssets) { asset ->
                        AssetItem(
                            asset = asset,
                            isVisible = isAmountVisible,
                            onEdit = { editingAsset = asset },
                            onDelete = { viewModel.deleteAsset(asset) },
                            onToggleHidden = { viewModel.updateAssetHidden(asset, !asset.isHidden) }
                        )
                    }
                }
            }
            
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }

    if (showAddDialog) {
        AddAssetDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, type, balance, customType, isHidden ->
                viewModel.addAsset(name, type, balance, customType, isHidden)
                showAddDialog = false
            }
        )
    }

    if (editingAsset != null) {
        EditBalanceDialog(
            asset = editingAsset!!,
            onDismiss = { editingAsset = null },
            onConfirm = { newBalance ->
                viewModel.updateAssetBalance(editingAsset!!, newBalance)
                editingAsset = null
            }
        )
    }
}

@Composable
fun AssetSummaryCard(totalAssets: Double, totalLiabilities: Double, isVisible: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text("净资产 (计入部分)", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium)
            Text(
                if (isVisible) "¥${String.format(Locale.getDefault(), "%.2f", totalAssets + totalLiabilities)}" else "****", 
                color = MaterialTheme.colorScheme.onPrimary, 
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("总资产", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
                    Text(if (isVisible) "¥${String.format(Locale.getDefault(), "%.2f", totalAssets)}" else "****", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("总负债", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
                    Text(if (isVisible) "¥${String.format(Locale.getDefault(), "%.2f", totalLiabilities)}" else "****", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun AssetItem(
    asset: AssetEntity, 
    isVisible: Boolean,
    onEdit: () -> Unit, 
    onDelete: () -> Unit,
    onToggleHidden: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() },
        shape = RoundedCornerShape(16.dp),
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
                    .background(Color(android.graphics.Color.parseColor(asset.color)).copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Text(asset.icon, fontSize = 20.sp)
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(asset.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    if (asset.isHidden) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.VisibilityOff,
                            contentDescription = "不计入总资产",
                            modifier = Modifier.size(14.dp),
                            tint = TextSecondary
                        )
                    }
                }
                Text(
                    text = if (asset.type == AssetType.CUSTOM) asset.customType ?: "自定义" else getTypeNameLabel(asset.type),
                    style = MaterialTheme.typography.bodySmall, 
                    color = TextSecondary
                )
            }
            
            Text(
                if (isVisible) "¥${String.format(Locale.getDefault(), "%.2f", asset.balance)}" else "****",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = if (asset.balance < 0) Warning else Color.Unspecified
            )
            
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = null, tint = TextSecondary)
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("修改余额") },
                        onClick = { showMenu = false; onEdit() },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text(if (asset.isHidden) "计入总资产" else "不计入总资产") },
                        onClick = { showMenu = false; onToggleHidden() },
                        leadingIcon = { 
                            Icon(
                                imageVector = if (asset.isHidden) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = null
                            ) 
                        }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("删除账户", color = Warning) },
                        onClick = { showMenu = false; onDelete() },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Warning) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddAssetDialog(
    onDismiss: () -> Unit, 
    onConfirm: (String, AssetType, Double, String?, Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var balance by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(AssetType.BANK) }
    var customTypeName by remember { mutableStateOf("") }
    var isHidden by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加资产账户") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name, 
                    onValueChange = { name = it }, 
                    label = { Text("账户名称") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = balance,
                    onValueChange = { balance = it },
                    label = { Text("初始余额") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                
                Text("账户类型", style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(), 
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssetType.entries
                        .filter { it != AssetType.OTHER }
                        .forEach { type ->
                            FilterChip(
                                selected = selectedType == type,
                                onClick = { selectedType = type },
                                label = { Text(getTypeNameLabel(type)) }
                            )
                        }
                }

                if (selectedType == AssetType.CUSTOM) {
                    OutlinedTextField(
                        value = customTypeName,
                        onValueChange = { customTypeName = it },
                        label = { Text("自定义类型名称") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("计入总资产", modifier = Modifier.weight(1f))
                    Switch(
                        checked = !isHidden,
                        onCheckedChange = { isHidden = !it }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { 
                    onConfirm(
                        name, 
                        selectedType, 
                        balance.toDoubleOrNull() ?: 0.0,
                        if (selectedType == AssetType.CUSTOM) customTypeName else null,
                        isHidden
                    ) 
                },
                enabled = name.isNotBlank() && (selectedType != AssetType.CUSTOM || customTypeName.isNotBlank())
            ) {
                Text("添加")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
fun EditBalanceDialog(asset: AssetEntity, onDismiss: () -> Unit, onConfirm: (Double) -> Unit) {
    var balance by remember { mutableStateOf(asset.balance.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改余额 - ${asset.name}") },
        text = {
            OutlinedTextField(
                value = balance,
                onValueChange = { balance = it },
                label = { Text("当前余额") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(balance.toDoubleOrNull() ?: 0.0) }) {
                Text("保存")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

fun getTypeNameLabel(type: AssetType): String = when (type) {
    AssetType.CASH -> "现金"
    AssetType.BANK -> "银行卡"
    AssetType.ALIPAY -> "三方支付"
    AssetType.INVESTMENT -> "投资理财"
    AssetType.CREDIT_CARD -> "信用卡"
    AssetType.LOAN -> "借贷"
    AssetType.DEPOSIT -> "存款"
    AssetType.HOUSING_FUND -> "公积金"
    AssetType.ENTERPRISE_ANNUITY -> "企业年金"
    AssetType.OTHER -> "其他"
    AssetType.CUSTOM -> "自定义"
}
