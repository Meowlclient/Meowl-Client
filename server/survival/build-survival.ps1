# Baut minecraft-server-setup.sh mit eingebettetem Web-Panel (panel.py) und kopiert es nach Downloads\Meowl-VPS.
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$tpl = [IO.File]::ReadAllText("$here\minecraft-server-setup.sh", [Text.Encoding]::UTF8)
$panel = [IO.File]::ReadAllText("$here\panel.py", [Text.Encoding]::UTF8).Replace("`r`n", "`n").TrimEnd("`n")
if ($panel -match "(?m)^PANELPY$") { throw "panel.py darf keine Zeile 'PANELPY' enthalten" }
$jar = [Convert]::ToBase64String([IO.File]::ReadAllBytes("$here\plugin\MeowlRules.jar"))
$b64 = ($jar -split "(.{76})" | Where-Object { $_ }) -join "`n"
$out = $tpl.Replace("`r`n", "`n").Replace("##PANEL_PY##", $panel).Replace("##MEOWLRULES_B64##", $b64)
New-Item -ItemType Directory -Force "$here\dist" | Out-Null
[IO.File]::WriteAllText("$here\dist\minecraft-server-setup.sh", $out, (New-Object Text.UTF8Encoding $false))
$vps = Join-Path $env:USERPROFILE "Downloads\Meowl-VPS"
if (Test-Path $vps) {
   Copy-Item "$here\dist\minecraft-server-setup.sh" $vps -Force
   Copy-Item "$here\Survival-Server-installieren.bat" $vps -Force
}
Write-Output "==> Fertig: dist\minecraft-server-setup.sh"
