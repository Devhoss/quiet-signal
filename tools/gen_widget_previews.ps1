Add-Type -AssemblyName System.Drawing

$outDir = "E:\dev\tailscale-companion\app\src\main\res\drawable-nodpi"
if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Path $outDir | Out-Null }

function New-RoundRectPath([float]$x, [float]$y, [float]$w, [float]$h, [float]$r) {
    $path = [System.Drawing.Drawing2D.GraphicsPath]::new()
    $d = $r * 2
    $path.AddArc($x, $y, $d, $d, 180, 90)
    $path.AddArc($x + $w - $d, $y, $d, $d, 270, 90)
    $path.AddArc($x + $w - $d, $y + $h - $d, $d, $d, 0, 90)
    $path.AddArc($x, $y + $h - $d, $d, $d, 90, 90)
    $path.CloseFigure()
    return $path
}

# Deep Obsidian liquid-glass panel: mirrors widget_glass_obsidian.xml /
# widget_compact_glass_obsidian.xml (body gradient #161E26 -> #05070B,
# rim #2EFFFFFF, top specular arc #59FFFFFF).
function Draw-GlassPanel([System.Drawing.Graphics]$g, [int]$w, [int]$h, [float]$radius) {
    $path = New-RoundRectPath 0 0 $w $h $radius
    $rect = [System.Drawing.RectangleF]::new(0, 0, $w, $h)
    $body = [System.Drawing.Drawing2D.LinearGradientBrush]::new($rect,
        [System.Drawing.Color]::FromArgb(255, 22, 30, 38),
        [System.Drawing.Color]::FromArgb(255, 5, 7, 11), 45)
    $g.FillPath($body, $path)
    $body.Dispose()
    $stroke = [System.Drawing.Pen]::new([System.Drawing.Color]::FromArgb(46, 255, 255, 255), 4)
    $inner = New-RoundRectPath 2 2 ($w - 4) ($h - 4) ([Math]::Max(1, $radius - 2))
    $g.DrawPath($stroke, $inner)
    $inner.Dispose(); $stroke.Dispose(); $path.Dispose()
    $spec = [System.Drawing.Pen]::new([System.Drawing.Color]::FromArgb(89, 255, 255, 255), 4)
    $spec.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
    $spec.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
    # Keep the arc inside the corner curve: solve the rounded-rect boundary
    # at the line's y, then add a small margin.
    $lineY = 6
    $dy = [Math]::Max(0, $radius * $radius - ($radius - $lineY) * ($radius - $lineY))
    $edgeX = $radius - [Math]::Sqrt($dy)
    $lineStart = $edgeX + 24
    $g.DrawLine($spec, $lineStart, $lineY, $w - $lineStart, $lineY)
    $spec.Dispose()
}

