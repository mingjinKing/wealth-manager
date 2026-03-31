package com.wealth.manager.ui.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wealth.manager.data.dao.CategoryDao
import com.wealth.manager.data.dao.ExpenseDao
import com.wealth.manager.data.entity.CategoryEntity
import com.wealth.manager.data.entity.ExpenseEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class AddExpenseViewModel @Inject constructor(
    private val expenseDao: ExpenseDao,
    private val categoryDao: CategoryDao
) : ViewModel() {

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
                    CategoryEntity(name = "餐饮", icon = "\uD83C\uDF57", color = "#ffc880"),
                    CategoryEntity(name = "购物", icon = "\uD83D\uDECD", color = "#4A90D9"),
                    CategoryEntity(name = "交通", icon = "\uD83D\uDE8C", color = "#E55B5B"),
                    CategoryEntity(name = "娱乐", icon = "\uD83C\uDFAE", color = "#9C27B0"),
                    CategoryEntity(name = "居住", icon = "\uD83C\uDFE0", color = "#4CAF50"),
                    CategoryEntity(name = "医疗", icon = "\uD83C\uDFE5", color = "#F44336"),
                    CategoryEntity(name = "学习", icon = "\uD83D\uDCDA", color = "#2196F3"),
                    CategoryEntity(name = "其他", icon = "\uD83D\uDCCB", color = "#9E9E9E")
                )
                categoryDao.insertCategories(defaultCategories)
            }
        }
    }

    fun addExpense(amount: Double, categoryId: Long, note: String = "") {
        viewModelScope.launch {
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val today = calendar.timeInMillis

            val expense = ExpenseEntity(
                amount = amount,
                categoryId = categoryId,
                date = today,
                note = note
            )
            expenseDao.insertExpense(expense)
        }
    }
}
