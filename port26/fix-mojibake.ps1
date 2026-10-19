# Repariert doppelt kodierte Umlaute (UTF-8 als Windows-1252 gelesen und erneut als UTF-8 gespeichert).
# Zeilenweise: laesst sich eine Zeile verlustfrei zurueckwandeln, war sie doppelt kodiert.
param([Parameter(Mandatory)][string]$File)
$lines = [IO.File]::ReadAllLines($File, [Text.Encoding]::UTF8)
$cp = [Text.Encoding]::GetEncoding(1252, [Text.EncoderFallback]::ExceptionFallback, [Text.DecoderFallback]::ExceptionFallback)
$strict = New-Object Text.UTF8Encoding($false, $true)
$count = 0
for ($i = 0; $i -lt $lines.Length; $i++) {
   $l = $lines[$i]
   if ($l -notmatch '[^\x00-\x7F]') { continue }
   try {
      $r = $strict.GetString($cp.GetBytes($l))
      if ($r -ne $l) { $lines[$i] = $r; $count++ }
   } catch { }
}
if ($count -gt 0) {
   [IO.File]::WriteAllLines($File, $lines, (New-Object Text.UTF8Encoding $false))
   Write-Host ('{0} Zeilen repariert: {1}' -f $count, $File)
}
