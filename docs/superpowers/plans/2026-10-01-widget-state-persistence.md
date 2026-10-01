# Widget State Persistence (v1.0.1) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stop the Quiet Signal widget from showing "disconnected" while Tailscale is still connected, by re-probing VPN evidence on every widget repaint and persisting the last known state as fallback.

**Architecture:** The widget is a passive renderer of an in-memory singleton (`StateRepository`), which resets to `UNKNOWN` in every cold process and is drawn as an off switch. Fix in two layers: (1) every widget render/tap performs an active, synchronous `ConnectivityManager` probe via `StateRepository.refresh()` before painting; (2) resolved `CONNECTED`/`DISCONNECTED` states are persisted to `SharedPreferences` with a wall-clock timestamp, and used as fallback only when the probe returns `UNAVAILABLE`. A state-unchanged guard in `setInternal` prevents repaint recursion introduced by layer 1.

**Tech Stack:** Kotlin (JVM 17), Android SDK 26–35 (minSdk 26, targetSdk 35), JUnit 4 (new, unit tests only), Gradle (AGP 8.9.1), adb for on-device verification.

**Spec / Evidence:** This plan implements the root cause confirmed by code audit + on-device reproduction on 2026-10-01 (Samsung, Android, Nova launcher `ginlemon.flowerfree`, widget id 79):

1. Warm process, VPN up → widget On (screenshot `E:\temp\qs_home_warm.png`, 14:39).
2. `am force-stop com.quiet.signal` (VPN stays up: `NetworkAgentInfo network{275} ni{VPN CONNECTED extra: VPN:com.tailscale.ipn}`, tun0 `100.118.52.31`).
3. System widget rebind (`pm enable com.quiet.signal` triggers the real protected `APPWIDGET_UPDATE`) → cold process 9870 → `render()` reads default `UNKNOWN` snapshot → widget Off while VPN connected (screenshot `E:\temp\qs_cold_repaint.png`, 14:42). **Bug reproduced.**
4. Widget tap → `onReceive` → `refresh()` → probe `VPN_PRESENT` → widget On again (screenshot `E:\temp\qs_after_tap.png`, 14:42:31). Proves the render-time probe is the correct fix.

## Global Constraints

- No new runtime dependencies; the app declares **no INTERNET permission** and must keep it that way (AndroidManifest.xml header comment).
- No foreground service, no polling, no BOOT receiver — the app's stated design is "callback-driven, no polling" (StateRepository.kt:39). Do not add one.
- minSdk 26, targetSdk 35, Java/Kotlin 17.
- Keep R8 release config as-is (`app/proguard-rules.pro`): `Log.d/v` are stripped in release; do not rely on debug logs for release diagnostics.
- Line endings are pinned LF by `.gitattributes`; commit messages follow existing style ("Add …", "Quiet …", imperative, no conventional-commit prefix).
- Tailscale package/receiver constants: `com.tailscale.ipn` / `com.tailscale.ipn.IPNReceiver`, actions `CONNECT_VPN`/`DISCONNECT_VPN` (TailscaleIntegration.kt:9-12).
- Persisted-state fallback TTL: 24 hours (`MAX_AGE_MILLIS = 24 * 60 * 60 * 1000L`).
- Verification device: connected via adb serial `RZCTB0HMQ5L`.

---

### Task 1: Active evidence probe on every widget repaint

**Files:**
- Modify: `app/src/main/java/com/quiet/signal/StateRepository.kt` (`setInternal`, ~line 115-126)
- Modify: `app/src/main/java/com/quiet/signal/widget/TailscaleWidgetProvider.kt` (`render` ~line 57-59, `onReceive` ~line 28-43)
- Modify: `app/src/main/java/com/quiet/signal/widget/TailscaleCompactWidgetProvider.kt` (`render` ~line 42-44, `onReceive` ~line 25-40)

**Interfaces:**
- Consumes: existing `StateRepository.refresh(context: Context): TailscaleSnapshot` (StateRepository.kt:41-45) — registers the monitor if needed and does a synchronous `ConnectivityManager` probe.
- Produces: `StateRepository.setInternal` that only pushes `WidgetUpdater.updateAll` when the state value actually changes. Task 2 adds persistence inside the same guard point.

