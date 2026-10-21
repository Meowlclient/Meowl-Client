package com.lennart.meowlserver.images;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Gemeinsame Wiedergabe eines Videos/Streams auf dem Meowl Server: yt-dlp lädt, ffmpeg wandelt in JPEG-Bilder (stdout)
 * und Ton als Mono-PCM (stderr). Eine eigene Abspieluhr gibt Bilder und Ton in Echtzeit frei und lässt sich pausieren
 * und verschieben (Vorspulen). Bricht die Verbindung zu YouTube/Twitch ab, lädt die Wiedergabe an derselben Stelle neu.
 * Alle Zuschauer holen sich dasselbe aktuelle Bild und denselben Ton – so läuft es bei allen gleichzeitig.
 */
public final class WatchSession {
   private static final Logger LOG = Logger.getLogger("Watch");
   public static final int AUDIO_RATE = 44100;
   public static final int FPS = 30;
   /** 0,1 s Ton pro Stück (Mono, 16 Bit). */
   private static final int AUDIO_CHUNK = AUDIO_RATE / 10 * 2;
   private static final int KEEP_AUDIO = 60;
   /** Wie oft hintereinander nach einem Abbruch neu geladen wird, bevor aufgegeben wird. */
   private static final int MAX_RESUMES = 6;
   /** Bis 720p mit getrenntem Bild/Ton (yt-dlp fügt zusammen) – sonst ein fertiger Datenstrom. */
   private static final String FORMAT_HQ = "bv*[height<=720][fps<=30]+ba/b[height<=720]/b";
   private static final String FORMAT = "b[height<=720]/b/bv*[height<=720]/bv*";
   /** ffmpeg (das yt-dlp zum Laden nutzt) soll abgerissene Verbindungen selbst wieder aufbauen. */
   private static final String RECONNECT = "ffmpeg_i:-reconnect 1 -reconnect_streamed 1 -reconnect_on_network_error 1 -reconnect_delay_max 8";
   /** Optionale YouTube-Cookies (Netscape-Format) gegen die Bot-Sperre für Rechenzentrums-IPs. */
   public static volatile java.nio.file.Path cookies;
   private final String url;
   private volatile Process ffmpeg;
   private volatile Process loader;
   private volatile boolean stopped;
   private volatile String status = "Lädt …";
   private volatile byte[] frame;
   private volatile long frameSeq;
   private final Object frameLock = new Object();
   private final Deque<long[]> audioIndex = new ArrayDeque<>();
   private final Deque<byte[]> audio = new ArrayDeque<>();
   private long audioSeq;
   /** Zählt Sprünge (Vorspulen): Clients verwerfen ihren Ton-Puffer, wenn sich die Zahl ändert. */
   private volatile int epoch;
   /** Laufende Wiedergabe-Generation: ein Sprung beendet die alte, Leser alter Generationen hören auf. */
   private volatile int gen;
   /** Dauer in Sekunden, ≤ 0 = Livestream bzw. unbekannt (dann kein Vorspulen). */
   private volatile double duration = -1.0;
   /** Medienzeit (ns), ab der die nächste Wiedergabe startet. */
   private volatile long startAt;
   // Abspieluhr (Nanosekunden Medienzeit); läuft ab dem ersten Bild/Ton, steht während Pause und Nachladen
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

   public int epoch() {
      return this.epoch;
   }

   /** Abspielposition in Sekunden. */
   public double position() {
      return this.mediaNanos() / 1.0E9;
   }

   /** Dauer in Sekunden, ≤ 0 bei Livestreams. */
   public double duration() {
      return this.duration;
   }

