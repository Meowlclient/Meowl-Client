package com.lennart.nebula.afk.pc;

import com.lennart.nebula.NebulaClient;
import com.lennart.nebula.afk.space.McSpaceCanvas;
import com.lennart.nebula.afk.space.Tex;
import com.lennart.nebula.module.hud.SpotifyOverlay;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import org.joml.Matrix3x2f;

/**
 * AFK-Szene „PC-Setup“: dein Gaming-Zimmer aus echten Minecraft-Blöcken (einmal per Raytracing gebaut, scharf in
 * Pixel-Optik). Links Discord, rechts Spotify mit deinem laufenden Song, in der Mitte läuft ein echtes kleines
 * Shooter-Spiel aus Blöcken – mit Gegnern in Minecraft-Skins, die laufen, zielen und umfallen. Dazu RGB-Licht, Partikel
 * und deine Hände an Tastatur und Maus.
 */
public final class PcScene {
   public static final String[] THEMES = new String[]{"Neon Modern", "Cozy Wooden", "Nether Red", "Nature Vibes", "End Dimension", "Snow Base"};
   private static final String ROOM = "pc_room";
   private static final String LED = "pc_led";
   private static final String KEYS = "pc_keys";
   private static final String GAME = "pc_game";
   private final McSpaceCanvas canvas = new McSpaceCanvas();
   private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
      Thread t = new Thread(r, "Meowl-AFK-PC");
      t.setDaemon(true);
      return t;
   });
   private String themeName;
   private PcTheme theme;
   private CompletableFuture<Built> building;
   private Built built;
   private boolean uploaded;
   private PcGame game;
   private volatile int[] frame;
   private volatile boolean rendering;
   private PcGame.Snapshot shown;
   private float time;
   private String trackKey = "";
   private volatile Tex composed;
   private volatile boolean composing;
   // Spotify
   private static volatile SpotifyOverlay.Track track;
   private static volatile long trackAt;
   private static volatile boolean polling;

   private record Built(PcRoom room, PcFont font, Tex discord, Map<String, Tex> tex, PcHud hud) {
   }

   public void restart() {
      this.time = 0.0F;
   }

   public void destroy() {
      this.canvas.destroy();
      this.uploaded = false;
      this.built = null;
      this.building = null;
      this.themeName = null;
   }

   // ================================================================== Aufbau

   private void prepare(String name) {
      if (!name.equals(this.themeName)) {
         this.destroy();
         this.themeName = name;
         this.theme = PcTheme.of(name);
         List<String> paths = new ArrayList<>(this.theme.textures());
         paths.addAll(PcGame.textures());
         paths.add("font/ascii");
         Map<String, Tex> tex = PcAssets.load(paths);
         PcTheme th = this.theme;
         this.building = CompletableFuture.supplyAsync(() -> {
            PcRoom room = new PcRoom(tex, th);
            room.render();
            PcFont font = new PcFont(tex.get("font/ascii"));
            return new Built(room, font, PcScreens.discord(font), tex, new PcHud(font, tex));
         });
      }
   }

   private boolean ready() {
      if (this.built == null && this.building != null && this.building.isDone()) {
         try {
            this.built = this.building.join();
            if (this.game == null) {
               this.game = new PcGame(this.built.tex());
            }
         } catch (RuntimeException var2) {
            NebulaClient.LOGGER.warn("AFK-PC-Setup konnte nicht gebaut werden", var2);
            this.building = null;
            this.themeName = null;
         }
      }

      return this.built != null;
   }

   /** Zimmerbild mit Discord und Spotify (neu, wenn sich der Song ändert). */
   private void compose(SpotifyOverlay.Track tr, int[] cover) {
      Built b = this.built;
      this.composing = true;
      this.worker.submit(() -> {
         try {
            Tex img = new Tex(PcRoom.VW, PcRoom.VH, b.room().image.px.clone());
            b.room().paintScreen(img, PcRoom.SCREEN_LEFT, b.discord(), 1.0F);
            Tex spot = PcScreens.spotify(b.font(), tr == null ? null : tr.title(), tr == null ? null : tr.artist(), tr != null && tr.playing(), cover);
            b.room().paintScreen(img, PcRoom.SCREEN_RIGHT, spot, 1.0F);
            this.composed = img;
         } catch (RuntimeException var6) {
            NebulaClient.LOGGER.debug("PC-Bildschirme: {}", var6.toString());
         } finally {
            this.composing = false;
         }
      });
   }

   private static void pollSpotify() {
      long now = System.currentTimeMillis();
      if (!polling && now - trackAt > 3000L) {
         polling = true;
         trackAt = now;
         CompletableFuture.runAsync(() -> {
            try {
               track = SpotifyOverlay.readTrack();
            } catch (Exception var4) {
               track = null;
            } finally {
               polling = false;
            }
         });
      }
   }

   // ================================================================== Zeichnen

   public void render(GuiGraphicsExtractor ctx, int w, int h, float dt, String name, float alpha) {
      this.prepare(name);
      Minecraft mc = Minecraft.getInstance();
      Font font = mc.font;
      if (!this.ready()) {
         ctx.fill(0, 0, w, h, 0xFF000000);
         ctx.centeredText(font, "Baue dein Zimmer …", w / 2, h / 2, 0xFF808080);
         return;
      }

      this.time += dt;
      float t = this.time;
      McSpaceCanvas c = this.canvas.bind(ctx);
      PcRoom room = this.built.room();
      if (!this.uploaded) {
         c.upload(ROOM, room.image);
         c.upload(LED, room.ledLight);
         c.upload(KEYS, room.keyLight);
         this.uploaded = true;
         this.trackKey = "";
      }

      // Song gewechselt → Bildschirme neu zeichnen
      pollSpotify();
      SpotifyOverlay.Track tr = track;
      String key = tr == null ? "-" : tr.title() + "|" + tr.artist() + "|" + tr.playing() + "|" + (SpotifyOverlay.coverPixels() != null);
      if (!key.equals(this.trackKey) && !this.composing) {
         this.trackKey = key;
         this.compose(tr, SpotifyOverlay.coverPixels());
      }

      Tex comp = this.composed;
      if (comp != null) {
         this.composed = null;
         c.upload(ROOM, comp);
      }

      // Bild füllt den Bildschirm (Seiten notfalls abgeschnitten)
      float s = Math.max((float)w / PcRoom.VW, (float)h / PcRoom.VH);
      float ox = (w - PcRoom.VW * s) / 2.0F;
      float oy = (h - PcRoom.VH * s) / 2.0F;
      c.image(ROOM, w / 2.0F, h / 2.0F, PcRoom.VW * s, PcRoom.VH * s, 0.0F, 0.0F, PcRoom.VW, PcRoom.VH, 0.0F, false, -1);
      // LED-Licht pulsiert und wechselt die Farbe, Tastatur läuft als Farbwelle durch
      float pulse = 0.55F + 0.45F * (float)Math.sin(t * 1.6F);
      int ledCol = Tex.mix(this.theme.accent, this.theme.accent2, 0.5F + 0.5F * (float)Math.sin(t * 0.7F));
      c.image(LED, w / 2.0F, h / 2.0F, PcRoom.VW * s, PcRoom.VH * s, 0.0F, 0.0F, PcRoom.VW, PcRoom.VH, 0.0F, false, Math.round(255 * pulse) << 24 | ledCol & 16777215);
      int keyCol = 0xFF000000 | java.awt.Color.HSBtoRGB(t * 0.15F, 0.85F, 1.0F) & 16777215;
      c.image(KEYS, w / 2.0F, h / 2.0F, PcRoom.VW * s, PcRoom.VH * s, 0.0F, 0.0F, PcRoom.VW, PcRoom.VH, 0.0F, false, 0xE0000000 | keyCol & 16777215);
      // Spiel auf dem mittleren Bildschirm
      int[] r = room.midRect;
      float gx = ox + r[0] * s;
      float gy = oy + r[1] * s;
      float gw = (r[2] - r[0]) * s;
      float gh = (r[3] - r[1]) * s;
      this.game(ctx, c, mc, font, gx, gy, gw, gh, dt);
      // Partikel, dann Hände
      this.particles(ctx, w, h, t);
      ctx.nextStratum();
      this.hands(ctx, mc, ox, oy, s, t);
   }

   private void game(GuiGraphicsExtractor ctx, McSpaceCanvas c, Minecraft mc, Font font, float x, float y, float w, float h, float dt) {
      PcGame g = this.game;
      g.update(dt);
      PcGame.Snapshot snap = g.snapshot();
      Built b = this.built;
      if (!this.rendering) {
         this.rendering = true;
         this.worker.submit(() -> {
            try {
               Tex fr = new Tex(PcGame.W, PcGame.H, g.render(snap));
               b.hud().draw(fr, g, snap);
               this.frame = this.toScreen(b.room(), fr);
            } catch (RuntimeException var6) {
               NebulaClient.LOGGER.debug("PC-Spiel: {}", var6.toString());
            } finally {
               this.rendering = false;
            }
         });
      }

      int[] f = this.frame;
      if (f != null) {
         this.frame = null;
         int[] r = b.room().midRect;
         c.upload(GAME, new Tex(r[2] - r[0], r[3] - r[1], f));
      }

      if (c.has(GAME)) {
         int[] r = b.room().midRect;
         c.image(GAME, x + w / 2.0F, y + h / 2.0F, w, h, 0.0F, 0.0F, r[2] - r[0], r[3] - r[1], 0.0F, false, -1);
      }
   }

   /** Spielbild perspektivisch auf den mittleren Bildschirm legen (nur dessen Pixel, Rest durchsichtig). */
   private int[] toScreen(PcRoom room, Tex frame) {
      int[] r = room.midRect;
      int bw = r[2] - r[0];
      int bh = r[3] - r[1];
      int[] out = new int[bw * bh];

      for (int yy = 0; yy < bh; yy++) {
         for (int xx = 0; xx < bw; xx++) {
            int i = (r[1] + yy) * PcRoom.VW + r[0] + xx;
            if (room.screen[i] == PcRoom.SCREEN_MID) {
               int col = frame.get((int)(room.su[i] * frame.w), (int)(room.sv[i] * frame.h));
               out[yy * bw + xx] = col | 0xFF000000;
            }
         }
      }

      return out;
   }
   private void flash(GuiGraphicsExtractor ctx, float x, float y, float r, int color) {
      for (int i = 4; i >= 1; i--) {
         float rr = r * i / 4.0F;
         ctx.fill(Math.round(x - rr), Math.round(y - rr), Math.round(x + rr), Math.round(y + rr), (60 + (4 - i) * 40) << 24 | color & 16777215);
      }
   }

   /** Text zentriert bei (cx, y), Skalierung {@code s}. */
   private void text(GuiGraphicsExtractor ctx, Font font, String s, float cx, float y, float scale, int color, boolean center) {
      ctx.pose().pushMatrix();
      ctx.pose().translate(cx, y);
      ctx.pose().scale(scale, scale);
      ctx.text(font, s, center ? -font.width(s) / 2 : 0, 0, color, true);
      ctx.pose().popMatrix();
   }

   private void hud(GuiGraphicsExtractor ctx, Font font, float x, float y, float w, float h, PcGame g, float t) {
      float ts = Math.max(0.35F, h / 190.0F);
      // oben: Punkte und Rundenzeit
      int sec = Math.max(0, (int)g.roundTime);
      String clock = String.format("%d:%02d", sec / 60, sec % 60);
      float cx = x + w / 2.0F;
      float bw = font.width(clock) * ts + 8;
      ctx.fill(Math.round(cx - bw / 2), Math.round(y + 2), Math.round(cx + bw / 2), Math.round(y + 3 + 10 * ts), 0xB0101418);
      this.text(ctx, font, clock, cx, y + 3, ts, 0xFFFFFFFF, true);
      this.text(ctx, font, String.valueOf(g.scoreUs), cx - bw / 2 - 8 * ts, y + 3, ts * 1.1F, 0xFF40E0C0, true);
      this.text(ctx, font, String.valueOf(g.scoreThem), cx + bw / 2 + 8 * ts, y + 3, ts * 1.1F, 0xFFFF5060, true);
      // unten links Leben, unten rechts Munition
      this.text(ctx, font, String.valueOf(g.health), x + 6, y + h - 13 * ts, ts * 1.4F, 0xFFFFFFFF, false);
      ctx.fill(Math.round(x + 6), Math.round(y + h - 3), Math.round(x + 6 + w * 0.16F), Math.round(y + h - 2), 0x60FFFFFF);
      ctx.fill(Math.round(x + 6), Math.round(y + h - 3), Math.round(x + 6 + w * 0.16F * g.health / 100.0F), Math.round(y + h - 2), 0xFF40E0C0);
      String am = g.reloadUntil > 0.0F ? "Lädt …" : g.ammo + " / 75";
      this.text(ctx, font, am, x + w - 6 - font.width(am) * ts * 1.4F, y + h - 13 * ts, ts * 1.4F, 0xFFFFFFFF, false);
      // Minikarte oben links
      int n = PcGame.mapSize();
      float ms = h * 0.26F / n;
      float mx = x + 4;
      float my = y + 4;
      ctx.fill(Math.round(mx), Math.round(my), Math.round(mx + n * ms), Math.round(my + n * ms), 0x70101418);

      for (int zz = 0; zz < n; zz++) {
         for (int xx = 0; xx < n; xx++) {
            if (g.wall(xx, zz)) {
               ctx.fill(Math.round(mx + xx * ms), Math.round(my + zz * ms), Math.round(mx + (xx + 1) * ms), Math.round(my + (zz + 1) * ms), 0x70D8C090);
            }
         }
      }

      float cs = PcGame.cellSize();
      float px = mx + g.px() / cs * ms;
      float pz = my + g.pz() / cs * ms;
      ctx.fill(Math.round(px - 1), Math.round(pz - 1), Math.round(px + 2), Math.round(pz + 2), 0xFF40E0C0);

      for (float[] e : g.enemyDots()) {
         if (e[2] > 0.5F) {
            float ex = mx + e[0] / cs * ms;
            float ez = my + e[1] / cs * ms;
            ctx.fill(Math.round(ex - 1), Math.round(ez - 1), Math.round(ex + 2), Math.round(ez + 2), 0xFFFF5060);
         }
      }

      // Abschuss-Meldung oben rechts
      if (t - g.lastKill < 3.5F && !g.feed.isEmpty()) {
         String fe = g.feed + (g.headshot ? "  ◎" : "");
         float fw = font.width(fe) * ts;
         float fx = x + w - fw - 6;
         float fy = y + h * 0.1F;
         boolean mine = g.feed.startsWith("Du");
         ctx.fill(Math.round(fx - 3), Math.round(fy - 2), Math.round(x + w - 3), Math.round(fy + 9 * ts + 2), 0xB0101418);
         ctx.fill(Math.round(fx - 3), Math.round(fy - 2), Math.round(fx - 2), Math.round(fy + 9 * ts + 2), mine ? 0xFF40E0C0 : 0xFFFF5060);
         this.text(ctx, font, fe, fx, fy, ts, 0xFFFFFFFF, false);
      }

      // Fähigkeiten unten in der Mitte
      for (int i = 0; i < 4; i++) {
         float ax = cx - 2.2F * 10 * ts + i * 11 * ts;
         ctx.fill(Math.round(ax), Math.round(y + h - 11 * ts), Math.round(ax + 9 * ts), Math.round(y + h - 3), i == 3 ? 0xC0FFD040 : 0x90303840);
      }
   }

   private static Identifier block(String name) {
      return Identifier.withDefaultNamespace("textures/block/" + name + ".png");
   }

   /** Rechteck mit Blocktextur (scharf, 1 Texel ≈ {@code px} Bildpunkte). */
   private static void texRect(GuiGraphicsExtractor ctx, String name, float x0, float y0, float x1, float y1, float px) {
      int w = Math.max(1, Math.round(x1 - x0));
      int h = Math.max(1, Math.round(y1 - y0));
      int rw = Math.max(1, Math.min(16, Math.round(w / Math.max(0.5F, px))));
      int rh = Math.max(1, Math.min(16, Math.round(h / Math.max(0.5F, px))));
      ctx.blit(RenderPipelines.GUI_TEXTURED, block(name), Math.round(x0), Math.round(y0), 0.0F, 0.0F, w, h, rw, rh, 16, 16);
   }

   /** Gewehr unten rechts aus Blocktexturen, gehalten von deiner Hand. */
   private void gun(GuiGraphicsExtractor ctx, Minecraft mc, float x, float y, float w, float h, PcGame g, float t) {
      float k = h / 144.0F;
      float bob = g.moving() ? (float)Math.sin(g.walkPhase()) * 3.0F * k : (float)Math.sin(t * 1.5F) * k;
      float kick = Math.max(0.0F, 1.0F - (t - g.lastShot) / 0.1F);
      float reload = g.reloadUntil > 0.0F ? (float)Math.sin(Math.min(1.0F, (g.reloadUntil - t) / 2.0F) * Math.PI) : 0.0F;
      float gx = x + w * 0.66F + kick * 3.0F * k;
      float gy = y + h * 0.66F + bob + kick * 4.0F * k + reload * h * 0.25F;
      float px = 1.6F * k;
      ctx.pose().pushMatrix();
      ctx.pose().translate(gx, gy);
      ctx.pose().rotate(-0.42F - kick * 0.08F + reload * 0.5F);
      // Lauf, Gehäuse, Magazin, Visier, Schaft
      texRect(ctx, "iron_block", -6 * px, -1 * px, 8 * px, 2 * px, px);
      texRect(ctx, "black_concrete", 8 * px, -3 * px, 30 * px, 5 * px, px);
      texRect(ctx, "gray_concrete", 14 * px, 5 * px, 19 * px, 14 * px, px);
      texRect(ctx, "black_concrete", 18 * px, -6 * px, 24 * px, -3 * px, px);
      texRect(ctx, "polished_blackstone", 30 * px, -2 * px, 44 * px, 6 * px, px);
      ctx.fill(Math.round(8 * px), Math.round(-3 * px), Math.round(30 * px), Math.round(-2 * px), 0x8040E0C0);
      if (kick > 0.4F) {
         this.flash(ctx, -8 * px, 0.5F * px, 6 * px, 0xFFFFE080);
      }

      ctx.pose().popMatrix();
      // Hand am Griff (Skin)
      if (mc.player != null) {
         var skin = mc.player.getSkin();
         Identifier tex = skin.body().texturePath();
         int aw = skin.model() == PlayerModelType.SLIM ? 3 : 4;
         ctx.pose().pushMatrix();
         ctx.pose().translate(gx + 22 * px * 0.9F, gy + 10 * px * 0.4F);
         ctx.pose().rotate(-0.42F + 0.9F);
         ctx.pose().scale(px * 2.0F, px * 2.6F);
         ctx.blit(RenderPipelines.GUI_TEXTURED, tex, 0, 0, 44.0F, 20.0F, aw, 12, aw, 12, 64, 64);
         ctx.blit(RenderPipelines.GUI_TEXTURED, tex, 0, 0, 44.0F, 36.0F, aw, 12, aw, 12, 64, 64);
         ctx.pose().popMatrix();
      }
   }

   private void particles(GuiGraphicsExtractor ctx, int w, int h, float t) {
      int fx = this.theme.fx;
      if (fx == PcTheme.FX_NONE) {
         return;
      }

      Random r = new Random(1234L);

      for (int i = 0; i < 60; i++) {
         float sx = r.nextFloat() * w;
         float speed = 8.0F + r.nextFloat() * 18.0F;
         float phase = r.nextFloat() * h;
         int size = 1 + r.nextInt(2);
         switch (fx) {
            case PcTheme.FX_SNOW: {
               float py = (phase + t * speed) % h;
               float px = sx + (float)Math.sin(t + i) * 6.0F;
               ctx.fill(Math.round(px), Math.round(py), Math.round(px) + size + 1, Math.round(py) + size + 1, 0xC0FFFFFF);
               break;
            }
            case PcTheme.FX_EMBERS: {
               float py = h - (phase + t * speed * 1.5F) % h;
               float px = sx + (float)Math.sin(t * 2.0F + i) * 4.0F;
               int c = Tex.mix(0xFFFF6020, 0xFFFFD040, r.nextFloat());
               ctx.fill(Math.round(px), Math.round(py), Math.round(px) + size, Math.round(py) + size, Math.round(200 * (py / h)) << 24 | c & 16777215);
               break;
            }
            case PcTheme.FX_END: {
               float py = h - (phase + t * speed * 0.5F) % h;
               float tw = 0.5F + 0.5F * (float)Math.sin(t * 3.0F + i);
               ctx.fill(Math.round(sx), Math.round(py), Math.round(sx) + size, Math.round(py) + size, Math.round(220 * tw) << 24 | 0xE080FF);
               break;
            }
            case PcTheme.FX_FIREFLIES: {
               float px = sx + (float)Math.sin(t * 0.5F + i) * 20.0F;
               float py = phase * 0.7F + (float)Math.cos(t * 0.7F + i) * 12.0F;
               float tw = Math.max(0.0F, (float)Math.sin(t * 2.0F + i * 1.7F));
               ctx.fill(Math.round(px) - 1, Math.round(py) - 1, Math.round(px) + 2, Math.round(py) + 2, Math.round(60 * tw) << 24 | 0xE8FF70);
               ctx.fill(Math.round(px), Math.round(py), Math.round(px) + 1, Math.round(py) + 1, Math.round(230 * tw) << 24 | 0xF8FFB0);
               break;
            }
            default: {
               float py = (phase + t * speed * 0.2F) % h;
               float px = sx + (float)Math.sin(t * 0.3F + i) * 10.0F;
               ctx.fill(Math.round(px), Math.round(py), Math.round(px) + 1, Math.round(py) + 1, 0x60FFE0B0);
            }
         }
      }
   }

   /** Deine Arme als echte Minecraft-Arme (4 x 4 x 12 Skin-Pixel) in 3D: links tippt auf der Tastatur, rechts an der Maus. */
   private void hands(GuiGraphicsExtractor ctx, Minecraft mc, float ox, float oy, float s, float t) {
      if (mc.player == null) {
         return;
      }

      var skin = mc.player.getSkin();
      Identifier tex = skin.body().texturePath();
      boolean slim = skin.model() == PlayerModelType.SLIM;

      for (float[] q : armFaces(slim, t)) {
         ctx.pose().pushMatrix();
         float p0x = ox + q[0] * s;
         float p0y = oy + q[1] * s;
         float p1x = ox + q[2] * s;
         float p1y = oy + q[3] * s;
         float p3x = ox + q[4] * s;
         float p3y = oy + q[5] * s;
         int w = (int)q[8];
         int h = (int)q[9];
         ctx.pose().mul(new Matrix3x2f((p1x - p0x) / w, (p1y - p0y) / w, (p3x - p0x) / h, (p3y - p0y) / h, p0x, p0y));
         int sh = Math.round(255 * q[10]);
         ctx.blit(RenderPipelines.GUI_TEXTURED, tex, 0, 0, q[6], q[7], w, h, w, h, 64, 64, 0xFF000000 | sh << 16 | sh << 8 | sh);
         ctx.pose().popMatrix();
      }
   }

   /**
    * Flächen beider Arme im Zimmerbild (Pixel): je {p0x, p0y, p1x, p1y, p3x, p3y, u, v, Breite, Höhe, Helligkeit} – p0 Ecke an
    * der Schulter, p1 quer, p3 längs zur Hand. Haut zuerst, dann Ärmel-Ebene.
    */
   public static List<float[]> armFaces(boolean slim, float t) {
      List<float[]> out = new ArrayList<>();
      float type = (float)Math.sin(t * 11.0F) * 0.012F + (float)Math.sin(t * 7.3F) * 0.01F;
      float aim = (float)Math.sin(t * 0.8F) * 0.04F + (float)Math.sin(t * 2.3F) * 0.012F;
      float wid = slim ? 0.17F : 0.22F;
      arm(out, slim, false, -0.3F, 1.09F + type, 0.24F, -0.82F, 0.86F, -0.55F, wid);
      arm(out, slim, true, 1.1F + aim, 1.1F, 0.3F, 1.2F, 0.86F, -0.52F, wid);
      return out;
   }

   private static void arm(List<float[]> out, boolean slim, boolean right, float hx, float hy, float hz, float bx, float by, float bz, float wid) {
      float dx = hx - bx;
      float dy = hy - by;
      float dz = hz - bz;
      float len = (float)Math.sqrt(dx * dx + dy * dy + dz * dz);
      dx /= len;
      dy /= len;
      dz /= len;
      // Seite (waagerecht) und Oberseite des Arms
      float sx = dz;
      float sz = -dx;
      float sl = (float)Math.sqrt(sx * sx + sz * sz);
      sx /= sl;
      sz /= sl;
      float ux = -sz * dy;
      float uy = sz * dx - sx * dz;
      float uz = sx * dy;
      if (uy < 0.0F) {
         ux = -ux;
         uy = -uy;
         uz = -uz;
      }

      float hw = wid / 2.0F;
      int aw = slim ? 3 : 4;
      int u0 = right ? 40 : 32;
      int v0 = right ? 16 : 48;
      int ou0 = right ? 40 : 48;
      int ov0 = right ? 32 : 48;
      // Flächen: Oberseite (Vorderseite der Skin), beide Seiten
      float[][] normals = {{ux, uy, uz}, {sx, 0.0F, sz}, {-sx, 0.0F, -sz}};
      int[][] regions = {{u0 + 4, v0 + 4, aw}, {u0, v0 + 4, 4}, {u0 + 4 + aw, v0 + 4, 4}};
      float[] shades = {1.0F, 0.72F, 0.62F};

      for (int k = 0; k < 3; k++) {
         float[] n = normals[k];
         float cx = (bx + hx) / 2.0F + n[0] * hw;
         float cy = (by + hy) / 2.0F + n[1] * hw;
         float cz = (bz + hz) / 2.0F + n[2] * hw;
         float facing = n[0] * (PcRoom.CAM.x - cx) + n[1] * (PcRoom.CAM.y - cy) + n[2] * (PcRoom.CAM.z - cz);
         if (facing <= 0.0F) {
            continue;
         }

         float ax = k == 0 ? sx : ux;
         float ay = k == 0 ? 0.0F : uy;
         float az = k == 0 ? sz : uz;
         float px = bx + n[0] * hw - ax * hw;
         float py = by + n[1] * hw - ay * hw;
         float pz = bz + n[2] * hw - az * hw;
         float[] p0 = proj(px, py, pz);
         float[] p1 = proj(px + ax * wid, py + ay * wid, pz + az * wid);
         float[] p3 = proj(px + dx * len, py + dy * len, pz + dz * len);
         int[] reg = regions[k];
         out.add(new float[]{p0[0], p0[1], p1[0], p1[1], p3[0], p3[1], reg[0], reg[1], reg[2], 12, shades[k]});
         out.add(new float[]{p0[0], p0[1], p1[0], p1[1], p3[0], p3[1], reg[0] - u0 + ou0, reg[1] - v0 + ov0, reg[2], 12, shades[k]});
      }
   }

   private static float[] proj(float x, float y, float z) {
      float[] p = new float[3];
      PcRoom.CAM.project(x, y, z, p);
      return new float[]{p[0], p[1]};
   }}
