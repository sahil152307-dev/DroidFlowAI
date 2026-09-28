package com.droidflow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.droidflow.ui.AgentViewModel
import com.droidflow.ui.navigation.DroidFlowNavHost
import com.droidflow.ui.theme.DroidFlowTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DroidFlowTheme {
                // Activity-scoped view model: shared by every destination.
                val agentViewModel: AgentViewModel = viewModel()
                DroidFlowNavHost(agentViewModel)
            }
        }
    }
}
