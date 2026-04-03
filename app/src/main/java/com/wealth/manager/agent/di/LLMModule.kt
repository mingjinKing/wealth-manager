package com.wealth.manager.agent.di

import com.wealth.manager.agent.LLMClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * LLM 相关依赖注入模块
 */
@Module
@InstallIn(SingletonComponent::class)
object LLMModule {

    // 火山引擎 Ark API 配置
    // TODO: 正式环境应从 BuildConfig 或安全存储获取
    private const val API_KEY = "2ab45fd0-6190-4843-a723-c98a864f19e5"
    private const val BASE_URL = "https://ark.cn-beijing.volces.com/api/coding/v3"
    private const val MODEL = "deepseek-v3.2"

    @Provides
    @Singleton
    fun provideLLMClient(): LLMClient {
        return LLMClient(
            apiKey = API_KEY,
            baseUrl = BASE_URL,
            model = MODEL
        )
    }
}
