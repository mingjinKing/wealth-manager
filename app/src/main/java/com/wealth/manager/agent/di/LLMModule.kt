package com.wealth.manager.agent.di

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.wealth.manager.BuildConfig
import com.wealth.manager.agent.LLMClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * LLM 相关依赖注入模块 - 全局单例连接池版
 */
@Module
@InstallIn(SingletonComponent::class)
object LLMModule {

    private const val BASE_URL = "https://ark.cn-beijing.volces.com/api/coding/v3"
    private const val MODEL = "deepseek-v3.2"

    private const val SECURE_PREFS_NAME = "secure_llm_prefs"
    private const val KEY_API_KEY = "llm_api_key"

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        // 优化并发：增加最大请求数，共享连接池
        val dispatcher = Dispatcher().apply {
            maxRequests = 64
            maxRequestsPerHost = 16
        }
        
        return OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    @Provides
    @Singleton
    fun provideLLMClient(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient // 注入全局单例
    ): LLMClient {
        val apiKey = getApiKey(context)
        return LLMClient(
            apiKey = apiKey,
            baseUrl = BASE_URL,
            model = MODEL,
            okHttpClient = okHttpClient
        )
    }

    fun getApiKey(context: Context): String {
        val buildConfigKey = BuildConfig.LLM_API_KEY
        if (buildConfigKey.isNotBlank() && buildConfigKey != "your_api_key_here") {
            return buildConfigKey
        }
        return try {
            val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            val securePrefs = EncryptedSharedPreferences.create(context, SECURE_PREFS_NAME, masterKey, EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
            securePrefs.getString(KEY_API_KEY, "") ?: ""
        } catch (e: Exception) { "" }
    }
}
