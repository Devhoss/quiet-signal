package com.quiet.signal.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.RemoteViews
import com.quiet.signal.GlassMaterial
import com.quiet.signal.GreenIntensity
import com.quiet.signal.R
import com.quiet.signal.SettingsRepository
import com.quiet.signal.StateRepository
import com.quiet.signal.TailscaleState

class TailscaleCompactWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) =
        ids.forEach { render(context, manager, it) }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION) {
            SettingsRepository.init(context)
            if (SettingsRepository.settings.value.hapticFeedback) {
                performHaptic(context)
            }
            val current = StateRepository.refresh(context).state
            when (current) {
                TailscaleState.CONNECTED -> StateRepository.requestDisconnect(context)
                TailscaleState.DISCONNECTED -> StateRepository.requestConnect(context)
                else -> StateRepository.refresh(context)
            }
            WidgetUpdater.updateAll(context)
        }
    }

    private fun render(context: Context, manager: AppWidgetManager, id: Int) {
        SettingsRepository.init(context)
        val snapshot = StateRepository.refresh(context)
        val settings = SettingsRepository.settings.value

        val bgRes = when (settings.material) {
            GlassMaterial.CLEAR ->
                if (settings.specularRim) R.drawable.widget_compact_glass_clear else R.drawable.widget_compact_glass_clear_flat
            GlassMaterial.GRAPHITE ->
                if (settings.specularRim) R.drawable.widget_compact_glass_graphite else R.drawable.widget_compact_glass_graphite_flat
            GlassMaterial.OBSIDIAN ->
                if (settings.specularRim) R.drawable.widget_compact_glass_obsidian else R.drawable.widget_compact_glass_obsidian_flat
        }

        val luminous = settings.greenIntensity == GreenIntensity.LUMINOUS
        val switchRes = when (snapshot.state) {
            TailscaleState.CONNECTED ->
                if (luminous) R.drawable.widget_switch_mini_on_luminous else R.drawable.widget_switch_mini_on
            TailscaleState.DISCONNECTED -> R.drawable.widget_switch_mini_off
            TailscaleState.CONNECTING ->
                if (luminous) R.drawable.widget_switch_mini_connecting_luminous else R.drawable.widget_switch_mini_connecting
            TailscaleState.DISCONNECTING ->
                if (luminous) R.drawable.widget_switch_mini_disconnecting_luminous else R.drawable.widget_switch_mini_disconnecting
            TailscaleState.ERROR -> R.drawable.widget_switch_mini_off
            TailscaleState.UNKNOWN -> R.drawable.widget_switch_mini_off
        }

        val views = RemoteViews(context.packageName, R.layout.widget_tailscale_compact).apply {
            setInt(R.id.widget_compact_root, "setBackgroundResource", bgRes)
            setImageViewResource(R.id.widget_compact_switch_view, switchRes)

            val clickIntent = actionIntent(context, ACTION, id)
            setOnClickPendingIntent(R.id.widget_compact_root, clickIntent)
            setOnClickPendingIntent(R.id.widget_compact_switch_view, clickIntent)
            setContentDescription(
                R.id.widget_compact_root,
                "Tailscale 1x1, ${snapshot.state.label}. Tap to toggle."
            )
        }
        manager.updateAppWidget(id, views)
    }

    private fun performHaptic(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    v?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                } else {
                    @Suppress("DEPRECATION")
                    v?.vibrate(20)
                }
            }
        } catch (_: Exception) { }
    }

    private fun actionIntent(context: Context, action: String, id: Int) =
        PendingIntent.getBroadcast(
            context,
            id,
            Intent(context, TailscaleCompactWidgetProvider::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    companion object {
        const val ACTION = "com.quiet.signal.widget.COMPACT_TOGGLE"
    }
}

