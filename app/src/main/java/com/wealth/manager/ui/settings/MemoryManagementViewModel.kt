package com.wealth.manager.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wealth.manager.data.MemoryRebuilder
import com.wealth.manager.data.dao.MemoryDao
import com.wealth.manager.data.dao.ExtractedFactDao
import com.wealth.manager.data.dao.MessageDao
import com.wealth.manager.data.dao.SessionDao
import com.wealth.manager.data.entity.MessageEntity
import com.wealth.manager.data.entity.SessionEntity
import com.wealth.manager.data.FactExtractor
import com.wealth.manager.data.MemoryRefiner
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import javax.inject.Inject

data class MemoryItem(
    val id: String,
    val key: String,
    val summary: String,
    val source: String,
    val confidence: Float,
    val updatedAt: Long,
    val isLongTerm: Boolean = false
)

data class MemoryManagementState(
    val memories: List<MemoryItem> = emptyList(),
    val isLoading: Boolean = false,
    val snackbarMessage: String? = null
)

@HiltViewModel
class MemoryManagementViewModel @Inject constructor(
    private val memoryDao: MemoryDao,
    private val extractedFactDao: ExtractedFactDao,
    private val messageDao: MessageDao,
    private val sessionDao: SessionDao,
    private val factExtractor: FactExtractor,
    private val memoryRefiner: MemoryRefiner,
    private val memoryRebuilder: MemoryRebuilder,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(MemoryManagementState())
    val state: StateFlow<MemoryManagementState> = _state.asStateFlow()

    init {
        observeDatabase()
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

    fun rebuildMemories() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, snackbarMessage = "记忆重建中...") }
            try {
                val result = withContext(Dispatchers.IO) {
                    memoryRebuilder.rebuild()
                }
                val msg = "重建完成：处理${result.sessionsProcessed}个会话，获得${result.memoryCount}条画像 + ${result.factCount}条事实"
                _state.update { it.copy(snackbarMessage = msg) }
            } catch (e: Exception) {
                android.util.Log.e("MemoryVM", "Rebuild failed", e)
                _state.update { it.copy(snackbarMessage = "重建失败: ${e.message}") }
            } finally {
                _state.update { it.copy(isLoading = false) }
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

    fun clearSnackbar() {
        _state.update { it.copy(snackbarMessage = null) }
    }
}
