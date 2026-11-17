package com.example

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.presentation.JuthoorViewModel
import com.example.presentation.navigation.JuthoorAppRoot

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val appContainer = (application as JuthoorApplication).container

        setContent {
            val viewModel: JuthoorViewModel = viewModel(
                factory = JuthoorViewModel.factory(
                    repository = appContainer.genealogyRepository,
                    preferencesRepository = appContainer.preferencesRepository,
                    mediaStorage = appContainer.mediaStorage,
                    backupManager = appContainer.backupManager,
                    exportManager = appContainer.exportManager,
                    cloudAuthRepository = appContainer.cloudAuthRepository,
                ),
            )
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            JuthoorAppRoot(
                uiState = uiState,
                viewModel = viewModel,
            )
        }
    }
}
