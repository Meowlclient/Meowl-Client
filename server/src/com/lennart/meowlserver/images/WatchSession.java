package com.lennart.meowlserver.images;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Gemeinsame Wiedergabe eines Videos/Streams auf dem Meowl Server: yt-dlp lädt, ffmpeg wandelt in JPEG-Bilder (stdout)
 * und Ton als Mono-PCM (stderr). Eine eigene Abspieluhr gibt Bilder und Ton in Echtzeit frei und lässt sich pausieren.
 * Alle Zuschauer holen sich dasselbe aktuelle Bild und denselben Ton – so läuft es bei allen gleichzeitig.
 */
public final class WatchSession {
   private static final Logger LOG = Logger.getLogger("Watch");
   public static final int AUDIO_RATE = 44100;
   public static final int FPS = 24;
   /** 0,1 s Ton pro Stück (Mono, 16 Bit). */
   private static final int AUDIO_CHUNK = AUDIO_RATE / 10 * 2;
   private static final int KEEP_AUDIO = 60;
   /** Bis 720p mit getrenntem Bild/Ton (yt-dlp fügt zusammen) – sonst ein fertiger Datenstrom. */
   private static final String FORMAT_HQ = "bv*[height<=720][fps<=30]+ba/b[height<=720]/b";
   private static final String FORMAT = "b[height<=720]/b/bv*[height<=720]/bv*";
   /** Optionale YouTube-Cookies (Netscape-Format) gegen die Bot-Sperre für Rechenzentrums-IPs. */
   public static volatile java.nio.file.Path cookies;
   private final String url;
   private volatile Process ffmpeg;
   private volatile Process loader;
   private volatile boolean stopped;
   private volatile String status = "Lädt …";
   private volatile byte[] frame;
   private volatile long frameSeq;
   private final Deque<long[]> audioIndex = new ArrayDeque<>();
   private final Deque<byte[]> audio = new ArrayDeque<>();
   private long audioSeq;
   // Abspieluhr (Nanosekunden Medienzeit); läuft ab dem ersten Bild/Ton, steht während der Pause
   private final Object clock = new Object();
   private boolean clockStarted;
   private boolean paused;
   private long played;
   private long runningSince = -1L;

   private static volatile boolean keeperStarted;

   /**
    * Hält die YouTube-Anmeldung frisch: YouTube erneuert die Cookies laufend, yt-dlp schreibt die neue Fassung zurück.
    * Alle 6 Stunden eine kurze Abfrage (ohne Download), damit die Cookies auch ohne Zuschauer nicht veralten.
    */
   public static synchronized void startCookieKeeper() {
      if (keeperStarted) {
         return;
      }

      keeperStarted = true;
      Thread t = new Thread(() -> {
         while (true) {
            try {
               Thread.sleep(6L * 3600L * 1000L);
               java.nio.file.Path c = cookies;
               if (c != null && java.nio.file.Files.isRegularFile(c)) {
                  Process p = new ProcessBuilder(
                        "yt-dlp", "--cookies", c.toString(), "--skip-download", "--no-playlist", "--quiet", "--no-warnings",
                        "https://www.youtube.com/watch?v=jNQXAC9IVRw"
                     )
                     .redirectErrorStream(true)
                     .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                     .start();
                  if (!p.waitFor(120L, TimeUnit.SECONDS)) {
                     p.destroy();
                  }
               }
            } catch (InterruptedException var3) {
               return;
            } catch (Exception var4) {
               LOG.warning("Cookies auffrischen fehlgeschlagen: " + var4);
            }
         }
      }, "Meowl-Cookies");
      t.setDaemon(true);
      t.start();
   }
   public WatchSession(String url) {
      this.url = url;
      Thread t = new Thread(this::run, "Meowl-Watch");
      t.setDaemon(true);
      t.start();
   }

   public String status() {
      return this.paused ? "⏸ Pausiert" : this.status;
   }

   public boolean paused() {
      return this.paused;
   }