**Why the guard is mandatory (not optional cleanup):** once `render()` calls `refresh()`, the chain `render → refresh → setObserved → resolve → setInternal → WidgetUpdater.updateAll → render → refresh → …` recurses forever because `setInternal` currently pushes an update unconditionally. With the guard, the second pass sees `old == state` and stops.

- [ ] **Step 1: Add the unchanged-state guard in `StateRepository.setInternal`**

Replace the body of `setInternal` (StateRepository.kt:115-126) with:

```kotlin
private fun setInternal(state: TailscaleState, detail: String, isTransition: Boolean) {
    val old = _snapshot.value.state
    _snapshot.value = TailscaleSnapshot(
        state = state,
        detail = detail,
        device = deviceName,
        transport = transportFor(state)
    )
    Log.d(TAG, "${SystemClock.elapsedRealtime()} state store $old -> $state detail=$detail")
    if (old == state) return
    appContext?.let { WidgetUpdater.updateAll(it) }
    if (isTransition) Log.d(TAG, "UI/widget update requested state=$state")
}
```

The only behavioral change versus the current code is the added `if (old == state) return` guard.

- [ ] **Step 2: Probe before painting in both providers' `render()`**

In `TailscaleWidgetProvider.render` (line 57-59) change:

```kotlin
private fun render(context: Context, manager: AppWidgetManager, id: Int) {
    SettingsRepository.init(context)
    val snapshot = TailscaleIntegration.snapshot()
```

to:

```kotlin
private fun render(context: Context, manager: AppWidgetManager, id: Int) {
    SettingsRepository.init(context)
    val snapshot = StateRepository.refresh(context)
```

Apply the identical change in `TailscaleCompactWidgetProvider.render` (line 42-44). Both files already import `StateRepository`. Remove the now-unused `TailscaleIntegration` import from both provider files if no other usage remains in that file.

- [ ] **Step 3: Probe before deciding the tap action in both providers' `onReceive`**

In `TailscaleWidgetProvider.onReceive` change:

```kotlin
val current = StateRepository.snapshot.value.state
```

to:

```kotlin
val current = StateRepository.refresh(context).state
```

Apply the identical change in `TailscaleCompactWidgetProvider.onReceive` (line 32). Effect: a cold tap on a connected VPN now toggles (disconnects) instead of silently "refreshing" — consistent with the warm-process behavior.

- [ ] **Step 4: Build debug APK and install**

```bash
./gradlew :app:assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Expected: `BUILD SUCCESSFUL`, `Success`.

- [ ] **Step 5: On-device regression — the exact repro from the spec**

Prerequisite: Tailscale installed and connectable; screen unlocked; Quiet Signal widget present on home screen (find its id with `adb shell "dumpsys appwidget" | grep -E "\] id=[0-9]+|app:10402"` — last-known id 79).

```bash
# 1. ensure VPN up
adb shell "am broadcast -a com.tailscale.ipn.CONNECT_VPN -n com.tailscale.ipn/.IPNReceiver"
sleep 8
adb shell "dumpsys connectivity | grep -c 'ni{VPN'"        # expect: 1
# 2. warm the app once so the widget paints On
adb shell "am start -n com.quiet.signal/.MainActivity"; sleep 2
adb shell input keyevent KEYCODE_HOME; sleep 1
adb exec-out screencap -p > /tmp/fix1_warm.png
# 3. kill the process, trigger a real system widget rebind
adb shell am force-stop com.quiet.signal; sleep 1
adb shell pm enable com.quiet.signal; sleep 3
# 4. VPN must still be up and the widget must still show On
adb shell "dumpsys connectivity | grep -c 'ni{VPN'"        # expect: 1
adb exec-out screencap -p > /tmp/fix1_cold.png
```

Visually inspect `fix1_cold.png` (Read tool on the Windows path from `cygpath -w /tmp/fix1_cold.png`): the widget switch must be **green/On**, matching `fix1_warm.png`. This is the fail-to-pass check: pre-fix this exact sequence produced a gray switch (evidence: `E:\temp\qs_cold_repaint.png`).

- [ ] **Step 6: Verify no repaint storm / recursion**

```bash
adb logcat -c; adb shell pm enable com.quiet.signal; sleep 5
adb logcat -d | grep -cE "QuietSignal"                     # expect: single-digit count, no repeating update loop
```

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/quiet/signal/StateRepository.kt app/src/main/java/com/quiet/signal/widget/TailscaleWidgetProvider.kt app/src/main/java/com/quiet/signal/widget/TailscaleCompactWidgetProvider.kt
git commit -m "Probe VPN evidence on every widget repaint so cold processes stop showing Unknown as disconnected"
```

