package com.lennart.nebula.clap;

import com.google.gson.JsonObject;
import com.lennart.nebula.fx.CameraFx;
import com.lennart.nebula.net.MeowlService;
import com.lennart.nebula.util.ChatUtil;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

public final class ClapManager {
   public static final long CHARGE_MS = 1300L;
   public static final double MIN_DIST = 1.1;
   public static final double MAX_DIST = 3.6;
   public static final double CANCEL_DIST = 10.0;
   private static final long RESULT_SHOW_MS = 2600L;
   private final Minecraft mc = Minecraft.getInstance();
   private final ClapManager.Options options;
   private final Random random = new Random();
   private String inviteId;
   private String inviteFrom;
   private long inviteExpires;
   private String outgoingTo;
   private long outgoingExpires;
   private ClapManager.Phase phase = ClapManager.Phase.NONE;
   private String id;
   private UUID partner;
   private String partnerName = "";
   private boolean meReady;
   private boolean partnerReady;
   private int readyTicks;
   private int missingTicks;
   private long readySentAt;
   private volatile long offset;
   private volatile boolean synced;
   private long goLocal;
   private long beatServer;
   private long graceMs = 1200L;
   private int beatsPlayed;
   private boolean charging;
   private boolean released;
   private boolean hitSent;
   private long chargeStart;
   private long releaseLocal;
   private float releaseCharge;
   private boolean partnerHit;
   private long partnerHitLocal;
   private ClapManager.Grade grade;
   private boolean missed;
   private long playLocal;
   private boolean played;
   private long flashUntil;
   private int flashColor;
   private long sessionStart;
   private long flashLength = 1L;

