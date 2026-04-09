package com.wealth.manager.ui.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wealth.manager.data.dao.AssetDao
import com.wealth.manager.data.dao.CategoryDao
import com.wealth.manager.data.dao.ExpenseDao
import com.wealth.manager.data.entity.CategoryEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RefundItem(
    val assetName: String,
    val amount: Double
)

data class CategoryManageState(
    val expenseCategories: List<CategoryEntity> = emptyList(),
    val incomeCategories: List<CategoryEntity> = emptyList(),
    val error: String? = null,
    val deleteRefundItems: List<RefundItem>? = null // 待确认的退款项
)

@HiltViewModel
class CategoryManageViewModel @Inject constructor(
    private val categoryDao: CategoryDao,
    private val expenseDao: ExpenseDao,
    private val assetDao: AssetDao
) : ViewModel() {

    private val _state = MutableStateFlow(CategoryManageState())
    val state: StateFlow<CategoryManageState> = _state.asStateFlow()

    init {
        loadCategories()
    }

    private fun loadCategories() {
        viewModelScope.launch {
            categoryDao.getAllCategories().collect { all ->
                _state.value = _state.value.copy(
                    expenseCategories = all.filter { it.type == "EXPENSE" },
                    incomeCategories = all.filter { it.type == "INCOME" }
                )
            }
        }
    }

    fun addCategory(name: String, icon: String, type: String) {
        viewModelScope.launch {
            val color = "#${String.format("%06X", (0xFFFFFF and (name.hashCode() or 0x800000)))}"
            val category = CategoryEntity(
                name = name,
                icon = icon,
                color = color,
                type = type,
                isDefault = false
            )
            categoryDao.insertCategory(category)
        }
    }

    fun updateCategory(category: CategoryEntity, newName: String, newIcon: String, newType: String) {
        viewModelScope.launch {
            categoryDao.updateCategory(
                category.copy(name = newName, icon = newIcon, type = newType)
            )
        }
    }

    fun migrateExpenses(fromCategoryId: Long, toCategoryId: Long) {
        viewModelScope.launch {
            val expenses = expenseDao.getExpensesByCategory(fromCategoryId).first()
            expenses.forEach { expense ->
                expenseDao.updateExpense(expense.copy(categoryId = toCategoryId))
            }
            categoryDao.deleteCategoryById(fromCategoryId)
        }
    }

    /**
     * 准备退款预览信息
     */
    fun prepareDeleteRefundInfo(categoryId: Long, shouldRefund: Boolean) {
        if (!shouldRefund) {
            _state.value = _state.value.copy(deleteRefundItems = emptyList())
            return
        }

        viewModelScope.launch {
            val expenses = expenseDao.getExpensesByCategory(categoryId).first()
            val category = categoryDao.getCategoryById(categoryId) ?: return@launch
            val items = mutableListOf<RefundItem>()

            val expensesByAsset = expenses.filter { it.assetId != null }.groupBy { it.assetId }
            for ((assetId, assetExpenses) in expensesByAsset) {
                val asset = assetDao.getAssetById(assetId!!) ?: continue
                val totalAmount = assetExpenses.sumOf { it.amount }
                val adjustment = if (category.type == "EXPENSE") totalAmount else -totalAmount
                items.add(RefundItem(asset.name, adjustment))
            }
            _state.value = _state.value.copy(deleteRefundItems = items)
        }
    }

    /**
     * 执行真正的删除和退款
     */
    fun confirmDeleteAndRefund(categoryId: Long, shouldRefund: Boolean) {
        viewModelScope.launch {
            val expenses = expenseDao.getExpensesByCategory(categoryId).first()
            val category = categoryDao.getCategoryById(categoryId) ?: return@launch

            if (shouldRefund) {
                val expensesByAsset = expenses.filter { it.assetId != null }.groupBy { it.assetId }
                for ((assetId, assetExpenses) in expensesByAsset) {
                    val asset = assetDao.getAssetById(assetId!!) ?: continue
                    val totalAmount = assetExpenses.sumOf { it.amount }
                    val adjustment = if (category.type == "EXPENSE") totalAmount else -totalAmount
                    assetDao.updateAsset(asset.copy(balance = asset.balance + adjustment))
                }
            }

            expenses.forEach { expenseDao.deleteExpense(it) }
            categoryDao.deleteCategoryById(categoryId)
            clearRefundInfo()
        }
    }

    fun deleteCategoryDirectly(id: Long) {
        viewModelScope.launch {
            categoryDao.deleteCategoryById(id)
        }
    }

    suspend fun getExpenseCount(categoryId: Long): Int {
        return expenseDao.getExpensesByCategory(categoryId).first().size
    }

    fun clearRefundInfo() {
        _state.value = _state.value.copy(deleteRefundItems = null)
    }
}
