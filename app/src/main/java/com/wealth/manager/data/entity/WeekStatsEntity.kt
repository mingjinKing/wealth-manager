package com.wealth.manager.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "week_stats")
data class WeekStatsEntity(
    @PrimaryKey
    val weekStartDate: Long,
    val totalAmount: Double,
    val categoryBreakdown: String,
    val wowTriggered: Boolean = false,
    val savedAmount: Double = 0.0
)