# Liquid-glass switch ON/Connected: mirrors widget_switch_on.xml and
# widget_switch_mini_on.xml (glow #3386C156, dark green track, radiant
# green thumb with glint).
function Draw-SwitchOn([System.Drawing.Graphics]$g, [float]$x, [float]$y,
                       [int]$w, [int]$h, [hashtable]$geo) {
    $glowPath = New-RoundRectPath $x $y $w $h ($h / 2)
    $glow = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(51, 134, 193, 86))
    $g.FillPath($glow, $glowPath)
    $glow.Dispose(); $glowPath.Dispose()

    $in = $geo.TrackInset
    $trackPath = New-RoundRectPath ($x + $in) ($y + $in) ($w - 2 * $in) ($h - 2 * $in) (($h - 2 * $in) / 2)
    $trackRect = [System.Drawing.RectangleF]::new($x + $in, $y + $in, $w - 2 * $in, $h - 2 * $in)
    $track = [System.Drawing.Drawing2D.LinearGradientBrush]::new($trackRect,
        [System.Drawing.Color]::FromArgb(204, 10, 24, 12),
        [System.Drawing.Color]::FromArgb(140, 20, 42, 22), 90)
    $g.FillPath($track, $trackPath)
    $track.Dispose()
    $trackPen = [System.Drawing.Pen]::new([System.Drawing.Color]::FromArgb(140, 134, 193, 86), 4)
    $trackInner = New-RoundRectPath ($x + $in + 2) ($y + $in + 2) ($w - 2 * $in - 4) ($h - 2 * $in - 4) ([Math]::Max(1, ($h - 2 * $in) / 2 - 2))
    $g.DrawPath($trackPen, $trackInner)
    $trackPen.Dispose(); $trackInner.Dispose(); $trackPath.Dispose()

    $tx = $x + $geo.ThumbLeft; $ty = $y + $geo.ThumbTop; $td = $geo.ThumbDia
    $shadow = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(179, 5, 12, 3))
    $g.FillEllipse($shadow, $tx, $ty + 4, $td, $td)
    $shadow.Dispose()

    $tRect = [System.Drawing.RectangleF]::new($tx, $ty, $td, $td)
    $thumb = [System.Drawing.Drawing2D.LinearGradientBrush]::new($tRect,
        [System.Drawing.Color]::White, [System.Drawing.Color]::FromArgb(34, 53, 20), 45)
    $blend = [System.Drawing.Drawing2D.ColorBlend]::new()
    $blend.Colors = @([System.Drawing.Color]::White,
        [System.Drawing.Color]::FromArgb(120, 171, 72),
        [System.Drawing.Color]::FromArgb(34, 53, 20))
    $blend.Positions = @(0.0, 0.4, 1.0)
    $thumb.SetColorBlend($blend)
    $g.FillEllipse($thumb, $tRect)
    $thumb.Dispose()
    $tPen = [System.Drawing.Pen]::new([System.Drawing.Color]::FromArgb(128, 180, 245, 120), 4)
    $g.DrawEllipse($tPen, $tx + 2, $ty + 2, $td - 4, $td - 4)
    $tPen.Dispose()

    $gRect = [System.Drawing.RectangleF]::new($x + $geo.GlintLeft, $y + $geo.GlintTop, $geo.GlintW, $geo.GlintH)
    $glint = [System.Drawing.Drawing2D.LinearGradientBrush]::new($gRect,
        [System.Drawing.Color]::FromArgb(240, 255, 255, 255),
        [System.Drawing.Color]::FromArgb(51, 255, 255, 255), 90)
    $g.FillEllipse($glint, $gRect)
    $glint.Dispose()
}

function New-Graphics([System.Drawing.Bitmap]$bmp) {
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
    $g.Clear([System.Drawing.Color]::Transparent)
    return $g
}

# ---- 2x1 preview (1000x320 px ≈ 250x80dp at 4x) ----
$bmp = [System.Drawing.Bitmap]::new(1000, 320, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$g = New-Graphics $bmp
Draw-GlassPanel $g 1000 320 128

$titleFont = [System.Drawing.Font]::new("Segoe UI", [float]56, [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)
$stateFont = [System.Drawing.Font]::new("Segoe UI", [float]44, [System.Drawing.FontStyle]::Regular, [System.Drawing.GraphicsUnit]::Pixel)
$titleBrush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(240, 244, 242))
$stateBrush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(152, 162, 156))
$g.DrawString("Tailscale", $titleFont, $titleBrush, 80, 92)
$g.DrawString("Connected", $stateFont, $stateBrush, 80, 176)

Draw-SwitchOn $g 688 92 248 136 @{
    TrackInset = 4; ThumbLeft = 124; ThumbTop = 12; ThumbDia = 112;
    GlintLeft = 140; GlintTop = 20; GlintW = 56; GlintH = 40
}

$bmp.Save("$outDir\widget_preview_2x1.png", [System.Drawing.Imaging.ImageFormat]::Png)
$titleFont.Dispose(); $stateFont.Dispose(); $titleBrush.Dispose(); $stateBrush.Dispose()
$g.Dispose(); $bmp.Dispose()

# ---- 1x1 preview (512x512 px squircle + mini switch, no text) ----
$bmp = [System.Drawing.Bitmap]::new(512, 512, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$g = New-Graphics $bmp
Draw-GlassPanel $g 512 512 161

# Visible switch now ≈ 36/72 of the squircle width (generous drawable margin).
Draw-SwitchOn $g 128 186 256 140 @{
    TrackInset = 6; ThumbLeft = 134; ThumbTop = 17; ThumbDia = 105;
    GlintLeft = 151; GlintTop = 23; GlintW = 47; GlintH = 41
}

$bmp.Save("$outDir\widget_preview_1x1.png", [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $bmp.Dispose()

Get-ChildItem $outDir -Filter "widget_preview_*.png" | Select-Object Name, Length
