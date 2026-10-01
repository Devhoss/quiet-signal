package com.quiet.signal

fun resolveObserved(
    probe: VpnEvidence,
    persisted: ObservedState?,
    ageMillis: Long?,
    maxAgeMillis: Long
): ObservedState = when (probe) {
    VpnEvidence.VPN_PRESENT -> ObservedState.CONNECTED
    VpnEvidence.VPN_ABSENT -> ObservedState.DISCONNECTED
    VpnEvidence.UNAVAILABLE ->
        if (persisted != null && persisted != ObservedState.UNKNOWN && ageMillis != null && ageMillis in 0..maxAgeMillis) persisted
        else ObservedState.UNKNOWN
}
