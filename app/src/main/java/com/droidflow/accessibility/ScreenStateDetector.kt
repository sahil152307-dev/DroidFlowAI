package com.droidflow.accessibility

import com.droidflow.models.UIElement

/**
 * Lightweight screen classification from visible elements (TRD §7).
 * Used by the verification engine to compare expected vs actual state.
 * Keyword rules with priority — deliberately simple and debuggable.
 */
object ScreenStateDetector {

    fun guess(packageName: String, elements: List<UIElement>): String {
        val texts = elements.mapNotNull { it.text?.lowercase() } +
            elements.mapNotNull { it.hint?.lowercase() } +
            elements.mapNotNull { it.contentDescription?.lowercase() }

        fun has(needle: String) = texts.any { it.contains(needle) }

        return when {
            has("amount to pay") || has("pay ₹") -> "Payment"
            has("book ride") -> "Booking"
            has("available rides") -> "RideResults"
            has("order placed") || has("ride booked") -> "Done"
            has("where to?") || has("drop location") -> "CabHome"
            has("create your account") -> "Form"
            has("registration submitted") -> "FormDone"
            has("search products") -> "MartHome"
            has("add to cart") || has("buy now") || has("add to bag") -> "ProductDetail"
            has("checkout") -> "Checkout"
            packageName.contains("droidflow") -> "DroidFlow"
            else -> "Unknown"
        }
    }
}
