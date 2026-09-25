## Quiet Signal v1.0.0

A lightweight, open-source **Android home-screen companion widget for Tailscale** — a liquid-glass
VPN toggle in a 1×1 squircle or a responsive 2×1 bar.

> Quiet Signal is an independent third-party project. It is **not** an official Tailscale
> application and is not affiliated with or endorsed by Tailscale Inc. The official Tailscale
> Android app remains the VPN implementation.

### Highlights

- **1×1 liquid-glass widget** — miniature switch in a tuned Deep Obsidian squircle, no text, no clipping.
- **Responsive 2×1 widget** — one provider, four breakpoints (minimum / normal / tall / wide + tall);
  extra space becomes breathing room, never a dashboard.
- **Connect / disconnect** — requests forwarded to the official Tailscale app, with real
  `Connecting…` / `Disconnecting…` transitions, per-request correlation ids and a 12 s safeguard timeout.
- **Three surfaces** — Deep Obsidian gloss, Graphite smoked glass, and **Clear** (a genuinely
  transparent housing: wallpaper visible, no dark fill, no heavy shadow, subtle rim plus switch only).
- **Specular Highlight Rim** ON/OFF, and **Tailscale Green Intensity** Restrained/Luminous — both
  applied to the ON / CONNECTING / DISCONNECTING switch states in *both* widget sizes.
- **Haptic Confirmation** and **Network Recheck** settings that really do what they say.
- **Settings persist** across app close, process recreation and reboot, and immediately re-render
  existing widgets.
- **Branding** — adaptive icon (foreground/background), monochrome icon for themed icons, and a
  Deep Obsidian splash screen.
- **Widget picker previews** showing the real widget design (Android 12+ `previewLayout`, plus a PNG
  fallback for older launchers).
- **No backend, no account, no analytics, no telemetry, no credentials — and no internet permission.**

### Installation

1. Install the official **Tailscale** app from Google Play and sign in.
2. Download `QuietSignal-v1.0.0.apk` below.
3. Allow installing from your browser/file manager when Android asks, then install.
4. Open **Quiet Signal** once and add the 1×1 / 2×1 widgets from your launcher's widget picker.

### Requirements

- **Android 8.0 (API 26) or newer**
- **Official Tailscale Android app** (`com.tailscale.ipn`) installed for connect/disconnect control

### Known limitation (please read)

VPN state is **best-effort evidence only**. Android exposes *whether* an Android VPN interface
exists — not which app owns it or whether it is Tailscale. Quiet Signal therefore shows honest
wording such as *“Android VPN active”*, *“VPN detected”*, *“VPN not detected”* or *“Unknown”*, and it
never claims authoritative Tailscale ownership state. Another VPN app produces the same evidence.
Connect/disconnect relies on Tailscale's own (unofficial) broadcast receiver actions, so a future
Tailscale release could require a Quiet Signal update.

**Default widget height.** A 2×1 widget placed at its default one-row size is shorter than 90 dp, so
it renders the compact single-line layout (brand only). Drag it one row taller to reveal the state line.

**Full details:** see [README.md](https://github.com/Devhoss/quiet-signal#readme) and
[CHANGELOG.md](https://github.com/Devhoss/quiet-signal/blob/main/CHANGELOG.md).

Licensed under [MIT](https://github.com/Devhoss/quiet-signal/blob/main/LICENSE).
