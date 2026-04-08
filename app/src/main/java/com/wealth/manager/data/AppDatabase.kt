package com.wealth.manager.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.wealth.manager.data.dao.AssetDao
import com.wealth.manager.data.dao.BudgetDao
import com.wealth.manager.data.dao.CategoryDao
import com.wealth.manager.data.dao.ExpenseDao
import com.wealth.manager.data.dao.MemoryDao
import com.wealth.manager.data.dao.MessageDao
import com.wealth.manager.data.dao.SessionDao
import com.wealth.manager.data.dao.WeekStatsDao
import com.wealth.manager.data.entity.AssetEntity
import com.wealth.manager.data.entity.BudgetEntity
import com.wealth.manager.data.entity.CategoryEntity
import com.wealth.manager.data.entity.ExpenseEntity
import com.wealth.manager.data.entity.ExtractedFactEntity
import com.wealth.manager.data.entity.MemoryEntity
import com.wealth.manager.data.entity.MessageEntity
import com.wealth.manager.data.entity.SessionEntity
import com.wealth.manager.data.entity.WeekStatsEntity

@Database(
    entities = [
        CategoryEntity::class,
        ExpenseEntity::class,
        WeekStatsEntity::class,
        AssetEntity::class,
        BudgetEntity::class,
        SessionEntity::class,
        MessageEntity::class,
        MemoryEntity::class,
        ExtractedFactEntity::class
    ],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun weekStatsDao(): WeekStatsDao
    abstract fun assetDao(): AssetDao
    abstract fun budgetDao(): BudgetDao
    abstract fun sessionDao(): SessionDao
    abstract fun messageDao(): MessageDao
    abstract fun memoryDao(): MemoryDao
    abstract fun extractedFactDao(): com.wealth.manager.data.dao.ExtractedFactDao

    companion object {
        const val DATABASE_NAME = "wealth_manager_db"
    }
}
