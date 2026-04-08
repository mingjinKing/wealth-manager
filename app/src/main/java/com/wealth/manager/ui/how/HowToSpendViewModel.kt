package com.wealth.manager.ui.how

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wealth.manager.agent.AppInitializer
import com.wealth.manager.agent.WangcaiAgent
import com.wealth.manager.data.ConversationStorage
import com.wealth.manager.data.FactExtractor
import com.wealth.manager.data.MemoryRefiner
import com.wealth.manager.data.MemoryRetriever
import com.wealth.manager.data.dao.AssetDao
import com.wealth.manager.data.dao.ExpenseDao
import com.wealth.manager.data.entity.AssetEntity
import com.wealth.manager.data.entity.ExpenseEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.*
import javax.inject.Inject

/**
 * 怎么花 - 花好帮手 ViewModel
 */
data class HowToSpendState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val inputText: String = "",
    val currentThinking: String = "",
    val sessionId: String = "",
    val isHistoryMode: Boolean = false,
    val ftsModeInfo: String = "",
    val cachedAllExpenses: List<ExpenseEntity>? = null,
    val cachedAssets: List<AssetEntity>? = null
)

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val isUser: Boolean = true,
    val content: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isUseful: Boolean = false,
    val isLiked: Boolean = false
)

data class ConversationSession(
    val id: String,
    val title: String,
    val timestamp: Long,
    val messageCount: Int
)

data class QuickEntry(val emoji: String, val title: String, val description: String)

