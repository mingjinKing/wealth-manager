package com.wealth.manager.ui.how

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wealth.manager.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*


import kotlin.text.Regex
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.text.BasicText

/**
 * Markdown 渲染文本
 * 支持 **bold**, *italic*, `code`, # 标题, - 列表
 */
@Composable
fun MarkdownText(
    content: String,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    textColor: Color = MaterialTheme.colorScheme.onSurface
) {
    // 不使用 remember 缓存，确保每次都重新解析最新内容
    val annotatedString = parseMarkdown(content, textStyle.fontSize)
    
    BasicText(
        text = annotatedString,
        modifier = modifier,
        style = textStyle.copy(color = textColor)
    )
}

private fun parseMarkdown(text: String, baseFontSize: androidx.compose.ui.unit.TextUnit): AnnotatedString {
    return buildAnnotatedString {
        val lines = text.split('\n')
        
        lines.forEachIndexed { index, line ->
            when {
                line.startsWith("### ") -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = baseFontSize * 1.1)) {
                        append(line.removePrefix("### "))
                    }
                }
                line.startsWith("## ") -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = baseFontSize * 1.2)) {
                        append(line.removePrefix("## "))
                    }
                }
                line.startsWith("# ") -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = baseFontSize * 1.3)) {
                        append(line.removePrefix("# "))
                    }
                }
                line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ") -> {
                    append("  • ")
                    parseInlineStyles(line.trimStart().removePrefix("- ").removePrefix("* "))
                }
                else -> {
                    parseInlineStyles(line)
                }
            }
            
            if (index < lines.size - 1) {
                append('\n')
            }
        }
    }
}

private fun AnnotatedString.Builder.parseInlineStyles(text: String) {
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                } else {
                    append(text[i])
                    i++
                }
            }
            text.startsWith("`", i) -> {
                val end = text.indexOf("`", i + 1)
                if (end != -1) {
                    withStyle(SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = Color.Gray.copy(alpha = 0.2f)
                    )) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(text[i])
                    i++
                }
            }
            text.startsWith("*", i) && !text.startsWith("**", i) -> {
                val end = text.indexOf("*", i + 1)
                if (end != -1 && end > i) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(text[i])
                    i++
                }
            }
            else -> {
                append(text[i])
                i++
            }
        }
    }
}

