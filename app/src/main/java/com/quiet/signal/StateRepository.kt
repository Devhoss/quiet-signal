package com.quiet.signal

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.quiet.signal.widget.WidgetUpdater
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ObservedState { UNKNOWN, DISCONNECTED, CONNECTED }
enum class TransitionState { NONE, CONNECTING, DISCONNECTING }

object StateRepository {
    private val _snapshot = MutableStateFlow(TailscaleSnapshot())
    val snapshot: StateFlow<TailscaleSnapshot> = _snapshot.asStateFlow()
    private var observed = ObservedState.UNKNOWN
    private var transition = TransitionState.NONE
    private var monitor: VpnStateMonitor? = null
    private var appContext: Context? = null
    private val handler = Handler(Looper.getMainLooper())
    private val maxAgeMillis = 24 * 60 * 60 * 1000L
    private var timeout: Runnable? = null
    private val reconciliations = mutableListOf<Runnable>()

    fun start(context: Context) {
        val app = context.applicationContext
        appContext = app
        if (monitor == null) {
            val m = VpnStateMonitor(app)
            monitor = m
            m.start({ evidence -> setObserved(evidence) }, { error -> Log.e(TAG, "monitor error", error) })
        } else {
            monitor?.currentEvidence()?.let { setObserved(it) }
        }
    }

    fun stop() { /* Keep the process monitor registered while the process is alive; no polling or foreground service. */ }

    fun refresh(context: Context): TailscaleSnapshot {
        start(context)
        monitor?.currentEvidence()?.let { setObserved(it) }
        return _snapshot.value
    }

    fun requestConnect(context: Context) = begin(context, TransitionState.CONNECTING, TailscaleState.CONNECTING)
    fun requestDisconnect(context: Context) = begin(context, TransitionState.DISCONNECTING, TailscaleState.DISCONNECTING)

    fun toggle(context: Context): Boolean {
        val state = _snapshot.value.state
        return when (state) {
            TailscaleState.CONNECTED -> requestDisconnect(context)
            TailscaleState.DISCONNECTED -> requestConnect(context)
            else -> { refresh(context); false }
        }
    }

    private fun begin(context: Context, nextTransition: TransitionState, nextState: TailscaleState): Boolean {
        start(context)
        val id = "${nextTransition.name.lowercase()}-${SystemClock.elapsedRealtime()}"
        monitor?.setCorrelation(id)
        Log.d(TAG, "${SystemClock.elapsedRealtime()} user tap transition=$nextTransition requestId=$id")
        transition = nextTransition
        setInternal(nextState, "Waiting for VPN state · $id", true, false)
        val requestedState = if (nextTransition == TransitionState.CONNECTING) TailscaleState.DISCONNECTED else TailscaleState.CONNECTED
        val sentAt = SystemClock.elapsedRealtime()
        val sent = TailscaleIntegration.request(context, requestedState, id)
        Log.d(TAG, "${SystemClock.elapsedRealtime()} broadcast result returned=$sent requestId=$id elapsedSinceSent=${SystemClock.elapsedRealtime() - sentAt}ms")
        if (!sent) setInternal(TailscaleState.ERROR, "Tailscale broadcast could not be sent", false, false)
        Log.d(TAG, "$id broadcast sent=$sent observed=$observed transition=$nextTransition")
        scheduleTimeout(id)
        // Network Recheck controls ONLY the additional one-shot reconciliation
        // sweep. Core callback-driven VPN detection (VpnStateMonitor) and the
        // timeout above keep working regardless of this setting.
        if (SettingsRepository.settings.value.networkRecheck) {
            scheduleReconciliations(id)
        } else {
            Log.d(TAG, "$id network recheck disabled - one-shot reconciliation skipped")
        }
        return sent
    }

    fun setObserved(evidence: VpnEvidence) {
        Log.d(TAG, "${SystemClock.elapsedRealtime()} VPN evidence detected=$evidence requestId=${monitor?.correlationId() ?: "-"}")
        observed = currentObserved(evidence)
        resolve(evidence != VpnEvidence.UNAVAILABLE)
        if (transition == TransitionState.NONE) cancelReconciliations()
    }

