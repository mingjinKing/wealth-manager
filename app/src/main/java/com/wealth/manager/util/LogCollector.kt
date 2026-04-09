package com.wealth.manager.util

import android.content.Context
import android.util.Log
import com.wealth.manager.config.AppConfig
import com.wealth.manager.config.BusinessConfig
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 调试日志收集器 - 统一连接池版
 */
@Singleton
class LogCollector @Inject constructor(
    private val client: OkHttpClient
) {
    companion object {
        private const val TAG = "LogCollector"
        
        // 静态日志缓冲区，支持静态方法记录
        private val logs = ConcurrentLinkedQueue<LogEntry>()
        private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
        
        data class LogEntry(
            val timestamp: Long = System.currentTimeMillis(),
            val tag: String,
            val level: String,
            val message: String,
            val thread: String = Thread.currentThread().name
        )

        fun log(tag: String, level: String = "D", message: String) {
            val entry = LogEntry(tag = tag, level = level, message = message)
            logs.offer(entry)
            while (logs.size > BusinessConfig.MAX_LOGS) logs.poll()
            when (level) {
                "E" -> Log.e(tag, message)
                "W" -> Log.w(tag, message)
                "I" -> Log.i(tag, message)
                else -> Log.d(tag, message)
            }
        }
        fun d(tag: String, msg: String) = log(tag, "D", msg)
        fun i(tag: String, msg: String) = log(tag, "I", msg)
        fun w(tag: String, msg: String) = log(tag, "W", msg)
        fun e(tag: String, msg: String) = log(tag, "E", msg)
        fun getSummary(): String = if (logs.isEmpty()) "暂无日志" else "${logs.size} 条日志"
        fun clear() = logs.clear()
    }
    
    /**
     * 上传所有日志到服务器 (复用全局连接池)
     */
    fun uploadAll(context: Context, deviceId: String, onComplete: (Boolean, String) -> Unit) {
        val logsArray = JSONArray()
        for (entry in logs) {
            logsArray.put(JSONObject().apply {
                put("t", dateFormat.format(Date(entry.timestamp)))
                put("l", entry.level)
                put("g", entry.tag)
                put("m", entry.message)
                put("th", entry.thread)
            })
        }
        
        val body = JSONObject().apply {
            put("type", "howtospend_debug_log")
            put("device_id", deviceId)
            put("count", logs.size)
            put("logs", logsArray)
        }.toString()
        
        val requestBody = body.toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(AppConfig.LOG_REPORT_URL)
            .post(requestBody)
            .build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "上传日志失败: ${e.message}")
                onComplete(false, e.message ?: "网络错误")
            }
            override fun onResponse(call: Call, response: Response) {
                val ok = response.isSuccessful
                onComplete(ok, if (ok) "上传成功" else "服务器错误")
            }
        })
    }
}
