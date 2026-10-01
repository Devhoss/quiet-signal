package com.quiet.signal.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.SizeF
import android.widget.RemoteViews
import com.quiet.signal.GlassMaterial
import com.quiet.signal.GreenIntensity
import com.quiet.signal.R
import com.quiet.signal.SettingsRepository
import com.quiet.signal.StateRepository
import com.quiet.signal.TailscaleSnapshot
import com.quiet.signal.TailscaleState

class TailscaleWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) =
        ids.forEach { render(context, manager, it) }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TOGGLE) {
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

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, manager, appWidgetId, newOptions)
        // Below Android 12 this is where layout swaps happen while resizing;
        // Android 12+ swaps live through the SizeF view mapping in render().
        render(context, manager, appWidgetId)
    }

    private fun render(context: Context, manager: AppWidgetManager, id: Int) {
        SettingsRepository.init(context)
        val snapshot = StateRepository.refresh(context)
        val settings = SettingsRepository.settings.value

        val bgRes = when (settings.material) {
            GlassMaterial.CLEAR ->
                if (settings.specularRim) R.drawable.widget_glass_clear else R.drawable.widget_glass_clear_flat
            GlassMaterial.GRAPHITE ->
                if (settings.specularRim) R.drawable.widget_glass_graphite else R.drawable.widget_glass_graphite_flat
            GlassMaterial.OBSIDIAN ->
                if (settings.specularRim) R.drawable.widget_glass_obsidian else R.drawable.widget_glass_obsidian_flat
        }

        val luminous = settings.greenIntensity == GreenIntensity.LUMINOUS
        val switchRes = when (snapshot.state) {
            TailscaleState.CONNECTED ->
                if (luminous) R.drawable.widget_switch_on_luminous else R.drawable.widget_switch_on
            TailscaleState.DISCONNECTED -> R.drawable.widget_switch_off
            TailscaleState.CONNECTING ->
                if (luminous) R.drawable.widget_switch_connecting_luminous else R.drawable.widget_switch_connecting
            TailscaleState.DISCONNECTING ->
                if (luminous) R.drawable.widget_switch_disconnecting_luminous else R.drawable.widget_switch_disconnecting
            TailscaleState.ERROR -> R.drawable.widget_switch_off
            TailscaleState.UNKNOWN -> R.drawable.widget_switch_off
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ responsive sizing: one update maps sizes (dp) to
            // layouts; the launcher swaps them live while the user resizes.
            // Keys are the smallest size at which each layout applies.
            val mapping = linkedMapOf(
                SizeF(MIN_WIDTH_DP.toFloat(), MIN_HEIGHT_DP.toFloat()) to
                    buildViews(context, id, R.layout.widget_tailscale_minimal, bgRes, switchRes, snapshot),
                SizeF(MIN_WIDTH_DP.toFloat(), NORMAL_MIN_HEIGHT_DP.toFloat()) to
                    buildViews(context, id, R.layout.widget_tailscale, bgRes, switchRes, snapshot),
                SizeF(MIN_WIDTH_DP.toFloat(), TALL_MIN_HEIGHT_DP.toFloat()) to
                    buildViews(context, id, R.layout.widget_tailscale_tall, bgRes, switchRes, snapshot),
                SizeF(WIDE_MIN_WIDTH_DP.toFloat(), TALL_MIN_HEIGHT_DP.toFloat()) to
                    buildViews(context, id, R.layout.widget_tailscale_wide_tall, bgRes, switchRes, snapshot)
            )
            manager.updateAppWidget(id, RemoteViews(mapping))
        } else {
            // API 26-30: pick from the current widget size; re-rendered on
            // every options change via onAppWidgetOptionsChanged().
            val (widthDp, heightDp) = currentSizeDp(manager, id)
            manager.updateAppWidget(
                id,
                buildViews(context, id, layoutFor(widthDp, heightDp), bgRes, switchRes, snapshot)
            )
        }
    }

    private fun buildViews(
        context: Context,
        id: Int,
        layoutRes: Int,
        bgRes: Int,
        switchRes: Int,
        snapshot: TailscaleSnapshot
    ): RemoteViews = RemoteViews(context.packageName, layoutRes).apply {
        setInt(R.id.widget_root, "setBackgroundResource", bgRes)
        setImageViewResource(R.id.widget_switch_view, switchRes)
        setTextViewText(R.id.widget_state, snapshot.state.label)

        val clickIntent = actionIntent(context, ACTION_TOGGLE, id)
        setOnClickPendingIntent(R.id.widget_root, clickIntent)
        setOnClickPendingIntent(R.id.widget_switch_view, clickIntent)
        setContentDescription(
            R.id.widget_root,
            "Tailscale, ${snapshot.state.label}. Tap to toggle."
        )
    }

    private fun currentSizeDp(manager: AppWidgetManager, id: Int): Pair<Int, Int> {
        val options = manager.getAppWidgetOptions(id)
        val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val maxWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
        val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
        val maxHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
        return maxOf(minWidth, maxWidth) to maxOf(minHeight, maxHeight)
    }

    private fun layoutFor(widthDp: Int, heightDp: Int): Int = when {
        widthDp >= WIDE_MIN_WIDTH_DP && heightDp >= TALL_MIN_HEIGHT_DP -> R.layout.widget_tailscale_wide_tall
        heightDp >= TALL_MIN_HEIGHT_DP -> R.layout.widget_tailscale_tall
        heightDp >= NORMAL_MIN_HEIGHT_DP -> R.layout.widget_tailscale
        else -> R.layout.widget_tailscale_minimal
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
            Intent(context, TailscaleWidgetProvider::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    companion object {
        const val ACTION_TOGGLE = "com.quiet.signal.widget.TOGGLE"

        // Layout selection thresholds (dp), shared by the Android 12+ SizeF
        // view mapping and the API 26-30 onAppWidgetOptionsChanged path.
        /** Smallest supported size: minimum breakpoint (single-line + small switch). */
        private const val MIN_WIDTH_DP = 110
        private const val MIN_HEIGHT_DP = 40
        /** Height at which the full normal layout (title + state) fits. */
        private const val NORMAL_MIN_HEIGHT_DP = 90
        /** Height at which extra vertical breathing room kicks in. */
        private const val TALL_MIN_HEIGHT_DP = 150
        /** Width at which the wider+tall layout kicks in. */
        private const val WIDE_MIN_WIDTH_DP = 300
    }
}

