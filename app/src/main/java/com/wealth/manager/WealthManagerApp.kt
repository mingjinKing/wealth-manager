package com.wealth.manager

import android.app.Application
import com.wealth.manager.agent.AppInitializer
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class WealthManagerApp : Application() {

    @Inject
    lateinit var appInitializer: AppInitializer

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        // 启动时执行旺财 Agent 初始化
        applicationScope.launch {
            appInitializer.bootstrap()
        }
    }
}
