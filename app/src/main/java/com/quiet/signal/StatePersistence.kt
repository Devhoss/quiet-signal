package com.quiet.signal

import android.content.Context
import androidx.core.content.edit

object StatePersistence {
    private const val PREFS_NAME = "quiet_signal_state"
    private const val KEY_OBSERVED = "last_observed"
    private const val KEY_AT = "last_observed_at"

    fun save(context: Context, state: ObservedState) {
        if (state == ObservedState.UNKNOWN) return
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putString(KEY_OBSERVED, state.name).putLong(KEY_AT, System.currentTimeMillis()) }
    }

    fun load(context: Context, nowMillis: Long): Pair<ObservedState, Long>? {
        val sp = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val name = sp.getString(KEY_OBSERVED, null) ?: return null
        val at = sp.getLong(KEY_AT, -1L)
        if (at < 0) return null
        val state = runCatching { ObservedState.valueOf(name) }.getOrNull() ?: return null
        return state to (nowMillis - at)
    }
}
