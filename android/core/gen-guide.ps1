# The same as gen-guide.py, for a Windows PC without Python: FEATURES.md and CHANGELOG.md ->
# common/org/omarchy/music/ui/FeatureGuide.kt (the Features and Changelog tabs show them). Run after each edit:
#   powershell -NoProfile -ExecutionPolicy Bypass -File android\core\gen-guide.ps1
$ErrorActionPreference = 'Stop'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$enc = New-Object System.Text.UTF8Encoding($false)
function Lit([string]$c) { '"' + $c.Replace('\', '\\').Replace('"', '\"').Replace('$', '\$') + '"' }
function Block([string]$name, [string]$doc, [string]$md) {
    $text = [System.IO.File]::ReadAllText((Join-Path $here $md), $enc).Replace("`r`n", "`n")
    $lines = $text.Split("`n")
    $body = ($lines | ForEach-Object { Lit $_ }) -join ",`n    "
    "/** $doc */`nval ${name}: String = listOf(`n    $body,`n).joinToString(`"\n`")`n"
}
$out = "package org.omarchy.music.ui`n`n// Made from core/FEATURES.md and core/CHANGELOG.md by core/gen-guide.py: edit those, then run it.`n`n" +
    (Block 'FEATURE_GUIDE' 'Everything the app does (the Features tab).' 'FEATURES.md') + "`n" +
    (Block 'CHANGELOG' 'What each update brought, the newest first (the Changelog tab).' 'CHANGELOG.md')
[System.IO.File]::WriteAllText((Join-Path $here 'common\org\omarchy\music\ui\FeatureGuide.kt'), $out, $enc)
"FeatureGuide.kt written: $($out.Split("`n").Count) lines"
