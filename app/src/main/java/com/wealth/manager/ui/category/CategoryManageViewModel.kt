package com.wealth.manager.ui.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wealth.manager.data.dao.CategoryDao
import com.wealth.manager.data.entity.CategoryEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CategoryManageState(
    val expenseCategories: List<CategoryEntity> = emptyList(),
    val incomeCategories: List<CategoryEntity> = emptyList()
)

@HiltViewModel
class CategoryManageViewModel @Inject constructor(
    private val categoryDao: CategoryDao
) : ViewModel() {

    private val _state = MutableStateFlow(CategoryManageState())
    val state: StateFlow<CategoryManageState> = _state.asStateFlow()

    init {
        loadCategories()
    }

    private fun loadCategories() {
        viewModelScope.launch {
            categoryDao.getAllCategories().collect { all ->
                _state.value = CategoryManageState(
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
            categoryDao.insertCategory(
                category.copy(name = newName, icon = newIcon, type = newType)
            )
        }
    }

    fun deleteCategory(id: Long) {
        viewModelScope.launch {
            categoryDao.deleteCategoryById(id)
        }
    }
}
