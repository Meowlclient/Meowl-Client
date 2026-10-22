package com.lennart.nebula.module.hud;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lennart.nebula.gui.Render2D;
import com.lennart.nebula.images.ImageGeometry;
import com.lennart.nebula.module.Category;
import com.lennart.nebula.module.Module;
import com.lennart.nebula.module.Section;
import com.lennart.nebula.net.MeowlService;
import com.lennart.nebula.setting.ActionSetting;
import com.lennart.nebula.setting.Bind;
import com.lennart.nebula.setting.BooleanSetting;
import com.lennart.nebula.setting.KeybindSetting;
import com.lennart.nebula.setting.NumberSetting;
import com.lennart.nebula.setting.Setting;
import com.lennart.nebula.setting.TextSetting;
import com.lennart.nebula.util.ChatUtil;
import com.lennart.nebula.util.DynamicTextures;
import com.lennart.nebula.util.WorldCtx;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.SourceDataLine;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Streaming (Watchparty): Link zu einem YouTube-Video, Twitch-Stream oder TikTok eintragen und einen Bildschirm an die
 * Wand hängen. Der Meowl Server spielt das Video ab und schickt allen Meowl-Spielern in der Nähe dasselbe Bild und
 * denselben Ton – bei allen gleichzeitig, ohne dass bei jemandem ein Fenster aufgeht.
 */
public class Streaming extends Module {
   private static final Identifier SCREEN = Identifier.fromNamespaceAndPath("nebula", "stream/screen");
   private static final Set<String> HOSTS = Set.of(
      "twitch.tv", "www.twitch.tv", "clips.twitch.tv", "youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be", "tiktok.com", "www.tiktok.com", "vm.tiktok.com", "m.tiktok.com"
   );
   private final TextSetting link = this.add(new TextSetting("Link", "YouTube-Video, Twitch-Stream oder TikTok", "", 300));
   private final NumberSetting volume = this.add(new NumberSetting("Lautstärke", "Lautstärke der Videos (wird mit Abstand zum Bildschirm leiser)", 70.0, 0.0, 100.0, 5.0, "%"));
   private final NumberSetting fps = this.add(new NumberSetting("Bildrate", "Wie viele Bilder pro Sekunde höchstens geholt werden (Server liefert bis 30)", 30.0, 4.0, 30.0, 1.0, "fps"));
   private final BooleanSetting showOthers = this.add(new BooleanSetting("Videos anderer zeigen", "Bildschirme anderer Meowl-Spieler zeigen und mitschauen", true));
   private final BooleanSetting hudShow = this.add(new BooleanSetting("Im HUD zeigen", "Das laufende Video zusätzlich klein im HUD (im HUD-Editor verschieben)", false));
   private final NumberSetting hudWidth = this.add(new NumberSetting("HUD-Breite", "Breite des Videos im HUD", 160.0, 80.0, 480.0, 5.0, "px"));
   private final HudBox box = this.hudBox("Stream", 98.0, 4.0);
   private final KeybindSetting placeKey = this.add(
      new KeybindSetting("Platzier-Taste", "Platzier-Modus an/aus: Rechtsklick hängt den Bildschirm an die Wand, Linksklick nimmt ihn ab", Bind.NONE)
   );
   private final KeybindSetting seekBackKey = this.add(
      new KeybindSetting("Zurückspulen", "Auf den Bildschirm schauen und drücken: 10 Sekunden zurück (für alle)", Bind.keyboard(263))
   );
   private final KeybindSetting seekFwdKey = this.add(
      new KeybindSetting("Vorspulen", "Auf den Bildschirm schauen und drücken: 10 Sekunden vor (für alle)", Bind.keyboard(262))
   );
   private final NumberSetting seekStep = this.add(new NumberSetting("Spul-Schritt", "Wie weit pro Tastendruck gespult wird", 10.0, 5.0, 60.0, 5.0, "s"));
   private final NumberSetting screenWidth = this.add(new NumberSetting("Bildschirm-Breite", "Breite des Wand-Bildschirms (Höhe im 16:9-Format)", 4.0, 1.0, 16.0, 0.5, "Blöcke"));
   private final Map<String, Streaming.Wall> walls = new ConcurrentHashMap<>();
   private final ExecutorService net = Executors.newFixedThreadPool(2, r -> {
      Thread t = new Thread(r, "Meowl-Watch");
      t.setDaemon(true);
      return t;
   });
   private String serverId = "";
   /** Bildschirm, dessen Video gerade läuft (der nächste in der Nähe). */
   private volatile String activeId;
   private volatile float audioGain;
   private long lastFrameFetch;
   /** Gleichzeitig offene Bild-Anfragen (Long-Poll): zwei, damit das nächste Bild schon unterwegs ist. */
   private final java.util.concurrent.atomic.AtomicInteger inflight = new java.util.concurrent.atomic.AtomicInteger();
   /** Nummer des nächsten Bilds, auf das eine Anfrage wartet (0 = erst das aktuelle holen). */
   private volatile long nextWant;
   private volatile long frameSeq;
   private boolean seekBackDown;
   private boolean seekFwdDown;
   /** Zeitleiste auf dem Bildschirm zeigen bis (ms). */
   private long timelineUntil;
   private volatile int[] pendingPx;
   private volatile int pendingW;
   private volatile int pendingH;
   private DynamicTexture tex;
   private int texW;
   private int texH;
   private String texFor;
   private Thread audioThread;
   private boolean placing;
   private boolean placeDown;
   private long lastRefresh;

