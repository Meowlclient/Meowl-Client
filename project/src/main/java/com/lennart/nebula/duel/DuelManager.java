package com.lennart.nebula.duel;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.lennart.nebula.gui.Render2D;
import com.lennart.nebula.net.MeowlService;
import com.lennart.nebula.util.ChatUtil;
import com.lennart.nebula.util.WorldRender;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import com.lennart.nebula.util.WorldCtx;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;

public final class DuelManager {
   private final Minecraft mc = Minecraft.getInstance();
   private final DuelNetworkManager network = new DuelNetworkManager();
   private final DuelRenderer renderer = new DuelRenderer();
   private final DuelWeapon weapon = new DuelWeapon();
   private DuelSession session;
   private DuelManager.Invite invite;
   private long lastPose;
   private boolean sounds = true;
   private static DuelManager current;
   private final BooleanSupplier enabled;
   private float recoilKick = 2.0F;
   private float pendingKick;

   public DuelManager(BooleanSupplier enabled) {
      this.enabled = enabled;
      current = this;
      MeowlService service = MeowlService.get();
      service.on("duel_invite", this::onInvite);
      service.on("duel_invite_result", this::onInviteResult);
      service.on("duel_start", this::onStart);
      service.on("duel_shot", this::onShot);
      service.on("duel_reload", this::onReload);
      service.on("duel_end", this::onEnd);
      service.on("duel_round", this::onRound);
      service.on("duel_arena", this::onArena);
      service.onConnected(() -> {
         if (this.session != null) {
            this.leave();
         }
      });
      ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
         this.invite = null;
         this.leave();
      }));
   }

   public static boolean isWeaponActive() {
      DuelManager m = current;
      return m != null && m.session != null && m.session.state != DuelSession.State.ENDED;
   }

   public boolean inDuel() {
      return this.session != null && this.session.state != DuelSession.State.ENDED;
   }

   public void setRecoilKick(float degrees) {
      this.recoilKick = degrees;
   }

   public void setSounds(boolean sounds) {
      this.sounds = sounds;
   }

   public void challenge(String name, double radius, int hp, int rounds) {
      if (!MeowlService.get().isConnected()) {
         ChatUtil.chat("§cSheriff Duel braucht eine Verbindung zum Meowl Server (Mods → Meowl Server).");
      } else if (this.inDuel()) {
         ChatUtil.chat("§cDu bist schon in einem Duell. §7/meowl duel leave zum Aufgeben.");
      } else {
         PlayerInfo entry = this.mc.getConnection() == null ? null : this.mc.getConnection().getPlayerInfo(name);
         if (entry == null) {
            ChatUtil.chat("§cSpieler §f" + name + "§c ist nicht auf diesem Server.");
         } else {
            LocalPlayer p = this.mc.player;
            this.network
               .invite(entry.getProfile().id(), this.dim(), p.getX(), p.getY(), p.getZ(), radius, hp, rounds)
               .whenComplete((r, e) -> this.mc.execute(() -> {
                  if (e != null) {
                     ChatUtil.chat("§cEinladung fehlgeschlagen: " + e.getMessage());
                  } else if (!r.ok()) {
                     ChatUtil.chat("§c" + r.error());
                  } else {
                     ChatUtil.chat("§dHerausforderung an §f" + name + "§d gesendet §7(" + roundsText(rounds) + ", " + hp + " Leben, 30 s gültig).");
                  }
               }));
         }
      }
   }

   public void respond(boolean accept) {
      DuelManager.Invite inv = this.invite;
      if (inv != null && inv.expires >= System.currentTimeMillis()) {
         this.invite = null;
         LocalPlayer p = this.mc.player;
         this.network.respond(inv.id, accept, this.dim(), p.getX(), p.getY(), p.getZ()).whenComplete((r, e) -> this.mc.execute(() -> {
            if (e != null) {
               ChatUtil.chat("§cFehlgeschlagen: " + e.getMessage());
            } else if (!r.ok()) {
               ChatUtil.chat("§c" + r.error());
            } else if (!accept) {
               ChatUtil.chat("§7Einladung von " + inv.fromName + " abgelehnt.");
            }
         }));
      } else {
         ChatUtil.chat("§7Keine offene Duell-Einladung.");
         this.invite = null;
      }
   }

   public void leave() {
      if (this.inDuel()) {
         this.network.leave();
      }

      this.end();
   }

   private String dim() {
      return this.mc.level.dimension().identifier().toString();
   }

   private void onInvite(JsonObject d) {
      if (!this.inDuel() && this.enabled.getAsBoolean()) {
         this.invite = new DuelManager.Invite(
            d.get("inviteId").getAsString(),
            UUID.fromString(d.get("from").getAsString()),
            d.get("fromName").getAsString(),
            d.get("radius").getAsDouble(),
            intOr(d, "hp", 100),
            intOr(d, "rounds", 1),
            System.currentTimeMillis() + 30000L
         );
         ChatUtil.chat(
            "§d"
               + this.invite.fromName
               + "§f fordert dich zum §5Sheriff-Duell§f heraus! §7("
               + roundsText(this.invite.rounds())
               + ", "
               + this.invite.hp()
               + " Leben)"
         );
         ChatUtil.chat(
            Component.literal("§7Annehmen? ")
               .append(ChatUtil.button("[JA]", 4906624, "/meowl duel accept", "Duell annehmen"))
               .append(Component.literal(" "))
               .append(ChatUtil.button("[NEIN]", 16281969, "/meowl duel decline", "Duell ablehnen"))
         );
         this.play(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.4F, 0.6F);
      }
   }

   private void onInviteResult(JsonObject d) {
      boolean accepted = d.get("accepted").getAsBoolean();
      ChatUtil.chat(accepted ? "§a" + d.get("by").getAsString() + " hat angenommen!" : "§7" + d.get("by").getAsString() + " hat abgelehnt.");
   }

   private void onStart(JsonObject d) {
      if (!this.enabled.getAsBoolean()) {
         this.network.leave();
      } else {
         DuelArena arena = DuelArena.fromJson(d);
         List<double[]> grounds = arena.measureGround(this.mc.level);
         arena = arena.grounded(grounds);
         String meId = MeowlService.get().myUuid();
         if (meId != null && d.has("challenger") && meId.equals(d.get("challenger").getAsString())) {
            this.network.ground(grounds);
         }

         this.session = new DuelSession(
            d.get("id").getAsString(),
            UUID.fromString(d.get("opponent").getAsString()),
            d.get("opponentName").getAsString(),
            arena,
            d.get("maxHp").getAsInt(),
            d.get("mag").getAsInt(),
            d.get("cooldown").getAsLong(),
            d.get("reload").getAsLong(),
            d.get("startIn").getAsLong()
         );
         this.session.rounds = intOr(d, "rounds", 1);
         this.session.winsNeeded = intOr(d, "winsNeeded", this.session.rounds / 2 + 1);
         this.session.round = intOr(d, "round", 1);
         this.invite = null;
         this.weapon.equip();
         this.renderer.clear();
         DuelObstacleManager.setArena(arena);
         this.play(SoundEvents.CROSSBOW_LOADING_MIDDLE.value(), 0.8F, 1.0F);
      }
   }

   private void onShot(JsonObject d) {
      if (this.session != null) {
         String me = MeowlService.get().myUuid();
         boolean mine = d.get("shooter").getAsString().equals(me);
         Vec3 from = vec(d.getAsJsonArray("origin"));
         Vec3 to = vec(d.getAsJsonArray("end"));
         String hit = d.get("hit").getAsString();
         JsonObject hp = d.getAsJsonObject("hp");
         if (me != null && hp.has(me)) {
            this.session.myHp = hp.get(me).getAsInt();
         }

         if (hp.has(this.session.opponent.toString())) {
            this.session.opponentHp = hp.get(this.session.opponent.toString()).getAsInt();
         }

         if (mine) {
            this.session.ammo = d.get("ammo").getAsInt();
         } else if (this.sounds) {
            this.mc.level.playLocalSound(from.x, from.y, from.z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 1.0F, 1.7F, false);
         }

         this.renderer.addTracer(from, to, mine ? -1056972662 : -1056994678);
         if (!hit.equals("miss")) {
            for (int i = 0; i < 6; i++) {
               this.mc
                  .level
                  .addParticle(
                     hit.equals("wall") ? ParticleTypes.SMOKE : ParticleTypes.CRIT,
                     to.x,
                     to.y,
                     to.z,
                     (Math.random() - 0.5) * 0.2,
                     Math.random() * 0.15,
                     (Math.random() - 0.5) * 0.2
                  );
            }
         }

         if (mine && (hit.equals("head") || hit.equals("body") || hit.equals("legs"))) {
            this.play(
               hit.equals("head") ? SoundEvents.NOTE_BLOCK_BELL.value() : SoundEvents.ARROW_HIT_PLAYER, hit.equals("head") ? 1.6F : 1.0F, 1.0F
            );
            ChatUtil.actionBar((hit.equals("head") ? "§6KOPFTREFFER " : "§fTreffer ") + "§c-" + d.get("damage").getAsInt());
         } else if (!mine && !hit.equals("miss") && !hit.equals("wall")) {
            this.play(SoundEvents.PLAYER_HURT, 1.0F, 1.0F);
         }
      }
   }

   private void onReload(JsonObject d) {
      if (this.session != null) {
         long duration = d.get("duration").getAsLong();
         if (d.get("player").getAsString().equals(MeowlService.get().myUuid())) {
            this.session.reloadUntil = System.currentTimeMillis() + duration;
            this.session.ammo = this.session.mag;
            this.weapon.onReload(duration);
            this.play(SoundEvents.CROSSBOW_LOADING_MIDDLE.value(), 1.3F, 1.0F);
         }
      }
   }

   private void onEnd(JsonObject d) {
      if (this.session != null) {
         String me = MeowlService.get().myUuid();
         boolean won = d.has("winner") && !d.get("winner").isJsonNull() && d.get("winner").getAsString().equals(me);
         boolean draw = !d.has("winner") || d.get("winner").isJsonNull();
         this.session.state = DuelSession.State.ENDED;
         this.session.endedAt = System.currentTimeMillis();
         this.session.resultTitle = draw ? "UNENTSCHIEDEN" : (won ? "SIEG" : "NIEDERLAGE");
         this.session.resultText = d.get("reason").getAsString();
         this.readWins(d);
         DuelObstacleManager.setArena(null);
         this.play(won ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE : SoundEvents.VILLAGER_NO, 1.0F, 1.0F);
      }
   }

   private void onArena(JsonObject d) {
      if (this.session != null && d.has("id") && d.get("id").getAsString().equals(this.session.id)) {
         this.session.arena = this.session.arena.withObstacles(DuelArena.parseObstacles(d.getAsJsonArray("obstacles")));
         DuelObstacleManager.setArena(this.session.arena);
      }
   }

   private void onRound(JsonObject d) {
      if (this.session != null) {
         String me = MeowlService.get().myUuid();
         this.readWins(d);
         this.session.round = intOr(d, "round", this.session.round + 1);
         this.session.myHp = this.session.maxHp;
         this.session.opponentHp = this.session.maxHp;
         this.session.ammo = intOr(d, "mag", this.session.mag);
         this.session.reloadUntil = 0L;
         this.session.state = DuelSession.State.COUNTDOWN;
         this.session.startAt = System.currentTimeMillis() + (d.has("startIn") ? d.get("startIn").getAsLong() : 3000L);
         boolean iWon = d.has("winner") && d.get("winner").getAsString().equals(me);
         this.session.roundBanner = iWon ? "§aRunde an dich!" : "§cRunde an " + this.session.opponentName;
         this.session.roundBannerUntil = this.session.startAt;
         this.weapon.onReload(0L);
         this.play(iWon ? SoundEvents.PLAYER_LEVELUP : SoundEvents.VILLAGER_NO, 1.0F, 1.0F);
         ChatUtil.chat(
            "§5Sheriff-Duell: "
               + this.session.roundBanner
               + " §7("
               + this.session.myWins
               + ":"
               + this.session.opponentWins
               + ", Runde "
               + this.session.round
               + "/"
               + this.session.rounds
               + ")"
         );
      }
   }

   private void readWins(JsonObject d) {
      if (d.has("wins") && d.get("wins").isJsonObject()) {
         JsonObject wins = d.getAsJsonObject("wins");
         String me = MeowlService.get().myUuid();
         if (me != null && wins.has(me)) {
            this.session.myWins = wins.get(me).getAsInt();
         }

         if (wins.has(this.session.opponent.toString())) {
            this.session.opponentWins = wins.get(this.session.opponent.toString()).getAsInt();
         }
      }
   }

   private static int intOr(JsonObject d, String key, int def) {
      return d.has(key) && d.get(key).isJsonPrimitive() ? d.get(key).getAsInt() : def;
   }

   public static String roundsText(int rounds) {
      return rounds <= 1 ? "1 Runde" : "Best of " + rounds;
   }

   private static Vec3 vec(JsonArray a) {
      return new Vec3(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble());
   }

   private void play(SoundEvent sound, float pitch, float volume) {
      if (this.sounds) {
         this.mc.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
      }
   }

   private void end() {
      this.session = null;
      DuelObstacleManager.setArena(null);
      this.renderer.clear();
   }

   public void preTick(boolean reloadPressed) {
      if (this.session != null && this.mc.player != null) {
         this.session.update();
         if (this.session.state == DuelSession.State.ENDED) {
            if (System.currentTimeMillis() - this.session.endedAt > 5000L) {
               this.end();
            }
         } else {
            boolean shoot = false;

            while (this.mc.options.keyUse.consumeClick()) {
               shoot = true;
            }

            while (this.mc.options.keyAttack.consumeClick()) {
            }

            if (this.mc.screen == null) {
               if (reloadPressed && this.session.ammo < this.session.mag && !this.session.isReloading()) {
                  this.network.reload();
               }

               if (shoot) {
                  this.fire();
               }
            }
         }
      }
   }

   private void fire() {
      if (!this.weapon.canShoot(this.session)) {
         if (this.session.state == DuelSession.State.ACTIVE && this.session.ammo <= 0) {
            this.play(SoundEvents.DISPENSER_FAIL, 1.4F, 0.6F);
         }
      } else {
         this.weapon.onShot();
         this.session.ammo--;
         if (this.recoilKick > 0.0F) {
            this.pendingKick = this.recoilKick;
         }

         this.play(SoundEvents.FIREWORK_ROCKET_BLAST, 1.6F, 0.9F);
         this.play(SoundEvents.CROSSBOW_SHOOT, 0.7F, 0.6F);
         Vec3 eye = this.mc.player.getEyePosition(1.0F);
         Vec3 dir = this.mc.player.getViewVector(1.0F);
         this.sendPose();
         if (this.pendingKick > 0.0F) {
            this.mc.player.setXRot(Math.max(-90.0F, this.mc.player.getXRot() - this.pendingKick));
            this.mc.player.setYRot(this.mc.player.getYRot() + (float)(Math.random() - 0.5) * this.pendingKick * 0.4F);
            this.pendingKick = 0.0F;
         }

         this.network.shot(eye.x, eye.y, eye.z, dir.x, dir.y, dir.z, this.blockDistance(eye, dir)).whenComplete((r, e) -> this.mc.execute(() -> {
            if (e == null && r != null && !r.ok() && this.session != null) {
               ChatUtil.actionBar("§7" + r.error());
            }
         }));
      }
   }

   private double blockDistance(Vec3 eye, Vec3 dir) {
      Vec3 end = eye.add(dir.scale(100.0));
      BlockHitResult hit = this.mc.level.clip(new ClipContext(eye, end, Block.COLLIDER, Fluid.NONE, this.mc.player));
      return hit.getType() == Type.BLOCK ? Math.min(100.0, hit.getLocation().distanceTo(eye)) : 100.0;
   }

   public void tick() {
      if (this.invite != null && this.invite.expires < System.currentTimeMillis()) {
         this.invite = null;
      }

      if (this.session != null && this.mc.player != null) {
         long now = System.currentTimeMillis();
         if (this.session.state != DuelSession.State.ENDED && now - this.lastPose >= 100L) {
            this.sendPose();
         }
      }
   }

   private void sendPose() {
      this.lastPose = System.currentTimeMillis();
      LocalPlayer p = this.mc.player;
      this.network.pose(p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getXRot(), p.isShiftKeyDown());
   }

   public void renderWorld(WorldCtx ctx, float tickDelta, int bubbleColor, boolean grid) {
      if (this.session != null && this.mc.level != null) {
         this.renderer.render(ctx, this.session, this.weapon, bubbleColor, grid, tickDelta);
      }
   }

   public static void renderFirstPerson(PoseStack matrices, SubmitNodeCollector queue, float tickDelta) {
      DuelManager m = current;
      if (m != null) {
         DuelWeapon w = m.weapon;
         float equip = 1.0F - w.equipProgress();
         float recoil = w.recoil();
         float reload = w.reloadProgress();
         matrices.pushPose();
         matrices.translate(0.3F, -0.36F - equip * 0.5F + recoil * 0.05F, -0.62F + recoil * 0.1F);
         matrices.mulPose(Axis.XP.rotationDegrees(recoil * 35.0F));
         float spin = 0.0F;
         if (reload >= 0.0F) {
            float tilt = (float)Math.sin(reload * Math.PI);
            matrices.mulPose(Axis.ZP.rotationDegrees(tilt * 40.0F));
            matrices.translate(-tilt * 0.08F, -tilt * 0.05F, 0.0F);
            spin = reload * 720.0F;
         }

         float s = 0.05625F;
         matrices.scale(s, s, s);
         boolean flash = w.muzzleFlash();
         float light = m.mc.level == null ? 1.0F : 0.55F + 0.45F * m.mc.level.getMaxLocalRawBrightness(m.mc.player.blockPosition()) / 15.0F;
         float spinFinal = spin;
         queue.submitCustomGeometry(matrices, WorldRender.solidColor(), (entry, vc) -> {
            PoseStack local = new PoseStack();
            local.last().pose().set(entry.pose());
            local.last().normal().set(entry.normal());
            SheriffModel.draw(local, vc, light, spinFinal, flash);
         });
         matrices.popPose();
      }
   }

   public void renderHud(GuiGraphics ctx) {
      int sw = ctx.guiWidth();
      int sh = ctx.guiHeight();
      Font tr = this.mc.font;
      if (this.invite != null && this.session == null) {
         long left = Math.max(0L, (this.invite.expires - System.currentTimeMillis()) / 1000L);
         String t1 = this.invite.fromName + " fordert dich zum Sheriff-Duell heraus!";
         String t2 = "/meowl duel accept  ·  /meowl duel decline  (" + left + " s)";
         int w = Math.max(tr.width(t1), tr.width(t2)) + 16;
         Render2D.roundRect(ctx, (sw - w) / 2, 30, w, 30, 4, -535686112);
         Render2D.roundRect(ctx, (sw - w) / 2, 30, 3, 30, 1, -7643914);
         ctx.drawString(tr, t1, (sw - tr.width(t1)) / 2, 35, -1578509, true);
         ctx.drawString(tr, t2, (sw - tr.width(t2)) / 2, 47, -3885571, false);
      }

      if (this.session != null) {
         String title = "SHERIFF-DUELL vs " + this.session.opponentName;
         ctx.drawString(tr, Component.literal(title).withStyle(ChatFormatting.BOLD), (sw - tr.width(title) - 6) / 2, 6, -3885571, true);
         this.hpBar(ctx, sw / 2 - 104, 18, 100, this.session.myHp, this.session.maxHp, -14498466, "Du");
         this.hpBar(ctx, sw / 2 + 4, 18, 100, this.session.opponentHp, this.session.maxHp, -1096636, this.session.opponentName);
         if (this.session.rounds > 1) {
            String score = "Runde "
               + Math.min(this.session.round, this.session.rounds)
               + "/"
               + this.session.rounds
               + "   "
               + this.session.myWins
               + " : "
               + this.session.opponentWins;
            int scoreW = tr.width(score) + 10;
            Render2D.roundRect(ctx, (sw - scoreW) / 2, 30, scoreW, 11, 3, -1072557024);
            ctx.drawString(tr, score, (sw - tr.width(score)) / 2, 32, -1578509, true);

            for (int i = 0; i < this.session.winsNeeded; i++) {
               ctx.fill(sw / 2 - 104 + i * 7, 30, sw / 2 - 99 + i * 7, 35, i < this.session.myWins ? -14498466 : 1090519039);
               ctx.fill(sw / 2 + 99 - i * 7, 30, sw / 2 + 104 - i * 7, 35, i < this.session.opponentWins ? -1096636 : 1090519039);
            }
         }

         String ammo = this.session.isReloading() ? "LÄDT …" : this.session.ammo + " / " + this.session.mag;
         int ax = sw - 92;
         int ay = sh - 40;
         Render2D.roundRect(ctx, ax, ay, 86, 30, 4, -1072557024);
         ctx.drawString(tr, "SHERIFF", ax + 6, ay + 5, -7564628, false);
         ctx.drawString(tr, ammo, ax + 80 - tr.width(ammo), ay + 5, this.session.ammo == 0 ? -1096636 : -1578509, true);

         for (int i = 0; i < this.session.mag; i++) {
            ctx.fill(ax + 6 + i * 12, ay + 18, ax + 14 + i * 12, ay + 24, i < this.session.ammo && !this.session.isReloading() ? -673470 : 1090519039);
         }

         long now = System.currentTimeMillis();
         if (this.session.state == DuelSession.State.COUNTDOWN) {
            String n = String.valueOf(Math.max(1, (int)Math.ceil((this.session.startAt - now) / 1000.0)));
            this.big(ctx, n, sw / 2, sh / 2 - 30, -1578509);
            if (this.session.roundBanner != null && now < this.session.roundBannerUntil) {
               String rb = this.session.roundBanner + " §7· Runde " + this.session.round + "/" + this.session.rounds;
               ctx.drawString(tr, rb, (sw - tr.width(rb)) / 2, sh / 2 - 46, -1578509, true);
            }
         } else if (this.session.state == DuelSession.State.ACTIVE && now - this.session.startAt < 800L) {
            this.big(ctx, "LOS!", sw / 2, sh / 2 - 30, -7934036);
         } else if (this.session.state == DuelSession.State.ENDED && this.session.resultTitle != null) {
            this.big(ctx, this.session.resultTitle, sw / 2, sh / 2 - 40, this.session.resultTitle.equals("SIEG") ? -278748 : -495247);
            ctx.drawString(tr, this.session.resultText, (sw - tr.width(this.session.resultText)) / 2, sh / 2 - 10, -1578509, true);
         }

         if (this.session.state != DuelSession.State.ENDED && !this.session.arena.inside(this.mc.player.position())) {
            String warn = "Zurück in die Arena!";
            ctx.drawString(tr, warn, (sw - tr.width(warn)) / 2, sh / 2 + 20, -495247, true);
         }
      }
   }

   private void hpBar(GuiGraphics ctx, int x, int y, int w, int hp, int max, int color, String label) {
      Render2D.roundRect(ctx, x, y, w, 10, 3, -1072557024);
      int fill = Math.round((float)((w - 2) * Math.max(0, hp)) / max);
      if (fill > 0) {
         Render2D.roundRect(ctx, x + 1, y + 1, fill, 8, 2, color);
      }

      String text = this.mc.font.plainSubstrByWidth(label, w - 30) + " " + Math.max(0, hp);
      ctx.drawString(this.mc.font, text, x + 4, y + 1, -1578509, true);
   }

   private void big(GuiGraphics ctx, String text, int cx, int y, int color) {
      ctx.pose().pushMatrix();
      ctx.pose().translate(cx, y);
      ctx.pose().scale(3.0F, 3.0F);
      ctx.drawString(this.mc.font, text, -this.mc.font.width(text) / 2, 0, color, true);
      ctx.pose().popMatrix();
   }

   public void shutdown() {
      this.leave();
      this.invite = null;
   }

   private record Invite(String id, UUID from, String fromName, double radius, int hp, int rounds, long expires) {
   }
}