---

### Task 2: Persist last-known state with offline fallback

**Files:**
- Create: `app/src/test/java/com/quiet/signal/ObservedStateResolverTest.kt`
- Create: `app/src/main/java/com/quiet/signal/ObservedStateResolver.kt`
- Create: `app/src/main/java/com/quiet/signal/StatePersistence.kt`
- Modify: `app/build.gradle.kts` (dependencies block, line 68-75)
- Modify: `app/src/main/java/com/quiet/signal/StateRepository.kt` (`setObserved` ~84-89, `timeout` ~91-98, `setInternal` ~115)

**Interfaces:**
- Consumes: `VpnEvidence` (VpnStateReader.kt:12), `ObservedState` (StateRepository.kt:13), `SettingsRepository.init` pattern for SharedPreferences usage.
- Produces:
  - `fun resolveObserved(probe: VpnEvidence, persisted: ObservedState?, ageMillis: Long?, maxAgeMillis: Long): ObservedState` in `ObservedStateResolver.kt` (top-level function, pure, no Android imports).
  - `object StatePersistence { fun save(context: Context, state: ObservedState); fun load(context: Context, nowMillis: Long): Pair<ObservedState, Long>? }` in `StatePersistence.kt`.

- [ ] **Step 1: Add the JUnit test dependency**

In `app/build.gradle.kts` dependencies block add:

```kotlin
testImplementation("junit:junit:4.13.2")
```

- [ ] **Step 2: Write the failing resolver tests**

Create `app/src/test/java/com/quiet/signal/ObservedStateResolverTest.kt`:

```kotlin
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
}
```

- [ ] **Step 3: Run tests to verify they fail**

```bash
./gradlew :app:testDebugUnitTest --tests "com.quiet.signal.ObservedStateResolverTest"
```

Expected: FAIL — unresolved reference `resolveObserved`.

- [ ] **Step 4: Implement the resolver**

Create `app/src/main/java/com/quiet/signal/ObservedStateResolver.kt`:

```kotlin
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
```

- [ ] **Step 5: Run tests to verify they pass**

```bash
./gradlew :app:testDebugUnitTest --tests "com.quiet.signal.ObservedStateResolverTest"
```

Expected: 5 tests PASS.

- [ ] **Step 6: Implement persistence**

Create `app/src/main/java/com/quiet/signal/StatePersistence.kt`:

```kotlin
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
```

- [ ] **Step 7: Wire resolver + persistence into `StateRepository`**

Add near the top of `StateRepository` (after `private val handler`):

```kotlin
private val maxAgeMillis = 24 * 60 * 60 * 1000L
```

Replace the `observed = when (evidence) { ... }` mapping inside `setObserved` (StateRepository.kt:86) with:

```kotlin
observed = currentObserved(evidence)
```

Replace the same mapping inside `timeout` (StateRepository.kt:92) with:

```kotlin
monitor?.currentEvidence()?.let { observed = currentObserved(it) }
```

Add a private helper to `StateRepository`:

```kotlin
private fun currentObserved(evidence: VpnEvidence): ObservedState {
    val ctx = appContext
    val persisted = ctx?.let { StatePersistence.load(it, System.currentTimeMillis()) }
    return resolveObserved(evidence, persisted?.first, persisted?.second, maxAgeMillis)
}
```

In `setInternal`, inside the existing `if (old == state) return` guard's complement (i.e. after the guard, before `updateAll`), persist the resolved state only when it is settled:

```kotlin
if (transition == TransitionState.NONE) appContext?.let { StatePersistence.save(it, observed) }
```

- [ ] **Step 8: Run the full unit test suite and build**

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Expected: all tests pass, `BUILD SUCCESSFUL`.

- [ ] **Step 9: On-device sanity check**

Install and repeat Task 1 Step 5's sequence once (kill → rebind → screenshot): widget must stay green. Then disconnect for real and confirm the widget follows:

