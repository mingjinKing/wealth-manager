package com.wealth.manager.config

import com.wealth.manager.BuildConfig

/**
 * App 配置统一管理
 *
 * 所有服务器地址、API 端点集中管理
 */
object AppConfig {

    // ==================== LLM 配置 ====================
    /** LLM API Base URL */
    const val LLM_BASE_URL = BuildConfig.LLM_BASE_URL

    /** 默认模型 */
    const val LLM_DEFAULT_MODEL = BuildConfig.LLM_MODEL

    // ==================== Embedding 服务 ====================
    /** Embedding API URL */
    const val EMBEDDING_API_URL = BuildConfig.EMBEDDING_API_URL

    // ==================== 版本更新服务 ====================
    /** 版本信息检查 URL */
    const val VERSION_CHECK_URL = BuildConfig.VERSION_URL

    /** APK 下载 URL */
    const val APK_DOWNLOAD_URL = BuildConfig.APK_URL

    // ==================== 日志服务 ====================
    /** 日志上报 URL */
    const val LOG_REPORT_URL = BuildConfig.LOG_REPORT_URL
}
