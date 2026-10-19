# Erzeugt die 26.3-Version aus dem 1.21.11-Quellcode:
#   1. Quellcode und Ressourcen nach ..\Meowl-Client-26.3 kopieren
#   2. Umbenennungsregeln aus rules.txt anwenden (Zeilen "alt => neu", "re: muster => ersatz")
#   3. Ersatzdateien aus overlay\ darüberkopieren (Dateien mit tieferen API-Änderungen)
#   4. fabric.mod.json für 26.3 anpassen
param([switch]$Build)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$src = Join-Path $root 'project\src\main'
$dst = Join-Path (Split-Path -Parent $root) 'Meowl-Client-26.3\project\src\main'
$utf8 = New-Object Text.UTF8Encoding $false

if (Test-Path $dst) { Remove-Item -Recurse -Force $dst }
Copy-Item -Recurse $src $dst

# Regeln laden
$rules = @()
foreach ($line in [IO.File]::ReadAllLines((Join-Path $PSScriptRoot 'rules.txt'), [Text.Encoding]::UTF8)) {
   $l = $line.Trim()
   if ($l -eq '' -or $l.StartsWith('#')) { continue }
   $isRe = $l.StartsWith('re:')
   if ($isRe) { $l = $l.Substring(3).Trim() }
   $parts = $l -split ' => ', 2
   if ($parts.Count -ne 2) { Write-Warning "Regel unlesbar: $line"; continue }
   $rules += [pscustomobject]@{ Re = $isRe; From = $parts[0]; To = $parts[1] }
}

$files = Get-ChildItem (Join-Path $dst 'java') -Recurse -Filter *.java
foreach ($f in $files) {
   $t = [IO.File]::ReadAllText($f.FullName, [Text.Encoding]::UTF8)
   $orig = $t
   foreach ($r in $rules) {
      if ($r.Re) { $t = [regex]::Replace($t, $r.From, $r.To) } else { $t = $t.Replace($r.From, $r.To) }
   }
   if ($t -ne $orig) { [IO.File]::WriteAllText($f.FullName, $t, $utf8) }
}

# Ersatzdateien
$overlay = Join-Path $PSScriptRoot 'overlay'
if (Test-Path $overlay) {
   Get-ChildItem $overlay -Recurse -File | ForEach-Object {
      $rel = $_.FullName.Substring($overlay.Length + 1)
      $target = Join-Path $dst $rel
      New-Item -ItemType Directory -Force (Split-Path -Parent $target) | Out-Null
      Copy-Item -Force $_.FullName $target
   }
}

# Zusätzliche Mixins aus overlay in nebula.mixins.json eintragen
$mj = Join-Path $dst 'resources\nebula.mixins.json'
$mt = [IO.File]::ReadAllText($mj, [Text.Encoding]::UTF8)
$extra = Join-Path $overlay 'java\com\lennart\nebula\mixin'
if (Test-Path $extra) {
   foreach ($mf in Get-ChildItem $extra -Filter *.java) {
      $n = $mf.BaseName
      if ($mt -notmatch ('"' + $n + '"')) { $mt = $mt.Replace('"client": [', '"client": [' + "`n    `"$n`",") }
   }
}
[IO.File]::WriteAllText($mj, $mt, $utf8)

# Mod-Metadaten
$mod = Join-Path $dst 'resources\fabric.mod.json'
$m = [IO.File]::ReadAllText($mod, [Text.Encoding]::UTF8)
$m = $m.Replace('"minecraft": "1.21.11"', '"minecraft": "~26.3"').Replace('"java": ">=21"', '"java": ">=25"').Replace('"fabricloader": ">=0.18.0"', '"fabricloader": ">=0.19.0"')
[IO.File]::WriteAllText($mod, $m, $utf8)
Write-Host "26.3-Quellcode erzeugt: $($files.Count) Dateien"

if ($Build) {
   Push-Location (Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $dst)))
   cmd /c ".\gradlew.bat build -x test --console=plain -q 2>&1"
   Pop-Location
}