    fun timeout(id: String) {
        val evidence = monitor?.currentEvidence()
        val probeConfirmed = evidence != null && evidence != VpnEvidence.UNAVAILABLE
        evidence?.let { observed = currentObserved(it) }
        val failed = when (transition) { TransitionState.CONNECTING -> observed != ObservedState.CONNECTED; TransitionState.DISCONNECTING -> observed != ObservedState.DISCONNECTED; TransitionState.NONE -> false }
        if (failed) observed = ObservedState.UNKNOWN
        transition = TransitionState.NONE
        Log.d(TAG, "$id timeout observed=$observed transition=$transition")
        resolve(probeConfirmed)
    }

    private fun resolve(probeConfirmed: Boolean) {
        val state = when {
            transition == TransitionState.CONNECTING && observed == ObservedState.CONNECTED -> { transition = TransitionState.NONE; TailscaleState.CONNECTED }
            transition == TransitionState.CONNECTING -> TailscaleState.CONNECTING
            transition == TransitionState.DISCONNECTING && observed == ObservedState.DISCONNECTED -> { transition = TransitionState.NONE; TailscaleState.DISCONNECTED }
            transition == TransitionState.DISCONNECTING -> TailscaleState.DISCONNECTING
            observed == ObservedState.CONNECTED -> TailscaleState.CONNECTED
            observed == ObservedState.DISCONNECTED -> TailscaleState.DISCONNECTED
            else -> TailscaleState.UNKNOWN
        }
        val detail = when (state) { TailscaleState.CONNECTED -> "VPN active · best-effort evidence"; TailscaleState.DISCONNECTED -> "No active VPN detected"; TailscaleState.UNKNOWN -> "VPN state unavailable"; else -> "Waiting for VPN state" }
        setInternal(state, detail, false, probeConfirmed)
        if (transition == TransitionState.NONE) cancelReconciliations()
    }

    private fun setInternal(state: TailscaleState, detail: String, isTransition: Boolean, probeConfirmed: Boolean) {
        val old = _snapshot.value.state
        _snapshot.value = TailscaleSnapshot(
            state = state,
            detail = detail,
            device = deviceName,
            transport = transportFor(state)
        )
        Log.d(TAG, "${SystemClock.elapsedRealtime()} state store $old -> $state detail=$detail")
        if (old == state) return
        if (transition == TransitionState.NONE && probeConfirmed) appContext?.let { StatePersistence.save(it, observed) }
        appContext?.let { WidgetUpdater.updateAll(it) }
        if (isTransition) Log.d(TAG, "UI/widget update requested state=$state")
    }

    private fun currentObserved(evidence: VpnEvidence): ObservedState {
        val ctx = appContext
        val persisted = ctx?.let { StatePersistence.load(it, System.currentTimeMillis()) }
        return resolveObserved(evidence, persisted?.first, persisted?.second, maxAgeMillis)
    }

    /**
     * Honest, evidence-based transport wording. We can only observe that an
     * Android VPN interface is present or absent — no protocol attribution.
     */
    private fun transportFor(state: TailscaleState): String = when (observed) {
        ObservedState.CONNECTED ->
            if (state == TailscaleState.CONNECTED) "Android VPN active" else "VPN detected"
        ObservedState.DISCONNECTED -> "VPN not detected"
        ObservedState.UNKNOWN -> "Unknown"
    }

    private fun scheduleReconciliations(id: String) {
        cancelReconciliations()
        listOf(250L, 750L, 1500L).forEach { delay ->
            val runnable = Runnable {
                if (transition == TransitionState.NONE) return@Runnable
                val evidence = monitor?.currentEvidence()
                Log.d(TAG, "${SystemClock.elapsedRealtime()} one-shot reconciliation delay=${delay}ms evidence=$evidence requestId=$id")
                evidence?.let { setObserved(it) }
            }
            reconciliations += runnable
            handler.postDelayed(runnable, delay)
        }
    }

    private fun cancelReconciliations() {
        reconciliations.forEach { handler.removeCallbacks(it) }
        reconciliations.clear()
    }

    private fun scheduleTimeout(id: String) {
        timeout?.let { handler.removeCallbacks(it) }
        val runnable = Runnable { timeout(id) }
        timeout = runnable
        handler.postDelayed(runnable, 12_000)
    }

    private const val TAG = "QuietSignal/State"
}
