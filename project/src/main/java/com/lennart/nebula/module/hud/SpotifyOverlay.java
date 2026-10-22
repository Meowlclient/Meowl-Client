package com.lennart.nebula.module.hud;

import com.lennart.nebula.module.Section;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lennart.nebula.NebulaClient;
import com.lennart.nebula.gui.Render2D;
import com.lennart.nebula.module.Category;
import com.lennart.nebula.module.Module;
import com.lennart.nebula.setting.BooleanSetting;
import com.lennart.nebula.setting.ColorSetting;
import com.lennart.nebula.setting.NumberSetting;
import com.lennart.nebula.setting.Setting;
import com.lennart.nebula.util.DynamicTextures;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;

public class SpotifyOverlay extends Module {
   private static final Identifier COVER = Identifier.fromNamespaceAndPath("nebula", "spotify/cover");
   private static final long POLL_MS = 3000L;
   private final BooleanSetting cover = this.add(new BooleanSetting("Cover", "Album-Cover neben dem Titel (Windows: über die iTunes-Suche)", true));
   private final com.lennart.nebula.setting.ModeSetting layout = this.add(
      new com.lennart.nebula.setting.ModeSetting("Stil", "Kasten mit großem Cover, Karte oder eine Zeile", "Kasten", "Kasten", "Karte", "Kompakt")
   );
   private final BooleanSetting scrollText = this.add(new BooleanSetting("Lauftext", "Lange Titel laufen durch statt abgeschnitten zu werden", true));
   private final NumberSetting scrollSpeed = this.add(new NumberSetting("Lauf-Tempo", "Wie schnell der Text läuft", 22.0, 8.0, 60.0, 1.0, "px/s"));
   private final BooleanSetting hidePaused = this.add(new BooleanSetting("Bei Pause ausblenden", "Karte verstecken, wenn nichts läuft", true));
   private final NumberSetting maxWidth = this.add(new NumberSetting("Textbreite", "Breite des Textbereichs (längere Titel laufen durch)", 110.0, 60.0, 300.0, 5.0, "px"));
   private final ColorSetting accent = this.add(new ColorSetting("Akzent", "Farbe des Notensymbols und des Rands", -14756000, false));
   private final BooleanSetting showArtist = this.add(new BooleanSetting("Interpret", "Interpret unter dem Titel zeigen", true));
   private final BooleanSetting showEq = this.add(new BooleanSetting("Equalizer", "Kleine tanzende Balken (Kasten-Stil)", true));
   private final BooleanSetting showLine = this.add(new BooleanSetting("Akzent-Linie", "Farbige Linie am unteren Rand", true));
   private final BooleanSetting showPausedHint = this.add(new BooleanSetting("„Pausiert“ anzeigen", "Hinweis, wenn der Song pausiert ist", true));
   private final HudBox box = this.hudBox("Spotify", 1.0, 70.0).reflow();
   // Lyrics
   private final BooleanSetting lyrics = this.add(new BooleanSetting("Lyrics", "Songtext zum laufenden Lied, zeitgenau mitlaufend (Windows)", false));
   private final NumberSetting lyricLines = this.add(new NumberSetting("Lyrics-Zeilen", "Wie viele Zeilen sichtbar sind", 9.0, 1.0, 15.0, 1.0, ""));
   private final NumberSetting lyricWidth = this.add(new NumberSetting("Lyrics-Breite", "Breite des Lyrics-Bereichs", 170.0, 100.0, 420.0, 5.0, "px"));
   private final com.lennart.nebula.setting.ModeSetting lyricAlign = this.add(
      new com.lennart.nebula.setting.ModeSetting("Lyrics-Ausrichtung", "Wo die Zeilen stehen", "Links", "Links", "Mitte", "Rechts")
   );
   private final ColorSetting lyricColor = this.add(new ColorSetting("Lyrics-Farbe", "Farbe der aktuellen Zeile", -1, false));
   private final ColorSetting lyricDim = this.add(new ColorSetting("Lyrics-Farbe Rest", "Farbe der anderen Zeilen", 0x90FFFFFF, false));
   private final com.lennart.nebula.setting.ModeSetting lyricStyle = this.add(
      new com.lennart.nebula.setting.ModeSetting("Lyrics-Stil", "Im HUD oder Wort für Wort in der Welt aufploppend", "HUD", "HUD", "In der Welt")
   );
   private final NumberSetting lyricWorldSize = this.add(new NumberSetting("Welt-Größe", "Größe der Lyrics in der Welt", 1.2, 0.3, 6.0, 0.1, "x"));
   private final NumberSetting lyricWorldDist = this.add(new NumberSetting("Welt-Abstand", "Wie weit die Zeilen von dir weg erscheinen", 8.0, 2.0, 32.0, 0.5, "Blöcke"));
   private final NumberSetting lyricWorldHeight = this.add(new NumberSetting("Welt-Höhe", "Wie weit über (+) oder unter (−) deinem Blick die Zeilen erscheinen", 14.0, -30.0, 40.0, 1.0, "°"));
   private final NumberSetting lyricWorldSpread = this.add(new NumberSetting("Welt-Streuung", "Wie weit die Zeilen seitlich verteilt erscheinen", 50.0, 0.0, 120.0, 5.0, "°"));
   private final NumberSetting lyricWorldTilt = this.add(new NumberSetting("Welt-Neigung", "Wie schräg die Zeilen stehen dürfen", 12.0, 0.0, 40.0, 1.0, "°"));
   private final com.lennart.nebula.setting.ModeSetting lyricWorldFx = this.add(
      new com.lennart.nebula.setting.ModeSetting("Welt-Effekt", "Sternschnuppe fliegt zur nächsten Zeile, Sternenstaub funkelt um die Zeile", "Beides", "Beides", "Sternschnuppe", "Sternenstaub", "Keine")
   );
   private final com.lennart.nebula.setting.ModeSetting lyricWorldMotion = this.add(
      new com.lennart.nebula.setting.ModeSetting("Welt-Bewegung", "Wie sich die Zeilen bewegen", "Schweben", "Schweben", "Welle", "Ruhig")
   );
   private final NumberSetting lyricFxStrength = this.add(new NumberSetting("Effekt-Stärke", "Helligkeit und Menge der Effekte", 100.0, 10.0, 200.0, 10.0, "%"));
   private final ColorSetting worldColorA = this.add(new ColorSetting("Welt-Farbe 1", "Farbe am Wortanfang (Lyrics in der Welt)", 0xFFF4EEFF, false));
   private final ColorSetting worldColorB = this.add(new ColorSetting("Welt-Farbe 2", "Farbe am Wortende (Lyrics in der Welt)", 0xFFB98CFF, false));
   private final NumberSetting lyricOffset = this.add(new NumberSetting("Lyrics-Verschiebung", "Text früher (-) oder später (+), falls er nicht passt", 0.0, -3000.0, 3000.0, 100.0, "ms"));
   private final NumberSetting lyricWordDelay = this.add(
      new NumberSetting("Wort-Tempo", "Wie schnell die Wörter einer Zeile nacheinander kommen (ms je Buchstabe)", 70.0, 30.0, 140.0, 5.0, "ms")
   );
   private final NumberSetting lyricWordAnim = this.add(new NumberSetting("Wort-Animation", "Dauer des Einblendens je Wort", 280.0, 80.0, 700.0, 20.0, "ms"));
   private final NumberSetting lyricWordGap = this.add(new NumberSetting("Wort-Abstand", "Abstand zwischen den Wörtern (in der Welt)", 1.2, 0.6, 3.0, 0.1, "x"));
   private final NumberSetting lyricGlow = this.add(new NumberSetting("Lyrics-Glow", "Lila Leuchten um die Wörter (in der Welt)", 100.0, 0.0, 200.0, 10.0, "%"));
   private final HudBox lyricBox = this.hudBox("Lyrics", 1.0, 22.0);
   /** Einmalig: Lyrics aus der alten Mitte-unten-Position an den linken Rand holen. */
   private final BooleanSetting lyricLayoutV2 = this.add(new BooleanSetting("Lyrics-Layout 2", "", false));
   private final SpotifyLyrics lyricSource = new SpotifyLyrics();
   private long lyricSwitchAt;
   private int lyricShown = -2;
   private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6L)).build();
   private ExecutorService worker;
   private volatile boolean polling;
   private long lastPoll;
   private volatile SpotifyOverlay.Track track;
   private volatile String status = "Suche Spotify …";
   private String coverKey;
   private volatile int[] pendingCover;
   private volatile String pendingCoverKey;
   private boolean coverReady;
   private DynamicTexture coverTex;
   private String marqueeKey;
   private long marqueeStart;

   private static SpotifyOverlay instance;
   private static volatile int[] lastCoverPixels;

   /** Pixel (64 x 64) des zuletzt geladenen Covers oder null. */
   public static int[] coverPixels() {
      SpotifyOverlay s = instance;
      return s != null && s.isEnabled() && s.coverReady ? lastCoverPixels : null;
   }

   /** Cover des laufenden Songs (nur wenn das Overlay an ist und es geladen hat), sonst null. */
   public static Identifier coverTexture() {
      SpotifyOverlay s = instance;
      return s != null && s.isEnabled() && s.coverReady ? COVER : null;
   }

   public SpotifyOverlay() {
      super("Spotify Overlay", "Zeigt den laufenden Spotify-Song (Titel, Interpret, optional Cover) klein im HUD.", Category.HUD);
      instance = this;
      this.section(Section.MEOWL);
      this.icon(Items.MUSIC_DISC_CAT);
      this.box.background.set(true);
      this.group(
         "Anzeige",
         new Setting[]{
            this.layout, this.cover, this.showArtist, this.showEq, this.showLine, this.showPausedHint, this.scrollText, this.scrollSpeed, this.hidePaused, this.maxWidth, this.accent
         }
      );
      this.showEq.setVisibility(() -> this.layout.is("Kasten"));
      this.scrollSpeed.setVisibility(this.scrollText::get);
      this.group("Karte", new Setting[]{this.box.scale, this.box.opacity, this.box.background, this.box.backgroundColor, this.box.rounding});
      this.lyricBox.background.set(false);
      this.group(
         "Lyrics",
         new Setting[]{
            this.lyrics, this.lyricStyle, this.lyricWorldSize, this.worldColorA, this.worldColorB, this.lyricLines, this.lyricWidth, this.lyricAlign, this.lyricColor, this.lyricDim, this.lyricOffset, this.lyricBox.scale, this.lyricBox.opacity,
            this.lyricBox.background, this.lyricBox.backgroundColor, this.lyricBox.rounding
         }
      );

      this.lyricLayoutV2.setVisibility(() -> false);

      this.lyricWorldSize.setVisibility(() -> this.lyrics.get() && this.lyricStyle.is("In der Welt"));
      this.worldColorA.setVisibility(() -> this.lyrics.get() && this.lyricStyle.is("In der Welt"));
      this.worldColorB.setVisibility(() -> this.lyrics.get() && this.lyricStyle.is("In der Welt"));

      for (Setting<?> st : new Setting[]{this.lyricStyle, this.lyricLines, this.lyricWidth, this.lyricAlign, this.lyricColor, this.lyricDim, this.lyricOffset, this.lyricBox.scale, this.lyricBox.opacity, this.lyricBox.background}) {
         st.setVisibility(this.lyrics::get);
      }
   }

   @Override
   protected void onEnable() {
      this.lastPoll = 0L;
   }

   @Override
   protected void onDisable() {
      if (this.worker != null) {
         this.worker.shutdownNow();
         this.worker = null;
      }

      this.polling = false;
      this.lyricSource.stop();
   }

   @Override
   public void onTick() {
      long now = System.currentTimeMillis();
      if (this.lyrics.get()) {
         this.lyricSource.ensureTimeline();
      } else {
         this.lyricSource.stop();
      }

      if (!this.polling && now - this.lastPoll >= 3000L) {
         this.lastPoll = now;
         this.polling = true;
         if (this.worker == null) {
            this.worker = Executors.newSingleThreadExecutor(r -> {
               Thread t = new Thread(r, "Meowl-Spotify");
               t.setDaemon(true);
               return t;
            });
         }

         boolean wantCover = this.cover.get();
         this.worker.submit(() -> {
            try {
               SpotifyOverlay.Track t = readTrack();
               this.track = t;
               this.status = t == null ? "Spotify läuft nicht" : (t.playing() ? "" : "Pausiert");
               if (t != null && wantCover && !t.key().equals(this.pendingCoverKey)) {
                  this.pendingCoverKey = t.key();
                  this.pendingCover = this.loadCover(t);
               }
            } catch (Exception var6) {
               this.status = "Spotify nicht lesbar";
               NebulaClient.LOGGER.debug("Spotify-Abfrage fehlgeschlagen: {}", var6.toString());
            } finally {
               this.polling = false;
            }
         });
      }
   }

   private static String os() {
      return System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
   }

   public static SpotifyOverlay.Track readTrack() throws Exception {
      String os = os();
      if (os.contains("win")) {
         return readWindows();
      } else {
         return os.contains("mac") ? readMac() : readLinux();
      }
   }

   private static List<String> run(String... cmd) throws Exception {
      Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
      List<String> lines = new ArrayList<>();
      BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));

      String line;
      try {
         while ((line = r.readLine()) != null) {
            lines.add(line);
         }
      } catch (Throwable var7) {
         try {
            r.close();
         } catch (Throwable var6) {
            var7.addSuppressed(var6);
         }

         throw var7;
      }

      r.close();
      if (!p.waitFor(5L, TimeUnit.SECONDS)) {
         p.destroyForcibly();
      }

      return lines;
   }

   private static SpotifyOverlay.Track readWindows() throws Exception {
      boolean running = false;

      for (String line : run("tasklist", "/v", "/fi", "imagename eq Spotify.exe", "/fo", "csv", "/nh")) {
         List<String> cols = csv(line);
         if (cols.size() >= 2 && cols.get(0).equalsIgnoreCase("Spotify.exe")) {
            running = true;
            String title = cols.get(cols.size() - 1).trim();
            SpotifyOverlay.Track t = parseWindowTitle(title);
            if (t != null) {
               return t;
            }
         }
      }

      return running ? new SpotifyOverlay.Track("", "", false, null) : null;
   }

   public static SpotifyOverlay.Track parseWindowTitle(String title) {
      if (!title.isEmpty()
         && !title.equals("N/A")
         && !title.startsWith("Spotify")
         && !title.equals("AngleHiddenWindow")
         && !title.contains("MSCTFIME")
         && !title.equals("Default IME")) {
         int i = title.indexOf(" - ");
         if (i <= 0 || title.contains("(Spotify.exe)") || title.startsWith("GDI+") || title.equalsIgnoreCase("Advertisement")) {
            // Hilfsfenster von Spotify oder Werbung – kein laufender Song
            return null;
         } else {
            return new SpotifyOverlay.Track(title.substring(i + 3).trim(), title.substring(0, i).trim(), true, null);
         }
      } else {
         return null;
      }
   }

   public static List<String> csv(String line) {
      List<String> out = new ArrayList<>();
      StringBuilder cur = new StringBuilder();
      boolean quoted = false;

      for (int i = 0; i < line.length(); i++) {
         char c = line.charAt(i);
         if (c == '"') {
            if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
               cur.append('"');
               i++;
            } else {
               quoted = !quoted;
            }
         } else if (c == ',' && !quoted) {
            out.add(cur.toString());
            cur.setLength(0);
         } else {
            cur.append(c);
         }
      }

      out.add(cur.toString());
      return out;
   }

   private static SpotifyOverlay.Track readMac() throws Exception {
      String script = "if application \"Spotify\" is running then\ntell application \"Spotify\"\nreturn (name of current track) & \"\\t\" & (artist of current track) & \"\\t\" & (player state as string) & \"\\t\" & (artwork url of current track)\nend tell\nend if\nreturn \"\"";
      List<String> out = run("osascript", "-e", script);
      return out.isEmpty() ? null : parseTabbed(out.get(0), "playing");
   }

   private static SpotifyOverlay.Track readLinux() throws Exception {
      List<String> out = run("playerctl", "-p", "spotify", "metadata", "--format", "{{title}}\t{{artist}}\t{{status}}\t{{mpris:artUrl}}");
      return !out.isEmpty() && !out.get(0).startsWith("No player") ? parseTabbed(out.get(0), "Playing") : null;
   }

   private static SpotifyOverlay.Track parseTabbed(String line, String playingWord) {
      String[] p = line.split("\t", -1);
      if (p.length >= 3 && !p[0].isBlank()) {
         String art = p.length > 3 && p[3].startsWith("http") ? p[3].trim() : null;
         return new SpotifyOverlay.Track(p[0].trim(), p[1].trim(), p[2].trim().equalsIgnoreCase(playingWord), art);
      } else {
         return null;
      }
   }

   private int[] loadCover(SpotifyOverlay.Track t) {
      try {
         String url = t.artUrl();
         if (url == null && !t.title().isEmpty()) {
            String term = URLEncoder.encode(t.artist() + " " + t.title(), StandardCharsets.UTF_8);
            String body = this.http
               .send(
                  HttpRequest.newBuilder(URI.create("https://itunes.apple.com/search?entity=song&limit=1&term=" + term))
                     .timeout(Duration.ofSeconds(8L))
                     .build(),
                  BodyHandlers.ofString()
               )
               .body();
            JsonArray results = JsonParser.parseString(body).getAsJsonObject().getAsJsonArray("results");
            if (results != null && !results.isEmpty()) {
               JsonObject r = results.get(0).getAsJsonObject();
               if (r.has("artworkUrl100")) {
                  url = r.get("artworkUrl100").getAsString();
               }
            }
         }

         if (url == null) {
            return null;
         } else {
            byte[] bytes = this.http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8L)).build(), BodyHandlers.ofByteArray()).body();
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
            if (img == null) {
               return null;
            } else {
               int size = 64;
               int[] px = new int[size * size];

               for (int y = 0; y < size; y++) {
                  for (int x = 0; x < size; x++) {
                     px[y * size + x] = 0xFF000000 | img.getRGB(x * img.getWidth() / size, y * img.getHeight() / size) & 16777215;
                  }
               }

               return px;
            }
         }
      } catch (Exception var9) {
         return null;
      }
   }

   @Override
   public void onRenderHud(GuiGraphicsExtractor ctx, float tickDelta) {
      boolean editing = HudLayout.isEditing();
      if (!this.mc.gui.hud.isHidden() || editing) {
         SpotifyOverlay.Track t = this.track;
         if (editing && (t == null || t.title().isEmpty())) {
            t = new SpotifyOverlay.Track("Ein ziemlich langer Songtitel zum Testen", "Interpret", true, null);
         }

         if (t != null && !t.title().isEmpty() && (t.playing() || !this.hidePaused.get() || editing)) {
            int[] pc = this.pendingCover;
            String pk = this.pendingCoverKey;
            if (pk != null && !pk.equals(this.coverKey)) {
               if (pc != null) {
                  lastCoverPixels = pc.clone();
                  this.coverTex = DynamicTextures.upload(COVER, this.coverTex, pc, 64, 64);
                  this.coverReady = true;
                  this.coverKey = pk;
                  this.pendingCover = null;
               } else if (this.track != null && pk.equals(this.track.key())) {
                  this.coverReady = false;
               }
            }

            if (!t.key().equals(this.marqueeKey)) {
               // neuer Song: Lauftext beginnt von vorn
               this.marqueeKey = t.key();
               this.marqueeStart = System.currentTimeMillis();
            }

            boolean showCover = this.cover.get() && this.coverReady && t.key().equals(this.coverKey);
            switch (this.layout.get()) {
               case "Kompakt" -> this.drawCompact(ctx, t, showCover);
               case "Karte" -> this.drawCard(ctx, t, showCover);
               default -> this.drawBox(ctx, t, showCover);
            }
         }

         if (this.lyrics.get() && (editing || !this.lyricStyle.is("In der Welt"))) {
            this.drawLyrics(ctx, editing);
         }
      }
   }

   private final java.util.Map<String, List<String>> lyricWrap = new java.util.HashMap<>();

   /** Eine Lyrics-Zeile auf die Breite umgebrochen (fett gemessen, damit nichts springt, wenn sie aktuell wird). */
   private List<String> lyricRows(Font tr, SpotifyLyrics.Line line, int maxW) {
      String text = line.text().isEmpty() ? "♪" : line.text();
      String key = maxW + "\n" + text;
      List<String> rows = this.lyricWrap.get(key);
      if (rows == null) {
         if (this.lyricWrap.size() > 400) {
            this.lyricWrap.clear();
         }

         rows = new java.util.ArrayList<>();

         for (net.minecraft.network.chat.FormattedText part : tr.getSplitter().splitLines(text, Math.max(20, maxW), net.minecraft.network.chat.Style.EMPTY.withBold(true))) {
            String s = part.getString().strip();
            if (!s.isEmpty()) {
               rows.add(s);
            }
         }

         if (rows.isEmpty()) {
            rows.add(text);
         }

         this.lyricWrap.put(key, rows);
      }

      return rows;
   }

   // ================================================================== Lyrics in der Welt

   /**
    * Eine Zeile in der Welt mit fertigem Layout (einmal beim Zeilenwechsel berechnet, nicht pro Frame): Ort, Neigung,
    * Wörter mit Erscheinungszeitpunkt (Song-ms), Position jedes Worts und Breiten der Zeichen für den Farbverlauf.
    */
   private record WorldLine(
      int index, Object source, net.minecraft.world.phys.Vec3 pos, float tilt, String[] words, long[] appear, long born, float[] wx, float[] wy, int[] ww,
      String[][] chars, int[][] charW
   ) {
   }

   private WorldLine worldLine;
   private WorldLine worldFading;
   /** Sternschnuppe von der alten zur neuen Zeile: Start, Ziel, Startzeit. */
   private net.minecraft.world.phys.Vec3 starFrom;
   private net.minecraft.world.phys.Vec3 starTo;
   private long starAt;
   private long worldFadeAt;
   private final java.util.Random worldRandom = new java.util.Random();

   /**
    * Zeitpunkte (Song-ms), zu denen die Wörter einer Zeile gesungen werden. LRCLIB liefert nur Zeilen-Zeitstempel, daher:
    * erstes Wort am Zeilenanfang, danach nach Wortlänge verteilt (einstellbar, ms je Buchstabe), höchstens 85 % der Zeile.
    */
   private long[] wordTimes(List<SpotifyLyrics.Line> lines, int index, String[] words) {
      long start = lines.get(index).timeMs();
      long next = index + 1 < lines.size() ? lines.get(index + 1).timeMs() : start + 3000L;
      long dur = Math.max(400L, next - start);
      int total = 0;
      int[] weight = new int[words.length];
      for (int i = 0; i < words.length; i++) {
         weight[i] = Math.max(2, words[i].replaceAll("[^\\p{L}\\p{N}]", "").length()) + (words[i].matches(".*[,.!?;:]$") ? 2 : 0);
         total += weight[i];
      }

      long span = Math.max(300L, Math.min(dur * 85L / 100L, Math.round(total * this.lyricWordDelay.get())));
      long[] appear = new long[words.length];
      int cum = 0;
      for (int i = 0; i < words.length; i++) {
         appear[i] = start + span * cum / Math.max(1, total);
         cum += weight[i];
      }

      return appear;
   }

   /** Wie im Video: jede Zeile baut sich Wort für Wort an einer neuen Stelle im Blickfeld auf; die alte blendet aus. */
   @Override
   public void onRenderWorld(com.lennart.nebula.util.WorldCtx context, float tickDelta) {
      if (!this.lyrics.get() || !this.lyricStyle.is("In der Welt") || this.mc.player == null) {
         return;
      }

      SpotifyLyrics src = this.lyricSource;
      if (!src.windows() || !src.playing()) {
         this.worldLine = null;
         this.worldFading = null;
         return;
      }

      SpotifyLyrics.Lyrics l = src.lyrics(src.title(), src.artist(), src.durationMs());
      if (l == null || l.lines().isEmpty() || !l.synced()) {
         this.worldLine = null;
         return;
      }

      List<SpotifyLyrics.Line> lines = l.lines();
      long pos = src.positionMs() + Math.round(this.lyricOffset.get());
      int current = SpotifyLyrics.indexAt(lines, pos);
      long now = System.currentTimeMillis();
      // neue Zeile, Sprung im Song oder anderes Lied (gleicher Index, aber andere Lyrics) → neu aufbauen
      if (this.worldLine == null ? current >= 0 : this.worldLine.index() != current || this.worldLine.source() != l) {
         if (this.worldLine != null) {
            this.worldFading = this.worldLine.source() == l ? this.worldLine : null;
            this.worldFadeAt = now;
         }

         String text = current >= 0 ? lines.get(current).text().trim() : "";
         WorldLine before = this.worldLine;
         this.worldLine = text.isEmpty() ? null : this.layoutWorldLine(l, lines, current, text, tickDelta, now);
         if (this.worldLine != null && this.worldLine.pos() != null) {
            // Sternschnuppe: von der alten Zeile (oder von schräg oben) zur neuen
            net.minecraft.world.phys.Vec3 to = this.worldLine.pos();
            net.minecraft.world.phys.Vec3 eye = this.mc.player.getEyePosition(tickDelta);
            this.starFrom = before != null && before.pos() != null
               ? before.pos()
               : to.add(to.subtract(eye).cross(new net.minecraft.world.phys.Vec3(0.0, 1.0, 0.0)).normalize().scale(-5.0)).add(0.0, 3.5, 0.0);
            this.starTo = to;
            this.starAt = now;
         }
      }

      if (this.worldFading != null) {
         if (now - this.worldFadeAt > 520L) {
            this.worldFading = null;
         } else {
            this.drawWorldLine(context, this.worldFading, now, pos, (now - this.worldFadeAt) / 1000.0F);
         }
      }

      if (this.worldLine != null) {
         this.drawWorldLine(context, this.worldLine, now, pos, -1.0F);
      }

      this.drawWorldFx(context, now, pos);
   }

   // ================================================================== Effekte

   private static float hash(int i, int k) {
      double v = Math.sin(i * 12.9898 + k * 78.233) * 43758.5453;
      return (float)(v - Math.floor(v));
   }

   /** Sternschnuppe zur neuen Zeile, Funkenregen beim Ankommen und Sternenstaub um die aktuelle Zeile. */
   private void drawWorldFx(com.lennart.nebula.util.WorldCtx ctx, long now, long songPos) {
      String fx = this.lyricWorldFx.get();
      if (fx.equals("Keine")) {
         return;
      }

      float k = (float)(this.lyricFxStrength.get() / 100.0);
      float size = (float)this.lyricWorldSize.get().doubleValue();
      int colB = this.worldColorB.get() & 0xFFFFFF;
      int colA = this.worldColorA.get() & 0xFFFFFF;
      net.minecraft.world.phys.Vec3 cam = ctx.cameraPos();
      com.mojang.blaze3d.vertex.PoseStack m = ctx.matrices();
      com.mojang.blaze3d.vertex.VertexConsumer vc = ctx.buffer(com.lennart.nebula.util.WorldRender.glow());
      m.pushPose();
      m.translate(-cam.x, -cam.y, -cam.z);
      if ((fx.equals("Beides") || fx.equals("Sternschnuppe")) && this.starFrom != null && this.starTo != null) {
         float age = (now - this.starAt) / 1000.0F;
         float fly = 0.75F;
         net.minecraft.world.phys.Vec3 a = this.starFrom, b = this.starTo;
         net.minecraft.world.phys.Vec3 c = a.add(b).scale(0.5).add(0.0, Math.max(1.5, a.distanceTo(b) * 0.35), 0.0);
         if (age < fly) {
            float u = age / fly;
            u = u * u * (3.0F - 2.0F * u);
            // Schweif: Punkte hinter dem Kopf, kleiner und blasser
            for (int i = 0; i < 18; i++) {
               float q = u - i * 0.03F;
               if (q < 0.0F) {
                  break;
               }

               net.minecraft.world.phys.Vec3 p = bezier(a, c, b, q);
               float fade = 1.0F - i / 18.0F;
               float r = (0.05F + 0.07F * fade) * Math.min(1.6F, size);
               com.lennart.nebula.cosmetic.Shapes.glowCube(m, vc, (float)p.x, (float)p.y, (float)p.z, r, i < 3 ? 0xFFFFFF : lerpRgb(colA, colB, i / 18.0F), 0.75F * fade * k);
            }

            net.minecraft.world.phys.Vec3 head = bezier(a, c, b, u);
            com.lennart.nebula.cosmetic.Shapes.glowCube(m, vc, (float)head.x, (float)head.y, (float)head.z, 0.22F * Math.min(1.6F, size), colB, 0.35F * k);
         } else if (age < fly + 0.8F) {
            // Ankunft: Funken fliegen auseinander
            float t = (age - fly) / 0.8F;
            float alpha = (1.0F - t) * (1.0F - t) * k;
            for (int i = 0; i < 14; i++) {
               double th = hash(i, 1) * Math.PI * 2.0, ph = (hash(i, 2) - 0.5) * Math.PI;
               double d = (0.3 + hash(i, 3) * 0.9) * t * Math.min(2.0F, size);
               net.minecraft.world.phys.Vec3 p = b.add(Math.cos(th) * Math.cos(ph) * d, Math.sin(ph) * d - t * t * 0.3, Math.sin(th) * Math.cos(ph) * d);
               com.lennart.nebula.cosmetic.Shapes.glowCube(m, vc, (float)p.x, (float)p.y, (float)p.z, 0.045F * Math.min(1.6F, size), i % 3 == 0 ? 0xFFFFFF : colB, 0.8F * alpha);
            }
         }
      }

      if ((fx.equals("Beides") || fx.equals("Sternenstaub")) && this.worldLine != null && this.worldLine.pos() != null) {
         // Sternenstaub: funkelnde Punkte in der Bildebene um die Zeile, steigen langsam auf
         org.joml.Quaternionf q = ctx.cameraOrientation();
         org.joml.Vector3f right = q.transform(new org.joml.Vector3f(1.0F, 0.0F, 0.0F));
         org.joml.Vector3f up = q.transform(new org.joml.Vector3f(0.0F, 1.0F, 0.0F));
         WorldLine line = this.worldLine;
         float half = 0.0F;
         for (int i = 0; i < line.words().length; i++) {
            half = Math.max(half, Math.abs(line.wx()[i]) + line.ww()[i]);
         }

         half = half * 0.028F * size + 0.6F;
         float t = (now - line.born()) / 1000.0F;
         int seed = line.index() * 31;
         int count = Math.round(16 * Math.min(2.0F, k));
         for (int i = 0; i < count; i++) {
            float x = (hash(seed + i, 4) * 2.0F - 1.0F) * half;
            float y = ((hash(seed + i, 5) * 2.0F - 1.0F) * 0.7F + (t * 0.12F + hash(seed + i, 6)) % 1.0F * 0.5F) * size;
            float tw = Math.max(0.0F, (float)Math.sin(t * (2.0F + hash(seed + i, 7) * 3.0F) + i * 1.7F));
            float appear = Math.min(1.0F, t / 0.5F);
            net.minecraft.world.phys.Vec3 p = line.pos().add(right.x * x + up.x * y, right.y * x + up.y * y, right.z * x + up.z * y);
            com.lennart.nebula.cosmetic.Shapes.glowCube(m, vc, (float)p.x, (float)p.y, (float)p.z, 0.035F * Math.min(1.6F, size), i % 4 == 0 ? 0xFFFFFF : colB, 0.7F * tw * tw * appear * k);
         }
      }

      m.popPose();
   }

   private static net.minecraft.world.phys.Vec3 bezier(net.minecraft.world.phys.Vec3 a, net.minecraft.world.phys.Vec3 c, net.minecraft.world.phys.Vec3 b, float t) {
      double u = 1.0 - t;
      return a.scale(u * u).add(c.scale(2.0 * u * t)).add(b.scale((double)t * t));
   }

   /** Layout einer Zeile: Wörter in höchstens zwei versetzte Reihen (wie im Video), Abstand einstellbar. */
   private WorldLine layoutWorldLine(SpotifyLyrics.Lyrics l, List<SpotifyLyrics.Line> lines, int index, String text, float tickDelta, long now) {
      Font tr = this.mc.font;
      String[] words = text.split("\\s+");
      int n = words.length;
      int[] ww = new int[n];
      String[][] chars = new String[n][];
      int[][] charW = new int[n][];
      int total = 0;
      for (int i = 0; i < n; i++) {
         ww[i] = tr.width("§l" + words[i]);
         total += ww[i];
         int[] cps = words[i].codePoints().toArray();
         chars[i] = new String[cps.length];
         charW[i] = new int[cps.length];
         for (int c = 0; c < cps.length; c++) {
            chars[i][c] = "§l" + new String(Character.toChars(cps[c]));
            charW[i][c] = tr.width(chars[i][c]);
         }
      }

      float space = tr.width(" ") * (float)this.lyricWordGap.get().doubleValue();
      // ab ~110 px Textbreite in zwei Reihen teilen, Trennung möglichst in der Mitte
      int split = n;
      if (n > 1 && total + space * (n - 1) > 110.0F) {
         float half = (total + space * (n - 1)) / 2.0F, acc = 0.0F, best = Float.MAX_VALUE;
         for (int i = 0; i < n - 1; i++) {
            acc += ww[i] + (i > 0 ? space : 0.0F);
            if (Math.abs(acc - half) < best) {
               best = Math.abs(acc - half);
               split = i + 1;
            }
         }
      }

      float[] wx = new float[n], wy = new float[n];
      float[] rowW = new float[2];
      for (int i = 0; i < n; i++) {
         int r = i < split ? 0 : 1;
         rowW[r] += ww[i] + (i == 0 || i == split ? 0.0F : space);
      }

      float maxRow = Math.max(rowW[0], rowW[1]);
      // zweite Reihe leicht nach rechts versetzt, wie in der Vorlage
      float[] cursor = {-maxRow / 2.0F - (split < n ? maxRow * 0.08F : 0.0F), -maxRow / 2.0F + maxRow * 0.12F};
      float rowY0 = split < n ? -6.5F : 0.0F;
      for (int i = 0; i < n; i++) {
         int r = i < split ? 0 : 1;
         wx[i] = cursor[r];
         wy[i] = rowY0 + r * 13.0F;
         cursor[r] += ww[i] + space;
      }

      double width = maxRow * 0.028 * this.lyricWorldSize.get();
      net.minecraft.world.phys.Vec3 at = this.freeSpot(tickDelta, width);
      long[] appear = this.wordTimes(lines, index, words);
      float tilt = (this.worldRandom.nextFloat() - 0.5F) * 2.0F * (float)this.lyricWorldTilt.get().doubleValue();
      return new WorldLine(index, l, at, tilt, words, appear, now, wx, wy, ww, chars, charW);
   }

   /** Sucht eine freie Stelle im Blickfeld; die Zeile (Breite {@code width}) darf keinen Block berühren. */
   private net.minecraft.world.phys.Vec3 freeSpot(float tickDelta, double width) {
      net.minecraft.world.phys.Vec3 eye = this.mc.player.getEyePosition(tickDelta);
      net.minecraft.world.phys.Vec3 best = null;
      double bestDist = -1.0;
      for (int attempt = 0; attempt < 10; attempt++) {
         float spread = attempt < 6 ? 1.0F : 0.4F;
         float yaw = this.mc.player.getYRot() + (this.worldRandom.nextFloat() - 0.5F) * (float)this.lyricWorldSpread.get().doubleValue() * spread;
         float height = (float)this.lyricWorldHeight.get().doubleValue();
         float pitch = Math.max(-75.0F, Math.min(75.0F, this.mc.player.getXRot() - (height + (this.worldRandom.nextFloat() - 0.5F) * 12.0F) * spread));
         net.minecraft.world.phys.Vec3 dir = net.minecraft.world.phys.Vec3.directionFromRotation(pitch, yaw);
         net.minecraft.world.phys.Vec3 side = dir.cross(new net.minecraft.world.phys.Vec3(0.0, 1.0, 0.0));
         side = side.lengthSqr() < 1.0E-4 ? new net.minecraft.world.phys.Vec3(1.0, 0.0, 0.0) : side.normalize();
         double base = this.lyricWorldDist.get();
         double want = base * (0.88 + this.worldRandom.nextDouble() * 0.24);
         // Mitte, beide Enden und Ober-/Unterkante der Zeile prüfen
         double free = want;
         for (int k = -1; k <= 1; k++) {
            for (int v = -1; v <= 1; v += 2) {
               net.minecraft.world.phys.Vec3 off = side.scale(k * width * 0.55).add(0.0, v * 0.35 * this.lyricWorldSize.get(), 0.0);
               net.minecraft.world.phys.Vec3 end = eye.add(dir.scale(want + 1.0)).add(off.scale((want + 1.0) / want));
               var hit = this.mc.level.clip(new net.minecraft.world.level.ClipContext(eye, end, net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, this.mc.player));
               if (hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
                  free = Math.min(free, hit.getLocation().distanceTo(eye) * want / (want + 1.0) - 1.0);
               }
            }
         }

         if (free >= want - 0.01) {
            return eye.add(dir.scale(want));
         }

         if (free > bestDist) {
            bestDist = free;
            best = eye.add(dir.scale(Math.max(1.5, free)));
         }
      }

      return best;
   }

   private static float easeOut(float x) {
      x = Math.max(0.0F, Math.min(1.0F, x));
      return 1.0F - (1.0F - x) * (1.0F - x) * (1.0F - x);
   }

   /**
    * Zeichnet eine Zeile. Jedes Wort: Einblenden, von leicht unten nach oben gleiten, sanft auf volle Größe; dazu ein
    * lila Leuchten, das beim Erscheinen weich und breit ist und sich dann zusammenzieht. {@code fadeOut} ≥ 0 = Sekunden,
    * seit die Zeile abgelöst wurde (Wörter schweben nacheinander nach oben weg).
    */
   private void drawWorldLine(com.lennart.nebula.util.WorldCtx ctx, WorldLine line, long now, long songPos, float fadeOut) {
      Font tr = this.mc.font;
      net.minecraft.world.phys.Vec3 cam = ctx.cameraPos();
      String motion = this.lyricWorldMotion.get();
      float lt = (now - line.born()) / 1000.0F;
      float drift = motion.equals("Ruhig") ? 0.0F : lt * 0.05F + (motion.equals("Schweben") ? (float)Math.sin(lt * 1.3F) * 0.06F : 0.0F);
      com.mojang.blaze3d.vertex.PoseStack m = ctx.matrices();
      m.pushPose();
      m.translate(line.pos().x - cam.x, line.pos().y - cam.y + drift, line.pos().z - cam.z);
      m.rotate(ctx.cameraOrientation());
      m.rotate(com.mojang.math.Axis.ZP.rotationDegrees(line.tilt()));
      float s = 0.028F * (float)this.lyricWorldSize.get().doubleValue();
      m.scale(s, -s, s);
      float anim = (float)(this.lyricWordAnim.get() / 1000.0);
      float glowK = (float)(this.lyricGlow.get() / 100.0);
      int colA = this.worldColorA.get() & 0xFFFFFF, colB = this.worldColorB.get() & 0xFFFFFF;
      int glowRgb = lerpRgb(colB, 0x7A3CFF, 0.35F);
      for (int i = 0; i < line.words().length; i++) {
         // etwas vor dem Singen starten, damit das Wort beim Singen schon fast fertig da ist
         float age = (songPos - line.appear()[i]) / 1000.0F + anim * 0.35F;
         if (age < 0.0F) {
            continue;
         }

         float e = easeOut(age / anim);
         float a = Math.min(1.0F, age / (anim * 0.7F));
         float rise = (1.0F - e) * 5.0F;
         float scale = 0.86F + 0.14F * e;
         if (fadeOut >= 0.0F) {
            float out = Math.max(0.0F, Math.min(1.0F, (fadeOut - i * 0.03F) / 0.35F));
            a *= 1.0F - out;
            rise -= out * 7.0F;
            scale += out * 0.08F;
         }

         if (a <= 0.01F) {
            continue;
         }

         int ww = line.ww()[i];
         if (motion.equals("Welle")) {
            rise += (float)Math.sin(lt * 2.6F - i * 0.7F) * 2.2F;
         }

         m.pushPose();
         m.translate(line.wx()[i] + ww / 2.0F, line.wy()[i] + rise, 0.0F);
         m.scale(scale, scale, scale);
         org.joml.Matrix4f mat = m.last().pose();
         float left = -ww / 2.0F;
         // Leuchten: vier versetzte Kopien in Lila, beim Erscheinen weit gestreut (weich), dann eng
         if (glowK > 0.0F) {
            float spread = 0.7F + (1.0F - e) * 2.6F;
            int ga = Math.round(Math.min(1.0F, a * glowK * (0.22F + 0.25F * (1.0F - e))) * 255.0F);
            if (ga > 3) {
               int g = ga << 24 | glowRgb;
               String bold = "§l" + line.words()[i];
               ctx.text(tr, bold, left - spread, -4.0F, g, false, mat, true, 0, 15728880);
               ctx.text(tr, bold, left + spread, -4.0F, g, false, mat, true, 0, 15728880);
               ctx.text(tr, bold, left, -4.0F - spread, g, false, mat, true, 0, 15728880);
               ctx.text(tr, bold, left, -4.0F + spread, g, false, mat, true, 0, 15728880);
            }
         }

         // Schrift: Verlauf hell → Lavendel über das Wort, mit Schatten für Lesbarkeit
         String[] chars = line.chars()[i];
         int[] cw = line.charW()[i];
         int alpha = Math.max(4, Math.round(a * 255.0F)) << 24;
         float cx = left;
         for (int c = 0; c < chars.length; c++) {
            float q = chars.length <= 1 ? 0.5F : (float)c / (chars.length - 1);
            int rgb = lerpRgb(colA, colB, q * 0.85F);
            // durch Blöcke hindurch sichtbar, damit nichts in Wänden verschwindet, wenn man sich bewegt
            ctx.text(tr, chars[c], cx, -4.0F, alpha | rgb, true, mat, true, 0, 15728880);
            cx += cw[c];
         }

         m.popPose();
      }

      m.popPose();
   }

   private static int lerpRgb(int a, int b, float t) {
      t = Math.max(0.0F, Math.min(1.0F, t));
      int r = Math.round((a >> 16 & 0xFF) + ((b >> 16 & 0xFF) - (a >> 16 & 0xFF)) * t);
      int g = Math.round((a >> 8 & 0xFF) + ((b >> 8 & 0xFF) - (a >> 8 & 0xFF)) * t);
      int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
      return r << 16 | g << 8 | bl;
   }
   /** Lyrics: aktuelle Zeile hell, davor/danach blasser; beim Zeilenwechsel rutscht der Text weich nach oben. */
   private void drawLyrics(GuiGraphicsExtractor ctx, boolean editing) {
      Font tr = this.mc.font;
      SpotifyLyrics src = this.lyricSource;
      List<SpotifyLyrics.Line> lines;
      int current;
      String hint = null;
      if (editing && !src.hasTimeline()) {
         lines = List.of(new SpotifyLyrics.Line(0, "Hier stehen die Lyrics"), new SpotifyLyrics.Line(1, "zeitgenau zum Song"), new SpotifyLyrics.Line(2, "aus Spotify"));
         current = 1;
      } else if (!src.windows()) {
         if (!editing) {
            return;
         }

         hint = "Lyrics gibt es nur unter Windows";
         lines = List.of();
         current = -1;
      } else if (!src.playing() && !editing) {
         // kein Lied oder pausiert: Lyrics ausblenden
         return;
      } else {
         SpotifyLyrics.Lyrics l = src.lyrics(src.title(), src.artist(), src.durationMs());
         if (l == null) {
            hint = "Lyrics werden geladen …";
            lines = List.of();
            current = -1;
         } else if (l.lines().isEmpty()) {
            hint = "Keine Lyrics gefunden";
            lines = List.of();
            current = -1;
         } else {
            lines = l.lines();
            current = l.synced() ? SpotifyLyrics.indexAt(lines, src.positionMs() + Math.round(this.lyricOffset.get())) : -1;
         }
      }

      if (!this.lyricLayoutV2.get()) {
         this.lyricLayoutV2.set(true);
         this.lyricBox.resetLayout();
         this.lyricLines.reset();
         this.lyricWidth.reset();
         this.lyricAlign.reset();
      }

      int n = this.lyricLines.getInt();
      int w = this.lyricBox.layoutWidth(this.lyricWidth.getInt());
      int lineH = 11;
      int h = hint != null ? 14 : n * lineH + 6;
      this.lyricBox.begin(ctx, w, h);
      if (hint != null) {
         int hw = tr.width(hint);
         int hx = switch (this.lyricAlign.get()) {
            case "Links" -> 3;
            case "Rechts" -> w - 3 - hw;
            default -> (w - hw) / 2;
         };
         ctx.text(tr, hint, hx, 3, this.lyricBox.fade(this.lyricDim.get()), true);
         this.lyricBox.end(ctx);
         return;
      }

      // weicher Zeilenwechsel
      long now = System.currentTimeMillis();
      if (current != this.lyricShown) {
         this.lyricShown = current;
         this.lyricSwitchAt = now;
      }

      float slide = 1.0F - Math.min(1.0F, (now - this.lyricSwitchAt) / 220.0F);
      slide = slide * slide;
      int center = Math.max(0, current);
      int maxW = w - 8;
      // von oben nach unten lesen: höchstens zwei Reihen davor, der Rest kommt danach
      int before = Math.min(2, (n - 1) / 2);
      int first = center;
      int rowsBefore = 0;

      while (first > 0 && rowsBefore + this.lyricRows(tr, lines.get(first - 1), maxW).size() <= before) {
         first--;
         rowsBefore += this.lyricRows(tr, lines.get(first), maxW).size();
      }

      // beim Wechsel rutscht die oberste Zeile nach oben weg und blendet aus
      int start = first;
      float y = 3.0F;
      if (first > 0 && slide > 0.0F) {
         start = first - 1;
         y -= this.lyricRows(tr, lines.get(start), maxW).size() * lineH * (1.0F - slide);
      }

      String align = this.lyricAlign.get();
      // Karaoke: Wörter der aktuellen Zeile leuchten in dem Moment auf, in dem sie gesungen werden
      long songPos = src.positionMs() + Math.round(this.lyricOffset.get());
      long[] times = current >= 0 && src.hasTimeline() ? this.hudWordTimes(lines, current) : null;
      int wordIndex = 0;

      for (int i = start; i < lines.size() && y + 9 <= h - 1; i++) {
         boolean cur = i == current;
         int base = this.lyricBox.fade(cur ? this.lyricColor.get() : this.lyricDim.get());
         int dim = this.lyricBox.fade(this.lyricDim.get());

         for (String row : this.lyricRows(tr, lines.get(i), maxW)) {
            if (y + 9 > h - 1) {
               break;
            }

            // nichts abschneiden: Reihen oberhalb des Felds blenden aus statt halb zu verschwinden
            float f = Math.max(0.0F, Math.min(1.0F, (y - 3.0F + lineH) / lineH));
            if (f > 0.04F) {
               Component c = cur ? Component.literal(row).withStyle(ChatFormatting.BOLD) : Component.literal(row);
               int rw = tr.width(c);
               int x = switch (align) {
                  case "Links" -> 4;
                  case "Rechts" -> w - 4 - rw;
                  default -> (w - rw) / 2;
               };
               int a = Math.round((base >>> 24) * f);
               if (cur && times != null) {
                  wordIndex = this.drawKaraokeRow(ctx, tr, row, x, Math.round(y), times, wordIndex, songPos, base, dim, f);
               } else {
                  ctx.text(tr, c, x, Math.round(y), a << 24 | base & 0xFFFFFF, true);
               }
            } else if (cur) {
               wordIndex += this.splitWords(row).length;
            }

            y += lineH;
         }
      }

      this.lyricBox.end(ctx);
   }

   private Object hudTimesSource;
   private int hudTimesIndex = -1;
   private long[] hudTimes;
   private final java.util.Map<String, String[]> wordSplit = new java.util.HashMap<>();

   /** Wort-Zeitpunkte der aktuellen Zeile (gecacht, nur beim Zeilen- oder Songwechsel neu berechnet). */
   private long[] hudWordTimes(List<SpotifyLyrics.Line> lines, int index) {
      if (this.hudTimes == null || this.hudTimesIndex != index || this.hudTimesSource != lines) {
         this.hudTimesSource = lines;
         this.hudTimesIndex = index;
         this.hudTimes = this.wordTimes(lines, index, this.splitWords(lines.get(index).text().trim()));
      }

      return this.hudTimes;
   }

   private String[] splitWords(String text) {
      String[] w = this.wordSplit.get(text);
      if (w == null) {
         if (this.wordSplit.size() > 400) {
            this.wordSplit.clear();
         }

         w = text.isEmpty() ? new String[0] : text.split("\\s+");
         this.wordSplit.put(text, w);
      }

      return w;
   }

   /**
    * Eine Reihe der aktuellen Zeile Wort für Wort: noch nicht gesungene Wörter blass, gesungene blenden in 150 ms auf die
    * volle Farbe und rutschen dabei einen Pixel hoch. Gibt den Wortzähler nach der Reihe zurück.
    */
   private int drawKaraokeRow(GuiGraphicsExtractor ctx, Font tr, String row, int x, int y, long[] times, int wordIndex, long songPos, int full, int dim, float f) {
      String[] words = this.splitWords(row);
      int space = tr.width(Component.literal(" ").withStyle(ChatFormatting.BOLD));
      float anim = (float)this.lyricWordAnim.get().doubleValue() * 0.55F;
      for (String word : words) {
         long at = wordIndex < times.length ? times[wordIndex] : Long.MAX_VALUE;
         float k = Math.max(0.0F, Math.min(1.0F, (songPos - at + 60.0F) / Math.max(40.0F, anim)));
         k = 1.0F - (1.0F - k) * (1.0F - k);
         int a = Math.round((((dim >>> 24) + ((full >>> 24) - (dim >>> 24)) * k)) * f);
         int rgb = lerpRgb(dim & 0xFFFFFF, full & 0xFFFFFF, k);
         Component c = Component.literal(word).withStyle(ChatFormatting.BOLD);
         int lift = k > 0.0F && k < 1.0F ? Math.round((1.0F - k) * 1.5F) : 0;
         ctx.text(tr, c, x, y + lift, Math.max(4, a) << 24 | rgb, true);
         x += tr.width(c) + space;
         wordIndex++;
      }

      return wordIndex;
   }

   /** Kasten: festes Format, großes Cover links, Titel und Interpret laufen durch, kleiner Equalizer. */
   private void drawBox(GuiGraphicsExtractor ctx, SpotifyOverlay.Track t, boolean showCover) {
      Font tr = this.mc.font;
      int textW = this.box.layoutWidth(this.maxWidth.getInt());
      boolean artist = this.showArtist.get() && !t.artist().isEmpty();
      int coverS = 30;
      int w = 4 + coverS + 6 + textW + 6;
      int h = coverS + 8;
      this.box.begin(ctx, w, h);
      int tx = 4 + coverS + 6;
      if (showCover) {
         ctx.blit(RenderPipelines.GUI_TEXTURED, COVER, 4, 4, 0.0F, 0.0F, coverS, coverS, 64, 64, 64, 64, this.box.fade(-1));
      } else {
         Render2D.roundRect(ctx, 4, 4, coverS, coverS, 4, this.box.fade(this.accent.get() & 16777215 | 0x50000000));
         ctx.pose().pushMatrix();
         ctx.pose().translate(4 + coverS / 2.0F, 4 + coverS / 2.0F - 6);
         ctx.pose().scale(1.6F, 1.6F);
         ctx.centeredText(tr, "♪", 0, 0, this.box.fade(this.accent.get()));
         ctx.pose().popMatrix();
      }

      int titleCol = this.box.fade(t.playing() ? -1 : -5592406);
      this.marquee(ctx, Component.literal(t.title()).withStyle(ChatFormatting.BOLD), tx, 6, textW, titleCol, 0L);
      if (artist) {
         this.marquee(ctx, Component.literal(t.artist()), tx, 17, textW, this.box.fade(-5000269), 700L);
      }

      // Equalizer-Balken (stehen still bei Pause)
      float time = (System.currentTimeMillis() - this.marqueeStart) / 1000.0F;
      int accent = this.accent.get() & 16777215;

      for (int i = 0; i < (this.showEq.get() ? 5 : 0); i++) {
         float level = t.playing() ? 0.35F + 0.65F * Math.abs((float)Math.sin(time * (3.1F + i * 0.9F) + i * 1.7F)) : 0.2F;
         int bh = Math.max(1, Math.round(6.0F * level));
         ctx.fill(tx + i * 3, h - 4 - bh, tx + i * 3 + 2, h - 4, this.box.fade(accent | 0xE0000000));
      }

      if (!t.playing() && this.showPausedHint.get()) {
         ctx.text(tr, "Pausiert", tx + (this.showEq.get() ? 18 : 0), h - 11, this.box.fade(-7829368), false);
      }

      if (this.showLine.get()) {
         Render2D.rect(ctx, 0, h - 1, w, 1, this.box.fade(accent | -1879048192));
      }
      this.box.end(ctx);
   }

   /** Karte (bisherige Darstellung), lange Titel laufen durch statt abgeschnitten zu werden. */
   private void drawCard(GuiGraphicsExtractor ctx, SpotifyOverlay.Track t, boolean showCover) {
      Font tr = this.mc.font;
      int maxW = this.maxWidth.getInt();
      Component title = Component.literal(t.title()).withStyle(ChatFormatting.BOLD);
      // feste Breite: die Karte springt nicht mehr mit jedem Song auf eine andere Größe
      int textW = this.box.layoutWidth(maxW);
      boolean artist = this.showArtist.get() && !t.artist().isEmpty();
      int iconW = showCover ? 22 : 10;
      int h = artist ? 26 : 16;
      this.box.begin(ctx, iconW + textW + 12, h);
      this.drawIcon(ctx, showCover, 3, artist ? 3 : 2, artist ? 20 : 12);
      this.marquee(ctx, title, iconW + 7, artist ? 4 : 4, textW, this.box.fade(t.playing() ? -1 : -5592406), 0L);
      if (artist) {
         this.marquee(ctx, Component.literal(t.artist()), iconW + 7, 15, textW, this.box.fade(-5000269), 700L);
      }

      if (this.showLine.get()) {
         Render2D.rect(ctx, 0, h - 1, Math.max(4, iconW + textW + 12), 1, this.box.fade(this.accent.get() & 16777215 | -1879048192));
      }
      this.box.end(ctx);
   }

   private void drawCompact(GuiGraphicsExtractor ctx, SpotifyOverlay.Track t, boolean showCover) {
      Font tr = this.mc.font;
      int maxW = this.maxWidth.getInt();
      String line = t.artist().isEmpty() || !this.showArtist.get() ? t.title() : t.title() + " – " + t.artist();
      int textW = this.box.layoutWidth(maxW + 40);
      int iconW = showCover ? 12 : 10;
      int h = 14;
      this.box.begin(ctx, iconW + textW + 10, h);
      this.drawIcon(ctx, showCover, 3, 1, 12);
      this.marquee(ctx, Component.literal(line), iconW + 6, 3, textW, this.box.fade(t.playing() ? -1 : -5592406), 0L);
      if (this.showLine.get()) {
         Render2D.rect(ctx, 0, h - 1, Math.max(4, iconW + textW + 10), 1, this.box.fade(this.accent.get() & 16777215 | -1879048192));
      }
      this.box.end(ctx);
   }

   /**
    * Text in einer festen Breite: passt er nicht hinein, läuft er nach einer kurzen Pause gleichmäßig durch und beginnt
    * nahtlos von vorn.
    */
   private void marquee(GuiGraphicsExtractor ctx, Component text, int x, int y, int width, int color, long delay) {
      Font tr = this.mc.font;
      int tw = tr.width(text);
      if (tw <= width || !this.scrollText.get()) {
         if (tw <= width) {
            ctx.text(tr, text, x, y, color, true);
         } else {
            String cut = tr.plainSubstrByWidth(text.getString(), width - 6) + "…";
            ctx.text(tr, Component.literal(cut).withStyle(text.getStyle()), x, y, color, true);
         }

         return;
      }

      float speed = (float)this.scrollSpeed.get().doubleValue();
      int gap = 28;
      float cycle = (tw + gap) / speed;
      float pause = 1.6F;
      float u = ((System.currentTimeMillis() - this.marqueeStart - delay) / 1000.0F) % (cycle + pause);
      float off = Math.max(0.0F, u - pause) * speed;
      if (u < 0.0F) {
         off = 0.0F;
      }

      ctx.enableScissor(x, y - 1, x + width, y + 10);
      ctx.pose().pushMatrix();
      ctx.pose().translate(x - off, y);
      ctx.text(tr, text, 0, 0, color, true);
      ctx.text(tr, text, tw + gap, 0, color, true);
      ctx.pose().popMatrix();
      ctx.disableScissor();
   }
   private void drawIcon(GuiGraphicsExtractor ctx, boolean showCover, int x, int y, int size) {
      if (showCover) {
         ctx.blit(RenderPipelines.GUI_TEXTURED, COVER, x, y, 0.0F, 0.0F, size, size, 64, 64, 64, 64, this.box.fade(-1));
      } else {
         ctx.text(this.mc.font, "♪", x, y + (size - 8) / 2, this.box.fade(this.accent.get()), true);
      }
   }

   public record Track(String title, String artist, boolean playing, String artUrl) {
      String key() {
         return this.artist + "\n" + this.title;
      }
   }
}
