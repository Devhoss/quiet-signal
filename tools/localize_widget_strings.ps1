# Replace the remaining hardcoded widget strings with @string resources so the
# lint "HardcodedText" findings are resolved without changing any rendered text.
#
# Usage: powershell -File tools/localize_widget_strings.ps1

$res = "E:\dev\tailscale-companion\app\src\main\res"
$targets = @(
    "layout\widget_tailscale.xml",
    "layout\widget_tailscale_minimal.xml",
    "layout\widget_tailscale_tall.xml",
    "layout\widget_tailscale_wide_tall.xml",
    "layout\widget_tailscale_preview.xml",
    "layout\widget_tailscale_compact.xml",
    "layout\widget_tailscale_compact_preview.xml"
)

$swaps = @(
    @{ From = 'android:text="Tailscale"';                 To = 'android:text="@string/widget_title"' },
    @{ From = 'android:text="Disconnected"';              To = 'android:text="@string/widget_state_default"' },
    @{ From = 'android:text="Connected"';                 To = 'android:text="@string/widget_preview_state_connected"' },
    @{ From = 'android:contentDescription="Toggle Tailscale VPN"'; To = 'android:contentDescription="@string/widget_toggle_description"' },
    @{ From = 'android:contentDescription="Connected"';   To = 'android:contentDescription="@string/widget_preview_state_connected"' }
)

foreach ($relative in $targets) {
    $path = Join-Path $res $relative
    $text = [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)
    $changes = 0
    foreach ($swap in $swaps) {
        if ($text.Contains($swap.From)) {
            $count = ([regex]::Matches($text, [regex]::Escape($swap.From))).Count
            $text = $text.Replace($swap.From, $swap.To)
            $changes += $count
        }
    }
    if ($changes -gt 0) {
        [System.IO.File]::WriteAllText($path, $text, (New-Object System.Text.UTF8Encoding($false)))
        "UPDATED $relative ($changes replacement(s))"
    } else {
        "unchanged $relative"
    }
}
