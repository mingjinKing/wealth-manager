package com.wealth.manager.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val icon: String,
    val color: String,
    val type: String = "EXPENSE", // "EXPENSE" or "INCOME"
    val isDefault: Boolean = true
)
