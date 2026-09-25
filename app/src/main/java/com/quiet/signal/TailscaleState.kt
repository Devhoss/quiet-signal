package com.quiet.signal

import android.os.Build

enum class TailscaleState(val label: String, val compactLabel: String, val actionLabel: String) {
    CONNECTED("Connected", "On", "Disconnect"),
    DISCONNECTED("Disconnected", "Off", "Connect"),
    CONNECTING("Connecting…", "…", "Cancel"),
    DISCONNECTING("Disconnecting…", "…", "Cancel"),
    ERROR("Needs attention", "Error", "Retry"),
    UNKNOWN("Unknown", "—", "Refresh")
}

/**
 * Actual device identity for this runtime, e.g. "samsung SM-G991B".
 * Never hardcoded: read from [Build] so it matches the real device.
 */
val deviceName: String = run {
    val manufacturer = Build.MANUFACTURER.orEmpty().trim()
    val model = Build.MODEL.orEmpty().trim()
    when {
        model.isEmpty() && manufacturer.isEmpty() -> "Android device"
        model.isEmpty() -> manufacturer
        manufacturer.isEmpty() -> model
        model.startsWith(manufacturer, ignoreCase = true) -> model
        else -> "$manufacturer $model"
    }
}

data class TailscaleSnapshot(
    val state: TailscaleState = TailscaleState.UNKNOWN,
    val detail: String = "Open Tailscale to check",
    val device: String = deviceName,
    val transport: String = "Unknown"
)
