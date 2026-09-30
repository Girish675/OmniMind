package com.omnimind

import android.app.Application
import android.util.Log
import com.omnimind.data.db.OmniMindDatabase
import com.omnimind.data.repository.BenchmarkRepository
import com.omnimind.data.repository.ConversationRepository
import com.omnimind.data.repository.ModelRepository
import com.omnimind.data.repository.SettingsRepository
import com.omnimind.native.LlamaEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class OmniMindApp : Application() {
    private val TAG = "OmniMindApp"
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var database: OmniMindDatabase
        private set

    lateinit var modelRepository: ModelRepository
        private set

    lateinit var conversationRepository: ConversationRepository
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var benchmarkRepository: BenchmarkRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Initialize native backend
        val initSuccess = LlamaEngine.init()
        Log.i(TAG, "Native LlamaEngine initialized: $initSuccess")

        // Initialize Room Database
        database = OmniMindDatabase.getInstance(this)

        // Initialize Repositories
        modelRepository = ModelRepository(this, database.modelDao())
        conversationRepository = ConversationRepository(database.conversationDao(), database.messageDao())
        settingsRepository = SettingsRepository(database.settingsDao())
        benchmarkRepository = BenchmarkRepository(database.benchmarkDao())

        // Initial scan for models placed on device
        appScope.launch {
            try {
                modelRepository.scanLocalModels()
            } catch (e: Exception) {
                Log.w(TAG, "Initial model scan warning", e)
            }
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        Log.w(TAG, "onTrimMemory received with level $level")
        // Note: For severe memory trimming, native model could be unloaded if app backgrounded
    }

    override fun onTerminate() {
        super.onTerminate()
        LlamaEngine.shutdown()
    }

    companion object {
        lateinit var instance: OmniMindApp
            private set
    }
}