   public ClapManager(ClapManager.Options options) {
      this.options = options;
      MeowlService service = MeowlService.get();
      service.on("clap_invite", this::onInvite);
      service.on("clap_invite_result", this::onInviteResult);
      service.on("clap_session", this::onSession);
      service.on("clap_ready", this::onReady);
      service.on("clap_go", this::onGo);
      service.on("clap_partner_hit", d -> {
         if (this.sameSession(d)) {
            this.partnerHit = true;
            this.partnerHitLocal = now();
         }
      });
      service.on("clap_result", this::onResult);
      service.on("clap_end", this::onEnd);
      service.onConnected(() -> {
         if (this.phase != ClapManager.Phase.NONE) {
            this.reset();
         }
      });
      ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(this::cancel));
   }

   public static long now() {
      return System.nanoTime() / 1000000L;
   }

   private long serverNow() {
      return now() + this.offset;
   }

   private void syncClock() {
      long[] best = new long[]{Long.MAX_VALUE};
      CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);

      for (int i = 0; i < 5; i++) {
         chain = chain.thenCompose(v -> {
            long t0 = now();
            return MeowlService.get().get("/clap/time").thenAccept(r -> {
               long t1 = now();
               if (r.ok() && r.json() != null && r.json().has("serverTime")) {
                  long rtt = t1 - t0;
                  if (rtt < best[0]) {
                     best[0] = rtt;
                     this.offset = r.json().get("serverTime").getAsLong() - (t0 + t1) / 2L;
                     this.synced = true;
                  }
               }
            });
         });
      }

      chain.exceptionally(e -> null);
   }

   private void roughOffset(JsonObject d) {
      if (!this.synced && d.has("serverTime")) {
         this.offset = d.get("serverTime").getAsLong() - now();
      }
   }

   public boolean busy() {
      return this.phase != ClapManager.Phase.NONE;
   }

   public void keyPressed() {
      if (this.phase == ClapManager.Phase.NONE) {
         if (this.inviteId != null && this.inviteExpires > System.currentTimeMillis()) {
            this.respond(true);
         } else {
            Player target = this.lookedAtPlayer();
            if (target == null) {
               ChatUtil.actionBar("§7Schau einen Mitspieler an und drück die Clap-Taste §8(oder /meowl clap <name>)");
            } else {
               this.invite(target.getGameProfile().name());
            }
         }
      }
   }

   private Player lookedAtPlayer() {
      if (this.mc.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof Player p && p != this.mc.player) {
         return p;
      } else {
         Vec3 eye = this.mc.player.getEyePosition();
         Vec3 look = this.mc.player.getViewVector(1.0F);
         Player best = null;
         double bestScore = Math.cos(Math.toRadians(25.0));

         for (Player p : this.mc.level.players()) {
            if (p != this.mc.player && !p.isSpectator() && !p.isInvisible()) {
               Vec3 to = p.getBoundingBox().getCenter().subtract(eye);
               double len = to.length();
               if (!(len > 6.0) && !(len < 0.1)) {
                  double dot = to.scale(1.0 / len).dot(look);
                  if (dot > bestScore) {
                     bestScore = dot;
                     best = p;
                  }
               }
            }
         }

         return best;
      }
   }

   public void invite(String name) {
      if (!MeowlService.get().isConnected()) {
         ChatUtil.chat("§cClap braucht eine Verbindung zum Meowl Server (Client → Meowl Server).");
      } else if (this.phase != ClapManager.Phase.NONE) {
         ChatUtil.chat("§7Du bist schon in einem Clap. §8/meowl clap cancel");
      } else {
         PlayerInfo entry = this.mc.getConnection() == null ? null : this.mc.getConnection().getPlayerInfo(name);
         if (entry == null) {
            ChatUtil.chat("§cSpieler §f" + name + "§c ist nicht auf diesem Server.");
         } else if (entry.getProfile().id().equals(this.mc.player.getUUID())) {
            ChatUtil.chat("§7Mit dir selbst abklatschen geht leider nicht.");
         } else {
            String targetName = entry.getProfile().name();
            JsonObject o = new JsonObject();
            o.addProperty("target", entry.getProfile().id().toString());
            MeowlService.get().post("/clap/invite", o).whenComplete((r, e) -> this.mc.execute(() -> {
               if (e != null) {
                  ChatUtil.chat("§cEinladung fehlgeschlagen: " + e.getMessage());
               } else if (!r.ok()) {
                  ChatUtil.chat("§c" + r.error());
               } else {
                  this.outgoingTo = targetName;
                  this.outgoingExpires = System.currentTimeMillis() + 15000L;
                  ChatUtil.chat("§dHigh-Five-Anfrage an §f" + targetName + "§d gesendet §7(15 s gültig).");
               }
            }));
         }
      }
   }

   public void respond(boolean accept) {
      this.respond(accept, "");
   }

   private void respond(boolean accept, String reason) {
      String inv = this.inviteId;
      String from = this.inviteFrom;
      if (inv != null && this.inviteExpires >= System.currentTimeMillis()) {
         this.inviteId = null;
         JsonObject o = new JsonObject();
         o.addProperty("inviteId", inv);
         o.addProperty("accept", accept);
         if (!reason.isEmpty()) {
            o.addProperty("reason", reason);
         }

         MeowlService.get().post("/clap/respond", o).whenComplete((r, e) -> this.mc.execute(() -> {
            if (reason.isEmpty()) {
               if (e != null) {
                  ChatUtil.chat("§cFehlgeschlagen: " + e.getMessage());
               } else if (!r.ok()) {
                  ChatUtil.chat("§c" + r.error());
               } else if (!accept) {
                  ChatUtil.chat("§7Anfrage von " + from + " abgelehnt.");
               }
            }
         }));
      } else {
         this.inviteId = null;
         if (reason.isEmpty()) {
            ChatUtil.chat("§7Keine offene High-Five-Anfrage.");
         }
      }
   }

   public void cancel() {
      if (this.phase != ClapManager.Phase.NONE && this.phase != ClapManager.Phase.RESULT && MeowlService.get().isConnected()) {
         MeowlService.get().post("/clap/cancel", new JsonObject());
      }

      this.inviteId = null;
      this.outgoingTo = null;
      this.reset();
   }

   private void reset() {
      if (this.phase == ClapManager.Phase.CHARGE || this.phase == ClapManager.Phase.POSITION) {
         CameraFx.setTilt(0.0F, 0.0F);
      }

      this.phase = ClapManager.Phase.NONE;
      this.id = null;
      this.partner = null;
      this.meReady = this.partnerReady = false;
      this.charging = this.released = this.hitSent = this.partnerHit = this.played = false;
      this.grade = null;
      this.readyTicks = this.missingTicks = 0;
   }

   private boolean sameSession(JsonObject d) {
      return this.id != null && d.has("id") && this.id.equals(d.get("id").getAsString());
   }

   private void onInvite(JsonObject d) {
      if (this.mc.player != null && this.phase == ClapManager.Phase.NONE) {
         this.inviteId = d.get("inviteId").getAsString();
         this.inviteFrom = d.get("fromName").getAsString();
         this.inviteExpires = System.currentTimeMillis() + (d.has("expiresIn") ? d.get("expiresIn").getAsLong() : 15000L);
         if (!this.options.enabled()) {
            this.respond(false, "disabled");
         } else if (!this.options.allowInvites()) {
            this.respond(false, "blocked");
         } else {
            ChatUtil.chat("§d" + this.inviteFrom + "§f möchte mit dir §5abklatschen§f!");
            ChatUtil.chat(
               Component.literal("§7Annehmen? ")
                  .append(ChatUtil.button("[JA]", 4906624, "/meowl clap accept", "High-Five annehmen (oder Clap-Taste)"))
                  .append(Component.literal(" "))
                  .append(ChatUtil.button("[NEIN]", 16281969, "/meowl clap decline", "Ablehnen"))
            );
            this.sound(SoundEvents.NOTE_BLOCK_CHIME.value(), this.mc.player.getEyePosition(), 1.4F, 0.7F);
         }
      } else {
         String tmpId = d.get("inviteId").getAsString();
         JsonObject o = new JsonObject();
         o.addProperty("inviteId", tmpId);
         o.addProperty("accept", false);
         o.addProperty("reason", "busy");
         MeowlService.get().post("/clap/respond", o);
      }
   }

   private void onInviteResult(JsonObject d) {
      this.outgoingTo = null;
      String by = d.get("by").getAsString();
      if (!d.get("accepted").getAsBoolean()) {
         String reason = d.has("reason") ? d.get("reason").getAsString() : "";

         ChatUtil.chat(switch (reason) {
            case "disabled" -> "§7" + by + " hat Clap ausgeschaltet.";
            case "blocked" -> "§7" + by + " nimmt gerade keine High-Five-Anfragen an.";
            case "busy" -> "§7" + by + " ist gerade beschäftigt.";
            default -> "§7" + by + " hat abgelehnt.";
         });
      }
   }

   private void onSession(JsonObject d) {
      if (!this.options.enabled()) {
         MeowlService.get().post("/clap/cancel", new JsonObject());
      } else {
         this.reset();
         this.inviteId = null;
         this.outgoingTo = null;
         this.id = d.get("id").getAsString();
         this.partner = UUID.fromString(d.get("partner").getAsString());
         this.partnerName = d.get("partnerName").getAsString();
         this.phase = ClapManager.Phase.POSITION;
         this.sessionStart = System.currentTimeMillis();
         this.synced = false;
         this.roughOffset(d);
         this.syncClock();
         ChatUtil.chat("§dHigh-Five mit §f" + this.partnerName + "§d! §7Stellt euch voreinander und schaut euch an.");
      }
   }

   private void onReady(JsonObject d) {
      if (this.sameSession(d)) {
         this.partnerReady = d.get("ready").getAsBoolean();
      }
   }

   private void onGo(JsonObject d) {
      if (this.sameSession(d) && this.phase == ClapManager.Phase.POSITION) {
         this.roughOffset(d);
         this.phase = ClapManager.Phase.CHARGE;
         this.goLocal = now();
         this.beatServer = d.get("beatAt").getAsLong();
         this.graceMs = d.has("grace") ? d.get("grace").getAsLong() : 1200L;
         this.beatsPlayed = 0;
         this.charging = this.released = this.hitSent = this.partnerHit = false;
      }
   }

   private void onResult(JsonObject d) {
      if (this.sameSession(d)) {
         try {
            this.grade = ClapManager.Grade.valueOf(d.get("grade").getAsString());
         } catch (IllegalArgumentException var4) {
            this.grade = ClapManager.Grade.BAD;
         }

         this.missed = d.has("missed") && d.get("missed").getAsBoolean();
         this.playLocal = d.get("playAt").getAsLong() - this.offset;
         long n = now();
         this.playLocal = Mth.clamp(this.playLocal, n, n + 1500L);
         this.played = false;
         this.phase = ClapManager.Phase.RESULT;
      }
   }

   private void onEnd(JsonObject d) {
      if (this.sameSession(d)) {
         if (this.phase != ClapManager.Phase.RESULT) {
            ChatUtil.chat("§7High-Five beendet: " + d.get("reason").getAsString());
         }

         if (this.phase != ClapManager.Phase.RESULT || this.played) {
            this.reset();
         }
      }
   }

   public void tick() {
      if (this.inviteId != null && this.inviteExpires < System.currentTimeMillis()) {
         this.inviteId = null;
      }

      if (this.outgoingTo != null && this.outgoingExpires < System.currentTimeMillis()) {
         this.outgoingTo = null;
      }

      if (this.phase != ClapManager.Phase.NONE && this.mc.player != null && this.mc.level != null) {
         Player p = this.partnerEntity();
         if (this.phase != ClapManager.Phase.RESULT) {
            if (p == null) {
               if (++this.missingTicks > 100) {
                  ChatUtil.chat("§7" + this.partnerName + " ist nicht mehr in deiner Nähe – High-Five abgebrochen.");
                  this.cancel();
                  return;
               }
            } else {
               this.missingTicks = 0;
               if (this.horizontalDistance(p) > 10.0) {
                  ChatUtil.chat("§7Ihr seid zu weit auseinander – High-Five abgebrochen.");
                  this.cancel();
                  return;
               }
            }
         }

         if (this.phase == ClapManager.Phase.POSITION) {
            boolean ok = p != null && this.inPosition(p);
            this.readyTicks = ok ? this.readyTicks + 1 : 0;
            boolean want = this.readyTicks >= 6;
            long n = System.currentTimeMillis();
            if (want != this.meReady && n - this.readySentAt > 250L) {
               this.meReady = want;
               this.readySentAt = n;
               JsonObject o = new JsonObject();
               o.addProperty("id", this.id);
               o.addProperty("ready", want);
               MeowlService.get().post("/clap/ready", o);
               if (want) {
                  this.sound(SoundEvents.NOTE_BLOCK_HAT.value(), this.mc.player.getEyePosition(), 1.6F, 0.5F);
               }
            }
         }

         if (this.phase == ClapManager.Phase.RESULT && this.played && now() - this.playLocal > 2600L) {
            this.reset();
         }
      }
   }

   public Player partnerEntity() {
      return this.partner != null && this.mc.level != null ? this.mc.level.getPlayerByUUID(this.partner) : null;
   }

   private double horizontalDistance(Entity e) {
      double dx = e.getX() - this.mc.player.getX();
      double dz = e.getZ() - this.mc.player.getZ();
      return Math.sqrt(dx * dx + dz * dz);
   }

   public boolean distanceOk(Player p) {
      double d = this.horizontalDistance(p);
      return d >= 1.1 && d <= 3.6 && Math.abs(p.getY() - this.mc.player.getY()) < 1.5;
   }

   public boolean facingOk(Player p) {
      double dx = p.getX() - this.mc.player.getX();
      double dz = p.getZ() - this.mc.player.getZ();
      float toward = (float)(Mth.atan2(dz, dx) * 180.0 / Math.PI) - 90.0F;
      return Math.abs(Mth.wrapDegrees(toward - this.mc.player.getYRot())) < 45.0F;
   }

   private boolean inPosition(Player p) {
      return this.distanceOk(p) && this.facingOk(p);
   }

   public void frame(boolean keyDown) {
      if (this.phase != ClapManager.Phase.NONE && this.mc.player != null) {
         long n = now();
         if (this.phase == ClapManager.Phase.CHARGE) {
            for (long[] beats = new long[]{this.beatLocal() - 1800L, this.beatLocal() - 1200L, this.beatLocal() - 600L};
               this.beatsPlayed < beats.length && n >= beats[this.beatsPlayed];
               this.beatsPlayed++
            ) {
               this.sound(SoundEvents.NOTE_BLOCK_HAT.value(), this.mc.player.getEyePosition(), this.beatsPlayed == 2 ? 1.5F : 1.2F, 0.8F);
            }

            boolean down = keyDown && this.mc.screen == null;
            if (!this.released) {
               if (down && !this.charging) {
                  this.charging = true;
                  this.chargeStart = n;
               } else if (!down && this.charging) {
                  this.release(n);
               } else if (this.charging && n > this.beatLocal() + this.graceMs - 100L) {
                  this.release(n);
               }
            }
         }

         if (this.phase == ClapManager.Phase.RESULT && !this.played && n >= this.playLocal) {
            this.played = true;
            this.impact();
         }
      }
   }

   private void release(long n) {
      this.released = true;
      this.charging = false;
      this.releaseLocal = n;
      this.releaseCharge = this.charge(n);
      if (!this.hitSent) {
         this.hitSent = true;
         JsonObject o = new JsonObject();
         o.addProperty("id", this.id);
         o.addProperty("at", n + this.offset);
         o.addProperty("charge", this.releaseCharge);
         MeowlService.get().post("/clap/hit", o);
      }

      this.sound(SoundEvents.PLAYER_ATTACK_SWEEP, this.mc.player.getEyePosition(), 1.6F + 0.3F * this.releaseCharge, 0.35F);
   }

   public float charge(long n) {
      if (this.released) {
         return this.releaseCharge;
      } else {
         return this.charging ? Mth.clamp((float)(n - this.chargeStart) / 1300.0F, 0.0F, 1.0F) : 0.0F;
      }
   }

   private Vec3 impactPoint() {
      Player p = this.partnerEntity();
      Vec3 me = this.mc.player.getEyePosition();
      return p == null
         ? me.add(this.mc.player.getViewVector(1.0F).scale(0.9)).add(0.0, 0.35, 0.0)
         : me.add(p.getEyePosition()).scale(0.5).add(0.0, 0.35, 0.0);
   }

   private Vec3 lastImpact;
   private long lastImpactTime;
   private ClapManager.Grade lastImpactGrade;

   /** Letzter Aufprall (für die Schockwelle in der Welt): Ort, Zeit (ms) und Bewertung – null = keiner. */
   public Vec3 lastImpact() {
      return this.lastImpact;
   }

   public long lastImpactTime() {
      return this.lastImpactTime;
   }

   public ClapManager.Grade lastImpactGrade() {
      return this.lastImpactGrade;
   }

   /** Zusätzliche Partikel je Effekt-Stil (Feuer, Blitz, Eis, Konfetti, Herzen). */
   private void styleBurst(Vec3 at, ClapManager.Grade g, float k) {
      float power = (g.ordinal() + 1) / 4.0F;
      int n = Math.max(2, (int)(28.0F * k * power));
      switch (this.options.effectStyle()) {
         case "Feuer":
            this.burst(ParticleTypes.FLAME, at, n, 0.18 + 0.2 * power);
            this.burst(ParticleTypes.SMALL_FLAME, at, n, 0.25);
            this.burst(ParticleTypes.LAVA, at, Math.max(1, n / 5), 0.1);
            if (g.ordinal() >= 2) {
               this.burst(ParticleTypes.SOUL_FIRE_FLAME, at, n / 2, 0.3);
               this.burst(ParticleTypes.LARGE_SMOKE, at, n / 3, 0.05);
            }

            this.sound(SoundEvents.FIRECHARGE_USE, at, 1.1F, 0.6F * power + 0.2F);
            break;
         case "Blitz":
            this.burst(ParticleTypes.ELECTRIC_SPARK, at, n * 2, 0.5);
            this.burst(ParticleTypes.END_ROD, at, n / 2, 0.15);
            if (g.ordinal() >= 2) {
               this.burst(ParticleTypes.ELECTRIC_SPARK, at, n, 0.9);
            }

            this.sound(SoundEvents.TRIDENT_THUNDER.value(), at, 1.8F, 0.25F * power);
            break;
         case "Eis":
            this.burst(ParticleTypes.SNOWFLAKE, at, n * 2, 0.3);
            this.burst(ParticleTypes.ITEM_SNOWBALL, at, n, 0.2);
            this.burst(ParticleTypes.END_ROD, at, n / 3, 0.08);
            this.sound(SoundEvents.GLASS_BREAK, at, 1.4F, 0.6F * power + 0.2F);
            break;
         case "Konfetti":
            this.burst(ParticleTypes.FIREWORK, at, n, 0.35);
            this.burst(ParticleTypes.HAPPY_VILLAGER, at, n / 2, 0.3);
            this.burst(ParticleTypes.NOTE, at, Math.max(1, n / 4), 0.0);
            this.burst(ParticleTypes.WAX_ON, at, n, 0.4);
            this.sound(SoundEvents.FIREWORK_ROCKET_TWINKLE_FAR, at, 1.4F, 0.6F);
            break;
         case "Herzen":
            this.burst(ParticleTypes.HEART, at, Math.max(2, n / 2), 0.15);
            this.burst(ParticleTypes.CHERRY_LEAVES, at, n, 0.2);
            this.sound(SoundEvents.AMETHYST_BLOCK_CHIME, at, 1.5F, 0.8F);
            break;
         default:
      }
   }

   private void impact() {
      ClapManager.Grade g = this.grade == null ? ClapManager.Grade.BAD : this.grade;
      Vec3 at = this.impactPoint();
      float k = this.options.intensity();
      this.lastImpact = at;
      this.lastImpactTime = System.currentTimeMillis();
      this.lastImpactGrade = g;
      if (this.options.particles() && g != ClapManager.Grade.BAD) {
         this.styleBurst(at, g, k);
      }

      if (this.options.particles()) {
         switch (g) {
            case BAD:
               this.burst(ParticleTypes.SMOKE, at, (int)(8.0F * k), 0.04);
               this.burst(ParticleTypes.POOF, at, (int)(3.0F * k), 0.03);
               break;
            case GOOD:
               this.burst(ParticleTypes.CRIT, at, (int)(16.0F * k), 0.35);
               this.burst(ParticleTypes.HAPPY_VILLAGER, at, (int)(6.0F * k), 0.1);
               break;
            case PERFECT:
               this.burst(ParticleTypes.CRIT, at, (int)(22.0F * k), 0.45);
               this.burst(ParticleTypes.ENCHANTED_HIT, at, (int)(22.0F * k), 0.45);
               this.burst(ParticleTypes.END_ROD, at, (int)(12.0F * k), 0.12);
               this.burst(ParticleTypes.NOTE, at, (int)(3.0F * k), 0.0);
               break;
            case MAX:
               this.burst(ParticleTypes.CRIT, at, (int)(30.0F * k), 0.6);
               this.burst(ParticleTypes.ENCHANTED_HIT, at, (int)(30.0F * k), 0.6);
               this.burst(ParticleTypes.END_ROD, at, (int)(24.0F * k), 0.2);
               this.burst(ParticleTypes.TOTEM_OF_UNDYING, at, (int)(50.0F * k), 0.5);
               this.burst(ParticleTypes.FIREWORK, at, (int)(30.0F * k), 0.3);
               this.burst(ParticleTypes.ELECTRIC_SPARK, at, (int)(16.0F * k), 0.4);
         }
      }

      if (this.options.sounds()) {
         switch (g) {
            case BAD:
               this.sound(SoundEvents.PLAYER_ATTACK_WEAK, at, 0.8F, 0.8F);
               break;
            case GOOD:
               this.sound(SoundEvents.PLAYER_ATTACK_STRONG, at, 1.3F, 0.9F);
               this.sound(SoundEvents.NOTE_BLOCK_BELL.value(), at, 1.0F, 0.5F);
               break;
            case PERFECT:
               this.sound(SoundEvents.PLAYER_ATTACK_CRIT, at, 1.2F, 1.0F);
               this.sound(SoundEvents.NOTE_BLOCK_CHIME.value(), at, 1.2F, 0.7F);
               this.sound(SoundEvents.PLAYER_LEVELUP, at, 1.6F, 0.5F);
               break;
            case MAX:
               this.sound(SoundEvents.PLAYER_ATTACK_CRIT, at, 1.0F, 1.0F);
               this.sound(SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, at, 1.1F, 0.8F);
               this.sound(SoundEvents.FIREWORK_ROCKET_TWINKLE, at, 1.2F, 0.7F);
               this.sound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, at, 1.3F, 0.5F);
         }
      }

      if (this.options.screenFx()) {
         switch (g) {
            case BAD:
               CameraFx.shake(0.15F * k, 150L);
               break;
            case GOOD:
               CameraFx.shake(0.35F * k, 220L);
               break;
            case PERFECT:
               CameraFx.shake(0.7F * k, 320L);
               this.flash(-3132, 260L);
               break;
            case MAX:
               CameraFx.shake(1.3F * k, 520L);
               this.flash(-1004548, 450L);
         }
      }

      this.options.onResult(g);
   }

   private void flash(int color, long ms) {
      this.flashColor = color;
      this.flashUntil = now() + ms;
      this.flashLength = ms;
   }

   private void burst(ParticleOptions type, Vec3 at, int count, double speed) {
      for (int i = 0; i < count; i++) {
         double vx = (this.random.nextDouble() - 0.5) * 2.0 * speed;
         double vy = this.random.nextDouble() * speed + 0.02;
         double vz = (this.random.nextDouble() - 0.5) * 2.0 * speed;
         this.mc
            .level
            .addParticle(
               type,
               at.x + (this.random.nextDouble() - 0.5) * 0.3,
               at.y + (this.random.nextDouble() - 0.5) * 0.3,
               at.z + (this.random.nextDouble() - 0.5) * 0.3,
               vx,
               vy,
               vz
            );
      }
   }

   private void sound(SoundEvent sound, Vec3 at, float pitch, float volume) {
      if (this.mc.level != null && this.options.sounds()) {
         this.mc.level.playLocalSound(at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch, false);
      }
   }

   public ClapManager.Phase phase() {
      return this.phase;
   }

   public String partnerName() {
      return this.partnerName;
   }

   public boolean meReady() {
      return this.meReady;
   }

   public boolean partnerReady() {
      return this.partnerReady;
   }

   public String pendingInviteFrom() {
      return this.inviteId != null && this.inviteExpires > System.currentTimeMillis() ? this.inviteFrom : null;
   }

   public long inviteSecondsLeft() {
      return Math.max(0L, (this.inviteExpires - System.currentTimeMillis()) / 1000L);
   }

   public String outgoingTo() {
      return this.outgoingTo;
   }

   public long sessionSecondsLeft() {
      return Math.max(0L, (this.sessionStart + 45000L - System.currentTimeMillis()) / 1000L);
   }

   public long goLocal() {
      return this.goLocal;
   }

   public long beatLocal() {
      return this.beatServer - this.offset;
   }

   public long graceMs() {
      return this.graceMs;
   }

   public boolean charging() {
      return this.charging;
   }

   public boolean released() {
      return this.released;
   }

   public long releaseLocal() {
      return this.releaseLocal;
   }

   public boolean partnerHit() {
      return this.partnerHit;
   }

   public long partnerHitLocal() {
      return this.partnerHitLocal;
   }

   public ClapManager.Grade grade() {
      return this.grade;
   }

   public boolean missed() {
      return this.missed;
   }

   public boolean played() {
      return this.played;
   }

   public long playLocal() {
      return this.playLocal;
   }

   private static float ease(float x) {
      x = Mth.clamp(x, 0.0F, 1.0F);
      return x * x * (3.0F - 2.0F * x);
   }

   public float[] pose(boolean self) {
      if (this.phase != ClapManager.Phase.CHARGE && this.phase != ClapManager.Phase.RESULT) {
         return null;
      } else {
         long n = now();
         float raise = ease((float)(n - this.goLocal) / 350.0F);
         float pull = 0.0F;
         float forward = 0.0F;
         float kick = 0.0F;
         boolean hit = self ? this.released : this.partnerHit;
         long hitAt = self ? this.releaseLocal : this.partnerHitLocal;
         if (!hit) {
            pull = self ? this.charge(n) : 0.6F * ease((float)(n - this.goLocal) / (float)Math.max(1L, this.beatLocal() - this.goLocal));
         } else {
            forward = 0.55F * ease((float)(n - hitAt) / 140.0F);
         }

         if (this.phase == ClapManager.Phase.RESULT) {
            if (hit) {
               forward = Math.max(forward, 0.55F + 0.45F * ease(1.0F - (float)(this.playLocal - n) / 110.0F));
            }

            if (this.played) {
               long t = n - this.playLocal;
               kick = hit ? Mth.clamp(1.0F - (float)t / 260.0F, 0.0F, 1.0F) : 0.0F;
               raise *= 1.0F - ease((float)(t - 650L) / 450.0F);
            }
         }

         return raise <= 0.001F ? null : new float[]{raise, pull, forward, kick};
      }
   }

   public int flash() {
      long n = now();
      if (n >= this.flashUntil) {
         return 0;
      } else {
         float a = (float)(this.flashUntil - n) / (float)this.flashLength;
         int alpha = (int)(a * a * 0.45F * 255.0F);
         return alpha << 24 | this.flashColor & 16777215;
      }
   }

   public static enum Grade {
      BAD("BAD", -6511697),
      GOOD("GOOD", -11870592),
      PERFECT("PERFECT", -278748),
      MAX("PERFECT MAX", -1541639);

      public final String label;
      public final int color;

      private Grade(String label, int color) {
         this.label = label;
         this.color = color;
      }
   }

   public interface Options {
      boolean enabled();

      boolean allowInvites();

      boolean particles();

      /** Effekt-Stil: Klassisch, Feuer, Blitz, Eis, Konfetti oder Herzen. */
      default String effectStyle() {
         return "Klassisch";
      }

      /** Schockwelle in der Welt beim Aufprall. */
      default boolean shockwave() {
         return true;
      }

      boolean sounds();

      boolean screenFx();

      float intensity();

      void onResult(ClapManager.Grade var1);
   }

   public static enum Phase {
      NONE,
      POSITION,
      CHARGE,
      RESULT;
   }
}
