package com.lennart.nebula.module.mods;

import com.mojang.blaze3d.platform.InputConstants;

import com.lennart.nebula.util.MeowlCommands;
import com.lennart.nebula.NebulaClient;
import com.lennart.nebula.clap.ClapManager;
import com.lennart.nebula.fx.FirstPersonFx;
import com.lennart.nebula.gui.Render2D;
import com.lennart.nebula.module.Category;
import com.lennart.nebula.module.Module;
import com.lennart.nebula.module.Section;
import com.lennart.nebula.setting.Bind;
import com.lennart.nebula.setting.BooleanSetting;
import com.lennart.nebula.setting.ColorSetting;
import com.lennart.nebula.setting.KeybindSetting;
import com.lennart.nebula.setting.NumberSetting;
import com.lennart.nebula.setting.Setting;
import com.lennart.nebula.util.ChatUtil;
import com.lennart.nebula.util.WorldRender;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.math.Axis;
import java.awt.Color;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import com.lennart.nebula.util.WorldCtx;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public class Clap extends Module {
   private static Clap instance;
   private final KeybindSetting key = this.add(new KeybindSetting("Clap-Taste", "Einladen / Annehmen, im Takt halten und loslassen", Bind.keyboard(InputConstants.KEY_J)));
   private final BooleanSetting allowInvites = this.add(new BooleanSetting("Anfragen erlauben", "Andere dürfen dich zum High-Five einladen", true));
   private final BooleanSetting guide = this.add(new BooleanSetting("Positions-Hilfe", "Ring am Boden zeigt, wo du stehen sollst", true));
   private final ColorSetting guideColor = this.add(new ColorSetting("Ring-Farbe", "Farbe der Positions-Hilfe", -5796870));
   private final BooleanSetting particles = this.add(new BooleanSetting("Partikel", "Partikel beim Klatschen", true));
   private final BooleanSetting sounds = this.add(new BooleanSetting("Sounds", "Countdown- und Klatsch-Geräusche", true));
   private final BooleanSetting screenFx = this.add(new BooleanSetting("Bildschirm-Effekte", "Wackeln und Aufblitzen bei guten Treffern", true));
   private final NumberSetting intensity = this.add(new NumberSetting("Effekt-Stärke", "Wie viele Partikel und wie stark das Wackeln", 1.0, 0.3, 2.0, 0.1, "x"));
   private final com.lennart.nebula.setting.ModeSetting effectStyle = this.add(
      new com.lennart.nebula.setting.ModeSetting(
         "Effekt-Stil", "Zusätzliche Effekte beim Klatschen", "Feuer", "Klassisch", "Feuer", "Blitz", "Eis", "Konfetti", "Herzen"
      )
   );
   private final BooleanSetting shockwave = this.add(new BooleanSetting("Schockwelle", "Leuchtender Ring, der sich beim Aufprall ausbreitet", true));
   private final BooleanSetting thirdPerson = this.add(
      new BooleanSetting("Arm-Pose sichtbar", "Arm-Haltung auch am Spielermodell (3rd Person / Partner)", true)
   );
   private final NumberSetting statClaps = this.stat("Claps");
   private final NumberSetting statMax = this.stat("PERFECT MAX");
   private final ClapManager manager;
   private boolean keyDown;

   public Clap() {
      super("Clap", "High-Five mit anderen Meowl-Spielern – mit Aufladen, Timing und PERFECT MAX.", Category.MEOWL);
      this.section(Section.GAMES);
      this.icon(Items.SLIME_BALL);
      instance = this;
      this.guideColor.setVisibility(this.guide::get);
      this.group("Anfragen", new Setting[]{this.allowInvites, this.guide, this.guideColor});
      this.group("Effekte", new Setting[]{this.effectStyle, this.shockwave, this.particles, this.sounds, this.screenFx, this.intensity, this.thirdPerson});
      this.manager = new ClapManager(new ClapManager.Options() {
         @Override
         public boolean enabled() {
            return Clap.this.isEnabled();
         }

         @Override
         public boolean allowInvites() {
            return Clap.this.allowInvites.get();
         }

         @Override
         public boolean particles() {
            return Clap.this.particles.get();
         }

         @Override
         public boolean sounds() {
            return Clap.this.sounds.get();
         }

         @Override
         public boolean screenFx() {
            return Clap.this.screenFx.get();
         }

         @Override
         public String effectStyle() {
            return Clap.this.effectStyle.get();
         }

         @Override
         public boolean shockwave() {
            return Clap.this.shockwave.get();
         }

         @Override
         public float intensity() {
            return (float)Clap.this.intensity.get().doubleValue();
         }

         @Override
         public void onResult(ClapManager.Grade grade) {
            Clap.this.statClaps.set(Clap.this.statClaps.get() + 1.0);
            if (grade == ClapManager.Grade.MAX) {
               Clap.this.statMax.set(Clap.this.statMax.get() + 1.0);
            }

            NebulaClient.getInstance().getModuleManager().notifyChanged();
         }
      });
      FirstPersonFx.setArmAnimation(this::firstPerson);
      this.setEnabled(true);
      MeowlCommands.add("clap", "clap <spieler>|accept|decline|cancel §7– High-Five", this::build);
   }

   private NumberSetting stat(String name) {
      NumberSetting s = this.add(new NumberSetting("Statistik: " + name, "", 0.0, 0.0, 1.0E9, 1.0, ""));
      s.setVisibility(() -> false);
      return s;
   }

   private LiteralArgumentBuilder<FabricClientCommandSource> build(String name) {
      return ClientCommandManager.literal(name)
         .executes(
            c -> this.run(
               () -> ChatUtil.chat(
                  "§d/meowl " + name + " <spieler>§7 einladen · §d/meowl " + name + " accept|decline|cancel §7· Taste: §f" + this.key.get().getDisplayName()
               )
            )
         )
         .then(ClientCommandManager.literal("accept").executes(c -> this.run(() -> this.manager.respond(true))))
         .then(ClientCommandManager.literal("decline").executes(c -> this.run(() -> this.manager.respond(false))))
         .then(ClientCommandManager.literal("cancel").executes(c -> this.run(() -> {
            this.manager.cancel();
            ChatUtil.chat("§7High-Five abgebrochen.");
         })))
         .then(
            ClientCommandManager.argument("spieler", StringArgumentType.word())
               .executes(c -> this.run(() -> this.manager.invite(StringArgumentType.getString(c, "spieler"))))
         );
   }

   private int run(Runnable action) {
      if (!this.isEnabled()) {
         ChatUtil.chat("§7Clap ist aus (Menü → Meowl → Clap).");
         return 0;
      } else {
         this.mc.execute(action);
         return 1;
      }
   }

   @Override
   protected void onDisable() {
      this.manager.cancel();
   }

   @Override
   public void onTick() {
      if (this.mc.player != null) {
         boolean down = this.key.get().isPressed() && this.mc.screen == null;
         if (down && !this.keyDown && !this.manager.busy()) {
            this.manager.keyPressed();
         }

         this.keyDown = down;
         this.manager.tick();
      }
   }

   private void frame() {
      this.manager.frame(this.key.get().isPressed());
   }

   private boolean firstPerson(PoseStack m, float tickProgress, boolean right) {
      if (!this.isEnabled()) {
         return false;
      } else {
         float[] p = this.manager.pose(true);
         if (p == null) {
            return false;
         } else {
            float raise = p[0];
            float pull = p[1];
            float forward = p[2];
            float kick = p[3];
            float f = right ? 1.0F : -1.0F;
            float px = f * 0.55F;
            float py = -0.55F;
            float pz = -0.7F;
            m.translate(-0.24F * f * raise - 0.07F * f * forward, 0.36F * raise - 0.05F * pull, 0.18F * pull - 0.24F * forward + 0.07F * kick);
            m.translate(px, py, pz);
            m.mulPose(Axis.ZP.rotationDegrees(f * (-18.0F * raise + 6.0F * pull)));
            m.mulPose(Axis.XP.rotationDegrees(14.0F * raise + 10.0F * pull - 12.0F * forward));
            m.translate(-px, -py, -pz);
            return true;
         }
      }
   }

   public static float[] modelPose(int entityId) {
      Clap c = instance;
      if (c == null || !c.isEnabled() || !c.thirdPerson.get() || c.mc.player == null) {
         return null;
      } else if (entityId == c.mc.player.getId()) {
         return c.manager.pose(true);
      } else {
         Player partner = c.manager.partnerEntity();
         return partner != null && partner.getId() == entityId ? c.manager.pose(false) : null;
      }
   }

   /** Farben der Schockwelle je Effekt-Stil. */
   private int[] waveColors() {
      return switch (this.effectStyle.get()) {
         case "Feuer" -> new int[]{0xFF7A1A, 0xFFD040};
         case "Blitz" -> new int[]{0x60B0FF, 0xF0FAFF};
         case "Eis" -> new int[]{0x9AE0FF, 0xFFFFFF};
         case "Konfetti" -> new int[]{0xFF5FD2, 0x5BE38A};
         case "Herzen" -> new int[]{0xFF4D7A, 0xFFB0C8};
         default -> new int[]{0xB08CFF, 0xFFFFFF};
      };
   }

   private void renderShockwave(WorldCtx context) {
      Vec3 at = this.manager.lastImpact();
      ClapManager.Grade g = this.manager.lastImpactGrade();
      if (at == null || g == null || g == ClapManager.Grade.BAD || !this.shockwave.get()) {
         return;
      }

      float t = (System.currentTimeMillis() - this.manager.lastImpactTime()) / 650.0F;
      if (t >= 1.0F) {
         return;
      }

      float power = (g.ordinal() + 1) / 4.0F;
      int[] c = this.waveColors();
      Vec3 cam = context.cameraPos();
      PoseStack m = context.matrices();
      VertexConsumer vc = context.buffer(WorldRender.glow());
      float ease = 1.0F - (1.0F - t) * (1.0F - t);
      float fade = 1.0F - t;
      m.pushPose();
      m.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
      // flacher Ring auf Höhe der Hände
      ItemBeam.ring(vc, m.last().pose(), 0.2F + ease * (1.6F + 1.6F * power), c[0], 0.9F * fade);
      ItemBeam.ring(vc, m.last().pose(), 0.1F + ease * (1.0F + 1.0F * power), c[1], 0.7F * fade);
      // zweiter, stehender Ring zur Kamera hin
      m.mulPose(context.cameraOrientation());
      m.mulPose(Axis.XP.rotationDegrees(90.0F));
      ItemBeam.ring(vc, m.last().pose(), 0.15F + ease * (0.9F + 0.9F * power), c[1], 0.8F * fade);
      m.popPose();
   }

   @Override
   public void onRenderWorld(WorldCtx context, float tickDelta) {
      this.frame();
      this.renderShockwave(context);
      if (this.guide.get() && this.manager.phase() == ClapManager.Phase.POSITION && this.mc.player != null) {
         Player p = this.manager.partnerEntity();
         if (p != null) {
            Vec3 partner = p.getPosition(tickDelta);
            Vec3 me = this.mc.player.getPosition(tickDelta);
            Vec3 dir = new Vec3(me.x - partner.x, 0.0, me.z - partner.z);
            dir = dir.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : dir.normalize();
            Vec3 spot = partner.add(dir.scale(2.0));
            boolean ok = this.manager.distanceOk(p);
            int rgb = ok ? (this.manager.facingOk(p) ? 4906624 : 16498468) : this.guideColor.get() & 16777215;
            Vec3 cam = context.cameraPos();
            PoseStack m = context.matrices();
            VertexConsumer vc = context.buffer(WorldRender.glow());
            double t = System.currentTimeMillis() / 1000.0;
            m.pushPose();
            m.translate(spot.x - cam.x, spot.y - cam.y, spot.z - cam.z);
            ItemBeam.ring(vc, m.last().pose(), 0.55F + 0.05F * (float)Math.sin(t * 5.0), rgb, 0.9F);
            ItemBeam.ring(vc, m.last().pose(), 0.25F, rgb, 0.5F);
            m.popPose();
         }
      }
   }

   @Override
   public void onRenderHud(GuiGraphics ctx, float tickDelta) {
      this.frame();
      int sw = ctx.guiWidth();
      int sh = ctx.guiHeight();
      Font tr = this.mc.font;
      int flash = this.manager.flash();
      if (flash != 0) {
         ctx.fill(0, 0, sw, sh, flash);
      }

      String from = this.manager.pendingInviteFrom();
      if (from != null && !this.manager.busy()) {
         String t1 = from + " möchte abklatschen!";
         String t2 = "[" + this.key.get().getDisplayName() + "] annehmen  ·  /meowl clap decline  (" + this.manager.inviteSecondsLeft() + " s)";
         this.panel(ctx, tr, sw, 30, t1, t2);
      }

      switch (this.manager.phase()) {
         case POSITION:
            this.renderPosition(ctx, tr, sw);
            break;
         case CHARGE:
            this.renderCharge(ctx, tr, sw, sh);
            break;
         case RESULT:
            this.renderResult(ctx, tr, sw, sh);
      }
   }

   private void panel(GuiGraphics ctx, Font tr, int sw, int y, String t1, String t2) {
      int w = Math.max(tr.width(t1), tr.width(t2)) + 18;
      Render2D.roundRect(ctx, (sw - w) / 2, y, w, 30, 5, -535686112);
      Render2D.roundRect(ctx, (sw - w) / 2, y, 3, 30, 1, -7643914);
      ctx.drawString(tr, t1, (sw - tr.width(t1)) / 2, y + 5, -1578509, true);
      ctx.drawString(tr, t2, (sw - tr.width(t2)) / 2, y + 17, -3885571, false);
   }

   private void renderPosition(GuiGraphics ctx, Font tr, int sw) {
      Player p = this.manager.partnerEntity();
      String title = "HIGH-FIVE mit " + this.manager.partnerName();
      String line;
      if (p == null) {
         line = "§7Warte, bis " + this.manager.partnerName() + " in Sichtweite ist …";
      } else {
         boolean dist = this.manager.distanceOk(p);
         boolean face = this.manager.facingOk(p);
         line = (dist ? "§a✔ Abstand" : "§c✘ Abstand")
            + "  "
            + (face ? "§a✔ Anschauen" : "§c✘ Anschauen")
            + "  "
            + (this.manager.partnerReady() ? "§a✔ " : "§7… ")
            + this.manager.partnerName();
      }

      int w = Math.max(tr.width(title) + 40, tr.width(line) + 18);
      int x = (sw - w) / 2;
      int y = 30;
      Render2D.roundRect(ctx, x, y, w, 32, 5, -535686112);
      Render2D.roundRect(ctx, x, y, 3, 32, 1, -7643914);
      ctx.drawString(tr, Component.literal(title).withStyle(ChatFormatting.BOLD), x + 9, y + 5, -3885571, true);
      String left = this.manager.sessionSecondsLeft() + " s";
      ctx.drawString(tr, left, x + w - tr.width(left) - 6, y + 5, -10656900, false);
      ctx.drawString(tr, line, (sw - tr.width(line)) / 2, y + 18, -1578509, false);
   }

   private void renderCharge(GuiGraphics ctx, Font tr, int sw, int sh) {
      long n = ClapManager.now();
      long beat = this.manager.beatLocal();
      long toBeat = beat - n;
      int bigColor = -1578509;
      String big;
      float pop;
      if (toBeat > 1800L) {
         big = "BEREIT";
         pop = 0.0F;
         bigColor = -3885571;
      } else if (toBeat > 0L) {
         int k = (int)(toBeat / 600L) + 1;
         big = String.valueOf(Math.min(3, k));
         pop = 1.0F - (float)((toBeat - 1L) % 600L) / 600.0F;
      } else {
         big = "KLATSCH!";
         pop = Mth.clamp((float)(-toBeat) / 200.0F, 0.0F, 1.0F);
         bigColor = -1541639;
      }

      float scale = 2.6F + 0.6F * (1.0F - pop) * (big.equals("BEREIT") ? 0 : 1);
      int cy = sh / 2 - 52;
      ctx.pose().pushMatrix();
      ctx.pose().translate(sw / 2.0F, cy);
      ctx.pose().scale(scale, scale);
      ctx.drawString(tr, Component.literal(big).withStyle(ChatFormatting.BOLD), -tr.width(big) / 2 - 1, -4, bigColor, true);
      ctx.pose().popMatrix();
      int bw = 180;
      int bh = 8;
      int bx = (sw - bw) / 2;
      int by = sh / 2 + 34;
      Render2D.roundRect(ctx, bx - 4, by - 16, bw + 8, 40, 5, -1072557024);
      Render2D.roundRect(ctx, bx, by, bw, bh, 3, -14012603);
      int mid = bx + bw / 2;
      float pxPerMs = bw / 2.0F / 1200.0F;
      int good = (int)(200.0F * pxPerMs);
      int perfect = (int)(90.0F * pxPerMs);
      int max = Math.max(1, (int)(45.0F * pxPerMs));
      Render2D.rect(ctx, mid - good, by, good * 2, bh, 1615519360);
      Render2D.rect(ctx, mid - perfect, by, perfect * 2, bh, -1594114268);
      Render2D.rect(ctx, mid - max, by, max * 2, bh, -1541639);
      long shown = this.manager.released() ? this.manager.releaseLocal() : n;
      int cursor = (int)Mth.clamp(mid + (float)(shown - beat) * pxPerMs, (float)bx, (float)(bx + bw));
      Render2D.rect(ctx, cursor - 1, by - 3, 3, bh + 6, this.manager.released() ? -1 : -3885571);
      float charge = this.manager.charge(n);
      int cw = (int)(bw * charge);
      int chargeColor = charge >= 0.9F ? -1541639 : (charge >= 0.5F ? -278748 : -7643914);
      Render2D.roundRect(ctx, bx, by + bh + 4, bw, 4, 2, -14933965);
      if (cw > 0) {
         Render2D.roundRect(ctx, bx, by + bh + 4, cw, 4, 2, chargeColor);
      }

      String hint = this.manager.released()
         ? (this.manager.partnerHit() ? "§aBeide geklatscht!" : "§7Warte auf " + this.manager.partnerName() + " …")
         : (this.manager.charging() ? "§dAufladen … loslassen im Takt!" : "§7[" + this.key.get().getDisplayName() + "] halten zum Aufladen");
      ctx.drawString(tr, hint, (sw - tr.width(hint)) / 2, by - 12, -1578509, false);
   }

   private void renderResult(GuiGraphics ctx, Font tr, int sw, int sh) {
      if (this.manager.played() && this.manager.grade() != null) {
         long t = ClapManager.now() - this.manager.playLocal();
         ClapManager.Grade g = this.manager.grade();
         float in = Mth.clamp((float)t / 180.0F, 0.0F, 1.0F);
         float out = Mth.clamp((float)(t - 2000L) / 500.0F, 0.0F, 1.0F);
         float alpha = in * (1.0F - out);
         if (!(alpha <= 0.01F)) {
            float overshoot = 1.0F + 0.5F * (float)Math.sin(in * Math.PI) * (g.ordinal() + 1) / 4.0F;
            float pulse = g == ClapManager.Grade.MAX ? 1.0F + 0.06F * (float)Math.sin(t / 70.0) : 1.0F;
            float scale = (2.0F + 0.4F * g.ordinal()) * overshoot * pulse;
            int color = g.color;
            if (g == ClapManager.Grade.MAX) {
               float hue = (float)(t % 1200L) / 1200.0F;
               color = 0xFF000000 | Color.HSBtoRGB(hue, 0.55F, 1.0F) & 16777215;
            }

            String label = this.manager.missed() ? "VERPASST" : g.label;
            int cy = sh / 2 - 50;
            ctx.pose().pushMatrix();
            ctx.pose().translate(sw / 2.0F, cy);
            ctx.pose().scale(scale, scale);
            ctx.drawString(tr, Component.literal(label).withStyle(ChatFormatting.BOLD), -tr.width(label) / 2, -4, Render2D.alpha(color, alpha), true);
            ctx.pose().popMatrix();
            String sub = "mit " + this.manager.partnerName();
            ctx.drawString(tr, sub, (sw - tr.width(sub)) / 2, cy + (int)(8.0F * scale), Render2D.alpha(-7564628, alpha), false);
         }
      }
   }
}
