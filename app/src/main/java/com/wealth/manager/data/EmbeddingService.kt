package com.wealth.manager.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Embedding 向量服务
 * 调用用户的 embedding API，将文本转为 768 维向量
 */
object EmbeddingService {
    
    private const val TAG = "EmbeddingService"
    private const val EMBEDDING_API = "http://82.157.16.215:5000/embeddings"
    private const val DIMENSION = 768  // 向量维度
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    /**
     * 将文本转为向量
     * @param text 输入文本
     * @return 768维 float 向量（kotlin FloatArray），失败返回 null
     */
    suspend fun embed(text: String): FloatArray? = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext null
        
        try {
            val jsonBody = JSONObject().put("input", text)
            val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())
            
            val request = Request.Builder()
                .url(EMBEDDING_API)
                .post(requestBody)
                .build()
            
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "embedding API 失败: ${response.code}")
                return@withContext null
            }
            
            val body = response.body?.string() ?: return@withContext null
            val json = JSONObject(body)
            val embeddingArray = json
                .getJSONArray("data")
                .getJSONObject(0)
                .getJSONArray("embedding")
            
            val vector = FloatArray(DIMENSION)
            for (i in 0 until DIMENSION) {
                vector[i] = embeddingArray.getDouble(i).toFloat()
            }
            
            Log.d(TAG, "embed 成功: ${text.take(30)}... → 向量维度=${vector.size}")
            vector
        } catch (e: Exception) {
            Log.e(TAG, "embedding 失败: ${e.message}")
            null
        }
    }
    
    /**
     * 计算两个向量的余弦相似度
     */
    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 0f
        
        var dotProduct = 0f
        var normA = 0f
        var normB = 0f
        
        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        
        val norm = kotlin.math.sqrt(normA) * kotlin.math.sqrt(normB)
        return if (norm > 0f) dotProduct / norm else 0f
    }
    
    /**
     * 将 FloatArray 转为 ByteArray（用于 SQLite BLOB 存储）
     */
    fun floatArrayToBytes(vector: FloatArray): ByteArray {
        val buffer = java.nio.ByteBuffer.allocate(vector.size * 4)
        for (v in vector) {
            buffer.putFloat(v)
        }
        return buffer.array()
    }
    
    /**
     * 将 ByteArray 转回 FloatArray
     */
    fun bytesToFloatArray(bytes: ByteArray): FloatArray {
        val buffer = java.nio.ByteBuffer.wrap(bytes)
        val vector = FloatArray(bytes.size / 4)
        for (i in vector.indices) {
            vector[i] = buffer.getFloat()
        }
        return vector
    }
}