   /** Gerade abgenommene Bildschirme: eine ältere Server-Liste darf sie nicht wieder einfügen. */
   private final Map<String, Long> removedAt = new java.util.concurrent.ConcurrentHashMap<>();

   private record Wall(
      String id, String link, String dim, BlockPos pos, Direction facing, double w, String creator, String name, String status, boolean paused, double at, double dur,
      long received
   ) {
      /** Aktuelle Abspielposition (Sekunden), seit der letzten Meldung weitergerechnet. */
      double position() {
         double p = this.paused || !this.status.isEmpty() ? this.at : this.at + (System.currentTimeMillis() - this.received) / 1000.0;
         return this.dur > 0.0 ? Math.min(this.dur, p) : p;
      }
   }

   public Streaming() {
      super("Streaming", "Watchparty: YouTube, Twitch oder TikTok auf einem Bildschirm an der Wand – der Meowl Server spielt ab, alle sehen und hören es gleichzeitig.", Category.HUD);
      this.section(Section.MEOWL);
      this.icon(Items.PAINTING);
      this.legacy("TikTok Phone");
      ActionSetting place = this.add(new ActionSetting("Platzier-Modus", "Rechtsklick an eine Wand: Bildschirm mit deinem Link aufhängen, Linksklick: abnehmen. Ohne Platzier-Modus: Rechtsklick = Pause, Shift + Rechtsklick = abnehmen", "An/Aus", this::togglePlacing));
      ActionSetting clear = this.add(new ActionSetting("Eigenen Bildschirm abnehmen", "Deinen Bildschirm entfernen (Video stoppt für alle)", "Entfernen", this::removeOwn));
      this.group("Video", new Setting[]{this.link, place, this.placeKey, this.screenWidth, clear});
      this.group("Wiedergabe", new Setting[]{this.volume, this.showOthers, this.fps, this.hudShow, this.hudWidth, this.box.scale});
      this.setEnabled(true);
      MeowlService s = MeowlService.get();
      s.onConnected(this::refresh);
      s.on("stream_added", d -> this.mc.execute(() -> this.putShared(d)));
      s.on("stream_removed", d -> this.mc.execute(() -> {
         if (d.has("id")) {
            this.forget(d.get("id").getAsString());
         }
      }));
   }

   // ================================================================== Bildschirme

   private static boolean validLink(String url) {
      try {
         URI u = new URI(url.trim());
         return "https".equalsIgnoreCase(u.getScheme()) && u.getHost() != null && HOSTS.contains(u.getHost().toLowerCase(Locale.ROOT));
      } catch (Exception var2) {
         return false;
      }
   }

   private void refresh() {
      MeowlService s = MeowlService.get();
      if (s.isConnected() && !this.serverId.isEmpty()) {
         s.get("/streams").thenAccept(r -> {
            if (r.ok() && r.json() != null && r.json().has("streams")) {
               this.mc.execute(() -> {
                  this.walls.clear();
                  for (JsonElement el : r.json().getAsJsonArray("streams")) {
                     this.putShared(el.getAsJsonObject());
                  }
               });
            }
         });
      }
   }

   /** Bildschirm sofort lokal weg: nicht mehr zeichnen, kein Bild und kein Ton mehr holen. */
   private void forget(String id) {
      this.removedAt.put(id, System.currentTimeMillis());
      this.walls.remove(id);
      if (id.equals(this.activeId)) {
         this.activeId = null;
         this.pendingPx = null;
         this.texFor = null;
         this.audioGain = 0.0F;
      }
   }

