# Baut den Meowl Server und bettet ihn in dist\meowl-server-setup.sh ein (Windows-Version von build-server.sh).
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
$main = Get-Content -Raw -Encoding UTF8 'src\com\lennart\meowlserver\MeowlServer.java'
$version = [regex]::Match($main, 'VERSION = "([0-9.]+)"').Groups[1].Value
if (Test-Path build) { Remove-Item -Recurse -Force build }
New-Item -ItemType Directory -Force build, dist | Out-Null
$files = Get-ChildItem -Recurse src -Filter *.java | ForEach-Object FullName
& javac -g -nowarn --release 21 -encoding UTF-8 -d build $files
if ($LASTEXITCODE -ne 0) { throw 'Kompilieren fehlgeschlagen' }
[IO.File]::WriteAllText("$PSScriptRoot\build\MANIFEST.MF", "Manifest-Version: 1.0`nMain-Class: com.lennart.meowlserver.MeowlServer`n")
Push-Location build
& jar --create --file ..\dist\meowl-server.jar --manifest MANIFEST.MF com
Pop-Location
if ($LASTEXITCODE -ne 0) { throw 'jar fehlgeschlagen' }
# Skript zusammensetzen: Kopf + base64(jar) + Ende, mit Unix-Zeilenenden
$head = (Get-Content -Raw -Encoding UTF8 'setup-head.sh') -replace '(?m)^MEOWL_VERSION=".*"', "MEOWL_VERSION=`"$version`""
$b64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes("$PSScriptRoot\dist\meowl-server.jar"))
$lines = for ($i = 0; $i -lt $b64.Length; $i += 76) { $b64.Substring($i, [Math]::Min(76, $b64.Length - $i)) }
$tail = Get-Content -Raw -Encoding UTF8 'setup-tail.sh'
$all = ($head.TrimEnd("`r", "`n") + "`n" + ($lines -join "`n") + "`n" + $tail) -replace "`r`n", "`n"
[IO.File]::WriteAllText("$PSScriptRoot\dist\meowl-server-setup.sh", $all, (New-Object Text.UTF8Encoding $false))
Write-Host "==> Fertig: dist\meowl-server-setup.sh (Server $version)"
