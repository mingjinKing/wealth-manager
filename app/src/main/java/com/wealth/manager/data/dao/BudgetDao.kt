package com.wealth.manager.data.dao

import androidx.room.*
import com.wealth.manager.data.entity.BudgetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets")
    fun getAllBudgets(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE month = :month AND categoryId IS NULL LIMIT 1")
    fun getGlobalBudget(month: String): Flow<BudgetEntity?>

    @Query("SELECT * FROM budgets WHERE month = :month AND categoryId IS NOT NULL")
    fun getCategoryBudgets(month: String): Flow<List<BudgetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudget(budget: BudgetEntity)

    @Update
    suspend fun updateBudget(budget: BudgetEntity)

    @Query("DELETE FROM budgets WHERE id = :id")
    suspend fun deleteBudget(id: Long)

    @Query("DELETE FROM budgets")
    suspend fun deleteAllBudgets()
}