   private void putShared(JsonObject d) {
      try {
         Long gone = this.removedAt.get(d.get("id").getAsString());
         if (gone != null && System.currentTimeMillis() - gone < 20000L) {
            return;
         }

         Direction facing = Direction.byName(d.get("facing").getAsString());
         if (facing == null || facing.getAxis().isVertical()) {
            return;
         }

         Streaming.Wall w = new Streaming.Wall(
            d.get("id").getAsString(),
            d.get("url").getAsString(),
            d.get("dim").getAsString(),
            new BlockPos(d.get("x").getAsInt(), d.get("y").getAsInt(), d.get("z").getAsInt()),
            facing,
            Math.max(1.0, Math.min(16.0, d.get("w").getAsDouble())),
            d.get("creator").getAsString(),
            d.has("name") ? d.get("name").getAsString() : "",
            d.has("status") ? d.get("status").getAsString() : "",
            d.has("paused") && d.get("paused").getAsBoolean(),
            d.has("pos") ? d.get("pos").getAsDouble() : 0.0,
            d.has("dur") ? d.get("dur").getAsDouble() : 0.0,
            System.currentTimeMillis()
         );
         this.walls.put(w.id(), w);
      } catch (RuntimeException var4) {
      }
   }

   private String myId() {
      String id = MeowlService.get().myUuid();
      return id != null ? id : (this.mc.player != null ? this.mc.player.getStringUUID() : "");
   }

   private static ImageGeometry geometry(BlockPos pos, Direction facing, double w) {
      double h = w * 9.0 / 16.0;
      Vec3 normal = Vec3.atLowerCornerOf(facing.getUnitVec3i());
      Vec3 right = Vec3.atLowerCornerOf(facing.getCounterClockWise().getUnitVec3i());
      Vec3 up = new Vec3(0.0, 1.0, 0.0);
      Vec3 faceCenter = Vec3.atCenterOf(pos).add(normal.scale(0.51));
      Vec3 bottomLeft = faceCenter.subtract(right.scale(0.5)).subtract(up.scale(0.5));
      return new ImageGeometry(bottomLeft, right, up, normal, w, h);
   }

   private void togglePlacing() {
      this.placing = !this.placing;
      ChatUtil.actionBar(this.placing ? "§bPlatzier-Modus: §fRechtsklick = Bildschirm aufhängen, Linksklick = abnehmen" : "§7Platzier-Modus aus");
   }

   private BlockHitResult target() {
      HitResult hit = this.mc.hitResult;
      return hit instanceof BlockHitResult bhr && hit.getType() == HitResult.Type.BLOCK ? bhr : null;
   }

   private void place() {
      BlockHitResult hit = this.target();
      String url = this.link.get().trim();
      MeowlService s = MeowlService.get();
      if (!validLink(url)) {
         ChatUtil.actionBar("§cErst einen YouTube-, Twitch- oder TikTok-Link eintragen");
      } else if (!s.isConnected()) {
         ChatUtil.actionBar("§cNicht mit dem Meowl Server verbunden");
      } else if (hit == null || hit.getDirection().getAxis().isVertical()) {
         ChatUtil.actionBar("§cZiele auf eine senkrechte Wand");
      } else {
         JsonObject o = new JsonObject();
         o.addProperty("url", url);
         o.addProperty("dim", this.mc.level.dimension().identifier().toString());
         o.addProperty("x", hit.getBlockPos().getX());
         o.addProperty("y", hit.getBlockPos().getY());
         o.addProperty("z", hit.getBlockPos().getZ());
         o.addProperty("facing", hit.getDirection().getSerializedName());
         o.addProperty("w", this.screenWidth.get());
         s.post("/streams", o).whenComplete((r, e) -> this.mc.execute(() -> {
            if (e != null || r == null || !r.ok()) {
               ChatUtil.actionBar("§cBildschirm konnte nicht aufgehängt werden" + (r != null && !r.ok() ? ": " + r.error() : ""));
            } else {
               // ein Bildschirm pro Spieler: der alte verschwindet (Server meldet das auch)
               String me = this.myId();
               this.walls.values().removeIf(w -> w.creator().equals(me));
               this.putShared(r.json());
               ChatUtil.actionBar("§bVideo startet – alle Meowl-Spieler hier schauen mit");
            }
         }));
      }
   }

   private void removeWall(Streaming.Wall w) {
      if (!w.creator().equals(this.myId())) {
         ChatUtil.actionBar("§cNur " + (w.name().isEmpty() ? "wer ihn aufgehängt hat" : w.name()) + " kann diesen Bildschirm abnehmen");
         return;
      }

      MeowlService.get().request("DELETE", "/streams/" + w.id(), null, null).whenComplete((r, e) -> this.mc.execute(() -> {
         // 404: auf dem Server schon weg (z.B. nach Neustart) – lokal trotzdem entfernen
         if (e == null && r != null && (r.ok() || r.status() == 404)) {
            this.forget(w.id());
            ChatUtil.actionBar("§7Bildschirm abgenommen");
         } else {
            ChatUtil.actionBar("§cAbnehmen fehlgeschlagen");
         }
      }));
   }

