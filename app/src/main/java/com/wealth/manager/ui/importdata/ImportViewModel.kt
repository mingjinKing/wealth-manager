package com.wealth.manager.ui.importdata

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wealth.manager.data.dao.AssetDao
import com.wealth.manager.data.dao.BudgetDao
import com.wealth.manager.data.dao.CategoryDao
import com.wealth.manager.data.dao.ExpenseDao
import com.wealth.manager.data.dao.WeekStatsDao
import com.wealth.manager.export.DataExporter
import com.wealth.manager.export.DataImporter
import com.wealth.manager.import.QianjiImporter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed class ImportUiState {
    data object Idle : ImportUiState()
    data object Parsing : ImportUiState()
    data object Exporting : ImportUiState()
    data class ExportSuccess(val filePath: String) : ImportUiState()
    data class Preview(
        val totalRows: Int,
        val expenseCount: Int,
        val skippedCount: Int,
        val skippedSample: List<SkippedItem>,
        val newCategories: List<String>,
        val sampleRecords: List<PreviewRecord>
    ) : ImportUiState()
    data class Importing(val count: Int) : ImportUiState()
    data class Success(val importedCount: Int) : ImportUiState()
    data class Error(val message: String) : ImportUiState()
}

data class PreviewRecord(
    val date: String,
    val categoryName: String,
    val categoryIcon: String,
    val amount: String,
    val note: String
)

data class SkippedItem(
    val category: String,
    val reason: String,
    val count: Int
)

@HiltViewModel
class ImportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val categoryDao: CategoryDao,
    private val expenseDao: ExpenseDao,
    private val assetDao: AssetDao,
    private val budgetDao: BudgetDao,
    private val weekStatsDao: WeekStatsDao
) : ViewModel() {

    private val _uiState = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    private var parsedRecords: List<com.wealth.manager.import.QianjiImporter.ImportRecord> = emptyList()
    private var fileName: String = ""

    fun onFileSelected(uri: Uri, name: String) {
        fileName = name
        viewModelScope.launch {
            _uiState.value = ImportUiState.Parsing
            try {
                val importer = QianjiImporter(context, categoryDao, expenseDao)
                val result = withContext(Dispatchers.IO) {
                    importer.parse(uri)
                }

                parsedRecords = result.expenseRecords

                // 采样5条预览
                val sampleRecords = result.expenseRecords.take(5).map { record ->
                    PreviewRecord(
                        date = formatDate(record.date),
                        categoryName = record.categoryName,
                        categoryIcon = record.categoryIcon,
                        amount = "¥ ${"%.2f".format(record.amount)}",
                        note = record.note.ifEmpty { "-" }
                    )
                }

                // 汇总跳过原因
                val skippedByReason = result.skippedRecords
                    .groupBy { it.skipReason ?: "未知" }
                    .map { (reason, items) ->
                        SkippedItem(
                            category = items.firstOrNull()?.categoryName ?: "-",
                            reason = reason,
                            count = items.size
                        )
                    }
                    .take(5)

                _uiState.value = ImportUiState.Preview(
                    totalRows = result.totalRows,
                    expenseCount = result.expenseRecords.size,
                    skippedCount = result.skippedRecords.size,
                    skippedSample = skippedByReason,
                    newCategories = result.newCategoriesWillBeCreated,
                    sampleRecords = sampleRecords
                )
            } catch (e: Exception) {
                _uiState.value = ImportUiState.Error("解析失败: ${e.message}")
            }
        }
    }

    fun confirmImport() {
        if (parsedRecords.isEmpty()) return

        viewModelScope.launch {
            _uiState.value = ImportUiState.Importing(parsedRecords.size)
            try {
                val importer = QianjiImporter(context, categoryDao, expenseDao)
                val count = withContext(Dispatchers.IO) {
                    importer.executeImport(parsedRecords)
                }
                _uiState.value = ImportUiState.Success(count)
            } catch (e: Exception) {
                _uiState.value = ImportUiState.Error("导入失败: ${e.message}")
            }
        }
    }

    fun reset() {
        parsedRecords = emptyList()
        _uiState.value = ImportUiState.Idle
    }

    /**
     * 导出全量数据到 Downloads 文件夹
     */
    fun exportData() {
        viewModelScope.launch {
            _uiState.value = ImportUiState.Exporting
            try {
                val exporter = DataExporter(context, expenseDao, assetDao, categoryDao, budgetDao, weekStatsDao)
                val filePath = exporter.exportToDownloads()
                if (filePath != null) {
                    _uiState.value = ImportUiState.ExportSuccess(filePath)
                } else {
                    _uiState.value = ImportUiState.Error("导出失败，请检查存储权限")
                }
            } catch (e: Exception) {
                _uiState.value = ImportUiState.Error("导出失败: ${e.message}")
            }
        }
    }

    /**
     * 从备份文件导入（覆盖模式）
     */
    fun importFromBackup(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = ImportUiState.Parsing
            try {
                val jsonContent = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }
                if (jsonContent.isNullOrEmpty()) {
                    _uiState.value = ImportUiState.Error("读取文件失败")
                    return@launch
                }

                val importer = DataImporter(context, expenseDao, assetDao, categoryDao, budgetDao, weekStatsDao)
                val count = importer.importAll(jsonContent)
                if (count != null) {
                    _uiState.value = ImportUiState.Success(count)
                } else {
                    _uiState.value = ImportUiState.Error("导入失败，数据格式错误")
                }
            } catch (e: Exception) {
                _uiState.value = ImportUiState.Error("导入失败: ${e.message}")
            }
        }
    }

    private fun formatDate(millis: Long): String {
        if (millis == 0L) return "-"
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = millis
        return "${cal.get(java.util.Calendar.MONTH) + 1}月${cal.get(java.util.Calendar.DAY_OF_MONTH)}日"
    }
}
