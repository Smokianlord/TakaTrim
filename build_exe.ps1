# Builds dist\TakaTrim.exe (single-file, windowed) with the app icon embedded and bundled.
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

py -m PyInstaller --noconfirm --clean --onefile --noconsole `
    --name TakaTrim `
    --icon app_icon.ico `
    --add-data "app_icon.ico;." `
    --exclude-module tkinter `
    bkash_zero_balance_desktop_app.py

if ($LASTEXITCODE -ne 0) { throw "PyInstaller failed with exit code $LASTEXITCODE" }

# The released EXE lives in the repo's main folder (tracked in git).
Copy-Item dist\TakaTrim.exe .\TakaTrim.exe -Force
Get-Item .\TakaTrim.exe | Select-Object FullName, Length, LastWriteTime
