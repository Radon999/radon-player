@echo off
rem Radon Player, a test build: runs from this folder, installs nothing, and keeps its files in "test-data" here.
rem The first time, your installed Radon Player's settings and lists are copied in, so it looks like yours. Your
rem real ones are only read, never changed. To start over, delete the "test-data" folder.
setlocal
set "RADON_DATA=%~dp0test-data"
if exist "%RADON_DATA%\copied.txt" goto run
echo Getting the test build ready: copying your Radon Player settings. Your real ones are not changed.
echo This takes a minute, once.
if exist "%APPDATA%\Radon Player" robocopy "%APPDATA%\Radon Player" "%RADON_DATA%" /E /XD cache dj-voice-kokoro dj-voice-pocket /XF instance.key log.txt /NFL /NDL /NJH /NJS /NP >nul
if not exist "%RADON_DATA%" mkdir "%RADON_DATA%"
if exist "%~dp0voice" robocopy "%~dp0voice" "%RADON_DATA%\dj-voice-pocket" /E /NFL /NDL /NJH /NJS /NP >nul
echo done> "%RADON_DATA%\copied.txt"
:run
start "" "%~dp0Radon Player.exe"
