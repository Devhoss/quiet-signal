package com.quiet.signal.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log

object WidgetUpdater {
    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val large = manager.getAppWidgetIds(ComponentName(context, TailscaleWidgetProvider::class.java))
        val compact = manager.getAppWidgetIds(ComponentName(context, TailscaleCompactWidgetProvider::class.java))
        Log.d("QuietSignal/Widgets", "${android.os.SystemClock.elapsedRealtime()} update requested 2x1=${large.size} 1x1=${compact.size} state=${com.quiet.signal.StateRepository.snapshot.value.state}")
        large.forEach { TailscaleWidgetProvider().onUpdate(context, manager, intArrayOf(it)) }
        compact.forEach { TailscaleCompactWidgetProvider().onUpdate(context, manager, intArrayOf(it)) }
        Log.d("QuietSignal/Widgets", "${android.os.SystemClock.elapsedRealtime()} update completed state=${com.quiet.signal.StateRepository.snapshot.value.state}")
    }
}
