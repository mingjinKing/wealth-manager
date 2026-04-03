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
import javax.inject.Singleton

/**
 * LLM 相关依赖注入模块
 *
 * API Key 获取优先级：
 * 1. BuildConfig.LLM_API_KEY（来自 gradle.properties，本地开发用）
 * 2. EncryptedSharedPreferences（安全存储，生产环境用）
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
    fun provideLLMClient(
        @ApplicationContext context: Context
    ): LLMClient {
        val apiKey = getApiKey(context)
        return LLMClient(
            apiKey = apiKey,
            baseUrl = BASE_URL,
            model = MODEL
        )
    }

    /**
     * 获取 API Key
     * 优先级：BuildConfig > EncryptedSharedPreferences > 空字符串
     */
    private fun getApiKey(context: Context): String {
        // 1. 优先用 BuildConfig（gradle.properties，本地开发）
        val buildConfigKey = BuildConfig.LLM_API_KEY
        if (buildConfigKey.isNotBlank() && buildConfigKey != "your_api_key_here") {
            return buildConfigKey
        }

        // 2. 兜底 EncryptedSharedPreferences（生产安全存储）
        return loadFromSecurePrefs(context)
    }

    private fun loadFromSecurePrefs(context: Context): String {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            val securePrefs = EncryptedSharedPreferences.create(
                context,
                SECURE_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )

            securePrefs.getString(KEY_API_KEY, "") ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * 保存 API Key 到安全存储（供生产环境或用户更换 Key 时调用）
     */
    fun saveApiKey(context: Context, apiKey: String) {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            val securePrefs = EncryptedSharedPreferences.create(
                context,
                SECURE_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )

            securePrefs.edit().putString(KEY_API_KEY, apiKey).apply()
        } catch (e: Exception) {
            // 忽略保存失败
        }
    }
}
