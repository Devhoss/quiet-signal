# Changelog

All notable changes to Quiet Signal are documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2026-09-25

Initial public release. Open source on GitHub, distributed as a manually installed APK.

### Added

- **1×1 home-screen widget** — miniature horizontal liquid-glass switch in a tuned squircle housing;
  no text, no status orb, no clipping, across ON / OFF / CONNECTING / DISCONNECTING.
- **Responsive 2×1 widget** — a single provider with four breakpoint layouts (minimum, normal, tall,
  wide + tall). Extra height/width becomes breathing room only: no wrapping, no clipping, no
  dashboard expansion, switch always inside bounds.
- **Tailscale companion controls** — connect/disconnect requests forwarded to the official
  Tailscale Android app via explicit `CONNECT_VPN` / `DISCONNECT_VPN` broadcasts, with real
  `Connecting…` / `Disconnecting…` transitions, a correlation id per request and a 12-second
  safeguard timeout.
- **Best-effort VPN state detection** — `ConnectivityManager` `TRANSPORT_VPN` callback observation
  plus an `activeNetwork` fallback, surfaced with deliberately honest wording
  (“Android VPN active”, “VPN detected”, “VPN not detected”, “Unknown”).
- **Three widget surfaces** — **Deep Obsidian** gloss, **Graphite** smoked glass, and **Clear**, a
  genuinely transparent housing that lets the wallpaper show through (no dark fill, no heavy
  shadow, subtle rim plus switch only).
- **Specular Highlight Rim** setting — ON keeps the prismatic rim + top specular arc; OFF switches
  every widget to `_flat` drawables (faint border, no arc) without making the widgets unusable.
- **Tailscale Green Intensity** setting — **Restrained** authentic green or **Luminous** brighter
  glow and track stroke, applied to the ON / CONNECTING / DISCONNECTING switch states in both
  widget sizes.
- **Haptic Confirmation** setting — real widget-tap haptics via `VibratorManager` /
  `VibrationEffect`, not a cosmetic toggle.
- **Network Recheck** setting — ON runs the optional one-shot reconciliation burst after a tap; OFF
  skips it while callback-driven VPN observation and the safeguard timeout keep working.
- **Settings persistence** — surface, rim, green intensity, haptics and network recheck are stored in
  `SharedPreferences`, survive app close / process recreation / reboot, and immediately re-render
  existing 1×1 and 2×1 widgets.
- **Companion app** — Compose UI with live state, a real device card (manufacturer/model read from
  `android.os.Build`, never hardcoded) and a best-effort VPN transport card.
- **Branding** — adaptive launcher icon (foreground + background), **monochrome** icon for themed
  icons, legacy mipmap fallbacks, and a Deep Obsidian splash screen.
- **Widget picker previews** — Android 12+ `previewLayout` plus PNG `previewImage` fallback showing
  the real widget design instead of the app icon.

### Security

- No internet permission, no backend, no account, no analytics, no telemetry, no credentials.
- Release builds strip verbose `Log.d` / `Log.v` diagnostics via R8; only `Log.e` remains.
- Release signing credentials are never committed; `keystore.properties` and keystores are gitignored.

### Known limitations

- Android VPN evidence is best-effort: it proves *an* Android VPN interface exists, not that
  Tailscale owns it. Another VPN app produces the same evidence.
- Connect/disconnect depends on Tailscale's own (unofficial) broadcast receiver contract.

[1.0.0]: https://github.com/Devhoss/quiet-signal/releases/tag/v1.0.0
