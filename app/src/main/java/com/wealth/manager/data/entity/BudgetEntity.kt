package com.wealth.manager.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amount: Double,
    val month: String, // format "yyyy-MM"
    val categoryId: Long? = null // null means global budget
)
