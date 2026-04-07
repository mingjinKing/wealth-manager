package com.wealth.manager.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wealth.manager.data.MemoryRefiner
import com.wealth.manager.data.dao.MemoryDao
import com.wealth.manager.data.entity.MemoryEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 记忆管理 ViewModel
 */
data class MemoryItem(
    val id: String,
    val key: String,
    val summary: String,
    val source: String,
    val confidence: Float,
    val updatedAt: Long
)

data class MemoryManagementState(
    val memories: List<MemoryItem> = emptyList(),
    val isLoading: Boolean = false,
    val messageCount: Int = 0,
    val sessionCount: Int = 0
)

@HiltViewModel
class MemoryManagementViewModel @Inject constructor(
    private val memoryDao: MemoryDao,
    private val memoryRefiner: MemoryRefiner
) : ViewModel() {

    private val _state = MutableStateFlow(MemoryManagementState())
    val state: StateFlow<MemoryManagementState> = _state.asStateFlow()

    init {
        loadMemories()
    }

    fun loadMemories() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            try {
                val memories = memoryDao.getAllMemoryOnce().map { entity ->
                    MemoryItem(
                        id = entity.id,
                        key = entity.key,
                        summary = entity.summary,
                        source = entity.source,
                        confidence = entity.confidence,
                        updatedAt = entity.updatedAt
                    )
                }
                _state.value = _state.value.copy(
                    memories = memories,
                    isLoading = false
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }

    fun deleteMemory(memoryId: String) {
        viewModelScope.launch {
            try {
                memoryDao.deleteMemory(memoryId)
                // 重新加载
                loadMemories()
            } catch (e: Exception) {
                // 忽略错误
            }
        }
    }

    fun clearAllMemories() {
        viewModelScope.launch {
            try {
                memoryDao.clearAllMemory()
                _state.value = _state.value.copy(memories = emptyList())
            } catch (e: Exception) {
                // 忽略错误
            }
        }
    }

    fun refineMemory() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            try {
                memoryRefiner.refineMemory(forceRefine = true)
                loadMemories()
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }
}
