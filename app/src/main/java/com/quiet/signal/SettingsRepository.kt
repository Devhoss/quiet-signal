package com.quiet.signal

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class GlassMaterial {
    OBSIDIAN,
    GRAPHITE,
    CLEAR
}

enum class GreenIntensity {
    RESTRAINED,
    LUMINOUS
}

data class UserSettings(
    val material: GlassMaterial = GlassMaterial.OBSIDIAN,
    val specularRim: Boolean = true,
    val greenIntensity: GreenIntensity = GreenIntensity.RESTRAINED,
    val hapticFeedback: Boolean = true,
    val networkRecheck: Boolean = true
)

object SettingsRepository {
    private const val PREFS_NAME = "quiet_signal_settings"
    private const val KEY_MATERIAL = "glass_material"
    private const val KEY_SPECULAR = "specular_rim"
    private const val KEY_INTENSITY = "green_intensity"
    private const val KEY_HAPTICS = "haptic_feedback"
    private const val KEY_RECHECK = "network_recheck"

    private val _settings = MutableStateFlow(UserSettings())
    val settings: StateFlow<UserSettings> = _settings.asStateFlow()

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        val sp = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = sp
        val material = when (sp.getString(KEY_MATERIAL, GlassMaterial.OBSIDIAN.name)) {
            GlassMaterial.GRAPHITE.name -> GlassMaterial.GRAPHITE
            GlassMaterial.CLEAR.name -> GlassMaterial.CLEAR
            else -> GlassMaterial.OBSIDIAN
        }
        val specular = sp.getBoolean(KEY_SPECULAR, true)
        val intensity = when (sp.getString(KEY_INTENSITY, GreenIntensity.RESTRAINED.name)) {
            GreenIntensity.LUMINOUS.name -> GreenIntensity.LUMINOUS
            else -> GreenIntensity.RESTRAINED
        }
        val haptics = sp.getBoolean(KEY_HAPTICS, true)
        val recheck = sp.getBoolean(KEY_RECHECK, true)

        _settings.value = UserSettings(
            material = material,
            specularRim = specular,
            greenIntensity = intensity,
            hapticFeedback = haptics,
            networkRecheck = recheck
        )
    }

    fun setMaterial(material: GlassMaterial) {
        prefs?.edit { putString(KEY_MATERIAL, material.name) }
        _settings.value = _settings.value.copy(material = material)
    }

    fun setSpecularRim(enabled: Boolean) {
        prefs?.edit { putBoolean(KEY_SPECULAR, enabled) }
        _settings.value = _settings.value.copy(specularRim = enabled)
    }

    fun setGreenIntensity(intensity: GreenIntensity) {
        prefs?.edit { putString(KEY_INTENSITY, intensity.name) }
        _settings.value = _settings.value.copy(greenIntensity = intensity)
    }

    fun setHapticFeedback(enabled: Boolean) {
        prefs?.edit { putBoolean(KEY_HAPTICS, enabled) }
        _settings.value = _settings.value.copy(hapticFeedback = enabled)
    }

    fun setNetworkRecheck(enabled: Boolean) {
        prefs?.edit { putBoolean(KEY_RECHECK, enabled) }
        _settings.value = _settings.value.copy(networkRecheck = enabled)
    }
}
