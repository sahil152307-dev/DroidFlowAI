package com.droidflow.demoapps.cab

import androidx.compose.ui.graphics.Color

/**
 * Mock cab app configuration. The two apps deliberately differ in
 * wording, colors and layout so the agent must do SEMANTIC matching,
 * not coordinate replay:
 *  - different destination hints ("Where to?" vs "Enter drop location")
 *  - different search button labels ("Search rides" vs "Find rides")
 *  - different fare structures
 */
data class CabConfig(
    val appName: String,
    val primary: Color,
    val primaryDark: Color,
    val destinationHint: String,
    val searchButtonLabel: String,
    val renamedSearchLabel: String,
    val rideClasses: List<RideClass>
)

data class RideClass(val name: String, val fare: Int, val etaMin: Int)

object CabConfigs {
    val RIDE_NOW = CabConfig(
        appName = "RideNow",
        primary = Color(0xFF00B074),
        primaryDark = Color(0xFF00865A),
        destinationHint = "Where to?",
        searchButtonLabel = "Search rides",
        renamedSearchLabel = "Find your ride",
        rideClasses = listOf(
            RideClass("Auto", 342, 3),
            RideClass("Sedan", 482, 5),
            RideClass("XL", 689, 7)
        )
    )

    val SWIFT_RIDE = CabConfig(
        appName = "SwiftRide",
        primary = Color(0xFF7C3AED),
        primaryDark = Color(0xFF5B21B6),
        destinationHint = "Enter drop location",
        searchButtonLabel = "Find rides",
        renamedSearchLabel = "Look up rides",
        rideClasses = listOf(
            RideClass("Auto", 361, 4),
            RideClass("Sedan", 519, 6),
            RideClass("XL", 712, 8)
        )
    )
}
