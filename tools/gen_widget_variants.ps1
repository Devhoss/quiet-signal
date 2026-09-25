$dr = "E:\dev\tailscale-companion\app\src\main\res\drawable"
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)

function Write-Dst([string]$dst, [string]$content) {
    [System.IO.File]::WriteAllText((Join-Path $dr $dst), $content, $utf8NoBom)
    'WROTE ' + $dst
}

function Add-Note([string]$xml, [string]$note) {
    $open = $xml.IndexOf('<layer-list')
    if ($open -lt 0) { return $xml }
    $gt = $xml.IndexOf('>', $open)
    if ($gt -lt 0) { return $xml }
    return $xml.Insert($gt + 1, "`r`n    <!-- " + $note + " -->")
}

# --- Specular rim OFF (flat): dim rim, no specular arc ---
function New-Flat([string]$src, [string]$note) {
    $c = Get-Content -Raw (Join-Path $dr $src)
    $c = $c.Replace('#2EFFFFFF', '#14FFFFFF')          # bright rim -> faint border
    $c = [regex]::Replace($c, '(?s)\s*<!-- Top-edge Specular.*?</item>', '')  # drop arc
    $c = Add-Note $c $note
    Write-Dst ($src -replace '\.xml$', '_flat.xml') $c
}

# --- Luminous green intensity: brighter glow + brighter track stroke ---
function New-Luminous([string]$src, [string]$note, [string[]]$map) {
    $c = Get-Content -Raw (Join-Path $dr $src)
    foreach ($pair in $map) {
        $p = $pair -split '=>', 2
        $c = $c.Replace($p[0], $p[1])
    }
    $c = Add-Note $c $note
    Write-Dst ($src -replace '\.xml$', '_luminous.xml') $c
}

New-Flat 'widget_glass_obsidian.xml' 'Specular rim OFF variant'
New-Flat 'widget_glass_graphite.xml' 'Specular rim OFF variant'
New-Flat 'widget_compact_glass_obsidian.xml' 'Specular rim OFF variant'
New-Flat 'widget_compact_glass_graphite.xml' 'Specular rim OFF variant'

# Disconnecting: stroke '#59F57C00' is replaced FIRST, then glow '#24F57C00'
# becomes '#59F57C00' - order matters to avoid double-substitution.
$onMap   = @('#8C86C156=>#B386C156', '#3386C156=>#6686C156')
$conMap  = @('#80FFB74D=>#B3FFB74D', '#2EADB74D=>#66ADB74D')
$disMap  = @('#59F57C00=>#8CF57C00', '#24F57C00=>#59F57C00')

New-Luminous 'widget_switch_on.xml'             'Luminous intensity variant' $onMap
New-Luminous 'widget_switch_connecting.xml'     'Luminous intensity variant' $conMap
New-Luminous 'widget_switch_disconnecting.xml'  'Luminous intensity variant' $disMap
New-Luminous 'widget_switch_mini_on.xml'             'Luminous intensity variant' $onMap
New-Luminous 'widget_switch_mini_connecting.xml'     'Luminous intensity variant' $conMap
New-Luminous 'widget_switch_mini_disconnecting.xml'  'Luminous intensity variant' $disMap