   private void removeOwn() {
      String me = this.myId();
      for (Streaming.Wall w : this.walls.values()) {
         if (w.creator().equals(me)) {
            this.removeWall(w);
         }
      }
   }

   /** Rechtsklick auf einen Bildschirm: Video für alle pausieren bzw. weiter abspielen. */
   private void togglePause(Streaming.Wall w) {
      MeowlService.get().post("/streams/" + w.id() + "/pause", new JsonObject()).whenComplete((r, e) -> this.mc.execute(() -> {
         if (e == null && r != null && r.ok() && r.json() != null) {
            this.putShared(r.json());
            Streaming.Wall now = this.walls.get(w.id());
            ChatUtil.actionBar(now != null && now.paused() ? "§e⏸ Video pausiert" : "§a▶ Video läuft weiter");
         } else {
            ChatUtil.actionBar("§cPause ging nicht" + (r != null && !r.ok() ? ": " + r.error() : ""));
         }
      }));
   }

   /** Pfeiltaste beim Hinschauen: für alle vor- bzw. zurückspulen. */
   private void seek(Streaming.Wall w, double by) {
      if (w.dur() <= 0.0) {
         ChatUtil.actionBar("§7Livestreams kann man nicht spulen");
         return;
      }

      JsonObject o = new JsonObject();
      o.addProperty("by", by);
      this.timelineUntil = System.currentTimeMillis() + 3500L;
      MeowlService.get().post("/streams/" + w.id() + "/seek", o).whenComplete((r, e) -> this.mc.execute(() -> {
         if (e == null && r != null && r.ok() && r.json() != null) {
            this.putShared(r.json());
            Streaming.Wall now = this.walls.get(w.id());
            if (now != null) {
               ChatUtil.actionBar((by > 0 ? "§b⏩ " : "§b⏪ ") + clock(now.position()) + " §7/ " + clock(now.dur()));
            }
         } else {
            ChatUtil.actionBar("§cSpulen ging nicht" + (r != null && !r.ok() ? ": " + r.error() : ""));
         }
      }));
   }

   private static String clock(double sec) {
      long s = Math.max(0L, Math.round(sec));
      return s >= 3600L ? String.format("%d:%02d:%02d", s / 3600L, s / 60L % 60L, s % 60L) : String.format("%d:%02d", s / 60L, s % 60L);
   }

   private void removeLookedAt() {
      Streaming.Wall best = this.lookedAt();
      if (best != null) {
         this.removeWall(best);
      }
   }

   /** Bildschirm, auf den man gerade schaut (vor dem Block, höchstens 16 Blöcke), sonst null. */
   private Streaming.Wall lookedAt() {
      Vec3 eye = this.mc.player.getEyePosition(1.0F);
      Vec3 dir = this.mc.player.getViewVector(1.0F);
      String dim = this.mc.level.dimension().identifier().toString();
      Streaming.Wall best = null;
      double bestT = Double.MAX_VALUE;
      for (Streaming.Wall w : this.walls.values()) {
         if (w.dim().equals(dim)) {
            double t = geometry(w.pos(), w.facing(), w.w()).ray(eye, dir, 16.0);
            if (t >= 0.0 && t < bestT) {
               bestT = t;
               best = w;
            }
         }
      }

      HitResult hit = this.mc.hitResult;
      if (best != null && hit != null && hit.getType() == HitResult.Type.BLOCK && hit.getLocation().distanceTo(eye) < bestT - 0.1) {
         return null;
      }

      return best;
   }

   // ================================================================== Wiedergabe

   /** Nächster sichtbarer Bildschirm (höchstens 48 Blöcke entfernt) und seine Entfernung. */
   private Streaming.Wall nearest(double[] distOut) {
      if (this.mc.player == null || this.mc.level == null) {
         return null;
      }

      String dim = this.mc.level.dimension().identifier().toString();
      String me = this.myId();
      Streaming.Wall best = null;
      double bestD = 48.0 * 48.0;
      for (Streaming.Wall w : this.walls.values()) {
         if (w.dim().equals(dim) && (this.showOthers.get() || me.equals(w.creator()))) {
            double d = this.mc.player.position().distanceToSqr(geometry(w.pos(), w.facing(), w.w()).center());
            if (d < bestD) {
               bestD = d;
               best = w;
            }
         }
      }

      distOut[0] = Math.sqrt(bestD);
      return best;
   }

