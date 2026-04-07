package com.wealth.manager.ui.how

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Request
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wealth.manager.agent.AppInitializer
import com.wealth.manager.agent.WangcaiAgent
import com.wealth.manager.data.ConversationStorage
import com.wealth.manager.data.MemoryRefiner
import com.wealth.manager.data.MemoryRetriever
import com.wealth.manager.data.dao.AssetDao
import com.wealth.manager.data.dao.ExpenseDao
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

/**
 * 怎么花 - 花好帮手 ViewModel
 * 支持 Session 持久化，对话历史保存到本地 JSON
 */
data class HowToSpendState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val inputText: String = "",
    val currentThinking: String = "",
    val sessionId: String = "",
    val isHistoryMode: Boolean = false
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

@HiltViewModel
class HowToSpendViewModel @Inject constructor(
    private val expenseDao: ExpenseDao,
    private val assetDao: AssetDao,
    private val wangcaiAgent: WangcaiAgent,
    private val appInitializer: AppInitializer,
    private val conversationStorage: ConversationStorage,
    private val memoryRetriever: MemoryRetriever,
    private val memoryRefiner: MemoryRefiner,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(HowToSpendState())
    val state: StateFlow<HowToSpendState> = _state.asStateFlow()

    // 兼容旧代码的文件路径（用于迁移期间读取旧数据）
    private val sessionFile: File by lazy {
        File(context.filesDir, "howtospend_sessions")
    }
    private val currentSessionFile: File by lazy {
        File(context.filesDir, "howtospend_current_session.json")
    }
    private val historyDir: File by lazy {
        File(context.filesDir, "howtospend_history")
    }

    private val profilePrefs: SharedPreferences by lazy {
        context.getSharedPreferences("user_profile_prefs", Context.MODE_PRIVATE)
    }
    
    init {
        // 初始化 FTS 索引（捕获异常防止闪退）
        try {
            memoryRetriever.initializeFtsTable()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        // Activity 级别 ViewModel：只要 ViewModel 存在，内存状态就保持
        // 不需要加载文件，StateFlow 的内存状态就是 Session
        // 只有当 StateFlow 为空时（首次创建），才创建新 sessionId
        if (_state.value.sessionId.isEmpty()) {
            _state.value = _state.value.copy(sessionId = UUID.randomUUID().toString())
        }
        // 启动时触发一次记忆提炼（长期记忆）
        viewModelScope.launch {
            try {
                memoryRefiner.refineMemory()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // 预设引导问题（description 发送后由 AI 追问细化）
    val quickEntries = listOf(
        QuickEntry("🎯", "买东西", "我想买个大件，帮我分析一下值不值"),
        QuickEntry("💰", "做投资", "最近想理理财，帮我分析下目前的财务状况"),
        QuickEntry("🍜", "日常开销", "最近花钱有点多，帮我分析下消费结构")
    )

    fun updateInput(text: String) {
        _state.value = _state.value.copy(inputText = text)
    }

    fun sendMessage(text: String = _state.value.inputText) {
        if (text.isBlank()) return

        val userMessage = ChatMessage(
            isUser = true,
            content = text
        )

        _state.value = _state.value.copy(
            messages = _state.value.messages + userMessage,
            inputText = "",
            isLoading = true,
            currentThinking = "好的！帮你分析一下...",
            isHistoryMode = false
        )

        // 保存到 SQLite（短期记忆）
        viewModelScope.launch {
            val sessionId = _state.value.sessionId.ifEmpty { UUID.randomUUID().toString() }
            _state.value = _state.value.copy(sessionId = sessionId)
            
            // 确保会话存在
            conversationStorage.getSession(sessionId) ?: conversationStorage.createSession(sessionId)
            
            // 保存用户消息到 SQLite + FTS
            val savedMsg = conversationStorage.addMessage(
                sessionId = sessionId,
                content = text,
                isUser = true
            )
            // 索引到 FTS
            memoryRetriever.indexMessage(
                messageId = savedMsg.id,
                sessionId = sessionId,
                content = text,
                isUser = true,
                createdAt = savedMsg.createdAt
            )
        }

        // 保存到当前 Session 文件（用于切换 tab 恢复）
        saveCurrentSession()

        viewModelScope.launch {
            try {
                // 只有全新对话才清空上下文；历史对话继续时保留上下文
                val existingMessages = _state.value.messages.dropLast(1) // 去掉刚加入的用户消息
                if (existingMessages.isEmpty()) {
                    wangcaiAgent.clearContext()
                    appInitializer.refreshAppData()
                }

                // 构建系统上下文（包含对话历史 + FTS5 检索）
                val systemContext = buildSystemContext(existingMessages)

                // 发送消息
                wangcaiAgent.thinkStream(
                    userMessage = text,
                    systemContext = systemContext
                ).collect { delta ->
                    val progressMatch = Regex("\\[PROGRESS: (.*?)\\]").find(delta)
                    if (progressMatch != null) {
                        val status = progressMatch.groupValues[1]
                        _state.value = _state.value.copy(currentThinking = status)
                    } else {
                        val current = _state.value.messages.lastOrNull()?.let { lastMsg ->
                            if (!lastMsg.isUser) lastMsg.content + delta else delta
                        } ?: delta

                        if (_state.value.messages.lastOrNull()?.isUser == true) {
                            // 创建新的 AI 消息
                            val aiMessage = ChatMessage(
                                isUser = false,
                                content = delta
                            )
                            _state.value = _state.value.copy(
                                messages = _state.value.messages + aiMessage,
                                isLoading = false,
                                currentThinking = ""
                            )
                        } else {
                            // 更新现有的 AI 消息
                            _state.value = _state.value.copy(
                                messages = _state.value.messages.dropLast(1) +
                                    ChatMessage(
                                        isUser = false,
                                        content = current
                                    ),
                                isLoading = false,
                                currentThinking = ""
                            )
                        }
                    }
                }

                // AI 回复完成后，保存到 SQLite + FTS
                val aiReplyMsg = _state.value.messages.lastOrNull()
                if (aiReplyMsg != null && !aiReplyMsg.isUser) {
                    val sessionId = _state.value.sessionId
                    conversationStorage.addMessage(
                        sessionId = sessionId,
                        content = aiReplyMsg.content,
                        isUser = false
                    ).also { savedMsg ->
                        memoryRetriever.indexMessage(
                            messageId = savedMsg.id,
                            sessionId = sessionId,
                            content = aiReplyMsg.content,
                            isUser = false,
                            createdAt = savedMsg.createdAt
                        )
                    }
                }

                // 保存到历史
                saveCurrentSession()
                addToHistory()
            } catch (e: Exception) {
                // 报告错误到服务器（异步，不阻塞 UI）
                viewModelScope.launch { reportError("howtospend_stream", e.message ?: "") }
                // 友好提示
                val friendlyMsg = when {
                    e.message?.contains("timeout", true) == true ->
                        "网络超时了，请检查网络后重试"
                    e.message?.contains("network", true) == true || e.message?.contains("Unable to resolve host", true) == true ->
                        "网络连接不稳定，请切换 WiFi 或流量后重试"
                    e.message?.contains("stream", true) == true ->
                        "AI 服务响应超时，请稍后重试"
                    else ->
                        "分析遇到问题，请稍后重试"
                }
                val errorMessage = ChatMessage(
                    isUser = false,
                    content = "😔 $friendlyMsg（错误已上报，我们会尽快修复）"
                )
                _state.value = _state.value.copy(
                    messages = _state.value.messages + errorMessage,
                    isLoading = false,
                    currentThinking = ""
                )
            }
        }
    }

    fun sendQuickEntry(entry: QuickEntry) {
        sendMessage(entry.description)
    }

    fun toggleLike(messageId: String) {
        _state.value = _state.value.copy(
            messages = _state.value.messages.map { msg ->
                if (msg.id == messageId) msg.copy(isLiked = !msg.isLiked) else msg
            }
        )
    }

    fun setUseful(messageId: String) {
        _state.value = _state.value.copy(
            messages = _state.value.messages.map { msg ->
                if (msg.id == messageId) msg.copy(isUseful = true) else msg
            }
        )
    }

    fun clearMessages() {
        viewModelScope.launch(Dispatchers.IO) {
            // 清除 Session 文件
            currentSessionFile.delete()
        }
        _state.value = _state.value.copy(
            messages = emptyList(),
            sessionId = UUID.randomUUID().toString(),
            isHistoryMode = false
        )
    }

    /**
     * 加载历史会话列表
     */
    suspend fun loadHistoryList(): List<ConversationSession> {
        return withContext(Dispatchers.IO) {
            val sessions = mutableListOf<ConversationSession>()
            if (sessionFile.exists()) {
                try {
                    val json = JSONArray(sessionFile.readText())
                    for (i in 0 until json.length()) {
                        val obj = json.getJSONObject(i)
                        sessions.add(
                            ConversationSession(
                                id = obj.getString("id"),
                                title = obj.getString("title"),
                                timestamp = obj.getLong("timestamp"),
                                messageCount = obj.getInt("messageCount")
                            )
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            sessions.sortedByDescending { it.timestamp }
        }
    }

    /**
     * 加载指定历史会话
     */
    fun loadHistorySession(sessionId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val historyDir = File(context.filesDir, "howtospend_history")
                val sessionFile = File(historyDir, "$sessionId.json")
                if (sessionFile.exists()) {
                    try {
                        val json = JSONObject(sessionFile.readText())
                        val messagesArray = json.getJSONArray("messages")
                        val messages = mutableListOf<ChatMessage>()
                        for (i in 0 until messagesArray.length()) {
                            val msgObj = messagesArray.getJSONObject(i)
                            messages.add(
                                ChatMessage(
                                    id = msgObj.getString("id"),
                                    isUser = msgObj.getBoolean("isUser"),
                                    content = msgObj.getString("content"),
                                    timestamp = msgObj.getLong("timestamp"),
                                    isUseful = msgObj.optBoolean("isUseful", false),
                                    isLiked = msgObj.optBoolean("isLiked", false)
                                )
                            )
                        }
                        _state.value = _state.value.copy(
                            messages = messages,
                            sessionId = sessionId,
                            isHistoryMode = true,
                            isLoading = false,
                            currentThinking = ""
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    /**
     * 删除指定历史会话
     */
    fun deleteHistorySession(sessionId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                // 删除会话详情文件
                val historyDir = File(context.filesDir, "howtospend_history")
                val sessionFile = File(historyDir, "$sessionId.json")
                if (sessionFile.exists()) {
                    sessionFile.delete()
                }

                // 从索引中移除
                val historyList = loadHistoryList().toMutableList()
                historyList.removeAll { it.id == sessionId }

                val jsonArray = JSONArray()
                historyList.take(50).forEach { session ->
                    jsonArray.put(JSONObject().apply {
                        put("id", session.id)
                        put("title", session.title)
                        put("timestamp", session.timestamp)
                        put("messageCount", session.messageCount)
                    })
                }
                this@HowToSpendViewModel.sessionFile.writeText(jsonArray.toString())
            }
        }
    }

    /**
     * 保存当前会话到历史
     */
    fun saveToHistory() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val messages = _state.value.messages
                if (messages.isEmpty()) return@withContext

                val sessionId = _state.value.sessionId.ifEmpty { UUID.randomUUID().toString() }
                val historyDir = File(context.filesDir, "howtospend_history")
                historyDir.mkdirs()

                // 生成标题（取第一条用户消息的前20字符）
                val title = messages.firstOrNull { it.isUser }?.content
                    ?.take(20)
                    ?.replace("\n", " ")
                    ?: "未命名会话"
                val timestamp = System.currentTimeMillis()

                // 保存会话详情
                val sessionDetail = JSONObject().apply {
                    put("id", sessionId)
                    put("title", title)
                    put("timestamp", timestamp)
                    put("messages", JSONArray().apply {
                        messages.forEach { msg ->
                            put(JSONObject().apply {
                                put("id", msg.id)
                                put("isUser", msg.isUser)
                                put("content", msg.content)
                                put("timestamp", msg.timestamp)
                                put("isUseful", msg.isUseful)
                                put("isLiked", msg.isLiked)
                            })
                        }
                    })
                }
                File(historyDir, "$sessionId.json").writeText(sessionDetail.toString())

                // 更新会话列表
                updateHistoryIndex(sessionId, title, timestamp, messages.size)

                // 清空当前会话
                _state.value = _state.value.copy(
                    messages = emptyList(),
                    sessionId = "",
                    isHistoryMode = false
                )
                currentSessionFile.delete()
            }
        }
    }

    private suspend fun updateHistoryIndex(sessionId: String, title: String, timestamp: Long, messageCount: Int) {
        val historyList = loadHistoryList().toMutableList()
        
        // 移除已存在的同名会话
        historyList.removeAll { it.id == sessionId }
        
        // 添加到列表头部
        historyList.add(0, ConversationSession(sessionId, title, timestamp, messageCount))
        
        // 保存索引
        val jsonArray = JSONArray()
        historyList.take(50).forEach { session ->
            jsonArray.put(JSONObject().apply {
                put("id", session.id)
                put("title", session.title)
                put("timestamp", session.timestamp)
                put("messageCount", session.messageCount)
            })
        }
        sessionFile.writeText(jsonArray.toString())
    }

    private fun saveCurrentSession() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val messages = _state.value.messages
                if (messages.isEmpty()) {
                    currentSessionFile.delete()
                    return@withContext
                }

                val sessionId = _state.value.sessionId.ifEmpty { UUID.randomUUID().toString() }
                _state.value = _state.value.copy(sessionId = sessionId)

                val json = JSONObject().apply {
                    put("id", sessionId)
                    put("timestamp", System.currentTimeMillis())
                    put("messages", JSONArray().apply {
                        messages.forEach { msg ->
                            put(JSONObject().apply {
                                put("id", msg.id)
                                put("isUser", msg.isUser)
                                put("content", msg.content)
                                put("timestamp", msg.timestamp)
                                put("isUseful", msg.isUseful)
                                put("isLiked", msg.isLiked)
                            })
                        }
                    })
                }
                currentSessionFile.writeText(json.toString())
            }
        }
    }

    /**
     * 添加当前会话到历史记录
     */
    private fun addToHistory() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val messages = _state.value.messages
                if (messages.isEmpty()) return@withContext

                val sessionId = _state.value.sessionId.ifEmpty { UUID.randomUUID().toString() }
                historyDir.mkdirs()

                // 生成标题（取第一条用户消息的前20字符）
                val title = messages.firstOrNull { it.isUser }?.content
                    ?.take(20)
                    ?.replace("\n", " ")
                    ?: "未命名会话"
                val timestamp = System.currentTimeMillis()

                // 保存会话详情
                val sessionDetail = JSONObject().apply {
                    put("id", sessionId)
                    put("title", title)
                    put("timestamp", timestamp)
                    put("messages", JSONArray().apply {
                        messages.forEach { msg ->
                            put(JSONObject().apply {
                                put("id", msg.id)
                                put("isUser", msg.isUser)
                                put("content", msg.content)
                                put("timestamp", msg.timestamp)
                                put("isUseful", msg.isUseful)
                                put("isLiked", msg.isLiked)
                            })
                        }
                    })
                }
                File(historyDir, "$sessionId.json").writeText(sessionDetail.toString())

                // 更新历史索引
                updateHistoryIndex(sessionId, title, timestamp, messages.size)

                // 触发记忆提炼（长期记忆更新）
                memoryRefiner.refineMemory()
            }
        }
    }

    private fun loadCurrentSession() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                if (currentSessionFile.exists()) {
                    try {
                        val json = JSONObject(currentSessionFile.readText())
                        val messagesArray = json.getJSONArray("messages")
                        val messages = mutableListOf<ChatMessage>()
                        for (i in 0 until messagesArray.length()) {
                            val msgObj = messagesArray.getJSONObject(i)
                            messages.add(
                                ChatMessage(
                                    id = msgObj.getString("id"),
                                    isUser = msgObj.getBoolean("isUser"),
                                    content = msgObj.getString("content"),
                                    timestamp = msgObj.getLong("timestamp"),
                                    isUseful = msgObj.optBoolean("isUseful", false),
                                    isLiked = msgObj.optBoolean("isLiked", false)
                                )
                            )
                        }
                        _state.value = _state.value.copy(
                            messages = messages,
                            sessionId = json.optString("id", "")
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    private suspend fun buildSystemContext(conversationHistory: List<ChatMessage> = emptyList()): String {
        return buildString {
            // 如果有对话历史，加上历史摘要
            if (conversationHistory.isNotEmpty()) {
                appendLine("=== 当前对话历史 ===")
                conversationHistory.forEach { msg ->
                    val role = if (msg.isUser) "用户" else "旺财"
                    appendLine("[$role] ${msg.content.take(200)}")
                }
                appendLine()
                appendLine("请结合以上对话历史，继续回答用户的问题。")
                appendLine()
            }
            
            // FTS5 检索相关历史消息（短期记忆检索）
            val userQuery = conversationHistory.lastOrNull()?.content ?: ""
            if (userQuery.isNotBlank()) {
                val relevantMessages = memoryRetriever.search(userQuery, topK = 5)
                if (relevantMessages.isNotEmpty()) {
                    appendLine("=== 相关历史对话（参考）===")
                    relevantMessages.forEach { result ->
                        val role = if (result.isUser) "用户" else "旺财"
                        appendLine("[$role] ${result.content.take(150)}")
                    }
                    appendLine()
                }
            }
            
            // 获取用户财务数据
            val allExpenses = expenseDao.getAllExpenses().first()
            val assets = assetDao.getAllAssets().first()

            // 近30天消费（用于计算近期月均）
            val now = System.currentTimeMillis()
            val thirtyDaysAgo = now - 30L * 24 * 60 * 60 * 1000
            val recentExpenses = allExpenses.filter { it.date >= thirtyDaysAgo }
            // 近30天消费合计（不是月均，是30天累加）
            val recent30DayTotal = if (recentExpenses.isNotEmpty()) {
                recentExpenses.sumOf { it.amount }
            } else 0.0

            // 日期范围格式化（用于避免"上个月"等模糊表述）
            val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            val startDateStr = dateFormat.format(java.util.Date(thirtyDaysAgo))
            val endDateStr = dateFormat.format(java.util.Date(now))

            // 全部消费月均（lifetime）
            val lifetimeMonthlyAvg = if (allExpenses.isNotEmpty()) {
                val dates = allExpenses.map { it.date }
                val diffMs = if (dates.maxOrNull() != null && dates.minOrNull() != null) {
                    dates.maxOrNull()!! - dates.minOrNull()!!
                } else 0L
                // 正确计算: 直接除以每天的毫秒数，得到总天数，再除以30得到月数
                val totalDays = diffMs.toDouble() / (24.0 * 60 * 60 * 1000)
                val months = maxOf(1.0, totalDays / 30.0)
                allExpenses.sumOf { it.amount } / months
            } else 0.0

            // 资产状况（排除 isHidden 的资产）
            val visibleAssets = assets.filter { !it.isHidden }
            val totalAssets = visibleAssets.sumOf { it.balance }

            // 资产结构分析（按类型分组，只统计未隐藏的资产）
            val assetsByType = visibleAssets.groupBy { it.type }
            val cashAssets = assetsByType.filterKeys { it.name in listOf("CASH", "BANK", "ALIPAY", "DEPOSIT") }.values.flatten().sumOf { it.balance }
            val investmentAssets = assetsByType.filterKeys { it.name in listOf("INVESTMENT", "ENTERPRISE_ANNUITY") }.values.flatten().sumOf { it.balance }
            val liabilityAssets = assetsByType.filterKeys { it.name in listOf("CREDIT_CARD", "LOAN") }.values.flatten().sumOf { it.balance }
            val netWorth = totalAssets - liabilityAssets

            // 财务目标（从 SharedPreferences 读取）
            val prefs = context.getSharedPreferences("achievements_prefs", Context.MODE_PRIVATE)
            val isAssetVisible = prefs.getBoolean("is_asset_visible", true)
            // 如果用户在成长页隐藏了资产金额，AI 上下文里也隐藏（尊重用户可见性偏好）
            val hideAssetData = !isAssetVisible

            val assetGoal = prefs.getFloat("asset_goal", 0f).toDouble()
            val goalDate = prefs.getLong("goal_date", System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000)
            val daysToGoal = ((goalDate - System.currentTimeMillis()) / (24 * 60 * 60 * 1000)).toInt()
            val goalProgress = if (assetGoal > 0) (netWorth / assetGoal * 100).coerceIn(0.0, 100.0) else 0.0

            // 预算信息
            val monthlyBudget = prefs.getFloat("monthly_budget", 0f).toDouble()
            val weeklyBudget = prefs.getFloat("weekly_budget", 0f).toDouble()

            // 找出有备注的消费（用户主动补充了上下文，从近期数据中找）
            val expensesWithNotes = recentExpenses.filter { it.note.isNotBlank() }
                .sortedByDescending { it.date }
                .take(20) // 最多传20条有备注的记录

            appendLine("=== 用户财务画像 ===")
            appendLine("【近30天】$startDateStr ~ $endDateStr")
            appendLine("消费合计: ¥${String.format("%.0f", recent30DayTotal)}")
            appendLine("【Lifetime】月均消费: ¥${String.format("%.0f", lifetimeMonthlyAvg)}")
            if (hideAssetData) {
                appendLine("总资产: ****（已隐藏）")
                appendLine("净资产: ****（已隐藏）")
                appendLine("  其中 - 现金流/现金类: ****（已隐藏）")
                appendLine("       - 投资类: ****（已隐藏）")
            } else {
                appendLine("总资产: ¥${String.format("%.0f", totalAssets)}")
                appendLine("净资产: ¥${String.format("%.0f", netWorth)}")
                appendLine("  其中 - 现金流/现金类: ¥${String.format("%.0f", cashAssets)}")
                appendLine("       - 投资类: ¥${String.format("%.0f", investmentAssets)}")
            }
            if (assetGoal > 0) {
                if (hideAssetData) {
                    appendLine("资产目标: ****（已隐藏）")
                } else {
                    appendLine("资产目标: ¥${String.format("%.0f", assetGoal)} (距目标还差 ¥${String.format("%.0f", (assetGoal - netWorth).coerceAtLeast(0.0))})")
                    appendLine("目标完成度: ${String.format("%.1f", goalProgress)}% | 剩余 ${daysToGoal} 天")
                }
            }
            if (monthlyBudget > 0) {
                val budgetStatus = if (recent30DayTotal > monthlyBudget) "⚠️ 超预算" else "✅ 在预算内"
                appendLine("月预算: ¥${String.format("%.0f", monthlyBudget)} (本月消费 $budgetStatus)")
            }
            if (weeklyBudget > 0) {
                val budgetStatus = if (recent30DayTotal > weeklyBudget) "⚠️ 超预算" else "✅ 在预算内"
                appendLine("周预算: ¥${String.format("%.0f", weeklyBudget)} (本周消费 $budgetStatus")
            }
            appendLine("近30天记录: ${recentExpenses.size} 笔")
            if (expensesWithNotes.isNotEmpty()) {
                appendLine()
                appendLine("=== 用户备注记录 ===")
                val dateFormat = java.text.SimpleDateFormat("MM-dd", java.util.Locale.getDefault())
                expensesWithNotes.forEach { exp ->
                    val dateStr = dateFormat.format(java.util.Date(exp.date))
                    appendLine("[$dateStr] ¥${String.format("%.0f", exp.amount)} - ${exp.note}")
                }
            }
            appendLine()
            appendLine("=== 分析要求 ===")
            appendLine("请从以下角度分析用户的问题：")
            appendLine("1. 结合用户的财务状况（包括资产结构、现金流、月均消费）")
            appendLine("2. 结合用户的资产目标进度（如果有目标，分析这笔支出对目标的影响）")
            appendLine("3. 结合用户的预算情况（是否超预算）")
            appendLine("4. 判断这笔支出是否值得")
            appendLine("5. 如果值得，给出最优的消费方式（时机、渠道、替代方案）")
            appendLine("6. 如果不值得，说明理由")
            appendLine("7. 请用友好的语气，简洁地给出建议")
            appendLine()
            appendLine("【追问引导】")
            appendLine("回答后，根据用户意图主动追问 1-2 个问题，引导用户思考或补充信息：")
            appendLine("- 如果用户问买什么东西值不值 → 追问\"你的预算是多少？\"或\"打算什么时候买？\"")
            appendLine("- 如果用户问消费分析 → 追问\"想改善哪方面的支出？\"或\"最近有大额支出吗？\"")
            appendLine("- 如果用户只是感慨 → 追问\"有没有想控制的地方？\"或\"下个月打算怎么调整？\"")
            appendLine("- 保持简短，每条追问不超过20字，不要一次追问太多")

            // 注入长期记忆摘要（来自 MemoryRefiner）
            val memorySummary = memoryRefiner.buildMemorySummary()
            if (memorySummary.isNotEmpty()) {
                append(memorySummary)
            }
        }
    }

    /**
     * 上报错误到服务器
     */
    private suspend fun reportError(type: String, message: String) {
        withContext(Dispatchers.IO) {
            try {
                val deviceId = try {
                    Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                } catch (e: Exception) { "unknown" }
                val ctxInfo = buildContextInfo("")
                val formBody = okhttp3.FormBody.Builder()
                    .add("device_id", deviceId)
                    .add("type", type)
                    .add("message", "$message | $ctxInfo")
                    .build()
                val request = Request.Builder()
                    .url("http://101.201.67.78/log/report")
                    .post(formBody)
                    .build()
                OkHttpClient().newCall(request).execute().close()
            } catch (e: Exception) {
                // 上报失败静默忽略
            }
        }
    }

    /**
     * AI 画像分析（增量触发，避免每次都调用）
     * 检查是否需要更新：AI 画像不存在，或超过7天未更新
     */
    private suspend fun buildContextInfo(userInput: String): String {
        return try {
            val expenses = expenseDao.getAllExpenses().first()
            val assets = assetDao.getAllAssets().first()
            val msgs = _state.value.messages
            "user_input=$userInput | msgs_count=${msgs.size} | expenses_count=${expenses.size} | assets_count=${assets.size}"
        } catch (e: Exception) {
            "error building context: ${e.message}"
        }
    }
}

data class QuickEntry(
    val emoji: String,
    val title: String,
    val description: String
)