   public void setPaused(boolean p) {
      synchronized (this.clock) {
         if (p == this.paused) {
            return;
         }

         this.paused = p;
         if (this.clockStarted) {
            if (p) {
               this.played += System.nanoTime() - this.runningSince;
               this.runningSince = -1L;
            } else {
               this.runningSince = System.nanoTime();
            }
         }
      }
   }

   private void startClock() {
      synchronized (this.clock) {
         if (!this.clockStarted) {
            this.clockStarted = true;
            if (!this.paused) {
               this.runningSince = System.nanoTime();
            }
         }
      }
   }

   private long mediaNanos() {
      synchronized (this.clock) {
         return this.played + (this.runningSince < 0L ? 0L : System.nanoTime() - this.runningSince);
      }
   }

   /** Wartet, bis die Abspieluhr {@code t} erreicht (pausiert solange); false, wenn gestoppt. */
   private boolean waitUntil(long t) {
      try {
         while (!this.stopped) {
            long d = t - this.mediaNanos();
            if (d <= 0L) {
               return true;
            }

            Thread.sleep(Math.max(1L, Math.min(50L, d / 1000000L)));
         }
      } catch (InterruptedException var5) {
      }

      return false;
   }

   public synchronized void stop() {
      this.stopped = true;
      this.audio.clear();
      this.audioIndex.clear();
      this.frame = null;
      for (Process p : new Process[]{this.ffmpeg, this.loader}) {
         if (p != null) {
            p.descendants().forEach(ProcessHandle::destroy);
            p.destroy();
         }
      }
   }

   private void run() {
      try {
         List<String> args = this.resolve();
         if (this.stopped || args == null) {
            return;
         }

         String err = this.play(args, FORMAT_HQ, true);
         if (this.stopped) {
            return;
         }

         if (this.frameSeq == 0L) {
            // Zusammenfügen ging nicht: fertigen Datenstrom (meist 360p) nehmen
            LOG.warning("HQ ohne Bild, versuche einfachen Datenstrom: " + err);
            err = this.play(args, FORMAT, false);
         }

         if (!this.stopped) {
            if (this.frameSeq == 0L) {
               LOG.warning("Wiedergabe ohne Bild: " + err);
               this.status = err.isEmpty() ? "Video konnte nicht abgespielt werden" : reason(err);
            } else {
               this.status = "Video zu Ende";
            }
         }
      } catch (Exception var5) {
         if (!this.stopped) {
            LOG.warning("Wiedergabe fehlgeschlagen: " + var5);
            this.status = String.valueOf(var5.getMessage()).contains("yt-dlp") ? "yt-dlp fehlt auf dem Server" : String.valueOf(var5.getMessage()).contains("ffmpeg") ? "ffmpeg fehlt auf dem Server" : "Wiedergabe fehlgeschlagen";
         }
      }
   }