   @Override
   public void onTick() {
      String now = MeowlService.currentServerId();
      long ms = System.currentTimeMillis();
      if (!now.equals(this.serverId) || ms - this.lastRefresh > 5000L) {
         // Serverwechsel oder regelmäßig: Liste (mit Status „lädt“, „zu Ende“ …) neu holen
         if (!now.equals(this.serverId)) {
            this.walls.clear();
         }

         this.serverId = now;
         this.lastRefresh = ms;
         this.refresh();
      }

      // ohne Bildschirme gibt es nichts zu tun (kein Blick-Strahl, keine Wiedergabe)
      this.lookedAtCached = !this.walls.isEmpty() && this.mc.player != null && this.mc.level != null ? this.lookedAt() : null;
      double[] dist = new double[1];
      Streaming.Wall near = this.walls.isEmpty() ? null : this.nearest(dist);
      // der Bildschirm, den man anschaut, hat Vorrang vor dem nächstgelegenen
      if (this.mc.player != null && this.mc.level != null) {
         Streaming.Wall seen = this.lookedAtCached;
         if (seen != null && seen != near && (this.showOthers.get() || this.myId().equals(seen.creator()))) {
            double d = this.mc.player.position().distanceTo(geometry(seen.pos(), seen.facing(), seen.w()).center());
            if (d < 48.0) {
               near = seen;
               dist[0] = d;
            }
         }
      }

      String id = near == null ? null : near.id();
      if (id == null ? this.activeId != null : !id.equals(this.activeId)) {
         this.activeId = id;
         this.frameSeq = 0L;
         this.nextWant = 0L;
         this.pendingPx = null;
      }

      // Ton: voll bis 5 Blöcke, dann leiser, ab 24 Blöcken still; nur wenn das Video wirklich läuft
      boolean playing = near != null && near.status().isEmpty() && !near.paused();
      float falloff = !playing ? 0.0F : (float)Math.max(0.0, Math.min(1.0, 1.0 - (dist[0] - 5.0) / 19.0));
      this.audioGain = (float)(this.volume.get() / 100.0) * falloff;
      if (id != null && playing && dist[0] < 24.0 && (this.audioThread == null || !this.audioThread.isAlive())) {
         this.audioThread = new Thread(this::audioLoop, "Meowl-Watch-Ton");
         this.audioThread.setDaemon(true);
         this.audioThread.start();
      }
   }

   /**
    * Bilder holen: zwei Anfragen gleichzeitig offen (Long-Poll mit {@code want}), der Server antwortet, sobald das
    * gewünschte Bild fertig ist – so kommt jedes Bild ohne Wartezeit für den Hin- und Rückweg an (bis 30 fps).
    */
   private void requestFrame() {
      String id = this.activeId;
      long now = System.currentTimeMillis();
      if (id == null) {
         return;
      }

      long minGap = 1000L / Math.max(1, this.fps.getInt()) - 4L;
      while (this.inflight.get() < 2 && now - this.lastFrameFetch >= minGap) {
         long want = this.nextWant;
         if (want <= 0L && this.inflight.get() > 0) {
            return;
         }

         this.lastFrameFetch = now;
         this.inflight.incrementAndGet();
         String q = want <= 0L ? "after=" + this.frameSeq : "want=" + want + "&after=" + (want - 1L);
         if (want > 0L) {
            this.nextWant = want + 1L;
         }

         MeowlService.get().get("/streams/" + id + "/frame?" + q).whenComplete((r, e) -> {
            try {
               if (e == null && r != null && r.status() == 200 && r.bytes() != null && id.equals(this.activeId)) {
                  String seqText = header(r.headers(), "x-seq");
                  long seq = seqText == null ? this.frameSeq + 1L : Long.parseLong(seqText);
                  if (seq > this.frameSeq) {
                     BufferedImage img = ImageIO.read(new ByteArrayInputStream(r.bytes()));
                     if (img != null && img.getWidth() <= 1280 && img.getHeight() <= 720) {
                        int[] px = com.lennart.nebula.facecam.VideoEncoder.pixels(img);
                        synchronized (this) {
                           if (seq > this.frameSeq) {
                              this.pendingPx = px;
                              this.pendingW = img.getWidth();
                              this.pendingH = img.getHeight();
                              this.frameSeq = seq;
                           }
                        }
                     }
                  }

                  if (seq >= this.nextWant) {
                     this.nextWant = seq + 1L;
                  }
               } else if (r != null && r.status() == 204) {
                  // kein neues Bild (Pause/Laden): wieder beim aktuellen Stand anfangen statt immer weiter vorauszufragen
                  this.nextWant = this.frameSeq + 1L;
               }
            } catch (Exception var9) {
            } finally {
               this.inflight.decrementAndGet();
            }
         });
         if (want <= 0L) {
            return;
         }
      }
   }

