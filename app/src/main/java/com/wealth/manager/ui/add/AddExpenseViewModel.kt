package com.wealth.manager.ui.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wealth.manager.data.dao.AssetDao
import com.wealth.manager.data.dao.CategoryDao
import com.wealth.manager.data.dao.ExpenseDao
import com.wealth.manager.data.entity.AssetEntity
import com.wealth.manager.data.entity.CategoryEntity
import com.wealth.manager.data.entity.ExpenseEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class AddExpenseViewModel @Inject constructor(
    private val expenseDao: ExpenseDao,
    private val categoryDao: CategoryDao,
    private val assetDao: AssetDao
) : ViewModel() {

    private val _editingExpense = MutableStateFlow<ExpenseEntity?>(null)
    val editingExpense: StateFlow<ExpenseEntity?> = _editingExpense.asStateFlow()

    private val _selectedTab = MutableStateFlow(0) // 0 for Expense, 1 for Income
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    val categories: StateFlow<List<CategoryEntity>> = categoryDao.getAllCategories()
        .combine(_selectedTab) { allCats, tabIndex ->
            val type = if (tabIndex == 0) "EXPENSE" else "INCOME"
            allCats.filter { it.type == type }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // 资产列表
    val assets: StateFlow<List<AssetEntity>> = assetDao.getAllAssets()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        ensureDefaultCategories()
    }

    private fun ensureDefaultCategories() {
        viewModelScope.launch {
            val allCategories = categoryDao.getAllCategories().first()
            val existingTypes = allCategories.map { it.type }.toSet()

            val toInsert = mutableListOf<CategoryEntity>()

            // 检查支出分类
            if (!existingTypes.contains("EXPENSE")) {
                toInsert.addAll(listOf(
                    CategoryEntity(name = "餐饮", icon = "🍗", color = "#ffc880", type = "EXPENSE"),
                    CategoryEntity(name = "购物", icon = "🛍️", color = "#4A90D9", type = "EXPENSE"),
                    CategoryEntity(name = "交通", icon = "🚌", color = "#E55B5B", type = "EXPENSE"),
                    CategoryEntity(name = "娱乐", icon = "🎮", color = "#9C27B0", type = "EXPENSE"),
                    CategoryEntity(name = "居住", icon = "🏠", color = "#4CAF50", type = "EXPENSE"),
                    CategoryEntity(name = "医疗", icon = "🏥", color = "#F44336", type = "EXPENSE"),
                    CategoryEntity(name = "学习", icon = "📚", color = "#2196F3", type = "EXPENSE"),
                    CategoryEntity(name = "其他", icon = "📋", color = "#9E9E9E", type = "EXPENSE")
                ))
            }

            // 检查收入分类
            if (!existingTypes.contains("INCOME")) {
                toInsert.addAll(listOf(
                    CategoryEntity(name = "工资", icon = "💰", color = "#4CAF50", type = "INCOME"),
                    CategoryEntity(name = "奖金", icon = "🧧", color = "#F44336", type = "INCOME"),
                    CategoryEntity(name = "兼职", icon = "🚲", color = "#FF9800", type = "INCOME"),
                    CategoryEntity(name = "理财", icon = "📈", color = "#2196F3", type = "INCOME"),
                    CategoryEntity(name = "礼金", icon = "🎁", color = "#E91E63", type = "INCOME"),
                    CategoryEntity(name = "报销", icon = "📝", color = "#00BCD4", type = "INCOME"),
                    CategoryEntity(name = "转卖", icon = "♻️", color = "#8BC34A", type = "INCOME"),
                    CategoryEntity(name = "其他收入", icon = "💸", color = "#9E9E9E", type = "INCOME")
                ))
            }

            if (toInsert.isNotEmpty()) {
                categoryDao.insertCategories(toInsert)
            }
        }
    }

    fun setTab(index: Int) {
        _selectedTab.value = index
    }

    fun loadExpense(id: Long) {
        viewModelScope.launch {
            val expense = expenseDao.getExpenseById(id)
            _editingExpense.value = expense
            if (expense != null) {
                val category = categoryDao.getCategoryById(expense.categoryId)
                if (category != null) {
                    _selectedTab.value = if (category.type == "INCOME") 1 else 0
                }
            }
        }
    }

    fun addExpense(amount: Double, categoryId: Long, note: String = "", dateMillis: Long = getTodayStartMillis(), assetId: Long? = null, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            val expense = ExpenseEntity(
                amount = amount,
                categoryId = categoryId,
                date = dateMillis,
                note = note,
                assetId = assetId
            )
            expenseDao.insertExpense(expense)
            // 更新账户余额
            if (assetId != null && amount > 0) {
                val asset = assetDao.getAssetById(assetId)
                asset?.let {
                    assetDao.updateAsset(it.copy(balance = it.balance - amount))
                }
            }
            onComplete()
        }
    }

    fun updateExpense(id: Long, amount: Double, categoryId: Long, note: String, dateMillis: Long, assetId: Long? = null, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            val existing = expenseDao.getExpenseById(id) ?: return@launch
            
            // 如果更换了账户，需要调整余额
            val oldAssetId = existing.assetId
            val oldAmount = existing.amount
            
            // 还原旧账户余额
            if (oldAssetId != null) {
                val oldAsset = assetDao.getAssetById(oldAssetId)
                oldAsset?.let {
                    assetDao.updateAsset(it.copy(balance = it.balance + oldAmount))
                }
            }
            
            // 扣除新账户余额
            if (assetId != null && amount > 0) {
                val newAsset = assetDao.getAssetById(assetId)
                newAsset?.let {
                    assetDao.updateAsset(it.copy(balance = it.balance - amount))
                }
            }
            
            val updated = existing.copy(
                amount = amount,
                categoryId = categoryId,
                note = note,
                date = dateMillis,
                assetId = assetId
            )
            expenseDao.updateExpense(updated)
            onComplete()
        }
    }

    fun deleteExpense(id: Long, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            // 先查询账单信息，用于恢复账户余额
            val expense = expenseDao.getExpenseById(id)
            expense?.let {
                // 如果有关联账户，恢复余额
                if (it.assetId != null && it.amount > 0) {
                    val asset = assetDao.getAssetById(it.assetId!!)
                    asset?.let { a ->
                        assetDao.updateAsset(a.copy(balance = a.balance + it.amount))
                    }
                }
            }
            // 删除账单
            expenseDao.deleteExpenseById(id)
            onComplete()
        }
    }

    fun deleteCategory(id: Long) {
        viewModelScope.launch {
            categoryDao.deleteCategoryById(id)
        }
    }

    private fun getTodayStartMillis(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
