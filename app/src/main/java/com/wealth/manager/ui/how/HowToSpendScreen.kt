package com.wealth.manager.ui.how

import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wealth.manager.ui.theme.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.foundation.text.BasicText

/**
 * Markdown 渲染文本
 */
@Composable
fun MarkdownText(
    content: String,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    textColor: Color = MaterialTheme.colorScheme.onSurface
) {
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
                line.startsWith("### ") -> withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = baseFontSize * 1.1)) { append(line.removePrefix("### ")) }
                line.startsWith("## ") -> withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = baseFontSize * 1.2)) { append(line.removePrefix("## ")) }
                line.startsWith("# ") -> withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = baseFontSize * 1.3)) { append(line.removePrefix("# ")) }
                line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ") -> {
                    append("  • ")
                    parseInlineStyles(line.trimStart().removePrefix("- ").removePrefix("* "))
                }
                else -> parseInlineStyles(line)
            }
            if (index < lines.size - 1) append('\n')
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
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(i + 2, end)) }
                    i = end + 2
                } else { append(text[i]); i++ }
            }
            text.startsWith("`", i) -> {
                val end = text.indexOf("`", i + 1)
                if (end != -1) {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color.Gray.copy(alpha = 0.2f))) { append(text.substring(i + 1, end)) }
                    i = end + 1
                } else { append(text[i]); i++ }
            }
            else -> { append(text[i]); i++ }
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

    val lastMessageContentLength = state.messages.lastOrNull()?.content?.length ?: 0
    val messagesCount = state.messages.size
    val isThinking = state.isLoading && state.currentThinking.isNotEmpty()

    LaunchedEffect(messagesCount, lastMessageContentLength, isThinking) {
        if (messagesCount > 0) {
            val anchorIndex = messagesCount + (if (isThinking) 1 else 0) 
            if (state.isLoading || lastMessageContentLength > 0) {
                scrollState.scrollToItem(anchorIndex)
            }
        }
    }

    var sessionToDelete by remember { mutableStateOf<ConversationSession?>(null) }
    var historyRefreshTrigger by remember { mutableIntStateOf(0) }

    if (showHistory) {
        HistoryListScreen(
            onBackClick = { showHistory = false },
            onSessionClick = { session -> viewModel.loadHistorySession(session.id); showHistory = false },
            onSessionDelete = { sessionToDelete = it },
            viewModel = viewModel,
            refreshTrigger = historyRefreshTrigger
        )
    }

    sessionToDelete?.let { session ->
        AlertDialog(
            onDismissRequest = { sessionToDelete = null },
            title = { Text("删除会话") },
            text = { Text("确定要删除 \"${session.title}\" 吗？") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteHistorySession(session.id); sessionToDelete = null; historyRefreshTrigger++ }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { sessionToDelete = null }) { Text("取消") } }
        )
    }

    if (!showHistory) {
        Column(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding()
        ) {
            HowToSpendTopBar(onNewConversation = { viewModel.clearMessages() }, onHistoryClick = { showHistory = true })

            Box(modifier = Modifier.weight(1f)) {
                if (state.messages.isEmpty()) {
                    EmptyStateContent(quickEntries = viewModel.quickEntries, onQuickEntryClick = { viewModel.sendQuickEntry(it) }, modifier = Modifier.fillMaxSize())
                } else {
                    LazyColumn(
                        state = scrollState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
                    ) {
                        items(state.messages, key = { it.id }) { message ->
                            if (message.isUser) UserMessageBubble(message.content)
                            else AiMessageBubble(message = message, onLikeClick = { viewModel.toggleLike(message.id) })
                        }

                        if (isThinking) {
                            item(key = "thinking") { ThinkingIndicator(state.currentThinking) }
                        }

                        item(key = "bottom_anchor") {
                            Spacer(modifier = Modifier.height(1.dp).fillMaxWidth())
                        }
                    }
                }
            }

            Surface(modifier = Modifier.fillMaxWidth(), shadowElevation = 8.dp) {
                HowToSpendInput(text = state.inputText, onTextChange = viewModel::updateInput, onSendClick = { focusManager.clearFocus(); viewModel.sendMessage() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryListScreen(onBackClick: () -> Unit, onSessionClick: (ConversationSession) -> Unit, onSessionDelete: (ConversationSession) -> Unit, viewModel: HowToSpendViewModel, refreshTrigger: Int) {
    var sessions by remember { mutableStateOf(emptyList<ConversationSession>()) }
    LaunchedEffect(refreshTrigger) { sessions = viewModel.loadHistoryList() }
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            title = { Text("历史记录", fontWeight = FontWeight.Bold) }, 
            navigationIcon = { 
                IconButton(onClick = onBackClick) { 
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") 
                } 
            }
        )
        if (sessions.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无历史记录", color = TextSecondary)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(sessions) { HistorySessionCard(it, { onSessionClick(it) }, { onSessionDelete(it) }) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistorySessionCard(session: ConversationSession, onClick: () -> Unit, onLongClick: () -> Unit) {
    val df = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)
    Card(modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Surface)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(session.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(4.dp))
            Row {
                Text(df.format(Date(session.timestamp)), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Spacer(modifier = Modifier.width(16.dp))
                Text("${session.messageCount} 条消息", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HowToSpendTopBar(onNewConversation: () -> Unit, onHistoryClick: () -> Unit) {
    TopAppBar(
        title = { Text("💭 怎么花", fontWeight = FontWeight.Bold) }, 
        actions = {
            IconButton(onClick = onNewConversation) { 
                Icon(Icons.Default.Add, "新建", tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)) 
            }
            IconButton(onClick = onHistoryClick) { 
                Icon(Icons.Default.History, "历史", tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)) 
            }
        }, 
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
    )
}

@Composable
private fun EmptyStateContent(quickEntries: List<QuickEntry>, onQuickEntryClick: (QuickEntry) -> Unit, modifier: Modifier) {
    var visible by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        delay(100)
        visible = true
    }
    
    Column(modifier = modifier.padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(32.dp))
        AnimatedVisibility(
            visible = visible,
            enter = scaleIn(tween(300)) + fadeIn()
        ) {
            Text("💭", style = MaterialTheme.typography.displayMedium)
        }
        Spacer(Modifier.height(DesignTokens.Spacing.md))
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(400, delayMillis = 100)) + slideInVertically(tween(400, delayMillis = 100)) { it / 4 }
        ) {
            Text("你是想买什么？", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        }
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(400, delayMillis = 200)) + slideInVertically(tween(400, delayMillis = 200)) { it / 4 }
        ) {
            Text("还是日常开销？", color = TextSecondary)
        }
        Spacer(Modifier.height(DesignTokens.Spacing.lg))
        quickEntries.forEachIndexed { index, entry ->
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(300, delayMillis = 300 + index * 50)) + slideInVertically(tween(300, delayMillis = 300 + index * 50)) { it / 3 }
            ) {
                Card(modifier = Modifier.fillMaxWidth().clickable { onQuickEntryClick(entry) }.padding(vertical = 6.dp), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Surface)) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(entry.emoji, style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column { Text(entry.title, fontWeight = FontWeight.Medium); Text(entry.description, style = MaterialTheme.typography.bodySmall, color = TextSecondary) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UserMessageBubble(content: String) {
    val cm = androidx.compose.ui.platform.LocalClipboardManager.current
    val ctx = LocalContext.current
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(shape = RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp), color = MaterialTheme.colorScheme.primary, modifier = Modifier.combinedClickable(onClick = {}, onLongClick = { cm.setText(AnnotatedString(content)); Toast.makeText(ctx, "已复制", Toast.LENGTH_SHORT).show() })) {
            Text(content, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AiMessageBubble(message: ChatMessage, onLikeClick: () -> Unit) {
    val cm = androidx.compose.ui.platform.LocalClipboardManager.current
    val ctx = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
        Surface(shape = RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp), color = Surface) {
            Column(modifier = Modifier.padding(12.dp).combinedClickable(onClick = {}, onLongClick = { cm.setText(AnnotatedString(message.content)); Toast.makeText(ctx, "已复制", Toast.LENGTH_SHORT).show() })) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🤖", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("旺财", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(modifier = Modifier.height(8.dp))
                MarkdownText(content = message.content)
            }
        }
        if (!message.isUser) {
            Row(modifier = Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(modifier = Modifier.clickable(onClick = onLikeClick), shape = RoundedCornerShape(16.dp), color = if (message.isLiked) MaterialTheme.colorScheme.primary.copy(0.1f) else Color.Transparent) {
                    Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (message.isLiked) "❤️" else "👍", style = MaterialTheme.typography.bodySmall)
                        Text("有用", style = MaterialTheme.typography.labelSmall, color = if (message.isLiked) MaterialTheme.colorScheme.primary else TextSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun ThinkingIndicator(status: String) {
    Surface(modifier = Modifier.padding(vertical = 4.dp), shape = RoundedCornerShape(16.dp), color = Surface) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🤖", style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.width(8.dp))
            Text(status, color = TextSecondary)
            Spacer(modifier = Modifier.width(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { repeat(3) { Text(".", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary) } }
        }
    }
}

@Composable
private fun HowToSpendInput(text: String, onTextChange: (String) -> Unit, onSendClick: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("说说你的消费困惑...", color = TextSecondary) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Surface,
                    unfocusedContainerColor = Surface,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSendClick() }),
                maxLines = 3
            )
            Spacer(modifier = Modifier.width(8.dp))
            FilledIconButton(onClick = onSendClick, modifier = Modifier.size(48.dp), shape = CircleShape) {
                Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = "发送", tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}