   private static String header(Map<String, List<String>> headers, String name) {
      for (Map.Entry<String, List<String>> e : headers.entrySet()) {
         if (e.getKey() != null && e.getKey().equalsIgnoreCase(name) && !e.getValue().isEmpty()) {
            return e.getValue().get(0);
         }
      }

      return null;
   }

   /** Ton vom Server holen und abspielen (22050 Hz, Mono, 16 Bit), solange ein Bildschirm in der Nähe ist. */
   private void audioLoop() {
      SourceDataLine line = null;
      long seq = 0L;
      String forId = null;
      String epoch = null;
      try {
         int rate = 22050;
         AudioFormat fmt = new AudioFormat(rate, 16, 1, true, false);
         line = AudioSystem.getSourceDataLine(fmt);
         line.open(fmt, rate * 2);
         line.start();
         while (this.isEnabled()) {
            String id = this.activeId;
            if (id == null || this.audioGain <= 0.001F) {
               // kein Bildschirm in Hörweite: sofort still (Rest im Puffer verwerfen)
               line.flush();
               return;
            }

            if (!id.equals(forId)) {
               forId = id;
               seq = 0L;
               line.flush();
            }

            var r = MeowlService.get().get("/streams/" + id + "/audio?after=" + seq).get(5L, java.util.concurrent.TimeUnit.SECONDS);
            if (r != null && r.status() == 200 && r.bytes() != null) {
               String s = header(r.headers(), "x-seq");
               if (s != null) {
                  seq = Long.parseLong(s);
               }

               // vorgespult: alten Ton sofort verwerfen
               String ep = header(r.headers(), "x-epoch");
               if (ep != null && epoch != null && !ep.equals(epoch)) {
                  line.flush();
               }

               epoch = ep;

               // Abtastrate vom Server übernehmen (neuere Server: 44100 Hz)
               String rs = header(r.headers(), "x-rate");
               int want = rs == null ? 22050 : Math.max(8000, Math.min(48000, Integer.parseInt(rs)));
               if (want != rate) {
                  rate = want;
                  line.close();
                  fmt = new AudioFormat(rate, 16, 1, true, false);
                  line = AudioSystem.getSourceDataLine(fmt);
                  line.open(fmt, rate * 2);
                  line.start();
               }

               setGain(line, this.audioGain);
               byte[] pcm = r.bytes();
               // nicht zu weit hinterherhinken: bei zu vollem Puffer Altes verwerfen
               if (line.getBufferSize() - line.available() > rate) {
                  line.flush();
               }

               line.write(pcm, 0, pcm.length);
            }

            Thread.sleep(150L);
         }
      } catch (Exception var10) {
      } finally {
         if (line != null) {
            line.flush();
            line.close();
         }
      }
   }

   private static void setGain(SourceDataLine line, float gain) {
      try {
         FloatControl c = (FloatControl)line.getControl(FloatControl.Type.MASTER_GAIN);
         float db = gain <= 0.001F ? c.getMinimum() : (float)(20.0 * Math.log10(gain));
         c.setValue(Math.max(c.getMinimum(), Math.min(c.getMaximum(), db)));
      } catch (Exception var4) {
      }
   }

   /** Neues Bild vom Server in die Textur laden (Render-Thread). */
   private void uploadPending() {
      int[] px = this.pendingPx;
      if (px != null) {
         this.pendingPx = null;
         int w = this.pendingW;
         int h = this.pendingH;
         if (this.tex != null && (w != this.texW || h != this.texH)) {
            DynamicTextures.destroy(SCREEN);
            this.tex = null;
         }

         this.tex = DynamicTextures.upload(SCREEN, this.tex, px, w, h);
         this.texW = w;
         this.texH = h;
         this.texFor = this.activeId;
      }
   }

   @Override
   protected void onDisable() {
      this.activeId = null;
      this.placing = false;
      if (this.tex != null) {
         DynamicTextures.destroy(SCREEN);
         this.tex = null;
      }
   }

