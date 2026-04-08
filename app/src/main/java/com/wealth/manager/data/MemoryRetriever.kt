package com.wealth.manager.data

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 记忆检索器 - 基于 SQLite FTS5 的全文搜索
 *
 * 负责:
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
        val mode = detectFtsMode()
        
        // 根据检测到的 FTS 模式创建对应的表
        if (mode == FtsMode.FTS5) {
            db.execSQL("""
                CREATE VIRTUAL TABLE IF NOT EXISTS messages_fts USING fts5(
                    message_id,
                    session_id,
                    content,
                    is_user,
                    created_at
                )
            """.trimIndent())
        } else if (mode == FtsMode.FTS4) {
            db.execSQL("""
                CREATE VIRTUAL TABLE IF NOT EXISTS messages_fts USING fts4(
                    message_id,
                    session_id,
                    content,
                    is_user,
                    created_at
                )
            """.trimIndent())
        }
        // LIKE 模式不需要创建特殊的 FTS 表
    }

    // FTS 模块类型
    enum class FtsMode { FTS5, FTS4, LIKE }

    // 当前使用的 FTS 模式
    @Volatile
    private var ftsMode: FtsMode? = null

    /**
     * 检测可用的 FTS 模式(优先级:FTS5 > FTS4 > LIKE)
     */
    fun detectFtsMode(): FtsMode {
        if (ftsMode != null) return ftsMode!!

        val db = dbHelper.writableDatabase

        // 1. 尝试 FTS5
        try {
            db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS _fts5_test USING fts5(content)")
            db.execSQL("DROP TABLE IF EXISTS _fts5_test")
            ftsMode = FtsMode.FTS5
            return FtsMode.FTS5
        } catch (e: Exception) { /* FTS5 不可用 */ }

        // 2. 尝试 FTS4
        try {
            db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS _fts4_test USING fts4(content)")
            db.execSQL("DROP TABLE IF EXISTS _fts4_test")
            ftsMode = FtsMode.FTS4
            return FtsMode.FTS4
        } catch (e: Exception) { /* FTS4 不可用 */ }

        // 3. 降级到 LIKE
        ftsMode = FtsMode.LIKE
        return FtsMode.LIKE
    }

    fun isFtsAvailable(): Boolean = detectFtsMode() != FtsMode.LIKE

    /**
     * 获取当前 FTS 模式(用于调试)
     */
    fun getFtsModeName(): String {
        return when (detectFtsMode()) {
            FtsMode.FTS5 -> "FTS5 ✓"
            FtsMode.FTS4 -> "FTS4 ✓"
            FtsMode.LIKE -> "LIKE (降级模式)"
        }
    }

    /**
     * 获取诊断信息
     */
    fun getDiagnosticInfo(): String {
        val mode = detectFtsMode()
        val indexCount = if (mode == FtsMode.LIKE) {
            try {
                val db = dbHelper.readableDatabase
                ensureMessagesTableExists(db)
                db.rawQuery("SELECT COUNT(*) FROM messages_fallback", null).use { it.moveToFirst(); it.getInt(0) }
            } catch (e: Exception) { -1 }
        } else {
            try {
                getIndexCount()
            } catch (e: Exception) { -1 }
        }
        return "FTS模式: ${getFtsModeName()} | 索引数: $indexCount"
    }

    /**
     * 索引单条消息(根据可用模式自动选择 FTS5/FTS4/LIKE)
     */
    fun indexMessage(messageId: String, sessionId: String, content: String, isUser: Boolean, createdAt: Long) {
        val mode = detectFtsMode()

        if (mode == FtsMode.LIKE) {
            // LIKE 模式:索引到 fallback 表
            indexMessageToFallback(messageId, sessionId, content, isUser, createdAt)
            return
        }

        // FTS5/FTS4 模式
        try {
            val db = dbHelper.writableDatabase
            ensureTableExists(db)
            db.execSQL("""
                INSERT INTO messages_fts(message_id, session_id, content, is_user, created_at)
                VALUES (?, ?, ?, ?, ?)
            """.trimIndent(), arrayOf(messageId, sessionId, content, if (isUser) 1 else 0, createdAt))
        } catch (e: Exception) {
            // FTS 操作失败,尝试降级到 LIKE
            indexMessageToFallback(messageId, sessionId, content, isUser, createdAt)
        }
    }

    private fun ensureTableExists(db: SQLiteDatabase) {
        val mode = detectFtsMode()
        if (mode == FtsMode.LIKE) return  // LIKE 模式不需要特殊表

        val cursor = db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='messages_fts'",
            null
        )
        val exists = cursor.use { it.count > 0 }
        if (!exists) {
            val ftsType = if (mode == FtsMode.FTS5) "fts5" else "fts4"
            db.execSQL("""
                CREATE VIRTUAL TABLE IF NOT EXISTS messages_fts USING $ftsType(
                    message_id,
                    session_id,
                    content,
                    is_user,
                    created_at
                )
            """.trimIndent())
        }
    }

    /**
     * 全文检索
     * @param query 搜索关键词
     * @param topK 返回数量
     * @param sessionId 可选,限定在某个会话内搜索
     */
    fun search(query: String, topK: Int = 5, sessionId: String? = null): List<SearchResult> {
        if (query.isBlank()) return emptyList()

        val mode = detectFtsMode()

        // LIKE 模式:降级搜索
        if (mode == FtsMode.LIKE) {
            return searchWithLike(query, topK, sessionId)
        }

        val db = dbHelper.readableDatabase
        val results = mutableListOf<SearchResult>()

        // FTS5 使用 MATCH 查询，FTS4 使用 LIKE（更稳定，支持中文）
        if (mode == FtsMode.FTS5) {
            // FTS5: 使用 MATCH 前缀搜索
            val ftsQuery = formatFtsQuery(query)
            val sql = if (sessionId != null) {
                """
                SELECT message_id, session_id, content, is_user, created_at
                FROM messages_fts
                WHERE messages_fts MATCH ?
                AND session_id = ?
                LIMIT ?
                """.trimIndent()
            } else {
                """
                SELECT message_id, session_id, content, is_user, created_at
                FROM messages_fts
                WHERE messages_fts MATCH ?
                LIMIT ?
                """.trimIndent()
            }

            try {
                val cursor: Cursor = if (sessionId != null) {
                    db.rawQuery(sql, arrayOf(ftsQuery, sessionId, topK.toString()))
                } else {
                    db.rawQuery(sql, arrayOf(ftsQuery, topK.toString()))
                }

                cursor.use {
                    while (it.moveToNext()) {
                        results.add(SearchResult(
                            messageId = it.getString(0),
                            sessionId = it.getString(1),
                            content = it.getString(2),
                            isUser = it.getInt(3) == 1,
                            createdAt = it.getLong(4),
                            rank = 0f
                        ))
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MemoryRetriever", "FTS5 search failed: query=$ftsQuery, error=${e.message}")
            }
        } else {
            // FTS4: 使用 LIKE 搜索 content 列（绕过 MATCH 兼容性问题）
            val likePattern = "%${query.trim()}%"
            val sql = if (sessionId != null) {
                """
                SELECT message_id, session_id, content, is_user, created_at
                FROM messages_fts
                WHERE content LIKE ? AND session_id = ?
                ORDER BY created_at DESC
                LIMIT ?
                """.trimIndent()
            } else {
                """
                SELECT message_id, session_id, content, is_user, created_at
                FROM messages_fts
                WHERE content LIKE ?
                ORDER BY created_at DESC
                LIMIT ?
                """.trimIndent()
            }

            try {
                val cursor: Cursor = if (sessionId != null) {
                    db.rawQuery(sql, arrayOf(likePattern, sessionId, topK.toString()))
                } else {
                    db.rawQuery(sql, arrayOf(likePattern, topK.toString()))
                }

                cursor.use {
                    while (it.moveToNext()) {
                        results.add(SearchResult(
                            messageId = it.getString(0),
                            sessionId = it.getString(1),
                            content = it.getString(2),
                            isUser = it.getInt(3) == 1,
                            createdAt = it.getLong(4),
                            rank = 0f
                        ))
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MemoryRetriever", "FTS4 LIKE search failed: error=${e.message}")
            }
        }

        return results
    }

    /**
     * LIKE 降级搜索(当 FTS5/FTS4 都不可用时)
     * 使用 messages_fallback 普通表存储消息
     */
    private fun searchWithLike(query: String, topK: Int, sessionId: String?): List<SearchResult> {
        val db = dbHelper.readableDatabase
        ensureMessagesTableExists(db)  // 创建 fallback 消息表
        val results = mutableListOf<SearchResult>()

        val likePattern = "%${query.trim()}%"

        val sql = if (sessionId != null) {
            """
            SELECT message_id, session_id, content, is_user, created_at, 0 as rank
            FROM messages_fallback
            WHERE content LIKE ? AND session_id = ?
            ORDER BY created_at DESC
            LIMIT ?
            """.trimIndent()
        } else {
            """
            SELECT message_id, session_id, content, is_user, created_at, 0 as rank
            FROM messages_fallback
            WHERE content LIKE ?
            ORDER BY created_at DESC
            LIMIT ?
            """.trimIndent()
        }

        val cursor: Cursor = if (sessionId != null) {
            db.rawQuery(sql, arrayOf(likePattern, sessionId, topK.toString()))
        } else {
            db.rawQuery(sql, arrayOf(likePattern, topK.toString()))
        }

        cursor.use {
            while (it.moveToNext()) {
                results.add(SearchResult(
                    messageId = it.getString(0),
                    sessionId = it.getString(1),
                    content = it.getString(2),
                    isUser = it.getInt(3) == 1,
                    createdAt = it.getLong(4),
                    rank = 0f
                ))
            }
        }

        return results
    }

    /**
     * 创建 fallback 消息表(用于 LIKE 搜索)
     */
    private fun ensureMessagesTableExists(db: SQLiteDatabase) {
        val cursor = db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='messages_fallback'",
            null
        )
        val exists = cursor.use { it.count > 0 }
        if (!exists) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS messages_fallback(
                    message_id TEXT PRIMARY KEY,
                    session_id TEXT,
                    content TEXT,
                    is_user INTEGER,
                    created_at INTEGER
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_fallback_session ON messages_fallback(session_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_fallback_content ON messages_fallback(content)")
        }
    }

    /**
     * 索引单条消息到 fallback 表(LIKE 模式)
     */
    private fun indexMessageToFallback(messageId: String, sessionId: String, content: String, isUser: Boolean, createdAt: Long) {
        try {
            val db = dbHelper.writableDatabase
            ensureMessagesTableExists(db)
            db.execSQL("""
                INSERT OR REPLACE INTO messages_fallback(message_id, session_id, content, is_user, created_at)
                VALUES (?, ?, ?, ?, ?)
            """.trimIndent(), arrayOf(messageId, sessionId, content, if (isUser) 1 else 0, createdAt))
        } catch (e: Exception) { /* 静默失败 */ }
    }

    /**
     * 检索用户消息(只返回用户发的消息)
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

        // 根据 FTS 版本使用不同语法:
        // FTS5: "word"* (带引号和星号)
        // FTS4: word* (不带引号，fts4 不支持引号包围的 prefix)
        val mode = ftsMode ?: detectFtsMode()
        val formattedWords = words.map { word ->
            when {
                mode == FtsMode.FTS5 && word.length > 2 -> "\"$word\"*"
                mode == FtsMode.FTS4 && word.length > 2 -> "$word*"
                else -> word
            }
        }

        return formattedWords.joinToString(" ")
    }

    /**
     * 删除会话的所有索引(级联删除)
     */
    fun deleteSessionIndex(sessionId: String) {
        if (!isFtsAvailable()) return
        try {
            val db = dbHelper.writableDatabase
            db.execSQL("DELETE FROM messages_fts WHERE session_id = ?", arrayOf(sessionId))
        } catch (e: Exception) { /* 静默失败 */ }
    }

    /**
     * 获取索引总数
     */
    fun getIndexCount(): Int {
        if (!isFtsAvailable()) return 0
        return try {
            val db = dbHelper.readableDatabase
            db.rawQuery("SELECT COUNT(*) FROM messages_fts", null).use {
                it.moveToFirst(); it.getInt(0)
            }
        } catch (e: Exception) { 0 }
    }

    /**
     * 重建索引
     */
    fun rebuildIndex() {
        if (!isFtsAvailable()) return
        try {
            val db = dbHelper.writableDatabase
            db.execSQL("INSERT INTO messages_fts(messages_fts) VALUES('rebuild')")
        } catch (e: Exception) { /* 静默失败 */ }
    }

    /**
     * 关闭数据库
     */
    fun close() {
        dbHelper.close()
    }

    // ========== 向量存储 & 相似度搜索 ==========

    /**
     * 为消息生成并存储向量
     * @param messageId 消息ID
     * @param content 消息内容(用于生成向量)
     * @return 是否成功
     */
    suspend fun indexMessageVector(messageId: String, content: String): Boolean {
        if (content.isBlank()) return false
        try {
            val vector = EmbeddingService.embed(content) ?: return false
            val vectorBytes = EmbeddingService.floatArrayToBytes(vector)

            val db = dbHelper.writableDatabase
            ensureVectorTableExists(db)
            db.execSQL(
                "INSERT OR REPLACE INTO message_vectors(message_id, vector) VALUES (?, ?)",
                arrayOf(messageId, vectorBytes)
            )
            return true
        } catch (e: Exception) {
            android.util.Log.e("MemoryRetriever", "indexMessageVector 失败: ${e.message}")
            return false
        }
    }

    /**
     * 向量相似度搜索(高效批量版本)
     * @param queryText 查询文本
     * @param topK 返回数量
     * @param sessionId 可选,限定在某个会话内
     * @return 搜索结果(FTS SearchResult 格式)
     */
    suspend fun searchVectors(queryText: String, topK: Int = 5, sessionId: String? = null): List<SearchResult> {
        if (queryText.isBlank()) return emptyList()

        // embed 加 5 秒超时,防止网络慢时阻塞协程
        val queryVector: FloatArray = try {
            withTimeoutOrNull(5000L) { EmbeddingService.embed(queryText) }
        } catch (e: Exception) {
            null
        } ?: return emptyList()  // 超时或失败则降级到纯 FTS 搜索

        val db = dbHelper.readableDatabase
        ensureVectorTableExists(db)
        ensureMessagesTableExists(db)  // 向量表 join 需要 messages_fallback 表存在

        // 一次性查出所有消息向量和元数据,在应用层计算余弦相似度
        val cursor = db.rawQuery("""
            SELECT v.message_id, m.session_id, m.content, m.is_user, m.created_at, v.vector
            FROM message_vectors v
            INNER JOIN messages_fallback m ON v.message_id = m.message_id
            ${if (sessionId != null) "WHERE m.session_id = ?" else ""}
            """.trimIndent(), if (sessionId != null) arrayOf(sessionId) else null)

        val candidateResults = mutableListOf<SearchResult>()

        cursor.use {
            while (it.moveToNext()) {
                val msgId = it.getString(0)
                val sessId = it.getString(1)
                val content = it.getString(2)
                val isUser = it.getInt(3) == 1
                val createdAt = it.getLong(4)
                val vectorBlob = it.getBlob(5)

                val storedVector = try {
                    EmbeddingService.bytesToFloatArray(vectorBlob)
                } catch (e: Exception) {
                    continue  // 向量数据损坏,跳过
                }

                val similarity = EmbeddingService.cosineSimilarity(queryVector, storedVector)

                candidateResults.add(SearchResult(
                    messageId = msgId,
                    sessionId = sessId,
                    content = content,
                    isUser = isUser,
                    createdAt = createdAt,
                    rank = similarity
                ))
            }
        }

        // 按相似度降序排列,取 topK
        return candidateResults
            .sortedByDescending { it.rank }
            .take(topK)
    }

    /**
     * 混合检索:RRF 融合 FTS4 + 向量相似度
     * @param queryText 查询文本
     * @param topK 返回结果数量
     * @param sessionId 可选,限定在某个 session 内检索
     * @return RRF 融合后的搜索结果
     */
    suspend fun searchHybrid(queryText: String, topK: Int = 5, sessionId: String? = null): List<SearchResult> {
        if (queryText.isBlank()) return emptyList()

        // FTS 检索(同步)
        val ftsResults = search(queryText, topK = topK * 2, sessionId = sessionId)

        // 向量检索(可能超时/失败,降级到纯 FTS)
        val vectorResults = try {
            searchVectors(queryText, topK = topK * 2, sessionId = sessionId)
        } catch (e: Exception) {
            // 向量检索失败时降级到纯 FTS 结果
            emptyList()
        }

        // 构建排名 map
        val ftsRankMap = ftsResults.mapIndexed { index, result -> result.messageId to (index + 1) }.toMap()
        val vectorRankMap = vectorResults.mapIndexed { index, result -> result.messageId to (index + 1) }.toMap()

        // 获取所有 messageId
        val allMessageIds = (ftsRankMap.keys + vectorRankMap.keys).toSet()

        // RRF 融合:score = Σ 1/(k + rank)
        val k = 60  // RRF 标准参数
        val rrfScores = mutableMapOf<String, Double>()

        for (msgId in allMessageIds) {
            val ftsRank = ftsRankMap[msgId]
            val vectorRank = vectorRankMap[msgId]

            val ftsScore = if (ftsRank != null) 1.0 / (k + ftsRank) else 0.0
            val vectorScore = if (vectorRank != null) 1.0 / (k + vectorRank) else 0.0

            rrfScores[msgId] = ftsScore + vectorScore
        }

        // 按 RRF 分数降序排列
        val allResults = mutableMapOf<String, SearchResult>()
        ftsResults.forEach { allResults[it.messageId] = it }
        vectorResults.forEach { allResults[it.messageId] = it }

        return rrfScores.entries
            .sortedByDescending { it.value }
            .take(topK)
            .mapNotNull { (msgId, _) -> allResults[msgId] }
    }

    /**
     * 检查某条消息是否已有向量
     */
    fun hasVectorForMessage(messageId: String): Boolean {
        return try {
            val db = dbHelper.readableDatabase
            ensureVectorTableExists(db)
            db.rawQuery(
                "SELECT COUNT(*) FROM message_vectors WHERE message_id = ?",
                arrayOf(messageId)
            ).use { cursor ->
                cursor.moveToFirst(); cursor.getInt(0) > 0
            }
        } catch (e: Exception) { false }
    }

    /**
     * 获取向量表的消息数量
     */
    fun getVectorCount(): Int {
        return try {
            val db = dbHelper.readableDatabase
            ensureVectorTableExists(db)
            db.rawQuery("SELECT COUNT(*) FROM message_vectors", null).use {
                it.moveToFirst(); it.getInt(0)
            }
        } catch (e: Exception) { 0 }
    }

    /**
     * 创建向量表
     */
    private fun ensureVectorTableExists(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS message_vectors(
                message_id TEXT PRIMARY KEY,
                vector BLOB NOT NULL
            )
        """.trimIndent())
    }

    /**
     * FTS5 专用数据库助手
     * 单独管理 FTS 表,不影响主数据库
     */
    private class FtsDatabaseHelper(context: Context) : SQLiteOpenHelper(
        context,
        "memory_fts.db",
        null,
        2  // 版本号增加以触发升级
    ) {
        override fun onCreate(db: SQLiteDatabase) {
            // 在数据库首次创建时,先检测可用 FTS 模式
            val mode = detectFtsModeForDb(db)
            createMessagesTable(db, mode)
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // 升级时重建表(先检测模式)
            val mode = detectFtsModeForDb(db)
            db.execSQL("DROP TABLE IF EXISTS messages_fts")
            createMessagesTable(db, mode)
        }

        /**
         * 为指定数据库检测可用 FTS 模式
         */
        private fun detectFtsModeForDb(db: SQLiteDatabase): FtsMode {
            // 1. 尝试 FTS5
            try {
                db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS _fts5_test USING fts5(content)")
                db.execSQL("DROP TABLE IF EXISTS _fts5_test")
                return FtsMode.FTS5
            } catch (e: Exception) { /* FTS5 不可用 */ }

            // 2. 尝试 FTS4
            try {
                db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS _fts4_test USING fts4(content)")
                db.execSQL("DROP TABLE IF EXISTS _fts4_test")
                return FtsMode.FTS4
            } catch (e: Exception) { /* FTS4 不可用 */ }

            // 3. 降级到 LIKE(普通表,无全文索引)
            return FtsMode.LIKE
        }

        /**
         * 根据 FTS 模式创建消息表
         */
        private fun createMessagesTable(db: SQLiteDatabase, mode: FtsMode) {
            when (mode) {
                FtsMode.FTS5 -> {
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
                FtsMode.FTS4 -> {
                    db.execSQL("""
                        CREATE VIRTUAL TABLE IF NOT EXISTS messages_fts USING fts4(
                            message_id,
                            session_id,
                            content,
                            is_user,
                            created_at
                        )
                    """.trimIndent())
                }
                FtsMode.LIKE -> {
                    // LIKE 模式:创建普通表,不创建 FTS 虚拟表
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS messages_fts(
                            message_id TEXT PRIMARY KEY,
                            session_id TEXT,
                            content TEXT,
                            is_user INTEGER,
                            created_at INTEGER
                        )
                    """.trimIndent())
                }
            }
        }
    }
}
