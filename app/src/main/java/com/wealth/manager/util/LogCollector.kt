package com.wealth.manager.util

import android.content.Context
import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * 调试日志收集器
 * 维护一个内存中的日志缓冲区，支持上传到服务器
 */
object LogCollector {
    private const val MAX_LOGS = 500
    private const val TAG = "LogCollector"
    
    private val logs = ConcurrentLinkedQueue<LogEntry>()
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    
    data class LogEntry(
        val timestamp: Long = System.currentTimeMillis(),
        val tag: String,
        val level: String,  // D/I/W/E
        val message: String,
        val thread: String = Thread.currentThread().name
    )
    
    /**
     * 记录一条日志
     */
    fun log(tag: String, level: String = "D", message: String) {
        val entry = LogEntry(tag = tag, level = level, message = message)
        logs.offer(entry)
        // 超过上限时移除最旧的
        while (logs.size > MAX_LOGS) {
            logs.poll()
        }
        // 同时打印到系统 Logcat
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
    
    /**
     * 上传所有日志到服务器
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
        
        val requestBody = body.toRequestBody("application/x-www-form-urlencoded".toMediaType())
        
        val request = Request.Builder()
            .url("http://101.201.67.78/log/report")
            .post(requestBody)
            .build()
        
        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "上传日志失败: ${e.message}")
                onComplete(false, e.message ?: "网络错误")
            }
            
            override fun onResponse(call: Call, response: Response) {
                val ok = response.isSuccessful
                Log.i(TAG, "上传日志结果: $ok")
                onComplete(ok, if (ok) "上传成功 (${logs.size} 条)" else "服务器错误")
            }
        })
    }
    
    /**
     * 获取当前日志数量
     */
    fun size(): Int = logs.size
    
    /**
     * 清空日志
     */
    fun clear() {
        logs.clear()
    }
    
    /**
     * 获取日志摘要（用于预览）
     */
    fun getSummary(): String {
        return if (logs.isEmpty()) {
            "暂无日志"
        } else {
            "${logs.size} 条日志"
        }
    }
}
