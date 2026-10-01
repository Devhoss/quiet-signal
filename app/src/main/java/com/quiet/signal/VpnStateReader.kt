package com.quiet.signal

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.SystemClock
import android.util.Log

/** Public Android VPN evidence. This does not identify the VPN owner. */
enum class VpnEvidence { VPN_PRESENT, VPN_ABSENT, UNAVAILABLE }

class VpnStateMonitor(private val context: Context) {
    private val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val vpnNetworks = mutableSetOf<Network>()
    private var listener: ((VpnEvidence) -> Unit)? = null
    private var started = false
    private var correlationId: String = "-"

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            // Capabilities may be unknown for a brand-new network; adding it here would make evidence() claim VPN_PRESENT for any plain Wi-Fi connect. Membership is decided only by onCapabilitiesChanged's TRANSPORT_VPN check.
            log("callback onAvailable network=$network requestId=$correlationId")
            reconcile()
        }
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) vpnNetworks += network else vpnNetworks -= network
            log("callback onCapabilitiesChanged network=$network vpn=${capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)} requestId=$correlationId")
            reconcile()
        }
        override fun onLost(network: Network) {
            vpnNetworks -= network
            log("callback onLost network=$network requestId=$correlationId")
            reconcile()
        }
    }

    fun start(onEvidence: (VpnEvidence) -> Unit, onError: (Throwable) -> Unit) {
        listener = onEvidence
        if (started) return
        started = true
        try {
            // Every app-registered registerNetworkCallback request gains an implicit NET_CAPABILITY_NOT_VPN (AOSP maybeMarkCapabilitiesRestricted), which VPN networks can never satisfy — even a filter-free one. registerDefaultNetworkCallback is the public path that tracks our default network including when it is a VPN; callbacks filter by transport.
            manager.registerDefaultNetworkCallback(callback)
            log("callback registered requestId=$correlationId")
        } catch (t: Throwable) {
            Log.e(TAG, "VPN callback registration failed", t)
            started = false
            onError(t)
        }
        reconcile()
    }

    fun setCorrelation(id: String) { correlationId = id }
    fun correlationId(): String = correlationId
    fun currentEvidence(): VpnEvidence = evidence()
    fun stop() {
        if (!started) return
        runCatching { manager.unregisterNetworkCallback(callback) }.onFailure { Log.e(TAG, "VPN callback unregister failed", it) }
        started = false
        log("callback unregistered")
    }

    private fun reconcile() {
        val evidence = evidence()
        log("snapshot vpnNetworks=${vpnNetworks.size} evidence=$evidence requestId=$correlationId")
        listener?.invoke(evidence)
    }

    private fun evidence(): VpnEvidence {
        if (vpnNetworks.isNotEmpty()) return VpnEvidence.VPN_PRESENT
        val active = manager.activeNetwork ?: return VpnEvidence.UNAVAILABLE
        val capabilities = manager.getNetworkCapabilities(active) ?: return VpnEvidence.UNAVAILABLE
        return if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) VpnEvidence.VPN_PRESENT else VpnEvidence.VPN_ABSENT
    }

    private fun log(message: String) = Log.d(TAG, "${SystemClock.elapsedRealtime()} $message")
    companion object { const val TAG = "QuietSignal/VpnMonitor" }
}

fun interface VpnObserverHandle { fun close() }

