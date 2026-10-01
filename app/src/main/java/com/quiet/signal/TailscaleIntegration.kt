package com.quiet.signal

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

object TailscaleIntegration {
    const val PACKAGE = "com.tailscale.ipn"
    const val RECEIVER = "com.tailscale.ipn.IPNReceiver"
    const val ACTION_CONNECT = "com.tailscale.ipn.CONNECT_VPN"
    const val ACTION_DISCONNECT = "com.tailscale.ipn.DISCONNECT_VPN"

    fun isInstalled(context: Context): Boolean = runCatching { context.packageManager.getPackageInfo(PACKAGE, 0); true }.getOrDefault(false)

    fun openApp(context: Context): Boolean = context.startActivitySafely(Intent(Intent.ACTION_MAIN).apply {
        component = ComponentName(PACKAGE, "com.tailscale.ipn.MainActivity")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    })

    fun request(context: Context, state: TailscaleState, requestId: String = "-"): Boolean {
        val action = if (state == TailscaleState.CONNECTED) ACTION_DISCONNECT else ACTION_CONNECT
        Log.d(TAG, "sending explicit broadcast action=$action component=${ComponentName(PACKAGE, RECEIVER)} requestId=$requestId")
        return context.sendBroadcastSafely(Intent(action).setComponent(ComponentName(PACKAGE, RECEIVER)))
    }

    private fun Context.startActivitySafely(intent: Intent): Boolean = runCatching { startActivity(intent); true }.getOrDefault(false)
    private fun Context.sendBroadcastSafely(intent: Intent): Boolean = runCatching { sendBroadcast(intent); true }.getOrElse { Log.e(TAG, "broadcast failed action=${intent.action}", it); false }
    const val TAG = "QuietSignal/Tailscale"
}
