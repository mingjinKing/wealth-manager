package com.wealth.manager.ui.how

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 用户画像管理器
 * 从「怎么花」历史对话中提取用户画像特征
 */
class UserProfileManager(private val context: Context) {

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("user_profile_prefs", Context.MODE_PRIVATE)
    }

    // 财务相关关键词映射
    private val financialKeywords = mapOf(
        "投资" to listOf("基金", "股票", "理财", "债券", "定投", "ETF", "国债", "REITs", "私募"),
        "房产" to listOf("买房", "房贷", "房子", "房租", "租金", "公积金"),
        "保险" to listOf("保险", "重疾险", "医疗险", "寿险", "意外险", "年金"),
        "大件消费" to listOf("电脑", "手机", "相机", "车", "MacBook", "iPhone", "汽车", "装修"),
        "日常消费" to listOf("餐饮", "外卖", "房租", "交通", "地铁", "打车", "买菜", "超市"),
        "教育" to listOf("学费", "培训", "课程", "学习", "书", "考证"),
        "旅行" to listOf("旅行", "旅游", "机票", "酒店", "出行", "度假"),
        "信用卡" to listOf("信用卡", "花呗", "白条", "分期", "最低还款"),
        "存款" to listOf("存款", "储蓄", "定期", "活期", "大额存单"),
        "工资" to listOf("工资", "收入", "奖金", "年终奖", "兼职", "副业")
    )

    /**
     * 基础画像数据（关键词统计）
     */
    data class UserProfile(
        val categoryInterests: Map<String, Int>,
        val financialTopicInterests: Map<String, Int>,
        val totalConversations: Int,
        val totalMessages: Int,
        val lastUpdatedAt: Long,
        val profileVersion: Int
    )

    /**
     * AI 分析画像（结构化洞察）
     */
    data class AIProfile(
        val spendingHabit: String,
        val financialMaturity: String,
        val decisionStyle: String,
        val investmentAppetite: String,
        val planningAwareness: String,
        val topInterests: List<String>,
        val summary: String
    )

    /**
     * 会话分析状态
     */
    data class SessionAnalysisState(
        val sessionId: String,
        val lastAnalyzedAt: Long,
        val profileVersion: Int
    )

    /**
     * 获取当前画像
     */
    fun getProfile(): UserProfile {
        val json = prefs.getString("user_profile", null) ?: return emptyProfile()
        return try {
            val obj = JSONObject(json)
            val categoryInterests = mutableMapOf<String, Int>()
            val categoryObj = obj.optJSONObject("categoryInterests")
            categoryObj?.let {
                it.keys().forEach { key -> categoryInterests[key] = it.getInt(key) }
            }
            val financialTopicInterests = mutableMapOf<String, Int>()
            val financialObj = obj.optJSONObject("financialTopicInterests")
            financialObj?.let {
                it.keys().forEach { key -> financialTopicInterests[key] = it.getInt(key) }
            }
            UserProfile(
                categoryInterests = categoryInterests,
                financialTopicInterests = financialTopicInterests,
                totalConversations = obj.getInt("totalConversations"),
                totalMessages = obj.getInt("totalMessages"),
                lastUpdatedAt = obj.getLong("lastUpdatedAt"),
                profileVersion = obj.getInt("profileVersion")
            )
        } catch (e: Exception) {
            emptyProfile()
        }
    }

    private fun emptyProfile() = UserProfile(
        categoryInterests = emptyMap(),
        financialTopicInterests = emptyMap(),
        totalConversations = 0,
        totalMessages = 0,
        lastUpdatedAt = 0L,
        profileVersion = 0
    )

    /**
     * 获取会话分析状态
     */
    fun getSessionAnalysisStates(): Map<String, SessionAnalysisState> {
        val json = prefs.getString("session_analysis_states", null) ?: return emptyMap()
        return try {
            val obj = JSONObject(json)
            val result = mutableMapOf<String, SessionAnalysisState>()
            obj.keys().forEach { sessionId ->
                val stateObj = obj.getJSONObject(sessionId)
                result[sessionId] = SessionAnalysisState(
                    sessionId = sessionId,
                    lastAnalyzedAt = stateObj.getLong("lastAnalyzedAt"),
                    profileVersion = stateObj.getInt("profileVersion")
                )
            }
            result
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /**
     * 增量分析：只分析新增或更新的会话
     */
    fun analyzeIncremental(sessionFile: File, historyDir: File): Boolean {
        if (!sessionFile.exists()) return false

        val states = getSessionAnalysisStates()
        val currentVersion = getProfile().profileVersion

        val sessionsToAnalyze = mutableListOf<Pair<String, Long>>()
        try {
            val json = JSONArray(sessionFile.readText())
            for (i in 0 until json.length()) {
                val obj = json.getJSONObject(i)
                val sessionId = obj.getString("id")
                val timestamp = obj.getLong("timestamp")

                val state = states[sessionId]
                when {
                    state == null -> sessionsToAnalyze.add(sessionId to timestamp)
                    timestamp > state.lastAnalyzedAt -> sessionsToAnalyze.add(sessionId to timestamp)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }

        if (sessionsToAnalyze.isEmpty()) return false

        val currentProfile = getProfile()
        val categoryCounts = currentProfile.categoryInterests.toMutableMap()
        val financialCounts = currentProfile.financialTopicInterests.toMutableMap()
        var totalMessages = currentProfile.totalMessages
        var totalConversations = currentProfile.totalConversations

        for ((sessionId, _) in sessionsToAnalyze) {
            val sessionFile2 = File(historyDir, "$sessionId.json")
            if (!sessionFile2.exists()) continue

            try {
                val json = JSONObject(sessionFile2.readText())
                val messages = json.getJSONArray("messages")
                totalConversations++

                for (i in 0 until messages.length()) {
                    val msg = messages.getJSONObject(i)
                    if (!msg.getBoolean("isUser")) continue
                    totalMessages++

                    val content = msg.getString("content").lowercase()

                    for ((category, keywords) in financialKeywords) {
                        for (keyword in keywords) {
                            if (content.contains(keyword.lowercase())) {
                                categoryCounts[category] = (categoryCounts[category] ?: 0) + 1
                                financialCounts[category] = (financialCounts[category] ?: 0) + 1
                                break
                            }
                        }
                    }
                }

                val newStates = getSessionAnalysisStates().toMutableMap()
                val oldState = newStates[sessionId]
                newStates[sessionId] = SessionAnalysisState(
                    sessionId = sessionId,
                    lastAnalyzedAt = System.currentTimeMillis(),
                    profileVersion = (oldState?.profileVersion ?: currentVersion) + 1
                )
                saveSessionAnalysisStates(newStates)

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        saveProfile(categoryCounts, financialCounts, totalConversations, totalMessages, currentVersion + 1)
        return true
    }

    private fun saveProfile(
        categoryInterests: Map<String, Int>,
        financialTopicInterests: Map<String, Int>,
        totalConversations: Int,
        totalMessages: Int,
        profileVersion: Int
    ) {
        val obj = JSONObject().apply {
            put("categoryInterests", JSONObject().apply {
                categoryInterests.forEach { (k, v) -> put(k, v) }
            })
            put("financialTopicInterests", JSONObject().apply {
                financialTopicInterests.forEach { (k, v) -> put(k, v) }
            })
            put("totalConversations", totalConversations)
            put("totalMessages", totalMessages)
            put("lastUpdatedAt", System.currentTimeMillis())
            put("profileVersion", profileVersion)
        }
        prefs.edit().putString("user_profile", obj.toString()).apply()
    }

    private fun saveSessionAnalysisStates(states: Map<String, SessionAnalysisState>) {
        val obj = JSONObject()
        states.forEach { (sessionId, state) ->
            obj.put(sessionId, JSONObject().apply {
                put("lastAnalyzedAt", state.lastAnalyzedAt)
                put("profileVersion", state.profileVersion)
            })
        }
        prefs.edit().putString("session_analysis_states", obj.toString()).apply()
    }

    /**
     * 构建 AI 分析 Prompt
     */
    fun buildAIAnalysisPrompt(userMessages: List<String>): String {
        val profile = getProfile()
        return buildString {
            appendLine("你是一个用户画像分析专家。请仔细阅读用户的消费咨询记录，提取用户特征。")
            appendLine()
            appendLine("=== 用户消费咨询记录 ===")
            userMessages.take(50).forEachIndexed { index, msg ->
                appendLine("[${index + 1}] $msg")
            }
            appendLine()
            appendLine("=== 基础统计 ===")
            appendLine("总会话数: ${profile.totalConversations}")
            appendLine("总消息数: ${profile.totalMessages}")
            if (profile.categoryInterests.filter { it.value > 0 }.isNotEmpty()) {
                appendLine("活跃话题: ${profile.categoryInterests.filter { it.value > 0 }.entries.sortedByDescending { it.value }.take(5).joinToString("、") { "${it.key}(${it.value}次)" }}")
            }
            appendLine()
            appendLine("请从以下维度分析用户（只输出JSON，不要任何解释）：")
            appendLine("{")
            appendLine("  \"spendingHabit\": \"描述用户的消费习惯，如理性消费型/冲动消费型/计划型\"")
            appendLine("  \"financialMaturity\": \"用户的财务成熟度，如新手/有经验/专家\"")
            appendLine("  \"decisionStyle\": \"用户的决策风格，如谨慎型/果断型/纠结型\"")
            appendLine("  \"investmentAppetite\": \"用户的投资偏好，如保守型/稳健型/激进型\"")
            appendLine("  \"planningAwareness\": \"用户的规划意识，如无意识/有意识/主动规划型\"")
            appendLine("  \"topInterests\": [\"用户最关注的2-3个消费领域\"]")
            appendLine("  \"summary\": \"一段话总结用户画像，不超过60字\"")
            appendLine("}")
        }
    }

    /**
     * 解析 AI 回复
     */
    fun parseAIProfile(aiResponse: String): AIProfile? {
        return try {
            var jsonStr = aiResponse.trim()
            // 去掉可能的前后_markdown格式
            if (jsonStr.startsWith("```")) {
                jsonStr = jsonStr.substringAfter("```").substringAfter("\n").substringBefore("```").trim()
            }
            // 去掉可能的前后空白
            jsonStr = jsonStr.trim()
            // 确保是JSON对象
            if (!jsonStr.startsWith("{")) {
                val start = jsonStr.indexOf("{")
                val end = jsonStr.lastIndexOf("}")
                if (start >= 0 && end > start) {
                    jsonStr = jsonStr.substring(start, end + 1)
                }
            }
            val obj = JSONObject(jsonStr)
            val topInterestsArr = obj.optJSONArray("topInterests")
            val topInterests = mutableListOf<String>()
            if (topInterestsArr != null) {
                for (i in 0 until topInterestsArr.length()) {
                    val item = topInterestsArr.optString(i)
                    if (item.isNotEmpty()) topInterests.add(item)
                }
            }
            AIProfile(
                spendingHabit = obj.optString("spendingHabit", ""),
                financialMaturity = obj.optString("financialMaturity", ""),
                decisionStyle = obj.optString("decisionStyle", ""),
                investmentAppetite = obj.optString("investmentAppetite", ""),
                planningAwareness = obj.optString("planningAwareness", ""),
                topInterests = topInterests,
                summary = obj.optString("summary", "")
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 保存 AI 画像
     */
    fun saveAIProfile(profile: AIProfile) {
        val obj = JSONObject().apply {
            put("spendingHabit", profile.spendingHabit)
            put("financialMaturity", profile.financialMaturity)
            put("decisionStyle", profile.decisionStyle)
            put("investmentAppetite", profile.investmentAppetite)
            put("planningAwareness", profile.planningAwareness)
            put("topInterests", JSONArray(profile.topInterests))
            put("summary", profile.summary)
            put("updatedAt", System.currentTimeMillis())
        }
        prefs.edit().putString("ai_profile", obj.toString()).apply()
    }

    /**
     * 获取 AI 画像
     */
    fun getAIProfile(): AIProfile? {
        val json = prefs.getString("ai_profile", null) ?: return null
        return try {
            val obj = JSONObject(json)
            val topInterestsArr = obj.optJSONArray("topInterests")
            val topInterests = mutableListOf<String>()
            if (topInterestsArr != null) {
                for (i in 0 until topInterestsArr.length()) {
                    val item = topInterestsArr.optString(i)
                    if (item.isNotEmpty()) topInterests.add(item)
                }
            }
            AIProfile(
                spendingHabit = obj.optString("spendingHabit"),
                financialMaturity = obj.optString("financialMaturity"),
                decisionStyle = obj.optString("decisionStyle"),
                investmentAppetite = obj.optString("investmentAppetite"),
                planningAwareness = obj.optString("planningAwareness"),
                topInterests = topInterests,
                summary = obj.optString("summary")
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 提取所有用户消息文本
     */
    fun extractUserMessages(sessionFile: File, historyDir: File): List<String> {
        val messages = mutableListOf<String>()

        // 当前会话
        try {
            if (sessionFile.exists()) {
                val json = JSONObject(sessionFile.readText())
                val msgs = json.optJSONArray("messages") ?: JSONArray()
                for (i in 0 until msgs.length()) {
                    val msg = msgs.getJSONObject(i)
                    if (!msg.getBoolean("isUser")) continue
                    val content = msg.getString("content")
                    if (content.isNotBlank()) messages.add(content)
                }
            }
        } catch (e: Exception) { e.printStackTrace() }

        // 历史会话
        if (historyDir.exists()) {
            historyDir.listFiles()?.filter { it.extension == "json" }?.forEach { file ->
                try {
                    val json = JSONObject(file.readText())
                    val msgs = json.optJSONArray("messages") ?: return@forEach
                    for (i in 0 until msgs.length()) {
                        val msg = msgs.getJSONObject(i)
                        if (!msg.getBoolean("isUser")) continue
                        val content = msg.getString("content")
                        if (content.isNotBlank()) messages.add(content)
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
        return messages
    }

    /**
     * 构建关键词画像摘要（用于注入 AI context）
     */
    fun buildKeywordProfileSummary(): String {
        val profile = getProfile()
        if (profile.totalConversations == 0) return ""

        val topCategories = profile.categoryInterests
            .filter { it.value > 0 }
            .entries
            .sortedByDescending { it.value }
            .take(5)

        if (topCategories.isEmpty()) return ""

        val sb = StringBuilder()
        sb.appendLine()
        sb.appendLine("=== 用户画像（参考）===")
        sb.appendLine("总会话数: ${profile.totalConversations}")
        sb.appendLine("活跃话题: ${topCategories.joinToString("、") { "${it.key}(${it.value}次)" }}")

        val topInterest = topCategories.firstOrNull()?.key ?: ""
        val hint = when (topInterest) {
            "投资" -> "用户对投资理财较为关注"
            "大件消费" -> "用户近期有大件购置计划或兴趣"
            "日常消费" -> "用户关注日常开支控制"
            "房产" -> "用户对房产话题较为关注"
            "教育" -> "用户对自我提升/教育投入较关注"
            "旅行" -> "用户有旅行规划或兴趣"
            else -> null
        }
        if (hint != null) {
            sb.appendLine("用户特征: $hint")
        }

        return sb.toString()
    }

    /**
     * 构建 AI 画像摘要（用于注入 AI context）
     */
    fun buildAIProfileSummary(): String {
        val profile = getAIProfile() ?: return ""
        return buildString {
            appendLine()
            appendLine("=== AI 用户画像分析 ===")
            if (profile.spendingHabit.isNotEmpty()) appendLine("消费习惯: ${profile.spendingHabit}")
            if (profile.financialMaturity.isNotEmpty()) appendLine("财务成熟度: ${profile.financialMaturity}")
            if (profile.decisionStyle.isNotEmpty()) appendLine("决策风格: ${profile.decisionStyle}")
            if (profile.investmentAppetite.isNotEmpty()) appendLine("投资偏好: ${profile.investmentAppetite}")
            if (profile.planningAwareness.isNotEmpty()) appendLine("规划意识: ${profile.planningAwareness}")
            if (profile.topInterests.isNotEmpty()) appendLine("关注领域: ${profile.topInterests.joinToString("、")}")
            if (profile.summary.isNotEmpty()) appendLine("画像总结: ${profile.summary}")
        }
    }

    /**
     * 清除所有画像数据
     */
    fun clearProfile() {
        prefs.edit()
            .remove("user_profile")
            .remove("session_analysis_states")
            .remove("ai_profile")
            .apply()
    }
}
