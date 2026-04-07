package com.wealth.manager.data

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 记忆检索器 - 基于 SQLite FTS5 的全文搜索
 * 
 * 负责：
 * 1. 管理 FTS5 虚拟表
 * 2. 索引消息
 * 3. 执行全文检索
 */
@Singleton
class MemoryRetriever @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    private val dbHelper = FtsDatabaseHelper(context)
    
    /**
     * 检索结果
     */
    data class SearchResult(
        val messageId: String,
        val sessionId: String,
        val content: String,
        val isUser: Boolean,
        val createdAt: Long,
        val rank: Float  // 搜索相关度排名
    )
    
    /**
     * 初始化 FTS5 表
     */
    fun initializeFtsTable() {
        val db = dbHelper.writableDatabase
        // 使用独立的 FTS5 表（不依赖外部 messages 表），通过 tokenizer='porter' 提供更好的中文搜索支持
        db.execSQL("""
            CREATE VIRTUAL TABLE IF NOT EXISTS messages_fts USING fts5(
                message_id,
                session_id,
                content,
                is_user,
                created_at
            )
        """.trimIndent())
    }
    
    /**
     * 索引单条消息
     */
    fun indexMessage(messageId: String, sessionId: String, content: String, isUser: Boolean, createdAt: Long) {
        val db = dbHelper.writableDatabase
        db.execSQL("""
            INSERT INTO messages_fts(message_id, session_id, content, is_user, created_at)
            VALUES (?, ?, ?, ?, ?)
        """.trimIndent(), arrayOf(messageId, sessionId, content, if (isUser) 1 else 0, createdAt))
    }
    
    /**
     * 全文检索
     * @param query 搜索关键词
     * @param topK 返回数量
     * @param sessionId 可选，限定在某个会话内搜索
     */
    fun search(query: String, topK: Int = 5, sessionId: String? = null): List<SearchResult> {
        if (query.isBlank()) return emptyList()
        
        val db = dbHelper.readableDatabase
        val results = mutableListOf<SearchResult>()
        
        // 使用 FTS5 MATCH 进行搜索
        // 格式化查询词，处理特殊字符
        val ftsQuery = formatFtsQuery(query)
        
        val sql = if (sessionId != null) {
            """
            SELECT message_id, session_id, content, is_user, created_at, rank
            FROM messages_fts
            WHERE messages_fts MATCH ?
            AND session_id = ?
            ORDER BY rank
            LIMIT ?
            """.trimIndent()
        } else {
            """
            SELECT message_id, session_id, content, is_user, created_at, rank
            FROM messages_fts
            WHERE messages_fts MATCH ?
            ORDER BY rank
            LIMIT ?
            """.trimIndent()
        }
        
        val cursor: Cursor = if (sessionId != null) {
            db.rawQuery(sql, arrayOf(ftsQuery, sessionId, topK.toString()))
        } else {
            db.rawQuery(sql, arrayOf(ftsQuery, topK.toString()))
        }
        
        cursor.use {
            while (it.moveToNext()) {
                val msgId = it.getString(0)
                val sessId = it.getString(1)
                val content = it.getString(2)
                val isUser = it.getInt(3) == 1
                val createdAt = it.getLong(4)
                val rank = it.getFloat(5).let { r -> if (r.isNaN() || r.isInfinite()) 0f else -r }  // FTS rank 越小越相关，取负值反转
                
                results.add(SearchResult(
                    messageId = msgId,
                    sessionId = sessId,
                    content = content,
                    isUser = isUser,
                    createdAt = createdAt,
                    rank = rank
                ))
            }
        }
        
        return results
    }
    
    /**
     * 检索用户消息（只返回用户发的消息）
     */
    fun searchUserMessages(query: String, topK: Int = 5): List<SearchResult> {
        return search(query, topK).filter { it.isUser }
    }
    
    /**
     * 格式化 FTS5 查询
     * - 添加通配符支持模糊搜索
     * - 处理多词查询
     */
    private fun formatFtsQuery(query: String): String {
        val words = query.trim()
            .replace("\"", "")
            .split("\\s+".toRegex())
            .filter { it.isNotEmpty() }
        
        if (words.isEmpty()) return ""
        
        // 对每个词添加 * 实现前缀匹配
        val formattedWords = words.map { word ->
            when {
                word.length <= 2 -> "\"$word\""
                else -> "\"$word\"*"
            }
        }
        
        return formattedWords.joinToString(" ")
    }
    
    /**
     * 删除会话的所有索引（级联删除）
     */
    fun deleteSessionIndex(sessionId: String) {
        val db = dbHelper.writableDatabase
        db.execSQL("DELETE FROM messages_fts WHERE session_id = ?", arrayOf(sessionId))
    }
    
    /**
     * 获取索引总数
     */
    fun getIndexCount(): Int {
        val db = dbHelper.readableDatabase
        return db.rawQuery("SELECT COUNT(*) FROM messages_fts", null).use { 
            it.moveToFirst(); it.getInt(0) 
        }
    }
    
    /**
     * 重建索引
     */
    fun rebuildIndex() {
        val db = dbHelper.writableDatabase
        db.execSQL("INSERT INTO messages_fts(messages_fts) VALUES('rebuild')")
    }
    
    /**
     * 关闭数据库
     */
    fun close() {
        dbHelper.close()
    }
    
    /**
     * FTS5 专用数据库助手
     * 单独管理 FTS 表，不影响主数据库
     */
    private class FtsDatabaseHelper(context: Context) : SQLiteOpenHelper(
        context,
        "memory_fts.db",
        null,
        1
    ) {
        override fun onCreate(db: SQLiteDatabase) {
            // FTS5 表会在 initializeFtsTable 中创建
        }
        
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            db.execSQL("DROP TABLE IF EXISTS messages_fts")
            onCreate(db)
        }
    }
}
