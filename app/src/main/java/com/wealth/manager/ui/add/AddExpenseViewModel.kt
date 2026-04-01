package com.wealth.manager.ui.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wealth.manager.data.dao.CategoryDao
import com.wealth.manager.data.dao.ExpenseDao
import com.wealth.manager.data.entity.CategoryEntity
import com.wealth.manager.data.entity.ExpenseEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class AddExpenseViewModel @Inject constructor(
    private val expenseDao: ExpenseDao,
    private val categoryDao: CategoryDao
) : ViewModel() {

    private val _editingExpense = MutableStateFlow<ExpenseEntity?>(null)
    val editingExpense: StateFlow<ExpenseEntity?> = _editingExpense.asStateFlow()

    val categories: StateFlow<List<CategoryEntity>> = categoryDao.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        ensureDefaultCategories()
    }

    private fun ensureDefaultCategories() {
        viewModelScope.launch {
            val count = categoryDao.getCategoryCount()
            if (count == 0) {
                val defaultCategories = listOf(
                    CategoryEntity(name = "餐饮", icon = "🍗", color = "#ffc880"),
                    CategoryEntity(name = "购物", icon = "🛍️", color = "#4A90D9"),
                    CategoryEntity(name = "交通", icon = "🚌", color = "#E55B5B"),
                    CategoryEntity(name = "娱乐", icon = "🎮", color = "#9C27B0"),
                    CategoryEntity(name = "居住", icon = "🏠", color = "#4CAF50"),
                    CategoryEntity(name = "医疗", icon = "🏥", color = "#F44336"),
                    CategoryEntity(name = "学习", icon = "📚", color = "#2196F3"),
                    CategoryEntity(name = "其他", icon = "📋", color = "#9E9E9E")
                )
                categoryDao.insertCategories(defaultCategories)
            }
        }
    }

    fun loadExpense(id: Long) {
        viewModelScope.launch {
            _editingExpense.value = expenseDao.getExpenseById(id)
        }
    }

    fun addExpense(amount: Double, categoryId: Long, note: String = "", dateMillis: Long = getTodayStartMillis(), onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            val expense = ExpenseEntity(
                amount = amount,
                categoryId = categoryId,
                date = dateMillis,
                note = note
            )
            expenseDao.insertExpense(expense)
            onComplete()
        }
    }

    fun updateExpense(id: Long, amount: Double, categoryId: Long, note: String, dateMillis: Long, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            val existing = expenseDao.getExpenseById(id) ?: return@launch
            val updated = existing.copy(
                amount = amount,
                categoryId = categoryId,
                note = note,
                date = dateMillis
            )
            expenseDao.updateExpense(updated)
            onComplete()
        }
    }

    fun deleteExpense(id: Long, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
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
