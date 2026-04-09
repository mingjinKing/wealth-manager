package com.wealth.manager.agent.di

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.wealth.manager.BuildConfig
import com.wealth.manager.agent.LLMClient
import com.wealth.manager.config.AppConfig
import com.wealth.manager.config.NetworkConfig
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

    private const val SECURE_PREFS_NAME = "secure_llm_prefs"
    private const val KEY_API_KEY = "llm_api_key"

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        // 使用 NetworkConfig 中的统一配置
        val dispatcher = Dispatcher().apply {
            maxRequests = NetworkConfig.DISPATCHER_MAX_REQUESTS
            maxRequestsPerHost = NetworkConfig.DISPATCHER_MAX_REQUESTS_PER_HOST
        }

        return OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .connectionPool(ConnectionPool(
                NetworkConfig.POOL_MAX_IDLE,
                NetworkConfig.POOL_KEEP_ALIVE_MINUTES,
                TimeUnit.MINUTES
            ))
            .connectTimeout(NetworkConfig.LLM_CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(NetworkConfig.LLM_READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(NetworkConfig.LLM_WRITE_TIMEOUT, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    @Provides
    @Singleton
    fun provideLLMClient(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient
    ): LLMClient {
        val apiKey = getApiKey(context)
        return LLMClient(
            apiKey = apiKey,
            baseUrl = AppConfig.LLM_BASE_URL,
            model = AppConfig.LLM_DEFAULT_MODEL,
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
            val securePrefs = EncryptedSharedPreferences.create(
                context, SECURE_PREFS_NAME, masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            securePrefs.getString(KEY_API_KEY, "") ?: ""
        } catch (e: Exception) { "" }
    }
}