   @Override
   public void onPreTick() {
      boolean key = this.placeKey.get().isPressed();
      if (key && !this.placeDown && this.mc.gui.screen() == null && this.mc.player != null) {
         this.togglePlacing();
      }

      this.placeDown = key;
      boolean back = this.seekBackKey.get().isPressed();
      boolean fwd = this.seekFwdKey.get().isPressed();
      if ((back && !this.seekBackDown || fwd && !this.seekFwdDown) && this.mc.gui.screen() == null && this.mc.player != null && this.mc.level != null) {
         Streaming.Wall w = this.lookedAt();
         if (w != null) {
            this.seek(w, (fwd ? 1.0 : -1.0) * this.seekStep.get());
         }
      }

      this.seekBackDown = back;
      this.seekFwdDown = fwd;
      if (!this.placing && this.mc.player != null && this.mc.level != null && this.mc.gui.screen() == null && this.mc.options.keyUse.isDown()) {
         Streaming.Wall w = this.lookedAt();
         if (w != null) {
            boolean use = false;
            while (this.mc.options.keyUse.consumeClick()) {
               use = true;
            }

            if (use) {
               // Shift + Rechtsklick: eigenen Bildschirm abnehmen, sonst Pause/Weiter
               if (this.mc.player.isShiftKeyDown()) {
                  this.removeWall(w);
               } else {
                  this.togglePause(w);
               }
            }
         }
      }

      if (this.placing && this.mc.player != null && this.mc.level != null && this.mc.gui.screen() == null) {
         boolean use = false;
         boolean attack = false;
         while (this.mc.options.keyUse.consumeClick()) {
            use = true;
         }

         while (this.mc.options.keyAttack.consumeClick()) {
            attack = true;
         }

         if (use) {
            this.place();
         }

         if (attack) {
            this.removeLookedAt();
         }
      }
   }

   // ================================================================== Zeichnen

   @Override
   public void onRenderWorld(WorldCtx context, float tickDelta) {
      if (this.mc.level == null) {
         return;
      }

      this.requestFrame();
      this.uploadPending();
      String dim = this.mc.level.dimension().identifier().toString();
      String me = this.myId();
      Vec3 cam = context.cameraPos();
      PoseStack m = context.matrices();
      for (Streaming.Wall w : this.walls.values()) {
         if (!w.dim().equals(dim) || !this.showOthers.get() && !me.equals(w.creator())) {
            continue;
         }

         ImageGeometry g = geometry(w.pos(), w.facing(), w.w());
         m.pushPose();
         m.translate(-cam.x, -cam.y, -cam.z);
         boolean live = this.tex != null && w.id().equals(this.texFor) && w.id().equals(this.activeId);
         if (live) {
            quad(m, context.buffer(RenderTypes.text(SCREEN)), g);
         } else {
            colorQuad(m, context.buffer(com.lennart.nebula.util.WorldRender.solidColor()), g, 0xFF0B0B10);
         }

         m.popPose();
         if (live && w.dur() > 0.0 && (System.currentTimeMillis() < this.timelineUntil || w.paused() || w == this.lookedAtCached)) {
            this.timeline(context, g, w);
         }

         if (live && w.paused()) {
            this.label(context, g, "⏸");
         } else if (!live) {
            // Status mitten auf dem Bildschirm (lädt, zu weit weg, Fehler …)
            double away = this.mc.player == null ? 0.0 : this.mc.player.position().distanceTo(g.center());
            String text = !w.status().isEmpty()
               ? w.status()
               : (w.id().equals(this.activeId) ? "Lädt …" : (away > 48.0 ? "▶ Näher rangehen" : "▶ Anschauen zum Abspielen"));
            this.label(context, g, text);
         }
      }

      if (this.placing) {
         BlockHitResult hit = this.target();
         if (hit != null && !hit.getDirection().getAxis().isVertical()) {
            m.pushPose();
            m.translate(-cam.x, -cam.y, -cam.z);
            colorQuad(m, context.buffer(com.lennart.nebula.util.WorldRender.translucentColor()), geometry(hit.getBlockPos(), hit.getDirection(), this.screenWidth.get()), 0x60FF3B5C);
            m.popPose();
         }
      }
   }

   /** Bildschirm, auf den man gerade schaut (einmal pro Tick bestimmt). */
   private Streaming.Wall lookedAtCached;

   /** Zeitleiste unten im Bild: Fortschritt und Zeit, beim Hinschauen und nach dem Spulen. */
   private void timeline(WorldCtx ctx, ImageGeometry g, Streaming.Wall w) {
      double f = Math.max(0.0, Math.min(1.0, w.position() / w.dur()));
      Vec3 cam = ctx.cameraPos();
      PoseStack m = ctx.matrices();
      Vec3 off = g.normal().scale(0.012);
      double barH = Math.max(0.04, g.h() * 0.022);
      double inset = g.w() * 0.04;
      Vec3 bl = g.at(inset, g.h() * 0.06).add(off);
      ImageGeometry track = new ImageGeometry(bl, g.right(), g.up(), g.normal(), g.w() - inset * 2.0, barH);
      ImageGeometry done = new ImageGeometry(bl.add(g.normal().scale(0.004)), g.right(), g.up(), g.normal(), (g.w() - inset * 2.0) * f, barH);
      m.pushPose();
      m.translate(-cam.x, -cam.y, -cam.z);
      colorQuad(m, context(ctx), track, 0xC0303038);
      colorQuad(m, context(ctx), done, 0xFFB98CFF);
      m.popPose();
      Font tr = this.mc.font;
      String text = clock(w.position()) + " / " + clock(w.dur());
      Vec3 c = g.at(g.w() / 2.0, g.h() * 0.06 + barH + g.h() * 0.05).add(g.normal().scale(0.02));
      m.pushPose();
      m.translate(c.x - cam.x, c.y - cam.y, c.z - cam.z);
      float yaw = (float)Math.atan2(g.normal().x, g.normal().z);
      m.rotate(com.mojang.math.Axis.YP.rotation(yaw));
      float sc = (float)(g.w() / 220.0);
      m.scale(sc, -sc, sc);
      ctx.text(tr, text, -tr.width(text) / 2.0F, -4.0F, 0xFFF0EAFF, true, m.last().pose(), false, 0, 15728880);
      m.popPose();
   }

