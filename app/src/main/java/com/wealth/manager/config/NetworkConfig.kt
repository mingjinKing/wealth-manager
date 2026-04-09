package com.wealth.manager.config

/**
 * 网络配置统一管理
 *
 * 所有网络相关超时、连接池配置集中管理
 */
object NetworkConfig {

    // ==================== LLM 超时配置 ====================
    /** LLM 连接超时（秒）*/
    const val LLM_CONNECT_TIMEOUT = 20L

    /** LLM 读取超时（秒）*/
    const val LLM_READ_TIMEOUT = 120L

    /** LLM 写入超时（秒）*/
    const val LLM_WRITE_TIMEOUT = 20L

    // ==================== LLM 连接池配置 ====================
    /** 连接池最大空闲连接数 */
    const val POOL_MAX_IDLE = 10

    /** 连接池保持活跃时间（分钟）*/
    const val POOL_KEEP_ALIVE_MINUTES = 5L

    // ==================== 版本检查超时配置 ====================
    /** 版本检查连接超时（秒）*/
    const val VERSION_CONNECT_TIMEOUT = 15

    /** 版本检查读取超时（秒）*/
    const val VERSION_READ_TIMEOUT = 15

    // ==================== HTTP 客户端配置 ====================
    /** 最大并发请求数 */
    const val DISPATCHER_MAX_REQUESTS = 64

    /** 每主机最大并发请求数 */
    const val DISPATCHER_MAX_REQUESTS_PER_HOST = 16

    // ==================== 客户端超时配置 ====================
    /** 客户端连接超时（秒）*/
    const val CLIENT_CONNECT_TIMEOUT = 30L

    /** 客户端读取超时（秒）*/
    const val CLIENT_READ_TIMEOUT = 120L

    /** 客户端写入超时（秒）*/
    const val CLIENT_WRITE_TIMEOUT = 30L
}