   /** yt-dlp lädt (mit Kopfzeilen/Cookies, die YouTube verlangt) und schreibt direkt in ffmpeg. Liefert yt-dlps Fehlertext. */
   private String play(List<String> args, String format, boolean merge) throws IOException, InterruptedException {
      List<String> dl = new ArrayList<>(List.of("yt-dlp", "--no-playlist", "--no-warnings", "--no-progress", "--socket-timeout", "15"));
      dl.addAll(args);
      if (merge) {
         dl.addAll(List.of("--merge-output-format", "mkv"));
      }

      dl.addAll(List.of("-f", format, "-o", "-", this.url));
      Process y = new ProcessBuilder(dl).start();
      this.loader = y;
      if (this.stopped) {
         // während des Ladens abgenommen: nichts mehr abspielen
         y.descendants().forEach(ProcessHandle::destroy);
         y.destroy();
         return "";
      }

      ByteArrayOutputStream yErr = new ByteArrayOutputStream();
      Thread ye = new Thread(() -> {
         try {
            y.getErrorStream().transferTo(yErr);
         } catch (IOException var3) {
         }
      }, "Meowl-Watch-Log");
      ye.setDaemon(true);
      ye.start();
      List<String> cmd = new ArrayList<>(List.of("ffmpeg", "-hide_banner", "-loglevel", "quiet", "-i", "pipe:0"));
      cmd.addAll(List.of(
         "-map", "0:v:0", "-vf", "fps=" + FPS + ",scale=854:480:force_original_aspect_ratio=decrease:flags=lanczos,pad=854:480:(ow-iw)/2:(oh-ih)/2",
         "-c:v", "mjpeg", "-q:v", "4", "-f", "image2pipe", "pipe:1",
         "-map", "0:a:0?", "-ac", "1", "-ar", String.valueOf(AUDIO_RATE), "-f", "s16le", "pipe:2"
      ));
      Process p = new ProcessBuilder(cmd).start();
      this.ffmpeg = p;
      if (this.stopped) {
         p.destroy();
         y.descendants().forEach(ProcessHandle::destroy);
         y.destroy();
         return "";
      }

      this.status = "";
      Thread pump = new Thread(() -> {
         try (var in = y.getInputStream(); var out = p.getOutputStream()) {
            in.transferTo(out);
         } catch (IOException var5) {
         }
      }, "Meowl-Watch-Daten");
      pump.setDaemon(true);
      pump.start();
      Thread audioReader = new Thread(() -> this.readAudio(p.getErrorStream()), "Meowl-Watch-Ton");
      audioReader.setDaemon(true);
      audioReader.start();
      this.readFrames(p.getInputStream());
      p.waitFor(5L, TimeUnit.SECONDS);
      p.destroy();
      y.destroy();
      ye.join(2000L);
      return yErr.toString(StandardCharsets.UTF_8).trim();
   }

   /** Prüft, mit welchem YouTube-Zugang das Video abrufbar ist; liefert die passenden Zusatz-Argumente oder null. */
   private List<String> resolve() throws IOException, InterruptedException {
      String[][] tries = {
         {},
         {"--extractor-args", "youtube:player_client=android_vr,tv,web_safari"},
         {"--extractor-args", "youtube:player_client=ios,mweb"}
      };
      String err = "";
      for (String[] extra : tries) {
         if (this.stopped) {
            break;
         }

         List<String> args = new ArrayList<>(List.of(extra));
         java.nio.file.Path c = cookies;
         if (c != null && java.nio.file.Files.isRegularFile(c)) {
            args.addAll(List.of("--cookies", c.toString()));
         }

         // ohne --no-warnings: yt-dlp warnt hier, wenn die Cookies abgelaufen sind
         List<String> cmd = new ArrayList<>(List.of("yt-dlp", "--no-playlist", "--socket-timeout", "15", "-g"));
         cmd.addAll(args);
         cmd.addAll(List.of("-f", FORMAT_HQ, this.url));
         Process p = new ProcessBuilder(cmd).start();
         // stderr parallel lesen, sonst kann yt-dlp hängen bleiben
         ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
         Thread et = new Thread(() -> {
            try {
               p.getErrorStream().transferTo(errBuf);
            } catch (IOException var3) {
            }
         });
         et.setDaemon(true);
         et.start();
         String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
         p.waitFor(60L, TimeUnit.SECONDS);
         et.join(2000L);
         if (out.lines().anyMatch(l -> l.startsWith("http"))) {
            return args;
         }

         err = errBuf.toString(StandardCharsets.UTF_8).trim();
         LOG.warning("yt-dlp " + String.join(" ", extra) + ": " + err);
      }

      this.status = reason(err);
      return null;
   }
   /** Kurzer, verständlicher Grund für den Bildschirm. */
   private static String reason(String err) {
      String e = err.toLowerCase();
      java.nio.file.Path c = cookies;
      boolean haveCookies = c != null && java.nio.file.Files.isRegularFile(c);
      if (e.contains("no longer valid") || haveCookies && (e.contains("sign in") || e.contains("bot"))) {
         return "YouTube-Cookies abgelaufen – neu exportieren";
      } else if (e.contains("sign in") || e.contains("bot")) {
         return "YouTube blockiert den Server";
      } else if (e.contains("private") || e.contains("unavailable") || e.contains("removed")) {
         return "Video nicht verfügbar";
      } else if (e.contains("offline") || e.contains("not currently live")) {
         return "Stream ist offline";
      } else if (e.contains("cannot run program") || e.contains("not found")) {
         return "yt-dlp fehlt auf dem Server";
      } else {
         String last = "";
         for (String line : err.split("\\R")) {
            if (line.contains("ERROR")) {
               last = line.replaceFirst(".*ERROR:\\s*(\\[[^]]*]\\s*)?([\\w-]+:\\s*)?", "");
            }
         }

         return last.isEmpty() ? "Video nicht abrufbar" : (last.length() > 48 ? last.substring(0, 47) + "…" : last);
      }
   }
   /** JPEG-Bilder aus dem Datenstrom schneiden (Start FFD8, Ende FFD9) und im Takt der Abspieluhr freigeben. */
   private void readFrames(InputStream in) throws IOException {
      ByteArrayOutputStream cur = new ByteArrayOutputStream(96 * 1024);
      int prev = -1;
      boolean inside = false;
      long count = 0L;
      byte[] buf = new byte[32768];
      int n;
      while (!this.stopped && (n = in.read(buf)) > 0) {
         for (int i = 0; i < n; i++) {
            int b = buf[i] & 0xFF;
            if (!inside) {
               if (prev == 0xFF && b == 0xD8) {
                  inside = true;
                  cur.reset();
                  cur.write(0xFF);
                  cur.write(0xD8);
               }
            } else {
               cur.write(b);
               if (prev == 0xFF && b == 0xD9) {
                  inside = false;
                  if (cur.size() <= 400000) {
                     this.startClock();
                     if (!this.waitUntil(count * 1000000000L / FPS)) {
                        return;
                     }

                     count++;
                     this.frame = cur.toByteArray();
                     this.frameSeq++;
                  }
               }
            }

            prev = b;
         }
      }
   }