   private static VertexConsumer context(WorldCtx ctx) {
      return ctx.buffer(com.lennart.nebula.util.WorldRender.translucentColor());
   }

   private void label(WorldCtx ctx, ImageGeometry g, String text) {
      Font tr = this.mc.font;
      Vec3 c = g.center().add(g.normal().scale(0.02));
      Vec3 cam = ctx.cameraPos();
      PoseStack m = ctx.matrices();
      m.pushPose();
      m.translate(c.x - cam.x, c.y - cam.y, c.z - cam.z);
      // Text liegt flach auf dem Bildschirm (nach vorn zur Wand-Normalen)
      float yaw = (float)Math.atan2(g.normal().x, g.normal().z);
      m.rotate(com.mojang.math.Axis.YP.rotation(yaw));
      float s = (float)(g.w() / 160.0);
      m.scale(s, -s, s);
      ctx.text(tr, text, -tr.width(text) / 2.0F, -4.0F, 0xFFE0E0E8, false, m.last().pose(), false, 0, 15728880);
      m.popPose();
   }

   private static void quad(PoseStack m, VertexConsumer vc, ImageGeometry g) {
      Matrix4f mat = m.last().pose();
      int light = 15728880;
      Vec3 bl = g.at(0.0, 0.0);
      Vec3 br = g.at(g.w(), 0.0);
      Vec3 tr = g.at(g.w(), g.h());
      Vec3 tl = g.at(0.0, g.h());
      vertex(vc, mat, bl, 0.0F, 1.0F, light);
      vertex(vc, mat, br, 1.0F, 1.0F, light);
      vertex(vc, mat, tr, 1.0F, 0.0F, light);
      vertex(vc, mat, tl, 0.0F, 0.0F, light);
      vertex(vc, mat, tl, 0.0F, 0.0F, light);
      vertex(vc, mat, tr, 1.0F, 0.0F, light);
      vertex(vc, mat, br, 1.0F, 1.0F, light);
      vertex(vc, mat, bl, 0.0F, 1.0F, light);
   }

   private static void vertex(VertexConsumer vc, Matrix4f mat, Vec3 p, float u, float v, int light) {
      vc.addVertex(mat, (float)p.x, (float)p.y, (float)p.z).setColor(-1).setUv(u, v).setLight(light);
   }

   private static void colorQuad(PoseStack m, VertexConsumer vc, ImageGeometry g, int color) {
      Matrix4f mat = m.last().pose();
      for (Vec3 p : new Vec3[]{g.at(0.0, 0.0), g.at(g.w(), 0.0), g.at(g.w(), g.h()), g.at(0.0, g.h())}) {
         vc.addVertex(mat, (float)p.x, (float)p.y, (float)p.z).setColor(color);
      }
   }

   @Override
   public void onRenderHud(GuiGraphicsExtractor ctx, float tickDelta) {
      boolean editing = HudLayout.isEditing();
      if (!this.hudShow.get() && !editing) {
         return;
      }

      int w = this.hudWidth.getInt();
      int h = w * 9 / 16;
      this.box.begin(ctx, w + 4, h + 4, false);
      Render2D.roundRect(ctx, 0, 0, w + 4, h + 4, 6, this.box.fade(0xFF0E0E12));
      if (this.tex != null && this.activeId != null) {
         ctx.blit(RenderPipelines.GUI_TEXTURED, SCREEN, 2, 2, 0.0F, 0.0F, w, h, this.texW, this.texH, this.texW, this.texH);
      } else {
         ctx.fillGradient(2, 2, w + 2, h + 2, 0xFF15151C, 0xFF07070A);
         ctx.centeredText(this.mc.font, "▶ Kein Video in der Nähe", w / 2 + 2, h / 2 - 2, 0xFFB0B0B0);
      }

      this.box.end(ctx);
   }
}
