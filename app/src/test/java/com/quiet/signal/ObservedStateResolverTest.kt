package com.quiet.signal

import org.junit.Assert.assertEquals
import org.junit.Test

class ObservedStateResolverTest {
    private val maxAge = 24 * 60 * 60 * 1000L

    @Test fun probePresentWins() {
        assertEquals(ObservedState.CONNECTED, resolveObserved(VpnEvidence.VPN_PRESENT, ObservedState.DISCONNECTED, 0, maxAge))
    }

    @Test fun probeAbsentWins() {
        assertEquals(ObservedState.DISCONNECTED, resolveObserved(VpnEvidence.VPN_ABSENT, ObservedState.CONNECTED, 0, maxAge))
    }

    @Test fun unavailableFallsBackToFreshPersisted() {
        assertEquals(ObservedState.CONNECTED, resolveObserved(VpnEvidence.UNAVAILABLE, ObservedState.CONNECTED, 30 * 60 * 1000L, maxAge))
        assertEquals(ObservedState.DISCONNECTED, resolveObserved(VpnEvidence.UNAVAILABLE, ObservedState.DISCONNECTED, 30 * 60 * 1000L, maxAge))
    }

    @Test fun unavailableWithStalePersistedIsUnknown() {
        assertEquals(ObservedState.UNKNOWN, resolveObserved(VpnEvidence.UNAVAILABLE, ObservedState.CONNECTED, maxAge + 1, maxAge))
    }

    @Test fun unavailableWithNoPersistedIsUnknown() {
        assertEquals(ObservedState.UNKNOWN, resolveObserved(VpnEvidence.UNAVAILABLE, null, null, maxAge))
    }

    @Test fun unavailableWithNegativeAgeIsUnknown() {
        // A future timestamp (clock rollback) yields a negative age and must not be trusted.
        assertEquals(ObservedState.UNKNOWN, resolveObserved(VpnEvidence.UNAVAILABLE, ObservedState.CONNECTED, -60_000, maxAge))
    }
}
