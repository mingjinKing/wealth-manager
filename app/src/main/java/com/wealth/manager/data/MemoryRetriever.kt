package com.wealth.manager.data

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.wealth.manager.data.dao.MemoryDao
import com.wealth.manager.util.LogCollector
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.exp

/**
 * 记忆检索器 - 支持 RRF 混合检索 + 结构化记忆提取
 */
@Singleton
class MemoryRetriever @Inject constructor(
    @ApplicationContext private val context: Context,
    private val embeddingService: EmbeddingService,
    private val memoryDao: MemoryDao
) {

    private val dbHelper = FtsDatabaseHelper(context)
    private val TAG = "MemoryRetriever"

    data class SearchResult(
        val messageId: String,
        val sessionId: String,
        val content: String,
        val isUser: Boolean,
        val createdAt: Long,
        val rank: Float,
        val source: String = "message"
    )

    fun initializeFtsTable() {
        val db = dbHelper.writableDatabase
        ensureTableExists(db)
    }

    /**
     * 清空所有索引和向量（用于系统重置或重建）
     */
    fun clearAllIndex() {
        try {
            val db = dbHelper.writableDatabase
            db.execSQL("DELETE FROM messages_fts")
            db.execSQL("DELETE FROM message_vectors")
            LogCollector.i(TAG, "已成功清空 FTS 索引和向量库")
        } catch (e: Exception) {
            LogCollector.e(TAG, "清空索引失败: ${e.message}")
        }
    }

    fun getDiagnosticInfo(): String {
        val count = getIndexCount()
        return "FTS模式: FTS4 (兼容模式) | 索引数: $count"
    }

    /**
     * 获取结构化记忆的文本摘要，供 AI 上下文注入
     */
    suspend fun getStructuredMemorySummary(): String {
        val memories = memoryDao.getAllMemoryOnce()
        if (memories.isEmpty()) return ""
        return "=== 用户核心画像与事实 ===\n" + 
               memories.joinToString("\n") { "- [${it.key}] ${it.summary}" }
    }

    /**
     * 获取向量总数
     */
    fun getVectorCount(): Int {
        return try {
            val db = dbHelper.readableDatabase
            db.rawQuery("SELECT COUNT(*) FROM message_vectors", null).use {
                if (it.moveToFirst()) it.getInt(0) else 0
            }
        } catch (e: Exception) { 0 }
    }

    /**
     * 获取指定消息/记忆的向量
     */
    fun getVectorForMemory(id: String): FloatArray? {
        return try {
            val db = dbHelper.readableDatabase
            db.rawQuery("SELECT vector FROM message_vectors WHERE message_id = ?", arrayOf(id)).use { cursor ->
                if (cursor.moveToFirst()) {
                    EmbeddingService.bytesToFloatArray(cursor.getBlob(0))
                } else null
            }
        } catch (e: Exception) { null }
    }

    /**
     * 混合检索: RRF 融合 FTS + 向量相似度 + 时间衰减
     */
    suspend fun searchHybrid(queryText: String, topK: Int = 5, sessionId: String? = null): List<SearchResult> {
        if (queryText.isBlank()) return emptyList()
        LogCollector.i(TAG, "开始混合检索: $queryText")

        // 1. FTS 检索
        val ftsResults = search(queryText, topK = topK * 2, sessionId = sessionId)
        
        // 2. 向量检索 (带 3 秒硬超时)
        val vectorResults = try {
            withTimeoutOrNull(3000L) {
                searchVectors(queryText, topK = topK * 2, sessionId = sessionId)
            } ?: emptyList()
        } catch (e: Exception) {
            LogCollector.e(TAG, "向量检索异常: ${e.message}")
            emptyList()
        }

        if (ftsResults.isEmpty() && vectorResults.isEmpty()) return emptyList()

        val k = 60
        val rrfScores = mutableMapOf<String, Double>()
        val allResults = mutableMapOf<String, SearchResult>()
        
        val now = System.currentTimeMillis()
        val dayMillis = 24 * 60 * 60 * 1000.0
        val decayLambda = 0.05 // 时间衰减系数

        // RRF 融合逻辑并加入时间衰减
        fun processResults(results: List<SearchResult>, isVector: Boolean) {
            results.forEachIndexed { index, res ->
                val id = res.messageId
                allResults[id] = res
                
                // 基础 RRF 分数
                val rrfPart = 1.0 / (k + index + 1)
                
                // 计算天数差
                val daysPassed = (now - res.createdAt) / dayMillis
                // 计算衰减权重: 越久远权重越低
                val timeDecay = exp(-decayLambda * daysPassed)
                
                val score = rrfPart * timeDecay
                rrfScores[id] = rrfScores.getOrDefault(id, 0.0) + score
            }
        }

        processResults(ftsResults, false)
        processResults(vectorResults, true)

        val finalResults = rrfScores.entries
            .sortedByDescending { it.value }
            .take(topK)
            .mapNotNull { allResults[it.key] }
        
        LogCollector.i(TAG, "检索完成: 召回 ${finalResults.size} 条最相关且较新的记忆")
        return finalResults
    }

    fun search(query: String, topK: Int = 5, sessionId: String? = null): List<SearchResult> {
        if (query.isBlank()) return emptyList()
        val db = dbHelper.readableDatabase
        val results = mutableListOf<SearchResult>()
        val likePattern = "%${query.trim()}%"
        
        // FTS4 使用 LIKE 效率也很高，且避开了部分真机 MATCH 语法的分词器不兼容问题
        val sql = if (sessionId != null) {
            "SELECT message_id, session_id, content, is_user, created_at FROM messages_fts WHERE content LIKE ? AND session_id = ? ORDER BY created_at DESC LIMIT ?"
        } else {
            "SELECT message_id, session_id, content, is_user, created_at FROM messages_fts WHERE content LIKE ? ORDER BY created_at DESC LIMIT ?"
        }

        try {
            db.rawQuery(sql, if (sessionId != null) arrayOf(likePattern, sessionId, topK.toString()) else arrayOf(likePattern, topK.toString())).use { cursor ->
                while (cursor.moveToNext()) {
                    results.add(SearchResult(
                        messageId = cursor.getString(0),
                        sessionId = cursor.getString(1),
                        content = cursor.getString(2),
                        isUser = cursor.getInt(3) == 1,
                        createdAt = cursor.getLong(4),
                        rank = 0f
                    ))
                }
            }
        } catch (e: Exception) {
            LogCollector.e(TAG, "FTS search 失败: ${e.message}")
        }
        return results
    }

    suspend fun searchVectors(queryText: String, topK: Int = 5, sessionId: String? = null): List<SearchResult> {
        val queryVector = embeddingService.embed(queryText) ?: return emptyList()
        val db = dbHelper.readableDatabase
        val candidates = mutableListOf<Pair<String, Float>>()
        
        try {
            db.rawQuery("SELECT message_id, vector FROM message_vectors", null).use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0)
                    val vector = EmbeddingService.bytesToFloatArray(cursor.getBlob(1))
                    val sim = EmbeddingService.cosineSimilarity(queryVector, vector)
                    if (sim > 0.4) candidates.add(id to sim)
                }
            }
        } catch (e: Exception) {}

        if (candidates.isEmpty()) return emptyList()

        val results = mutableListOf<SearchResult>()
        candidates.sortedByDescending { it.second }.take(topK).forEach { (id, sim) ->
            db.rawQuery("SELECT message_id, session_id, content, is_user, created_at FROM messages_fts WHERE message_id = ?", arrayOf(id)).use { c ->
                if (c.moveToFirst()) {
                    results.add(SearchResult(
                        messageId = c.getString(0),
                        sessionId = c.getString(1),
                        content = c.getString(2),
                        isUser = c.getInt(3) == 1,
                        createdAt = c.getLong(4),
                        rank = sim
                    ))
                }
            }
        }
        return results
    }

    suspend fun indexMessageVector(messageId: String, content: String): Boolean {
        val vector = embeddingService.embed(content) ?: return false
        try {
            val db = dbHelper.writableDatabase
            db.execSQL("INSERT OR REPLACE INTO message_vectors(message_id, vector) VALUES (?, ?)",
                arrayOf(messageId, EmbeddingService.floatArrayToBytes(vector)))
            return true
        } catch (e: Exception) { return false }
    }

    fun indexMessage(messageId: String, sessionId: String, content: String, isUser: Boolean, createdAt: Long) {
        try {
            val db = dbHelper.writableDatabase
            db.execSQL("""
                INSERT OR REPLACE INTO messages_fts(message_id, session_id, content, is_user, created_at)
                VALUES (?, ?, ?, ?, ?)
            """.trimIndent(), arrayOf(messageId, sessionId, content, if (isUser) 1 else 0, createdAt))
        } catch (e: Exception) {}
    }

    fun hasVectorForMessage(messageId: String): Boolean {
        return try {
            val db = dbHelper.readableDatabase
            db.rawQuery("SELECT COUNT(*) FROM message_vectors WHERE message_id = ?", arrayOf(messageId)).use {
                it.moveToFirst() && it.getInt(0) > 0
            }
        } catch (e: Exception) { false }
    }

    private fun getIndexCount(): Int {
        return try {
            val db = dbHelper.readableDatabase
            db.rawQuery("SELECT COUNT(*) FROM messages_fts", null).use {
                if (it.moveToFirst()) it.getInt(0) else 0
            }
        } catch (e: Exception) { 0 }
    }

    private fun ensureTableExists(db: SQLiteDatabase) {
        db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS messages_fts USING fts4(message_id, session_id, content, is_user, created_at)")
        db.execSQL("CREATE TABLE IF NOT EXISTS message_vectors(message_id TEXT PRIMARY KEY, vector BLOB NOT NULL)")
    }

    private class FtsDatabaseHelper(context: Context) : SQLiteOpenHelper(context, "memory_fts.db", null, 2) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS messages_fts USING fts4(message_id, session_id, content, is_user, created_at)")
            db.execSQL("CREATE TABLE IF NOT EXISTS message_vectors(message_id TEXT PRIMARY KEY, vector BLOB NOT NULL)")
        }
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            db.execSQL("DROP TABLE IF EXISTS messages_fts")
            onCreate(db)
        }
    }
}
