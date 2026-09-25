# Release asset optimization.
#
# The design masters live in /assets (full-resolution PNGs, unchanged).
# The res/ copies are only ever drawn at icon/splash display sizes, so they are
# resampled here to the standard density targets to keep the APK small:
#   adaptive icon layers (108dp viewport) -> 432px (xxxhdpi x 4)
#   legacy launcher icon    (48dp)        -> 192px (xxxhdpi x 4)
#   splash brand artwork                  ->  512px tall
#
# Usage: powershell -File tools/optimize_release_assets.ps1

Add-Type -AssemblyName System.Drawing

$res = "E:\dev\tailscale-companion\app\src\main\res"

function Resize-Png([string]$relativePath, [int]$targetW, [int]$targetH) {
    $path = Join-Path $res $relativePath
    if (-not (Test-Path $path)) { "SKIP (missing) $relativePath"; return }

    $source = [System.Drawing.Image]::FromFile($path)
    try {
        if ($source.Width -le $targetW -and $source.Height -le $targetH) {
            "KEEP $relativePath ($($source.Width)x$($source.Height) already <= target)"
            return
        }
        $scale = [Math]::Min($targetW / $source.Width, $targetH / $source.Height)
        $w = [int][Math]::Round($source.Width * $scale)
        $h = [int][Math]::Round($source.Height * $scale)

        $bitmap = New-Object System.Drawing.Bitmap($w, $h, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
        $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
        $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
        $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
        $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
        $graphics.DrawImage($source, 0, 0, $w, $h)
        $graphics.Dispose()

        $temp = "$path.tmp.png"
        $bitmap.Save($temp, [System.Drawing.Imaging.ImageFormat]::Png)
        $bitmap.Dispose()
        $source.Dispose()
        Move-Item -Force $temp $path
        $before = (Get-Item $path).Length
        "RESIZED $relativePath -> ${w}x${h} ($([math]::Round($before / 1KB)) KB)"
    } finally {
        if ($source) { $source.Dispose() }
    }
}

Resize-Png "mipmap-xxxhdpi\ic_launcher.png" 192 192
Resize-Png "mipmap-xxxhdpi\ic_launcher_round.png" 192 192
Resize-Png "drawable-nodpi\ic_launcher_background.png" 432 432
Resize-Png "drawable-nodpi\ic_launcher_foreground.png" 432 432
Resize-Png "drawable-nodpi\ic_launcher_monochrome.png" 432 432
Resize-Png "drawable-nodpi\splash_brand.png" 512 512
