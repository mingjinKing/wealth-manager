package com.wealth.manager.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wealth.manager.data.MemoryExtractor
import com.wealth.manager.data.MemoryRebuilder
import com.wealth.manager.data.dao.MemoryDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject

data class MemoryItem(
    val id: String,
    val key: String,
    val displayKey: String, // 中文显示名
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
    private val memoryRebuilder: MemoryRebuilder,
    private val memoryExtractor: MemoryExtractor // 注入以获取字典
) : ViewModel() {

    private val _state = MutableStateFlow(MemoryManagementState())
    val state: StateFlow<MemoryManagementState> = _state.asStateFlow()

    init {
        observeDatabase()
    }

    private fun observeDatabase() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            memoryDao.getAllMemory().collect { memories ->
                val items = memories.map { entity ->
                    val isLongTerm = try {
                        JSONObject(entity.value).optBoolean("is_long_term", false)
                    } catch (e: Exception) {
                        false
                    }
                    
                    MemoryItem(
                        id = entity.id,
                        key = entity.key,
                        displayKey = memoryExtractor.keyDictionary[entity.key] ?: entity.key,
                        summary = entity.summary,
                        source = entity.source,
                        confidence = entity.confidence,
                        updatedAt = entity.updatedAt,
                        isLongTerm = isLongTerm
                    )
                }.sortedByDescending { it.updatedAt }
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
                val msg = "重建完成：扫描${result.sessionsProcessed}个历史会话，沉淀${result.memoryCount}条核心记忆"
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
            memoryDao.deleteMemory(item.id)
        }
    }

    fun clearAllMemories() {
        viewModelScope.launch {
            memoryDao.clearAllMemory()
        }
    }

    fun clearSnackbar() {
        _state.update { it.copy(snackbarMessage = null) }
    }
}
