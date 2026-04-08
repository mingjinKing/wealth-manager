package com.wealth.manager.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wealth.manager.data.MemoryRefiner
import com.wealth.manager.data.dao.MemoryDao
import com.wealth.manager.data.dao.ExtractedFactDao
import com.wealth.manager.data.dao.MessageDao
import com.wealth.manager.data.dao.SessionDao
import com.wealth.manager.data.entity.MemoryEntity
import com.wealth.manager.data.entity.MessageEntity
import com.wealth.manager.data.entity.SessionEntity
import com.wealth.manager.data.FactExtractor
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.UUID
import javax.inject.Inject

/**
 * 记忆项 UI 模型
 */
data class MemoryItem(
    val id: String,
    val key: String,
    val summary: String,
    val source: String,
    val confidence: Float,
    val updatedAt: Long,
    val isLongTerm: Boolean = false
)

/**
 * 记忆管理页面状态
 */
data class MemoryManagementState(
    val memories: List<MemoryItem> = emptyList(),
    val isLoading: Boolean = false,
    val diagInfo: String = ""
)

/**
 * 记忆管理 ViewModel - 深度排查版
 */
@HiltViewModel
class MemoryManagementViewModel @Inject constructor(
    private val memoryDao: MemoryDao,
    private val extractedFactDao: ExtractedFactDao,
    private val messageDao: MessageDao,
    private val sessionDao: SessionDao,
    private val memoryRefiner: MemoryRefiner,
    private val factExtractor: FactExtractor,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(MemoryManagementState())
    val state: StateFlow<MemoryManagementState> = _state.asStateFlow()

    init {
        observeDatabase()
        runDiagnostics()
        // 关键：初始化时强制 Dump 一次数据到日志
        debugDumpDatabase()
    }

    private fun debugDumpDatabase() {
        viewModelScope.launch(Dispatchers.IO) {
            val allMsgs = messageDao.getRecentMessages(20)
            android.util.Log.e("DATA_DUMP", "======= 数据库原始消息开始 (Total: ${allMsgs.size}) =======")
            allMsgs.forEach { msg ->
                android.util.Log.e("DATA_DUMP", "[${if(msg.isUser) "用户" else "AI"}] ${msg.content.take(50)}")
            }
            android.util.Log.e("DATA_DUMP", "======= 数据库原始消息结束 =======")
            
            val allFacts = extractedFactDao.getAllFactsOnce()
            android.util.Log.e("DATA_DUMP", "======= 事实表内容 (Total: ${allFacts.size}) =======")
            allFacts.forEach { fact ->
                android.util.Log.e("DATA_DUMP", "FACT: ${fact.summary}")
            }
            android.util.Log.e("DATA_DUMP", "======= 事实表内容结束 =======")
        }
    }

    private fun observeDatabase() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            combine(
                memoryDao.getAllMemory(),
                extractedFactDao.getAllFacts()
            ) { shortTerm, longTerm ->
                val shortItems = shortTerm.map { entity ->
                    MemoryItem(entity.id, entity.key, entity.summary, entity.source, entity.confidence, entity.updatedAt, false)
                }
                val longItems = longTerm.map { entity ->
                    MemoryItem(entity.id, entity.key, entity.summary, "extracted_fact", 1.0f, entity.updatedAt, true)
                }
                (shortItems + longItems).sortedByDescending { it.updatedAt }
            }.collect { items ->
                _state.update { it.copy(memories = items, isLoading = false) }
            }
        }
    }

    fun runDiagnostics() {
        viewModelScope.launch {
            val msgCount = messageDao.getMessageCount()
            val factCount = extractedFactDao.getFactCount()
            val memCount = memoryDao.getMemoryCount()
            _state.update { it.copy(diagInfo = "诊断: 消息表($msgCount), 事实表($factCount), 洞察表($memCount)") }
        }
    }

    fun syncAndRefine() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            withContext(Dispatchers.IO) {
                try {
                    // 1. 同步 JSON 到 Room
                    syncJsonToDb()
                    
                    // 2. 触发提取
                    val allSessions = sessionDao.getAllSessionsOnce()
                    allSessions.forEach { session ->
                        factExtractor.extractFromConversation(session.id)
                    }
                    
                    runDiagnostics()
                    debugDumpDatabase()
                } catch (e: Exception) {
                    android.util.Log.e("MemoryVM", "Refine failed", e)
                }
            }
            _state.update { it.copy(isLoading = false) }
        }
    }

    private suspend fun syncJsonToDb() {
        val historyDir = File(context.filesDir, "howtospend_history")
        if (!historyDir.exists()) return
        historyDir.listFiles()?.forEach { file ->
            if (file.extension == "json") {
                val json = JSONObject(file.readText())
                val sessionId = json.getString("id")
                if (sessionDao.getSessionById(sessionId) == null) {
                    sessionDao.insert(SessionEntity(sessionId, json.optString("title", "历史会话"), json.optLong("timestamp"), json.optLong("timestamp")))
                }
                val msgsArr = json.getJSONArray("messages")
                for (i in 0 until msgsArr.length()) {
                    val m = msgsArr.getJSONObject(i)
                    val msgId = m.getString("id")
                    if (messageDao.getMessageCountBySession(sessionId) < msgsArr.length()) {
                        messageDao.insert(MessageEntity(
                            id = msgId,
                            sessionId = sessionId,
                            isUser = m.getBoolean("isUser"),
                            content = m.getString("content"),
                            createdAt = m.getLong("timestamp")
                        ))
                    }
                }
            }
        }
    }

    fun deleteMemory(item: MemoryItem) {
        viewModelScope.launch {
            if (item.isLongTerm) extractedFactDao.deleteFact(item.id)
            else memoryDao.deleteMemory(item.id)
        }
    }

    fun clearAllMemories() {
        viewModelScope.launch {
            memoryDao.clearAllMemory()
            extractedFactDao.clearAll()
        }
    }
}