@Composable
fun HowToSpendScreen(
    viewModel: HowToSpendViewModel = hiltViewModel(
        viewModelStoreOwner = LocalContext.current as androidx.lifecycle.ViewModelStoreOwner
    )
) {
    val state by viewModel.state.collectAsState()
    val scrollState = rememberLazyListState()
    var showHistory by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    // 自动滚动到最新消息
    LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.content) {
        if (state.messages.isNotEmpty()) {
            scrollState.animateScrollToItem(state.messages.size - 1)
        }
    }

    // 删除历史会话弹窗状态
    var sessionToDelete by remember { mutableStateOf<ConversationSession?>(null) }
    var historyRefreshTrigger by remember { mutableIntStateOf(0) }

    // 显示历史记录列表
    if (showHistory) {
        HistoryListScreen(
            onBackClick = { showHistory = false },
            onSessionClick = { session ->
                viewModel.loadHistorySession(session.id)
                showHistory = false
            },
            onSessionDelete = { session ->
                sessionToDelete = session
            },
            viewModel = viewModel,
            refreshTrigger = historyRefreshTrigger
        )
    }

    // 删除确认弹窗
    sessionToDelete?.let { session ->
        AlertDialog(
            onDismissRequest = { sessionToDelete = null },
            title = { Text("删除会话") },
            text = { Text("确定要删除 \"${session.title}\" 吗？删除后无法恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteHistorySession(session.id)
                        sessionToDelete = null
                        historyRefreshTrigger++
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionToDelete = null }) {
                    Text("取消")
                }
            }
        )
    }

    if (!showHistory) {
        // 显示当前对话
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .imePadding()
        ) {
            // TopBar
            HowToSpendTopBar(
                onNewConversation = { viewModel.clearMessages() },
                onHistoryClick = { showHistory = true }
            )

            // 内容区 - 使用 Box 确保内容可滚动但不被压缩
            Box(modifier = Modifier.weight(1f)) {
                if (state.messages.isEmpty()) {
                    // 空状态 - 显示引导
                    EmptyStateContent(
                        quickEntries = viewModel.quickEntries,
                        onQuickEntryClick = { viewModel.sendQuickEntry(it) },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // 对话列表
                    LazyColumn(
                        state = scrollState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp)
                    ) {
                        items(state.messages, key = { it.id }) { message ->
                            if (message.isUser) {
                                UserMessageBubble(message.content)
                            } else {
                                AiMessageBubble(
                                    message = message,
                                    onLikeClick = { viewModel.toggleLike(message.id) }
                                )
                            }
                        }

                        // AI 思考中
                        if (state.isLoading && state.currentThinking.isNotEmpty()) {
                            item {
                                ThinkingIndicator(state.currentThinking)
                            }
                        }
                    }
                }
            }

            // 输入区 - 固定在底部，跟随键盘
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 8.dp
            ) {
                HowToSpendInput(
                    text = state.inputText,
                    onTextChange = viewModel::updateInput,
                    onSendClick = {
                        focusManager.clearFocus() // 收起键盘
                        viewModel.sendMessage()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryListScreen(
    onBackClick: () -> Unit,
    onSessionClick: (ConversationSession) -> Unit,
    onSessionDelete: (ConversationSession) -> Unit,
    viewModel: HowToSpendViewModel,
    refreshTrigger: Int = 0
) {
    var sessions by remember { mutableStateOf(emptyList<ConversationSession>()) }

    LaunchedEffect(Unit, refreshTrigger) {
        sessions = viewModel.loadHistoryList()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopAppBar(
            title = { Text("历史记录", fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "返回")
                }
            }
        )

        if (sessions.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("暂无历史记录", color = TextSecondary)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(sessions) { session ->
                    HistorySessionCard(
                        session = session,
                        onClick = { onSessionClick(session) },
                        onLongClick = { onSessionDelete(session) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistorySessionCard(
    session: ConversationSession,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val dateFormat = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = session.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row {
                Text(
                    text = dateFormat.format(Date(session.timestamp)),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "${session.messageCount} 条消息",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HowToSpendTopBar(
    onNewConversation: () -> Unit,
    onHistoryClick: () -> Unit
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "💭 ",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "怎么花",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        actions = {
            IconButton(onClick = onNewConversation) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "新建对话",
                    tint = Color.Black.copy(alpha = 0.6f)
                )
            }
            IconButton(onClick = onHistoryClick) {
                Icon(
                    imageVector = Icons.Default.MilitaryTech,
                    contentDescription = "历史记录",
                    tint = Color.Black.copy(alpha = 0.6f)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        )
    )
}

@Composable
private fun EmptyStateContent(
    quickEntries: List<QuickEntry>,
    onQuickEntryClick: (QuickEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        // 引导语
        Text(
            text = "💭",
            style = MaterialTheme.typography.displayMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "你是想买什么？",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = "还是日常开销？",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 快捷入口卡片
        quickEntries.forEach { entry ->
            QuickEntryCard(
                entry = entry,
                onClick = { onQuickEntryClick(entry) }
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun QuickEntryCard(
    entry: QuickEntry,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = entry.emoji,
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = entry.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UserMessageBubble(content: String, onCopied: () -> Unit = {}) {
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val context = LocalContext.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 4.dp,
                bottomStart = 16.dp,
                bottomEnd = 16.dp
            ),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.combinedClickable(
                onClick = {},
                onLongClick = {
                    clipboardManager.setText(AnnotatedString(content))
                    Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                    onCopied()
                }
            )
        ) {
            Text(
                text = content,
                modifier = Modifier.padding(12.dp),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AiMessageBubble(
    message: ChatMessage,
    onLikeClick: () -> Unit,
    onCopied: () -> Unit = {}
) {
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 4.dp,
                topEnd = 16.dp,
                bottomStart = 16.dp,
                bottomEnd = 16.dp
            ),
            color = Surface
        ) {
            Column(
                modifier = Modifier
                    .padding(12.dp)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = {
                            clipboardManager.setText(AnnotatedString(message.content))
                            Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                            onCopied()
                        }
                    )
            ) {
                // AI 头像 + 名称
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "🤖", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "旺财",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 消息内容 - Markdown 渲染（长按复制）
                MarkdownText(
                    content = message.content,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    textColor = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // 快捷操作栏（移除追问按钮）
        if (!message.isUser) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickActionChip(
                    icon = if (message.isLiked) "❤️" else "👍",
                    text = if (message.isLiked) "有用" else "有用",
                    isActive = message.isLiked,
                    onClick = onLikeClick
                )
                QuickActionChip(
                    icon = "⭐",
                    text = "收藏",
                    onClick = { /* TODO */ }
                )
            }
        }
    }
}

@Composable
private fun QuickActionChip(
    icon: String,
    text: String,
    isActive: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = icon, style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = if (isActive) MaterialTheme.colorScheme.primary else TextSecondary
            )
        }
    }
}

@Composable
private fun ThinkingIndicator(status: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Surface
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "🤖", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.width(8.dp))
                LoadingDots()
            }
        }
    }
}

@Composable
private fun LoadingDots() {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(3) { index ->
            Text(
                text = ".",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun HowToSpendInput(
    text: String,
    onTextChange: (String) -> Unit,
    onSendClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        text = "说说你的消费困惑...",
                        color = TextSecondary
                    )
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Surface,
                    unfocusedContainerColor = Surface,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSendClick() }),
                singleLine = false,
                maxLines = 3
            )

            Spacer(modifier = Modifier.width(8.dp))

            FilledIconButton(
                onClick = onSendClick,
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "发送",
                    tint = Color.White
                )
            }
        }
    }
}
