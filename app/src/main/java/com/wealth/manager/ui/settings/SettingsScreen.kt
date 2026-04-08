package com.wealth.manager.ui.settings

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.wealth.manager.BuildConfig
import com.wealth.manager.ui.navigation.Screen
import com.wealth.manager.util.LogCollector
import com.wealth.manager.ui.theme.*
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    navController: NavController? = null,
    viewModel: ThemeViewModel = hiltViewModel<ThemeViewModel>()
) {
    val currentThemeColor by viewModel.currentThemeColor.collectAsState()
    val assetPasswordProtection by viewModel.assetPasswordProtection.collectAsState()

    // 密码设置弹窗状态
    var showPasswordSetDialog by remember { mutableStateOf(false) }
    var showPasswordChangeDialog by remember { mutableStateOf(false) }
    var passwordInput by remember { mutableStateOf("") }
    var passwordConfirm by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current

    // 启用密码保护时弹窗
    if (showPasswordSetDialog) {
        AlertDialog(
            onDismissRequest = {
                showPasswordSetDialog = false
                passwordInput = ""
                passwordConfirm = ""
                passwordError = null
            },
            title = { Text("设置密码") },
            text = {
                Column {
                    if (!viewModel.hasAssetPassword()) {
                        Text("请设置资产管理访问密码", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { if (it.length <= 6) passwordInput = it },
                        label = { Text("输入密码（最多6位）") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        isError = passwordError != null,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = passwordConfirm,
                        onValueChange = { if (it.length <= 6) passwordConfirm = it },
                        label = { Text("确认密码") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        isError = passwordError != null,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (passwordError != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = passwordError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        passwordError = null
                        if (passwordInput.length < 4) {
                            passwordError = "密码至少4位"
                            return@TextButton
                        }
                        if (passwordInput != passwordConfirm) {
                            passwordError = "两次密码不一致"
                            return@TextButton
                        }
                        viewModel.setAssetPassword(passwordInput)
                        viewModel.setAssetPasswordProtection(true)
                        showPasswordSetDialog = false
                        passwordInput = ""
                        passwordConfirm = ""
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPasswordSetDialog = false
                        passwordInput = ""
                        passwordConfirm = ""
                        passwordError = null
                        // 关闭开关（用户取消了设置密码）
                        if (!viewModel.hasAssetPassword()) {
                            viewModel.setAssetPasswordProtection(false)
                        }
                    }
                ) {
                    Text("取消")
                }
            }
        )
    }

    // 修改密码弹窗
    if (showPasswordChangeDialog) {
        AlertDialog(
            onDismissRequest = {
                showPasswordChangeDialog = false
                passwordInput = ""
                passwordConfirm = ""
                passwordError = null
            },
            title = { Text("修改密码") },
            text = {
                Column {
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { if (it.length <= 6) passwordInput = it },
                        label = { Text("输入新密码（最多6位）") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        isError = passwordError != null,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = passwordConfirm,
                        onValueChange = { if (it.length <= 6) passwordConfirm = it },
                        label = { Text("确认新密码") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        isError = passwordError != null,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (passwordError != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = passwordError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        passwordError = null
                        if (passwordInput.length < 4) {
                            passwordError = "密码至少4位"
                            return@TextButton
                        }
                        if (passwordInput != passwordConfirm) {
                            passwordError = "两次密码不一致"
                            return@TextButton
                        }
                        viewModel.setAssetPassword(passwordInput)
                        showPasswordChangeDialog = false
                        passwordInput = ""
                        passwordConfirm = ""
                        Toast.makeText(context, "密码修改成功", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPasswordChangeDialog = false
                        passwordInput = ""
                        passwordConfirm = ""
                        passwordError = null
                    }
                ) {
                    Text("取消")
                }
            }
        )
    }

    val prefs = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
    val currentBgUri = prefs.getString("custom_bg_uri", null)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        // TopBar
        TopAppBar(
            title = { Text("设置", fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Background)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // 主题色选择
            Text(
                text = "主题颜色",
                style = MaterialTheme.typography.titleMedium,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    ThemeColorItem("华丽金", ThemeGold, currentThemeColor) {
                        viewModel.setThemeColor(ThemeGold)
                    }
                    ThemeColorItem("清新蓝", ThemeBlue, currentThemeColor) {
                        viewModel.setThemeColor(ThemeBlue)
                    }
                    ThemeColorItem("魅力粉", ThemePink, currentThemeColor) {
                        viewModel.setThemeColor(ThemePink)
                    }
                    ThemeColorItem("优雅紫", ThemePurple, currentThemeColor) {
                        viewModel.setThemeColor(ThemePurple)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 背景图设置
            Text(
                text = "界面定制",
                style = MaterialTheme.typography.titleMedium,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Surface)
            ) {
                val launcher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri: Uri? ->
                    uri?.let {
                        try {
                            val inputStream = context.contentResolver.openInputStream(it)
                            val fileName = "custom_background_${System.currentTimeMillis()}.jpg"
                            val file = File(context.filesDir, fileName)
                            inputStream?.use { input ->
                                file.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                            prefs.edit().putString("custom_bg_uri", file.absolutePath).apply()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { launcher.launch(arrayOf("image/*")) }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.padding(start = 16.dp)) {
                        Text(
                            text = "首页背景图",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (currentBgUri != null) "已设置自定义背景" else "使用默认背景",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    if (currentBgUri != null) {
                        TextButton(onClick = {
                            prefs.edit().remove("custom_bg_uri").apply()
                        }) {
                            Text("清除", color = Warning)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "隐私保护",
                style = MaterialTheme.typography.titleMedium,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Surface)
            ) {
                Column {
                    // 资产管理密码保护开关
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "资产管理密码保护",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = if (assetPasswordProtection) "访问资产管理需输入密码" else "关闭密码保护",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        Switch(
                            checked = assetPasswordProtection,
                            onCheckedChange = { enabled ->
                                if (enabled && !viewModel.hasAssetPassword()) {
                                    // 还没有设置密码，先弹窗设置
                                    showPasswordSetDialog = true
                                } else if (enabled && viewModel.hasAssetPassword()) {
                                    viewModel.setAssetPasswordProtection(true)
                                } else {
                                    viewModel.setAssetPasswordProtection(false)
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            )
                        )
                    }

                    // 如果已经启用，显示"修改密码"按钮
                    if (assetPasswordProtection && viewModel.hasAssetPassword()) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = Background
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showPasswordChangeDialog = true }
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "修改密码",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = TextSecondary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "AI 记忆",
                style = MaterialTheme.typography.titleMedium,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { navController?.navigate(Screen.MemoryManagement.route) }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "记忆管理",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "查看和清除 AI 从对话中学到的记忆",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "记账设置",
                style = MaterialTheme.typography.titleMedium,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "关联账户",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "记账时选择资金从哪个账户支出",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    Switch(
                        checked = viewModel.showAssetSelection.collectAsState().value,
                        onCheckedChange = { viewModel.setShowAssetSelection(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        )
                    )
                }
            }

            // ===== 调试工具区域（仅测试版本显示） =====
            if (BuildConfig.DEBUG) {
                val ctx = LocalContext.current
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "调试工具",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Surface)
                ) {
                    var isUploading by remember { mutableStateOf(false) }
                    var uploadResult by remember { mutableStateOf<String?>(null) }
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "上传调试日志",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "将本机运行日志上传到服务器，帮助定位问题",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                            Button(
                                onClick = {
                                    isUploading = true
                                    uploadResult = null
                                    val deviceId = android.provider.Settings.Secure.getString(ctx.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
                                    viewModel.uploadLogs(deviceId) { success: Boolean, msg: String ->
                                        isUploading = false
                                        uploadResult = msg
                                    }
                                },
                                enabled = !isUploading,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.tertiary
                                )
                            ) {
                                if (isUploading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = MaterialTheme.colorScheme.onTertiary,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("上传中...")
                                } else {
                                    Text("${LogCollector.getSummary()}")
                                }
                            }
                        }
                        if (uploadResult != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = uploadResult!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (uploadResult!!.contains("成功")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ThemeColorItem(
    name: String,
    color: Color,
    selectedColor: Color,
    onClick: () -> Unit
) {
    val isSelected = color == selectedColor
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(color)
                .border(
                    width = if (isSelected) 2.dp else 0.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = if (color == ThemeGold) Color.Black else Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.labelMedium,
            color = if (isSelected) MaterialTheme.colorScheme.primary else TextSecondary
        )
    }
}