   private void readAudio(InputStream in) {
      byte[] chunk = new byte[AUDIO_CHUNK];
      long count = 0L;
      try {
         while (!this.stopped) {
            int off = 0;
            while (off < chunk.length) {
               int n = in.read(chunk, off, chunk.length - off);
               if (n < 0) {
                  return;
               }

               off += n;
            }

            this.startClock();
            if (!this.waitUntil(count * 100000000L)) {
               return;
            }

            count++;
            synchronized (this) {
               this.audioSeq++;
               this.audio.addLast(chunk.clone());
               this.audioIndex.addLast(new long[]{this.audioSeq});
               while (this.audio.size() > KEEP_AUDIO) {
                  this.audio.removeFirst();
                  this.audioIndex.removeFirst();
               }
            }
         }
      } catch (IOException var5) {
      }
   }
   public byte[] frame() {
      return this.frame;
   }

   public long frameSeq() {
      return this.frameSeq;
   }

   /** Ton nach {@code after} (höchstens 1 s); beim Einstieg nur das neueste Stück. Liefert {letzteNummer, Daten}. */
   public synchronized Object[] audioAfter(long after) {
      if (this.audio.isEmpty()) {
         return null;
      }

      ByteArrayOutputStream out = new ByteArrayOutputStream();
      long last = after;
      var it = this.audio.iterator();
      var idx = this.audioIndex.iterator();
      boolean start = after <= 0L || after < this.audioIndex.peekFirst()[0] - 1L;
      long newest = this.audioIndex.peekLast()[0];
      while (it.hasNext()) {
         byte[] c = it.next();
         long s = idx.next()[0];
         if (start ? s >= newest - 2L : s > after) {
            if (out.size() + c.length > AUDIO_CHUNK * 10) {
               break;
            }

            out.write(c, 0, c.length);
            last = s;
         }
      }

      return last == after ? null : new Object[]{last, out.toByteArray()};
   }
}