@HiltViewModel
class HowToSpendViewModel @Inject constructor(
    private val expenseDao: ExpenseDao,
    private val assetDao: AssetDao,
    private val wangcaiAgent: WangcaiAgent,
    private val appInitializer: AppInitializer,
    private val conversationStorage: ConversationStorage,
    private val memoryRetriever: MemoryRetriever,
    private val memoryRefiner: MemoryRefiner,
    private val factExtractor: FactExtractor,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(HowToSpendState())
    val state: StateFlow<HowToSpendState> = _state.asStateFlow()

    private val sessionFile: File by lazy { File(context.filesDir, "howtospend_sessions") }
    private val currentSessionFile: File by lazy { File(context.filesDir, "howtospend_current_session.json") }
    private val historyDir: File by lazy { File(context.filesDir, "howtospend_history") }
    
    private val backgroundScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val quickEntries = listOf(
        QuickEntry("📱", "想买新手机", "最近想换个手机，帮我分析一下现在的财务状况适合买吗？"),
        QuickEntry("☕", "日常咖啡支出", "我每天都要喝咖啡，这种支出对我的长期存款目标影响大吗？"),
        QuickEntry("📉", "最近支出太高", "帮我分析一下最近一个月的支出，看看哪里可以优化？"),
        QuickEntry("💰", "存款目标进度", "我想在年底存够5万块，按照现在的花钱速度能实现吗？")
    )

    init {
        viewModelScope.launch {
            try {
                memoryRetriever.initializeFtsTable()
                _state.value = _state.value.copy(ftsModeInfo = memoryRetriever.getDiagnosticInfo())
            } catch (e: Exception) { e.printStackTrace() }
        }
        
        if (_state.value.sessionId.isEmpty()) {
            _state.value = _state.value.copy(sessionId = UUID.randomUUID().toString())
        }
    }

    fun updateInput(text: String) {
        _state.value = _state.value.copy(inputText = text)
    }

    fun sendMessage(text: String = _state.value.inputText) {
        if (text.isBlank()) return

        val userMessage = ChatMessage(isUser = true, content = text)
        val isFirstMessage = _state.value.messages.isEmpty()
        
        _state.value = _state.value.copy(
            messages = _state.value.messages + userMessage,
            inputText = "",
            isLoading = true,
            currentThinking = "正在检索相关记忆...",
            isHistoryMode = false
        )

        val sessionId = _state.value.sessionId.ifEmpty { UUID.randomUUID().toString() }
        
        viewModelScope.launch {
            try {
                // 核心修复：如果是第一条消息，先确保 Session 在数据库中创建，避免外键错误
                if (isFirstMessage) {
                    conversationStorage.createSession(sessionId, text.take(20))
                }
                
                // 保存用户消息
                conversationStorage.addMessage(sessionId, text, true)
            } catch (e: Exception) {
                Log.e("HowToSpendVM", "Failed to init session or save user message", e)
            }

            var hasReceivedContent = false
            var currentAiText = ""
            val aiMessageId = UUID.randomUUID().toString()
            
            try {
                if (isFirstMessage) {
                    wangcaiAgent.clearContext()
                    appInitializer.refreshAppData()
                    withContext(Dispatchers.IO) {
                        _state.value = _state.value.copy(
                            cachedAllExpenses = expenseDao.getAllExpenses().first(),
                            cachedAssets = assetDao.getAllAssets().first()
                        )
                    }
                }

                val systemContext = buildSystemContext(_state.value.messages.dropLast(1))

                wangcaiAgent.thinkStream(userMessage = text, systemContext = systemContext)
                    .onCompletion { cause ->
                        _state.value = _state.value.copy(isLoading = false, currentThinking = "")
                        
                        if (cause != null) {
                             Log.e("HowToSpendVM", "Stream flow error onCompletion", cause)
                        }

                        if (!hasReceivedContent) {
                            val errorContent = if (cause != null) "❌ 哎呀，服务开小差了: ${cause.message}" else "😔 刚才信号走丢了，模型没有返回内容"
                            _state.value = _state.value.copy(
                                messages = _state.value.messages + ChatMessage(isUser = false, content = errorContent)
                            )
                        }
                        
                        saveCurrentSession()
                        addToHistoryAsync()
                    }
                    .collect { delta ->
                        val progressMatch = Regex("\\[PROGRESS: (.*?)\\]").find(delta)
                        if (progressMatch != null) {
                            val progressText = progressMatch.groupValues[1]
                            Log.d("HowToSpendVM", "Progress update: $progressText")
                            _state.value = _state.value.copy(currentThinking = progressText)
                        } else if (delta.isNotEmpty() && !delta.startsWith("\n[生成中断")) {
                            hasReceivedContent = true
                            currentAiText += delta
                            val currentState = _state.value
                            val lastMsg = currentState.messages.lastOrNull()
                            
                            if (lastMsg?.id == aiMessageId) {
                                _state.value = currentState.copy(
                                    messages = currentState.messages.dropLast(1) + lastMsg.copy(content = currentAiText),
                                    currentThinking = ""
                                )
                            } else {
                                _state.value = currentState.copy(
                                    messages = currentState.messages + ChatMessage(id = aiMessageId, isUser = false, content = currentAiText),
                                    isLoading = false,
                                    currentThinking = ""
                                )
                            }
                        }
                    }

                // 保存最后一条 AI 消息
                val aiReplyMsg = _state.value.messages.lastOrNull()
                if (aiReplyMsg != null && !aiReplyMsg.isUser && hasReceivedContent) {
                    try {
                        conversationStorage.addMessage(sessionId, aiReplyMsg.content, false)
                    } catch (e: Exception) { Log.e("HowToSpendVM", "Save AI reply fail", e) }
                }

            } catch (e: Exception) {
                Log.e("HowToSpendVM", "Outer try-catch error", e)
                _state.value = _state.value.copy(
                    isLoading = false, 
                    currentThinking = "",
                    messages = _state.value.messages + ChatMessage(isUser = false, content = "系统异常: ${e.message}")
                )
            }
        }
    }

    private suspend fun buildSystemContext(conversationHistory: List<ChatMessage>): String {
        return buildString {
            val factsSummary = factExtractor.buildFactsSummary()
            if (factsSummary.isNotEmpty()) {
                appendLine("=== 用户核心背景 ===\n$factsSummary\n")
            }

            val userQuery = _state.value.messages.lastOrNull { it.isUser }?.content ?: ""
            if (userQuery.isNotBlank()) {
                val expandedQuery = "$userQuery 存钱 目标 计划 预算"
                val relevantMessages = memoryRetriever.searchHybrid(expandedQuery, topK = 3)
                if (relevantMessages.isNotEmpty()) {
                    appendLine("=== 相关历史对话 ===\n" + relevantMessages.joinToString("\n") { " [${if(it.isUser) "用户" else "旺财"}] ${it.content.take(100)}" } + "\n")
                }
            }
            
            val allExpenses = _state.value.cachedAllExpenses ?: expenseDao.getAllExpenses().first()
            val assets = _state.value.cachedAssets ?: assetDao.getAllAssets().first()
            val thirtyDaysAgo = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
            val recent30DayTotal = allExpenses.filter { it.date >= thirtyDaysAgo }.sumOf { it.amount }

            appendLine("=== 基础财务数据 ===")
            appendLine("近30天支出: ¥${String.format("%.0f", recent30DayTotal)}")
            appendLine("总资产/净值: ¥${String.format("%.0f", assets.filter { !it.isHidden }.sumOf { it.balance })}")
        }
    }

    fun sendQuickEntry(entry: QuickEntry) = sendMessage(entry.description)
    fun toggleLike(id: String) { _state.value = _state.value.copy(messages = _state.value.messages.map { if (it.id == id) it.copy(isLiked = !it.isLiked) else it }) }
    fun setUseful(id: String) { _state.value = _state.value.copy(messages = _state.value.messages.map { if (it.id == id) it.copy(isUseful = true) else it }) }
    
    fun clearMessages() {
        _state.value = _state.value.copy(messages = emptyList(), sessionId = UUID.randomUUID().toString(), isHistoryMode = false)
    }

    suspend fun loadHistoryList(): List<ConversationSession> = withContext(Dispatchers.IO) {
        val sessions = mutableListOf<ConversationSession>()
        if (sessionFile.exists()) {
            try {
                val json = JSONArray(sessionFile.readText())
                for (i in 0 until json.length()) {
                    val obj = json.getJSONObject(i)
                    sessions.add(ConversationSession(obj.getString("id"), obj.getString("title"), obj.getLong("timestamp"), obj.getInt("messageCount")))
                }
            } catch (e: Exception) {}
        }
        sessions.sortedByDescending { it.timestamp }
    }

    fun loadHistorySession(sessionId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val file = File(historyDir, "$sessionId.json")
                if (file.exists()) {
                    try {
                        val json = JSONObject(file.readText())
                        val msgsArr = json.getJSONArray("messages")
                        val msgs = (0 until msgsArr.length()).map { i ->
                            val o = msgsArr.getJSONObject(i)
                            ChatMessage(o.getString("id"), o.getBoolean("isUser"), o.getString("content"), o.getLong("timestamp"), o.optBoolean("isUseful", false), o.optBoolean("isLiked", false))
                        }
                        _state.value = _state.value.copy(messages = msgs, sessionId = sessionId, isHistoryMode = true)
                    } catch (e: Exception) {}
                }
            }
        }
    }

    fun deleteHistorySession(id: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                File(historyDir, "$id.json").delete()
                val list = loadHistoryList().filter { it.id != id }
                saveSessionList(list)
            }
        }
    }

    private fun saveCurrentSession() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val msgs = _state.value.messages
                if (msgs.isEmpty()) { currentSessionFile.delete(); return@withContext }
                val json = JSONObject().apply {
                    put("id", _state.value.sessionId)
                    put("messages", JSONArray().apply { msgs.forEach { m -> put(JSONObject().apply { put("id", m.id); put("isUser", m.isUser); put("content", m.content); put("timestamp", m.timestamp) }) } })
                }
                currentSessionFile.writeText(json.toString())
            }
        }
    }

    private fun addToHistoryAsync() {
        backgroundScope.launch {
            val sessionId = _state.value.sessionId
            val msgs = _state.value.messages
            if (msgs.isEmpty()) return@launch
            
            try {
                if (!historyDir.exists()) historyDir.mkdirs()
                val title = msgs.firstOrNull { it.isUser }?.content?.take(20) ?: "未命名"
                val ts = System.currentTimeMillis()
                val detail = JSONObject().apply {
                    put("id", sessionId); put("title", title); put("timestamp", ts)
                    put("messages", JSONArray().apply { msgs.forEach { m -> put(JSONObject().apply { put("id", m.id); put("isUser", m.isUser); put("content", m.content); put("timestamp", m.timestamp) }) } })
                }
                File(historyDir, "$sessionId.json").writeText(detail.toString())
                updateHistoryIndex(sessionId, title, ts, msgs.size)
                
                // 提炼
                memoryRefiner.refineMemory() 
                factExtractor.extractFromConversation(sessionId)
            } catch (e: Exception) {
                Log.e("HowToSpendVM", "Memory Refinement failed", e)
            }
        }
    }

    private suspend fun updateHistoryIndex(id: String, title: String, ts: Long, count: Int) {
        val list = loadHistoryList().toMutableList()
        list.removeAll { it.id == id }
        list.add(0, ConversationSession(id, title, ts, count))
        saveSessionList(list)
    }

    private fun saveSessionList(list: List<ConversationSession>) {
        val arr = JSONArray()
        list.take(50).forEach { s -> arr.put(JSONObject().apply { put("id", s.id); put("title", s.title); put("timestamp", s.timestamp); put("messageCount", s.messageCount) }) }
        sessionFile.writeText(arr.toString())
    }
}
