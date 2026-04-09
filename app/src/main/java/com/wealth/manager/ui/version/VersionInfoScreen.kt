package com.wealth.manager.ui.version

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wealth.manager.config.AppConfig
import com.wealth.manager.config.NetworkConfig
import com.wealth.manager.ui.theme.Primary
import com.wealth.manager.ui.theme.Surface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VersionInfoScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    // 从手机实际安装的 APK 读取版本号
    val currentVersion = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0)).versionName
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }
    } catch (e: Exception) {
        "1.0.3" // fallback
    } ?: "1.0.3"
    val latestVersion = remember { mutableStateOf<String?>(null) }
    val isChecking = remember { mutableStateOf(false) }
    val hasNewVersion = remember { mutableStateOf(false) }
    val errorMessage = remember { mutableStateOf<String?>(null) }
    
    // 下载状态
    val isDownloading = remember { mutableStateOf(false) }
    val downloadProgress = remember { mutableStateOf(0) }
    val isInstalling = remember { mutableStateOf(false) }
    
    // 权限请求
    var pendingInstallFile by remember { mutableStateOf<File?>(null) }
    
    val scope = rememberCoroutineScope()
    
    // 检查安装权限
    val installPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        // 权限结果回来后，检查是否已授权
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (context.packageManager.canRequestPackageInstalls()) {
                // 继续安装
                pendingInstallFile?.let { file ->
                    pendingInstallFile = null
                    scope.launch {
                        installApk(context, file)
                    }
                }
            }
        }
    }

    // 检测最新版本
    fun checkForUpdates() {
        isChecking.value = true
        errorMessage.value = null
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val url = URL(AppConfig.VERSION_CHECK_URL)
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = NetworkConfig.VERSION_CONNECT_TIMEOUT * 1000
                    conn.readTimeout = NetworkConfig.VERSION_READ_TIMEOUT * 1000
                    conn.instanceFollowRedirects = false // 禁用重定向，手动处理
                    try {
                        if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                            val version = conn.inputStream.bufferedReader().readText().trim()
                            latestVersion.value = version
                            hasNewVersion.value = version != currentVersion
                        } else {
                            // 服务器错误
                            errorMessage.value = "版本服务器异常"
                            latestVersion.value = currentVersion
                            hasNewVersion.value = false
                        }
                    } finally {
                        conn.disconnect()
                    }
                }
            } catch (e: Exception) {
                // 网络错误时不显示"已是最新"，让用户知道网络有问题
                errorMessage.value = "网络异常，无法检测版本"
                latestVersion.value = currentVersion
                hasNewVersion.value = false
            } finally {
                isChecking.value = false
            }
        }
    }

    // 下载并安装 APK
    fun downloadAndInstall() {
        isDownloading.value = true
        downloadProgress.value = 0
        errorMessage.value = null
        
        scope.launch {
            try {
                val url = URL(AppConfig.APK_DOWNLOAD_URL)
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = NetworkConfig.VERSION_CONNECT_TIMEOUT * 1000
                conn.readTimeout = NetworkConfig.VERSION_READ_TIMEOUT * 1000
                
                val fileName = "update_${latestVersion.value}.apk"
                val apkFile = File(context.cacheDir, fileName)
                
                withContext(Dispatchers.IO) {
                    conn.inputStream.use { input ->
                        FileOutputStream(apkFile).use { output ->
                            val buffer = ByteArray(8192)
                            var bytesRead: Int
                            val totalBytes = conn.contentLength
                            var downloadedBytes = 0L
                            
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                downloadedBytes += bytesRead
                                if (totalBytes > 0) {
                                    downloadProgress.value = ((downloadedBytes * 100) / totalBytes).toInt()
                                }
                            }
                        }
                    }
                }
                
                isDownloading.value = false
                
                // 检查是否需要安装权限
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    if (!context.packageManager.canRequestPackageInstalls()) {
                        pendingInstallFile = apkFile
                        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        installPermissionLauncher.launch(intent)
                        return@launch
                    }
                }
                
                // 安装 APK
                isInstalling.value = true
                installApk(context, apkFile)
                
            } catch (e: Exception) {
                e.printStackTrace()
                isDownloading.value = false
                errorMessage.value = "下载失败: ${e.message}"
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("版本信息", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))

            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "App Logo",
                modifier = Modifier.size(80.dp),
                tint = Primary
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "知财",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "知其财，治其财",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // 版本信息卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Surface)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "当前版本",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = currentVersion,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "最新版本",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        if (isChecking.value) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = latestVersion.value ?: "-",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // 更新提示
            if (!isChecking.value && hasNewVersion.value) {
                Spacer(modifier = Modifier.height(24.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Primary.copy(alpha = 0.1f))
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "发现新版本",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "版本 ${latestVersion.value} 已经发布，点击下方按钮更新",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // 下载进度
                        if (isDownloading.value) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(
                                    progress = { downloadProgress.value / 100f },
                                    modifier = Modifier.size(48.dp),
                                    color = Primary,
                                    trackColor = Primary.copy(alpha = 0.2f),
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "下载中 ${downloadProgress.value}%",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        } else if (isInstalling.value) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(48.dp),
                                    color = Primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "安装中...",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        } else if (errorMessage.value != null) {
                            Text(
                                text = errorMessage.value!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { downloadAndInstall() },
                                colors = ButtonDefaults.buttonColors(containerColor = Primary)
                            ) {
                                Text("重试")
                            }
                        } else {
                            Button(
                                onClick = { downloadAndInstall() },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Primary)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("下载并安装更新")
                            }
                        }
                    }
                }
            }

            // 无需更新或网络错误时，显示检测按钮
            if (!isChecking.value && latestVersion.value != null && !hasNewVersion.value) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = if (errorMessage.value != null) errorMessage.value!! else "已是最新版本",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (errorMessage.value != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { checkForUpdates() },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Primary)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("重新检测")
                }
            }

            // 首次加载时自动检测
            if (latestVersion.value == null && !isChecking.value) {
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { checkForUpdates() },
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("检测版本")
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "© 2026 知财",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }
    }
}

// 安装 APK
private suspend fun installApk(ctx: Context, apkFile: File) {
    withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val packageInstaller = ctx.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                val sessionId = packageInstaller.createSession(params)
                val session = packageInstaller.openSession(sessionId)
                
                apkFile.inputStream().use { input ->
                    session.openWrite("base.apk", 0, apkFile.length()).use { output ->
                        input.copyTo(output)
                    }
                }
                
                val intent = Intent(ctx, Class.forName("com.wealth.manager.ui.MainActivity")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                val pendingIntent = android.app.PendingIntent.getActivity(
                    ctx, 0, intent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                )
                
                session.commit(pendingIntent.intentSender)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                // 安装失败，提示用户
            }
        }
    }
}

// Extension for copyTo
private fun java.io.InputStream.copyTo(out: java.io.OutputStream, bufferSize: Int = 8192): Long {
    var bytesCopied: Long = 0
    val buffer = ByteArray(bufferSize)
    var bytes = read(buffer)
    while (bytes >= 0) {
        out.write(buffer, 0, bytes)
        bytesCopied += bytes
        bytes = read(buffer)
    }
    return bytesCopied
}
