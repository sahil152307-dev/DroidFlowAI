package com.droidflow.demoapps.cab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme

/** Mock "App B" for the hero demo — purple SwiftRide. */
class SwiftRideActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                CabAppScreen(CabConfigs.SWIFT_RIDE)
            }
        }
    }
}