   public boolean seekable() {
      return this.duration > 0.0;
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

   /**
    * Springt an eine Stelle (Sekunden): die laufende Wiedergabe endet, ab dort wird neu geladen. Die Uhr steht so lange
    * an der neuen Stelle, bis das erste Bild da ist. Liefert die tatsächliche Zielposition oder -1 (nicht spulbar).
    */
   public double seek(double seconds) {
      double dur = this.duration;
      if (dur <= 0.0 || this.stopped) {
         return -1.0;
      }

      double to = Math.max(0.0, Math.min(dur - 2.0, seconds));
      long target = (long)(to * 1.0E9);
      synchronized (this.clock) {
         this.played = target;
         this.runningSince = -1L;
         this.clockStarted = false;
      }

      this.startAt = target;
      synchronized (this) {
         this.audio.clear();
         this.audioIndex.clear();
      }

      this.epoch++;
      this.gen++;
      this.status = "";
      this.killProcesses();
      return to;
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

   /** Uhr anhalten, bis wieder Bilder kommen (beim Nachladen nach einem Abbruch). */
   private void holdClock() {
      synchronized (this.clock) {
         if (this.clockStarted) {
            this.played += this.runningSince < 0L ? 0L : System.nanoTime() - this.runningSince;
            this.runningSince = -1L;
            this.clockStarted = false;
         }
      }
   }

   private long mediaNanos() {
      synchronized (this.clock) {
         return this.played + (this.runningSince < 0L ? 0L : System.nanoTime() - this.runningSince);
      }
   }

   /** Wartet, bis die Abspieluhr {@code t} erreicht (pausiert solange); false, wenn gestoppt oder übersprungen. */
   private boolean waitUntil(long t, int myGen) {
      try {
         while (!this.stopped && this.gen == myGen) {
            long d = t - this.mediaNanos();
            if (d <= 0L) {
               return true;
            }

            Thread.sleep(Math.max(1L, Math.min(50L, d / 1000000L)));
         }
      } catch (InterruptedException var6) {
      }

      return false;
   }

   private void killProcesses() {
      for (Process p : new Process[]{this.ffmpeg, this.loader}) {
         if (p != null) {
            p.descendants().forEach(ProcessHandle::destroy);
            p.destroy();
         }
      }
   }

   public synchronized void stop() {
      this.stopped = true;
      this.audio.clear();
      this.audioIndex.clear();
      this.frame = null;
      this.killProcesses();
      synchronized (this.frameLock) {
         this.frameLock.notifyAll();
      }
   }

   private void run() {
      try {
         List<String> args = this.resolve();
         if (this.stopped || args == null) {
            return;
         }

         boolean hq = true;
         boolean anyFrame = false;
         int resumes = 0;
         while (!this.stopped) {
            int myGen = this.gen;
            long from = this.startAt;
            long before = this.frameSeq;
            String err = this.play(args, hq ? FORMAT_HQ : FORMAT, hq, from, myGen);
            if (this.stopped) {
               return;
            }

            long got = this.frameSeq - before;
            if (this.gen != myGen) {
               // vorgespult: an der neuen Stelle weiter
               resumes = 0;
               continue;
            }

            if (got == 0L && !anyFrame && hq) {
               // Zusammenfügen ging nicht: fertigen Datenstrom (meist 360p) nehmen
               LOG.warning("HQ ohne Bild, versuche einfachen Datenstrom: " + err);
               hq = false;
               continue;
            }

            if (got > 0L) {
               anyFrame = true;
            }

            if (!anyFrame) {
               LOG.warning("Wiedergabe ohne Bild: " + err);
               this.status = err.isEmpty() ? "Video konnte nicht abgespielt werden" : reason(err);
               return;
            }

            double pos = this.position();
            double dur = this.duration;
            if (dur > 0.0 && pos >= dur - 4.0) {
               this.status = "Video zu Ende";
               return;
            }

            // mittendrin abgebrochen (Verbindung zu YouTube/Twitch weg): an derselben Stelle neu laden
            resumes = got > FPS * 20L ? 1 : resumes + 1;
            if (resumes > MAX_RESUMES) {
               LOG.warning("Wiedergabe bricht immer wieder ab, gebe auf: " + err);
               this.status = "Verbindung zum Video verloren";
               return;
            }

            LOG.info("Wiedergabe abgebrochen bei " + Math.round(pos) + " s, lade neu (" + resumes + "): " + err);
            this.holdClock();
            this.startAt = dur > 0.0 ? this.mediaNanos() : 0L;
            this.status = "Lädt weiter …";
            Thread.sleep(Math.min(5000L, 700L * resumes));
            this.status = "";
         }
      } catch (Exception var9) {
         if (!this.stopped) {
            LOG.warning("Wiedergabe fehlgeschlagen: " + var9);
            this.status = String.valueOf(var9.getMessage()).contains("yt-dlp") ? "yt-dlp fehlt auf dem Server" : String.valueOf(var9.getMessage()).contains("ffmpeg") ? "ffmpeg fehlt auf dem Server" : "Wiedergabe fehlgeschlagen";
         }
      }
   }

   /**
    * yt-dlp lädt (mit Kopfzeilen/Cookies, die YouTube verlangt) ab {@code from} (ns) und schreibt direkt in ffmpeg.
    * Liefert yt-dlps Fehlertext.
    */
   private String play(List<String> args, String format, boolean merge, long from, int myGen) throws IOException, InterruptedException {
      List<String> dl = new ArrayList<>(List.of("yt-dlp", "--no-playlist", "--no-warnings", "--no-progress", "--socket-timeout", "15"));
      dl.addAll(args);
      dl.addAll(List.of("--downloader-args", RECONNECT));
      if (merge) {
         dl.addAll(List.of("--merge-output-format", "mkv"));
      }

      if (from > 500000000L && this.duration > 0.0) {
         dl.addAll(List.of("--download-sections", String.format(Locale.ROOT, "*%.2f-inf", from / 1.0E9)));
      }

      dl.addAll(List.of("-f", format, "-o", "-", this.url));
      Process y = new ProcessBuilder(dl).start();
      this.loader = y;
      if (this.stopped || this.gen != myGen) {
         // während des Ladens abgenommen oder vorgespult: nichts mehr abspielen
         y.descendants().forEach(ProcessHandle::destroy);
         y.destroy();
         return "";
      }

      ByteArrayOutputStream yErr = new ByteArrayOutputStream();
      Thread ye = new Thread(() -> {
         try {
            // nur das Ende behalten (Fehlermeldung), sonst wächst der Puffer bei langen Videos
            byte[] b = new byte[4096];
            int n;
            InputStream es = y.getErrorStream();
            while ((n = es.read(b)) > 0) {
               synchronized (yErr) {
                  if (yErr.size() > 16384) {
                     yErr.reset();
                  }

                  yErr.write(b, 0, n);
               }
            }
         } catch (IOException var5) {
         }
      }, "Meowl-Watch-Log");
      ye.setDaemon(true);
      ye.start();
      List<String> cmd = new ArrayList<>(List.of("ffmpeg", "-hide_banner", "-loglevel", "quiet", "-i", "pipe:0"));
      cmd.addAll(List.of(
         "-map", "0:v:0", "-vf", "fps=" + FPS + ",scale=854:480:force_original_aspect_ratio=decrease:flags=bicubic,pad=854:480:(ow-iw)/2:(oh-ih)/2",
         "-c:v", "mjpeg", "-q:v", "5", "-f", "image2pipe", "pipe:1",
         "-map", "0:a:0?", "-ac", "1", "-ar", String.valueOf(AUDIO_RATE), "-f", "s16le", "pipe:2"
      ));
      Process p = new ProcessBuilder(cmd).start();
      this.ffmpeg = p;
      if (this.stopped || this.gen != myGen) {
         p.destroy();
         y.descendants().forEach(ProcessHandle::destroy);
         y.destroy();
         return "";
      }

      if (this.status.startsWith("Lädt")) {
         this.status = "";
      }

      Thread pump = new Thread(() -> {
         try (var in = y.getInputStream(); var out = p.getOutputStream()) {
            in.transferTo(out);
         } catch (IOException var5) {
         }
      }, "Meowl-Watch-Daten");
      pump.setDaemon(true);
      pump.start();
      Thread audioReader = new Thread(() -> this.readAudio(p.getErrorStream(), myGen, from), "Meowl-Watch-Ton");
      audioReader.setDaemon(true);
      audioReader.start();
      this.readFrames(p.getInputStream(), myGen, from);
      p.waitFor(5L, TimeUnit.SECONDS);
      p.descendants().forEach(ProcessHandle::destroy);
      p.destroy();
      y.descendants().forEach(ProcessHandle::destroy);
      y.destroy();
      ye.join(2000L);
      audioReader.join(2000L);
      synchronized (yErr) {
         return yErr.toString(StandardCharsets.UTF_8).trim();
      }
   }

   /**
    * Prüft, mit welchem YouTube-Zugang das Video abrufbar ist, und merkt sich die Dauer; liefert die passenden
    * Zusatz-Argumente oder null.
    */
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
         List<String> cmd = new ArrayList<>(List.of("yt-dlp", "--no-playlist", "--socket-timeout", "15", "--print", "duration", "--print", "urls"));
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
            String first = out.lines().findFirst().orElse("").trim();
            try {
               this.duration = Double.parseDouble(first);
            } catch (NumberFormatException var12) {
               // „NA“ bei Livestreams: nicht spulbar
               this.duration = -1.0;
            }

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
   private void readFrames(InputStream in, int myGen, long from) throws IOException {
      ByteArrayOutputStream cur = new ByteArrayOutputStream(96 * 1024);
      int prev = -1;
      boolean inside = false;
      long count = 0L;
      byte[] buf = new byte[32768];
      int n;
      while (!this.stopped && this.gen == myGen && (n = in.read(buf)) > 0) {
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
                     if (!this.waitUntil(from + count * 1000000000L / FPS, myGen)) {
                        return;
                     }

                     count++;
                     this.frame = cur.toByteArray();
                     synchronized (this.frameLock) {
                        this.frameSeq++;
                        this.frameLock.notifyAll();
                     }
                  }
               }
            }

            prev = b;
         }
      }
   }

   private void readAudio(InputStream in, int myGen, long from) {
      byte[] chunk = new byte[AUDIO_CHUNK];
      long count = 0L;
      try {
         while (!this.stopped && this.gen == myGen) {
            int off = 0;
            while (off < chunk.length) {
               int n = in.read(chunk, off, chunk.length - off);
               if (n < 0) {
                  return;
               }

               off += n;
            }

            this.startClock();
            if (!this.waitUntil(from + count * 100000000L, myGen)) {
               return;
            }

            count++;
            synchronized (this) {
               if (this.gen != myGen) {
                  return;
               }

               this.audioSeq++;
               this.audio.addLast(chunk.clone());
               this.audioIndex.addLast(new long[]{this.audioSeq});
               while (this.audio.size() > KEEP_AUDIO) {
                  this.audio.removeFirst();
                  this.audioIndex.removeFirst();
               }
            }
         }
      } catch (IOException var8) {
      }
   }

   public byte[] frame() {
      return this.frame;
   }

   public long frameSeq() {
      return this.frameSeq;
   }

   /**
    * Long-Poll: wartet höchstens {@code timeoutMs}, bis Bild Nummer {@code want} (oder neuer) da ist. So können Clients
    * mehrere Anfragen gleichzeitig offen halten und bekommen jedes Bild, sobald es freigegeben wird.
    */
   public void waitFrame(long want, long timeoutMs) {
      long end = System.currentTimeMillis() + timeoutMs;
      synchronized (this.frameLock) {
         try {
            while (!this.stopped && this.frameSeq < want) {
               long left = end - System.currentTimeMillis();
               if (left <= 0L) {
                  return;
               }

               this.frameLock.wait(left);
            }
         } catch (InterruptedException var9) {
            Thread.currentThread().interrupt();
         }
      }
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
