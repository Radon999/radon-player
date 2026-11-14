$dst = "C:\Users\Administrator\Desktop\rp-claude-test\test-data"
$rp = Join-Path $PSScriptRoot "rp.ps1"
function Ask { & $rp $dst @args }
function Stamp($t) { "{0:HH:mm:ss}  {1}" -f (Get-Date), $t }
function Brief { $s = Ask status; $now = $s | Select-Object -First 1; $dj = ($s | Where-Object { $_ -like "DJ:*" }); if ($dj) { $dj = $dj.Substring(0, [Math]::Min(110, $dj.Length)) }; "$now  ||  $dj" }
Stamp "D1 skipping through the DJ's set and into its next one"
for ($k = 0; $k -lt 7; $k++) { Ask skip | Out-Null; Start-Sleep 4; Stamp (Brief) }
Stamp "D2 switch it up"
Ask dj switch | Out-Null; Start-Sleep 6; Stamp (Brief)
Stamp "D3 a song under the DJ left to play to its end; watching the change (up to 7 minutes)"
$first = (Ask status | Select-Object -First 1) -replace " \(\d+:\d+ of.*$", ""
$end = (Get-Date).AddMinutes(7); $lastPos = ""
while ((Get-Date) -lt $end) {
    $line = Ask status | Select-Object -First 1
    $title = $line -replace " \(\d+:\d+ of.*$", ""
    if ($title -ne $first) { Stamp "changed -> $line  (the one before was last seen at $lastPos)"; Start-Sleep 5; Stamp (Brief); break }
    if ($line -match "\((\d+:\d+ of \d+:\d+)\)") { $lastPos = $Matches[1] }
    Start-Sleep -Milliseconds 800
}
Stamp "D4 stop the DJ: the music goes on"
Ask dj off | Out-Null; Start-Sleep 3; Stamp (Brief)
Ask pause | Out-Null; Start-Sleep 2; Stamp (Brief)
"--- Problems written during this"
Get-Content (Join-Path $dst "problems.txt") -Tail 40 | Where-Object { $_ -match "song change|stopped|ran on|DJ:" } | Select-Object -Last 8
