# A short song left to end by itself (crossfade on), then one under the DJ: the hand-overs, by what the app reports.
$dst = "C:\Users\Administrator\Desktop\rp-claude-test\test-data"
$rp = Join-Path $PSScriptRoot "rp.ps1"
function Ask { & $rp $dst @args }
function Stamp($t) { "{0:HH:mm:ss}  {1}" -f (Get-Date), $t }
$j = [IO.File]::ReadAllText("$dst\moods.json") | ConvertFrom-Json
$short = $j.songs.PSObject.Properties | Where-Object { $_.Value.len -ge 45 -and $_.Value.len -le 95 -and (Test-Path -LiteralPath $_.Name) } | Sort-Object { $_.Value.len } | Select-Object -First 3 -ExpandProperty Name
Stamp "playing $($short.Count) short songs, left alone"
Ask @short | Out-Null; Start-Sleep 3
$last = ""; $pos = ""; $changes = 0; $end = (Get-Date).AddSeconds(200)
while ((Get-Date) -lt $end -and $changes -lt 3) {
    $line = Ask status | Select-Object -First 1; $title = $line -replace " \(\d+:\d+ of.*$", ""
    if ($title -ne $last) { if ($last) { Stamp "   (the one before was last seen at $pos)"; $changes++ }; Stamp $line; $last = $title }
    if ($line -match "\((\d+:\d+ of \d+:\d+)\)") { $pos = $Matches[1] }
    Start-Sleep -Milliseconds 700
}
Ask pause | Out-Null
"--- Problems since the start"; Get-Content (Join-Path $dst "problems.txt") -Tail 30 | Where-Object { $_ -match "^\d+ \w+ 20\d\d" -and $_ -match "song change|stopped at|ran on|DJ:" } | Select-Object -Last 5
