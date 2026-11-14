# The owner's mood scan results as a plain table for the evaluation (read-only; written to the scratchpad only).
$p = Join-Path $env:APPDATA "Radon Player\moods.json"
$j = [IO.File]::ReadAllText($p) | ConvertFrom-Json
$out = New-Object System.Collections.Generic.List[string]
function Flat($o) { if (-not $o) { return "" }; (($o.PSObject.Properties | ForEach-Object { ($_.Name -replace "[\t|=]", " ") + "=" + $_.Value }) -join "|") }
$artists = @{}; foreach ($a in $j.artists.PSObject.Properties) { $artists[$a.Name] = Flat $a.Value }
foreach ($pr in $j.songs.PSObject.Properties) {
    $s = $pr.Value; if (-not $s.sound) { continue }
    $path = $pr.Name
    $artist = ""
    $m = [regex]::Match($path, "\\Artists\\([^\\]+)\\"); if ($m.Success) { $artist = $m.Groups[1].Value.ToLower() }
    if (-not $artists.ContainsKey($artist)) { $m2 = [regex]::Match([IO.Path]::GetFileNameWithoutExtension($path), " - (.+?) - "); if ($m2.Success) { $artist = $m2.Groups[1].Value.ToLower() } }
    $at = ""; if ($artists.ContainsKey($artist)) { $at = $artists[$artist] }
    $so = $s.sound
    $row = @([IO.Path]::GetFileNameWithoutExtension($path) -replace "\t", " "), $so.loud, $so.range, $so.bpm, $so.beat, $so.bright, $so.onsets, $so.major, $so.vary, $so.sections,
        (("" + $s.lyr) -replace "[\t\r\n]", " "), ("" + $s.wrote), ("" + $s.picked), (Flat $s.tags), $at
    $out.Add(($row -join "`t"))
}
[IO.File]::WriteAllLines((Join-Path $PSScriptRoot "songs.tsv"), $out, (New-Object Text.UTF8Encoding($false)))
"rows: $($out.Count); with artist tags: $(($out | Where-Object { ($_ -split "`t")[14] }).Count); picked: $(($out | Where-Object { ($_ -split "`t")[12] }).Count)"