```bash
adb shell "am broadcast -a com.tailscale.ipn.DISCONNECT_VPN -n com.tailscale.ipn/.IPNReceiver"
sleep 8
adb shell "dumpsys connectivity | grep -c 'ni{VPN'"        # expect: 0
adb shell pm enable com.quiet.signal; sleep 3
adb exec-out screencap -p > /tmp/fix2_off.png
```

`fix2_off.png` must show the gray/off switch (no stale "connected" fallback leaking through, because the probe returns `VPN_ABSENT`, which always wins over persisted state).

- [ ] **Step 10: Commit**

```bash
git add app/build.gradle.kts app/src/test app/src/main/java/com/quiet/signal/ObservedStateResolver.kt app/src/main/java/com/quiet/signal/StatePersistence.kt app/src/main/java/com/quiet/signal/StateRepository.kt
git commit -m "Persist last settled VPN evidence and use it only when a live probe is unavailable"
```

---

### Task 3: Release hygiene — version bump, changelog, release-build verification

**Files:**
- Modify: `app/build.gradle.kts` (versionCode/versionName, lines 34-35)
- Modify: `CHANGELOG.md`
- Read-only check: `app/proguard-rules.pro` (no change expected)

**Interfaces:**
- Consumes: Tasks 1-2 code.
- Produces: installable signed-or-unsigned release APK `app/build/outputs/apk/release/app-release.apk` verified on device.

- [ ] **Step 1: Bump version**

In `app/build.gradle.kts`: `versionCode = 2`, `versionName = "1.0.1"`.

- [ ] **Step 2: Add changelog entry**

Prepend to `CHANGELOG.md` (match its existing heading style — read the file first):

```markdown
## 1.0.1 - 2026-10-01
Fixed: the widget no longer shows "disconnected" when the app process was
killed and the launcher repaints it. Every repaint now probes live VPN
evidence; the last settled state is persisted and used only when the probe
itself cannot see connectivity.
```

- [ ] **Step 3: Build release and verify R8 kept the probe path**

```bash
./gradlew :app:assembleRelease
```

Expected: `BUILD SUCCESSFUL`. Confirm `StateRepository.refresh` survives into the release mapping: check `app/build/outputs/mapping/release/mapping.txt` for `com.quiet.signal.StateRepository ->` and that `refresh` is present (kept via the existing `-keep class com.quiet.signal.StateRepository { *; }` rule; no rule change needed).

- [ ] **Step 4: Install release build and rerun the full repro**

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

Rerun Task 1 Step 5 end-to-end (connect → warm → force-stop → rebind → screenshot). Widget must stay green. Then restore the user's preferred build if they were on a different one.

- [ ] **Step 5: Commit**

```bash
git add app/build.gradle.kts CHANGELOG.md
git commit -m "Quiet Signal v1.0.1 - widget repaints probe live VPN evidence instead of showing cold-process Unknown"
```

---

## Non-goals / explicit decisions

- **No new service, alarm, or BOOT receiver** — the periodic `updatePeriodMillis=1800000` plus repaint-time probing covers the failure mode; the app's no-polling design stays intact.
- **No new UNKNOWN artwork** — with repaint-time probing, `UNKNOWN` becomes rare (only when the probe fails AND no fresh persisted state exists). The 2x1 layouts already label it; the 1x1 keeps its current off-switch look.
- **Cold tap semantics change** (Task 1 Step 3): first tap on a cold widget now toggles instead of refreshing. This is intentional and matches warm behavior.
- **Stale-persisted risk accepted:** if the user disconnects inside the Tailscale app while this device has no repaint for >probe-failure window, the widget only shows the stale state when the probe is `UNAVAILABLE` (rare); a successful probe always overrides persistence.
- **Release diagnosability gap** (Log.d stripped by R8) is pre-existing and out of scope; `Log.e` paths remain.

## Verification ledger (what must be run before claiming done)

| Check | Command | Proves |
|---|---|---|
| Unit tests | `./gradlew :app:testDebugUnitTest` | resolver truth table incl. TTL edge |
| Fail-to-pass repro | Task 1 Step 5 sequence + screenshot diff | the exact user-reported bug is gone |
| Disconnect still reflected | Task 2 Step 9 | no stale-connected regression |
| Release build | `./gradlew :app:assembleRelease` + device repro | R8 didn't strip the fix |
