package com.wealth.manager.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 关键信息提取实体 - AI 从对话中自动提炼的事实
 *
 * @param id UUID，唯一标识
 * @param key 记忆键，语义化命名，支持前缀匹配
 *   例如: "budget_clothes_monthly", "income_salary", "preference_investment"
 * @param value JSON 结构化值（供程序读取）
 *   例如: {"amount": 1000, "currency": "CNY", "frequency": "monthly"}
 * @param summary 人类可读摘要（向量嵌入对象）
 *   例如: "用户每月买衣服预算1000元"
 * @param source 来源：'auto_extract' / 'user_input' / 'ai_analysis'
 * @param confidence 置信度 0-1，越高越确定
 * @param sourceMessageId 溯源：关联的消息 ID
 * @param expiresAt 过期时间戳（可选，null 表示永不过期）
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 */
@Entity(tableName = "extracted_facts")
data class ExtractedFactEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "fact_key")
    val key: String,

    @ColumnInfo(name = "value")
    val value: String,

    @ColumnInfo(name = "summary")
    val summary: String,

    @ColumnInfo(name = "source")
    val source: String = "auto_extract",

    @ColumnInfo(name = "confidence")
    val confidence: Float,

    @ColumnInfo(name = "source_message_id")
    val sourceMessageId: String? = null,

    @ColumnInfo(name = "expires_at")
    val expiresAt: Long? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)
