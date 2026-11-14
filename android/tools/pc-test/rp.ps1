param([string]$data, [Parameter(ValueFromRemainingArguments = $true)][string[]]$words)
# Asks a running Radon Player (the one keeping its files in $data) to do something, the way a second start of the app
# does: its local socket, with the key only this account can read. Prints what it answers.
$key = (Get-Content (Join-Path $data "instance.key") -Raw).Trim()
$c = New-Object Net.Sockets.TcpClient
$c.Connect("127.0.0.1", 8896)
$c.ReceiveTimeout = 4000
$s = $c.GetStream()
$w = New-Object IO.StreamWriter($s, (New-Object Text.UTF8Encoding($false))); $w.NewLine = "`n"
$w.WriteLine($key); foreach ($x in $words) { $w.WriteLine($x) }; $w.WriteLine(""); $w.Flush()
$r = New-Object IO.StreamReader($s, [Text.Encoding]::UTF8)
try { while ($true) { $l = $r.ReadLine(); if ($null -eq $l -or $l -eq "ok") { break }; $l } } catch { "(no answer: $($_.Exception.Message))" }
$c.Close()
