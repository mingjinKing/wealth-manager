package com.wealth.manager.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 长期记忆实体 - AI 提炼的结构化洞察
 * 
 * @param id UUID
 * @param key 记忆键，如 'spending_habit', 'investment_preference', 'budget_awareness'
 * @param value JSON 格式的结构化值
 * @param summary 人类可读的摘要
 * @param source 来源：'user_input' / 'ai_analysis' / 'auto_extract'
 * @param confidence 置信度 0-1
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 */
@Entity(tableName = "memory")
data class MemoryEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    
    @ColumnInfo(name = "key")
    val key: String,
    
    @ColumnInfo(name = "value")
    val value: String,
    
    @ColumnInfo(name = "summary")
    val summary: String,
    
    @ColumnInfo(name = "source")
    val source: String,
    
    @ColumnInfo(name = "confidence")
    val confidence: Float,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)
