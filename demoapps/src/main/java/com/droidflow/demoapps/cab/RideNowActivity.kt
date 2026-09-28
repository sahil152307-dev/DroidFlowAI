package com.droidflow.demoapps.cab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import com.droidflow.demoapps.cab.CabConfigs

/** Mock "App A" for the hero demo — green RideNow. */
class RideNowActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                CabAppScreen(CabConfigs.RIDE_NOW)
            }
        }
    }
}
