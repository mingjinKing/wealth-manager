package com.wealth.manager.ui.achievements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wealth.manager.data.dao.CategoryDao
import com.wealth.manager.data.dao.ExpenseDao
import com.wealth.manager.data.dao.WeekStatsDao
import com.wealth.manager.data.entity.CategoryEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class AchievementsViewModel @Inject constructor(
    private val expenseDao: ExpenseDao,
    private val weekStatsDao: WeekStatsDao,
    private val categoryDao: CategoryDao
) : ViewModel() {

    private val _state = MutableStateFlow(AchievementsState())
    val state: StateFlow<AchievementsState> = _state.asStateFlow()

    init {
        loadAchievements()
    }

    private fun loadAchievements() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)

            // 1. 获取本周和历史数据
            val (weekStart, weekEnd) = getCurrentWeekRange()
            val recentStats = weekStatsDao.getRecentWeekStats(8).first()
            val categories = categoryDao.getAllCategories().first()
            val allExpenses = expenseDao.getAllExpenses().first()
            
            // 2. 分析核心成就 (Current Wow)
            val currentWow = analyzeCurrentWeekWow(recentStats, categories)
            
            // 3. 构建成就墙 (Achievement Wall)
            val allBadges = buildAchievementWall(recentStats, allExpenses.size)

            _state.value = AchievementsState(
                isLoading = false,
                currentWow = currentWow,
                allBadges = allBadges,
                recentAchievements = allBadges.filter { it.isUnlocked }.take(5).map { 
                    Achievement(it.emoji, it.name, it.description, it.unlockDate ?: "")
                }
            )
        }
    }

    private fun analyzeCurrentWeekWow(
        recentStats: List<com.wealth.manager.data.entity.WeekStatsEntity>,
        categories: List<CategoryEntity>
    ): CurrentWow? {
        val currentWeek = recentStats.firstOrNull() ?: return null
        if (!currentWeek.wowTriggered) return null

        val savedAmount = currentWeek.savedAmount
        val equivalent = when {
            savedAmount >= 500 -> "一台 Nintendo Switch 游戏"
            savedAmount >= 200 -> "一顿双人豪华火锅"
            savedAmount >= 100 -> "三杯精品手冲咖啡"
            savedAmount >= 50 -> "两张电影票"
            else -> "一顿丰盛的早餐"
        }

        val streak = recentStats.takeWhile { it.wowTriggered }.size

        return CurrentWow(
            savedAmount = savedAmount,
            reason = "你已连续 $streak 周达成消费目标，表现非常稳定！",
            equivalent = equivalent,
            badge = if (streak >= 4) "攒钱达人" else "精打细算",
            progressHint = "再坚持 ${4 - (streak % 4)} 周解锁下一级勋章",
            progress = (streak % 4) / 4f
        )
    }

    private fun buildAchievementWall(
        recentStats: List<com.wealth.manager.data.entity.WeekStatsEntity>,
        totalExpensesCount: Int
    ): List<BadgeMetadata> {
        val streak = recentStats.takeWhile { it.wowTriggered }.size
        val totalSaved = recentStats.sumOf { it.savedAmount }
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)

        return listOf(
            BadgeMetadata(
                id = "starter",
                name = "理财启航",
                emoji = "⛵",
                description = "迈出财务管理的第一步",
                criteria = "完成1笔记账",
                isUnlocked = totalExpensesCount >= 1,
                unlockDate = if (totalExpensesCount >= 1) "刚刚" else null,
                progress = if (totalExpensesCount >= 1) 1f else 0f
            ),
            BadgeMetadata(
                id = "streak_1",
                name = "初试身手",
                emoji = "🌱",
                description = "完成首次连续一周消费达标",
                criteria = "连续1周达标",
                isUnlocked = streak >= 1,
                unlockDate = if (streak >= 1) dateFormat.format(Date(recentStats[0].weekStartDate)) else null,
                progress = if (streak >= 1) 1f else 0f
            ),
            BadgeMetadata(
                id = "streak_4",
                name = "攒钱达人",
                emoji = "💰",
                description = "连续四周控制预算，养成良好习惯",
                criteria = "连续4周达标",
                isUnlocked = streak >= 4,
                unlockDate = if (streak >= 4) dateFormat.format(Date(recentStats[3].weekStartDate)) else null,
                progress = (streak.coerceAtMost(4) / 4f)
            ),
            BadgeMetadata(
                id = "save_1000",
                name = "千元里程碑",
                emoji = "🏆",
                description = "累计节省金额超过 1,000 元",
                criteria = "累计节省 ¥1,000",
                isUnlocked = totalSaved >= 1000,
                progress = (totalSaved.toFloat() / 1000f).coerceAtMost(1f)
            ),
            BadgeMetadata(
                id = "frugal_master",
                name = "节俭专家",
                emoji = "👑",
                description = "连续八周消费达标，财务管理大师",
                criteria = "连续8周达标",
                isUnlocked = streak >= 8,
                progress = (streak.coerceAtMost(8) / 8f)
            )
        )
    }

    private fun getCurrentWeekRange(): Pair<Long, Long> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val weekStart = calendar.timeInMillis

        calendar.add(Calendar.DAY_OF_WEEK, 6)
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        val weekEnd = calendar.timeInMillis

        return Pair(weekStart, weekEnd)
    }
}

data class AchievementsState(
    val isLoading: Boolean = true,
    val currentWow: CurrentWow? = null,
    val allBadges: List<BadgeMetadata> = emptyList(),
    val recentAchievements: List<Achievement> = emptyList()
)

data class CurrentWow(
    val savedAmount: Double,
    val reason: String,
    val equivalent: String?,
    val badge: String?,
    val progressHint: String?,
    val progress: Float = 0f
)

data class BadgeMetadata(
    val id: String,
    val name: String,
    val emoji: String,
    val description: String,
    val criteria: String,
    val isUnlocked: Boolean,
    val unlockDate: String? = null,
    val progress: Float = 0f
)

data class Achievement(
    val emoji: String,
    val name: String,
    val description: String,
    val date: String
)
