package com.lennart.nebula.module.hud;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lennart.nebula.NebulaClient;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lyrics zum laufenden Spotify-Song: Text kommt (zeitgenau) von LRCLIB (freie, öffentliche Lyrics-Datenbank), die
 * Abspielposition unter Windows aus der Windows-Mediensteuerung (dieselbe, die die Lautstärke-Anzeige nutzt).
 */
public final class SpotifyLyrics {
   public record Line(long timeMs, String text) {
   }

   /** Ergebnis für einen Song: zeitgenaue Zeilen, sonst einfache Zeilen ohne Zeiten, sonst leer. */
   public record Lyrics(List<Line> lines, boolean synced) {
      static final Lyrics NONE = new Lyrics(List.of(), false);
   }

   private static final Pattern LRC = Pattern.compile("\\[(\\d+):(\\d+(?:\\.\\d+)?)\\]\\s?(.*)");
   private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6L)).build();
   private final Map<String, Lyrics> cache = new ConcurrentHashMap<>();
   private final Map<String, Boolean> loading = new ConcurrentHashMap<>();
   // Zeitleiste aus der Windows-Mediensteuerung
   private volatile Process process;
   private volatile String title = "";
   private volatile String artist = "";
   private volatile boolean playing;
   private volatile long position;
   private volatile long updatedAt;
   private volatile long duration;
   private volatile long lastLine;

   // ================================================================== Zeitleiste

   public boolean windows() {
      return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
   }

   /** Startet (bzw. hält am Leben) den kleinen Hintergrund-Prozess, der die Spotify-Position liefert. */
   public void ensureTimeline() {
      if (!this.windows()) {
         return;
      }

      Process p = this.process;
      if (p != null && p.isAlive()) {
         return;
      }

      try {
         Path script = Files.createTempFile("meowl-spotify", ".ps1");
         Files.writeString(script, "﻿" + SCRIPT, StandardCharsets.UTF_8);
         script.toFile().deleteOnExit();
         Process np = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-File", script.toString(), String.valueOf(ProcessHandle.current().pid()))
            .redirectErrorStream(true)
            .start();
         this.process = np;
         Thread t = new Thread(() -> this.read(np), "Meowl-Spotify-Zeit");
         t.setDaemon(true);
         t.start();
      } catch (Exception var5) {
         NebulaClient.LOGGER.debug("Spotify-Zeitleiste nicht startbar: {}", var5.toString());
      }
   }

   private void read(Process p) {
      try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
         String line;
         while ((line = r.readLine()) != null) {
            String[] f = line.split("\\|", -1);
            if (f.length >= 6) {
               try {
                  this.title = f[0].trim();
                  this.artist = f[1].trim();
                  this.playing = f[2].trim().equalsIgnoreCase("Playing");
                  this.position = Long.parseLong(f[3].trim());
                  this.updatedAt = Long.parseLong(f[4].trim());
                  this.duration = Long.parseLong(f[5].trim());
                  this.lastLine = System.currentTimeMillis();
               } catch (NumberFormatException var7) {
               }
            } else if (line.trim().equals("-")) {
               this.title = "";
               this.lastLine = System.currentTimeMillis();
            }
         }
      } catch (Exception var9) {
      }
   }

   public void stop() {
      Process p = this.process;
      this.process = null;
      if (p != null) {
         p.destroy();
      }
   }

   public boolean hasTimeline() {
      return System.currentTimeMillis() - this.lastLine < 4000L && !this.title.isEmpty();
   }

   /** Läuft gerade ein Lied (nicht pausiert)? */
   public boolean playing() {
      return this.hasTimeline() && this.playing;
   }

   public String title() {
      return this.title;
   }

   public String artist() {
      return this.artist;
   }

   public long durationMs() {
      return this.duration;
   }

   /** Aktuelle Position im Song (läuft zwischen den Meldungen weiter). */
   public long positionMs() {
      long p = this.position;
      if (this.playing && this.updatedAt > 0L) {
         p += System.currentTimeMillis() - this.updatedAt;
      }

      return Math.max(0L, this.duration > 0L ? Math.min(p, this.duration) : p);
   }

   // ================================================================== Text

   /** Lyrics eines Songs; lädt im Hintergrund und liefert bis dahin null. */
   public Lyrics lyrics(String title, String artist, long durationMs) {
      String key = (artist + "\n" + title).toLowerCase(Locale.ROOT);
      Lyrics l = this.cache.get(key);
      if (l != null) {
         return l;
      }

      if (this.loading.putIfAbsent(key, Boolean.TRUE) == null) {
         Thread t = new Thread(() -> {
            try {
               this.cache.put(key, this.fetch(title, artist, durationMs));
            } catch (Exception var10) {
               NebulaClient.LOGGER.debug("Lyrics nicht ladbar: {}", var10.toString());
               this.cache.put(key, Lyrics.NONE);
            } finally {
               this.loading.remove(key);
            }
         }, "Meowl-Lyrics");
         t.setDaemon(true);
         t.start();
      }

      return null;
   }

   private Lyrics fetch(String title, String artist, long durationMs) throws Exception {
      String url = "https://lrclib.net/api/search?track_name=" + URLEncoder.encode(title, StandardCharsets.UTF_8) + "&artist_name=" + URLEncoder.encode(artist, StandardCharsets.UTF_8);
      String body = null;

      for (int attempt = 0; attempt < 3 && body == null; attempt++) {
         var res = this.http
            .send(HttpRequest.newBuilder(URI.create(url)).header("User-Agent", "MeowlClient (Minecraft-Mod, Lyrics-Overlay)").timeout(Duration.ofSeconds(10L)).build(), BodyHandlers.ofString());
         if (res.statusCode() == 200) {
            body = res.body();
         } else {
            Thread.sleep(1500L);
         }
      }

      if (body == null) {
         return Lyrics.NONE;
      }

      JsonElement root = JsonParser.parseString(body);
      if (!root.isJsonArray()) {
         return Lyrics.NONE;
      }

      JsonArray arr = root.getAsJsonArray();
      JsonObject best = null;
      double bestScore = Double.MAX_VALUE;
      JsonObject plain = null;

      for (JsonElement e : arr) {
         if (!e.isJsonObject()) {
            continue;
         }

         JsonObject o = e.getAsJsonObject();
         boolean synced = o.has("syncedLyrics") && o.get("syncedLyrics").isJsonPrimitive() && !o.get("syncedLyrics").getAsString().isBlank();
         if (!synced) {
            if (plain == null && o.has("plainLyrics") && o.get("plainLyrics").isJsonPrimitive()) {
               plain = o;
            }

            continue;
         }

         double d = o.has("duration") && o.get("duration").isJsonPrimitive() ? o.get("duration").getAsDouble() * 1000.0 : 0.0;
         double score = durationMs > 0L && d > 0.0 ? Math.abs(d - durationMs) : 0.0;
         if (score < bestScore) {
            bestScore = score;
            best = o;
         }
      }

      if (best != null) {
         List<Line> lines = new ArrayList<>();

         for (String raw : best.get("syncedLyrics").getAsString().split("\n")) {
            Matcher m = LRC.matcher(raw.trim());
            if (m.matches()) {
               long ms = Long.parseLong(m.group(1)) * 60000L + Math.round(Double.parseDouble(m.group(2)) * 1000.0);
               lines.add(new Line(ms, m.group(3).trim()));
            }
         }

         Collections.sort(lines, (a, b) -> Long.compare(a.timeMs(), b.timeMs()));
         return new Lyrics(lines, true);
      } else if (plain != null) {
         List<Line> lines = new ArrayList<>();

         for (String raw : plain.get("plainLyrics").getAsString().split("\n")) {
            lines.add(new Line(-1L, raw.trim()));
         }

         return new Lyrics(lines, false);
      } else {
         return Lyrics.NONE;
      }
   }

   /** Index der Zeile, die gerade gesungen wird (-1 = vor der ersten Zeile). */
   public static int indexAt(List<Line> lines, long ms) {
      int idx = -1;

      for (int i = 0; i < lines.size(); i++) {
         if (lines.get(i).timeMs() <= ms) {
            idx = i;
         } else {
            break;
         }
      }

      return idx;
   }

   /** PowerShell: liest einmal pro Sekunde Titel, Status und Position der Spotify-Sitzung aus der Windows-Mediensteuerung. */
   private static final String SCRIPT = String.join("\n",
      "param($owner)",
      "$ErrorActionPreference = 'SilentlyContinue'",
      "Add-Type -AssemblyName System.Runtime.WindowsRuntime",
      "$asTask = ([System.WindowsRuntimeSystemExtensions].GetMethods() | ? { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]",
      "function Await($op, [Type]$t) { $m = $asTask.MakeGenericMethod($t); $task = $m.Invoke($null, @($op)); $task.Wait(-1) | Out-Null; $task.Result }",
      "[Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager,Windows.Media.Control,ContentType=WindowsRuntime] | Out-Null",
      "$mgr = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])",
      "[Console]::OutputEncoding = [Text.Encoding]::UTF8",
      "while ($true) {",
      "  if ($owner -and -not (Get-Process -Id $owner)) { exit }",
      "  $s = $mgr.GetSessions() | ? { $_.SourceAppUserModelId -like '*Spotify*' } | select -First 1",
      "  if ($s) {",
      "    $tl = $s.GetTimelineProperties(); $pb = $s.GetPlaybackInfo()",
      "    $props = Await ($s.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])",
      "    $t = ($props.Title -replace '\\|', '/'); $a = ($props.Artist -replace '\\|', '/')",
      "    [Console]::Out.WriteLine(('{0}|{1}|{2}|{3}|{4}|{5}' -f $t, $a, $pb.PlaybackStatus, [long]$tl.Position.TotalMilliseconds, $tl.LastUpdatedTime.ToUnixTimeMilliseconds(), [long]$tl.EndTime.TotalMilliseconds))",
      "  } else { [Console]::Out.WriteLine('-') }",
      "  [Console]::Out.Flush()",
      "  Start-Sleep -Milliseconds 1000",
      "}"
   );
}
