package com.wealth.manager.data

import android.util.Log
import com.wealth.manager.config.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Embedding 向量服务
 * 使用 AppConfig 中的统一配置
 */
@Singleton
class EmbeddingService @Inject constructor(
    private val client: OkHttpClient
) {
    
    companion object {
        private const val TAG = "EmbeddingService"
        private const val DIMENSION = 768
        
        // 静态工具方法保留，方便转换数据
        fun floatArrayToBytes(vector: FloatArray): ByteArray {
            val buffer = java.nio.ByteBuffer.allocate(vector.size * 4)
            for (v in vector) buffer.putFloat(v)
            return buffer.array()
        }
        
        fun bytesToFloatArray(bytes: ByteArray): FloatArray {
            val buffer = java.nio.ByteBuffer.wrap(bytes)
            val vector = FloatArray(bytes.size / 4)
            for (i in vector.indices) vector[i] = buffer.getFloat()
            return vector
        }

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
    }
    
    suspend fun embed(text: String): FloatArray? = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext null
        
        try {
            val jsonBody = JSONObject().put("input", text)
            val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())
            
            val request = Request.Builder()
                .url(AppConfig.EMBEDDING_API_URL)
                .post(requestBody)
                .build()
            
            // 使用注入的 client 执行请求
            client.newCall(request).execute().use { response ->
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
                vector
            }
        } catch (e: Exception) {
            Log.e(TAG, "embedding 失败: ${e.message}")
            null
        }
    }
}
