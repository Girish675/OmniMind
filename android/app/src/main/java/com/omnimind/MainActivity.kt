package com.omnimind

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.omnimind.ui.chat.ChatViewModel
import com.omnimind.ui.diagnostics.DiagnosticsViewModel
import com.omnimind.ui.models.ModelManagerViewModel
import com.omnimind.ui.navigation.OmniMindAppScaffold
import com.omnimind.ui.settings.SettingsViewModel
import com.omnimind.ui.theme.OmniMindTheme

class MainActivity : ComponentActivity() {

    private val chatViewModel: ChatViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = application as OmniMindApp
                return ChatViewModel(
                    modelRepository = app.modelRepository,
                    conversationRepository = app.conversationRepository,
                    settingsRepository = app.settingsRepository
                ) as T
            }
        }
    }

    private val modelManagerViewModel: ModelManagerViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = application as OmniMindApp
                return ModelManagerViewModel(
                    context = app.applicationContext,
                    modelRepository = app.modelRepository,
                    settingsRepository = app.settingsRepository
                ) as T
            }
        }
    }

    private val diagnosticsViewModel: DiagnosticsViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = application as OmniMindApp
                return DiagnosticsViewModel(
                    context = app.applicationContext,
                    modelRepository = app.modelRepository,
                    settingsRepository = app.settingsRepository,
                    benchmarkRepository = app.benchmarkRepository
                ) as T
            }
        }
    }

    private val settingsViewModel: SettingsViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = application as OmniMindApp
                return SettingsViewModel(
                    settingsRepository = app.settingsRepository
                ) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OmniMindTheme {
                OmniMindAppScaffold(
                    chatViewModel = chatViewModel,
                    modelManagerViewModel = modelManagerViewModel,
                    diagnosticsViewModel = diagnosticsViewModel,
                    settingsViewModel = settingsViewModel
                )
            }
        }
    }
}
