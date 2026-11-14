# Brings the test copy up to date with the newest compiled code from the checks, and starts it.
$dst = "C:\Users\Administrator\Desktop\rp-claude-test"
$g = "C:\Program Files\Git\cmd\git.exe"; $repo = "C:\Users\Administrator\Desktop\clauds folder\radon-player"
$rp = Join-Path $PSScriptRoot "rp.ps1"   # (the test copy is a copy of a test build's folder, with its own test-data: never the installed app)
if (Test-Path "$dst\test-data\instance.key") { try { & $rp "$dst\test-data" quit | Out-Null } catch {}; Start-Sleep 5 }
Get-Process javaw -ErrorAction SilentlyContinue | Where-Object { $_.Path -like "*zulu-21*" } | Stop-Process -Force -ErrorAction SilentlyContinue
& $g -C $repo fetch -q origin rebuild-ci
$tmp = "$dst\new-jars"
foreach ($n in "desktop.jar", "shared.jar", "ui-common-desktop.jar", "COMMIT") { Start-Process -FilePath $g -ArgumentList "-C", "`"$repo`"", "show", "FETCH_HEAD:jars/$n" -RedirectStandardOutput "$tmp\$n" -NoNewWindow -Wait }
Copy-Item "$tmp\desktop.jar" (Get-ChildItem "$dst\app\desktop-*.jar" | Where-Object { $_.Name -notlike "desktop-jvm*" } | Select-Object -First 1).FullName -Force
Copy-Item "$tmp\shared.jar" (Get-ChildItem "$dst\app\shared-*.jar" | Select-Object -First 1).FullName -Force
Copy-Item "$tmp\ui-common-desktop.jar" (Get-ChildItem "$dst\app\ui-common-desktop-*.jar" | Select-Object -First 1).FullName -Force
"code from commit " + (Get-Content "$tmp\COMMIT").Substring(0, 7)
Remove-Item "$dst\test-data\instance.key" -ErrorAction SilentlyContinue
$env:RADON_DATA = "$dst\test-data"
Start-Process -FilePath "C:\Program Files\Zulu\zulu-21\bin\javaw.exe" -ArgumentList "-cp", "`"$dst\app\*`"", "-Dcompose.application.resources.dir=`"$dst\app\resources`"", "-Dskiko.library.path=`"$dst\app`"", "-Dcompose.application.configure.swing.globals=true", "org.omarchy.music.desktop.MainKt" -RedirectStandardError "$dst\err.txt" -RedirectStandardOutput "$dst\out.txt" | Out-Null
for ($i = 0; $i -lt 25; $i++) { Start-Sleep -Seconds 2; if (Test-Path "$dst\test-data\instance.key") { break } }
Start-Sleep 4
& $rp "$dst\test-data" status
