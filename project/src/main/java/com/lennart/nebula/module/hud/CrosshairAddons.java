package com.lennart.nebula.module.hud;

import com.lennart.nebula.module.Section;
import com.lennart.nebula.NebulaClient;
import com.lennart.nebula.gui.ModulePreview;
import com.lennart.nebula.module.Category;
import com.lennart.nebula.module.Module;
import com.lennart.nebula.setting.BooleanSetting;
import com.lennart.nebula.setting.ColorSetting;
import com.lennart.nebula.setting.ModeSetting;
import com.lennart.nebula.setting.NumberSetting;
import com.lennart.nebula.setting.Setting;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;

public class CrosshairAddons extends Module {
   // erst beim Zeichnen anlegen: beim Start sind Item-Daten in 26.x noch nicht fertig
   private static ItemStack shield;

   private static ItemStack shield() {
      if (shield == null) {
         shield = new ItemStack(Items.SHIELD);
      }

      return shield;
   }
   private final BooleanSetting hitmarker = this.add(new BooleanSetting("Hitmarker", "X-Markierung beim Schlagen eines Ziels", true));
   private final ModeSetting hitStyle = this.add(new ModeSetting("Hitmarker-Stil", "Form der Markierung", "Klassisch", "Klassisch", "Lang", "Ecken", "Plus"));
   // Größen in echten Bildschirmpixeln (unabhängig von der GUI-Größe) – fein wie bei CS-Crosshairs
   private final NumberSetting hitSize = this.add(new NumberSetting("Hitmarker-Größe (px)", "Länge der Markierung in Bildschirmpixeln", 12.0, 4.0, 40.0, 1.0, "px"));
   private final NumberSetting hitGap = this.add(new NumberSetting("Hitmarker-Abstand", "Freiraum in der Mitte", 4.0, 0.0, 20.0, 1.0, "px"));
   private final NumberSetting hitThick = this.add(new NumberSetting("Hitmarker-Dicke", "Strichstärke", 2.0, 1.0, 6.0, 1.0, "px"));
   private final ModeSetting hitAnim = this.add(new ModeSetting("Hitmarker-Animation", "Wie die Markierung erscheint", "Pop", "Pop", "Ausdehnen", "Keine"));
   private final NumberSetting hitDuration = this.add(
      new NumberSetting("Hitmarker-Dauer", "Wie lange die Markierung sichtbar ist", 350.0, 150.0, 1000.0, 50.0, "ms")
   );
   private final ColorSetting hitColor = this.add(new ColorSetting("Hitmarker-Farbe", "Farbe beim Schlag", -1));
   private final ColorSetting confirmColor = this.add(new ColorSetting("Treffer-Farbe", "Farbe, sobald das Ziel wirklich Schaden genommen hat", -1096636));
   private final BooleanSetting hitSound = this.add(new BooleanSetting("Hit-Sound", "Kurzer Ton bei bestätigtem Treffer", false));
   private final BooleanSetting killMarker = this.add(new BooleanSetting("Kill-Marker", "Große rote Markierung, wenn dein Ziel stirbt", true));
   private final ColorSetting killColor = this.add(new ColorSetting("Kill-Farbe", "Farbe des Kill-Markers", -1688010));
   private final NumberSetting killSize = this.add(new NumberSetting("Kill-Marker-Größe", "Größe des Kill-Markers", 20.0, 6.0, 60.0, 1.0, "px"));
   private final BooleanSetting cooldown = this.add(
      new BooleanSetting("Attack-Indikator", "Anzeige, bis der nächste Schlag voll lädt", true).alias(new String[]{"Attack-Cooldown"})
   );
   private final ModeSetting cooldownStyle = this.add(
      new ModeSetting("Indikator-Stil", "Darstellung", "Balken", "Balken", "Bogen", "Ring", "Punkte").alias(new String[]{"Cooldown-Stil"})
   );
   private final ColorSetting cooldownColor = this.add(new ColorSetting("Indikator-Farbe", "Farbe der Anzeige", -3885571).alias(new String[]{"Cooldown-Farbe"}));
   private final ColorSetting cooldownBg = this.add(new ColorSetting("Indikator-Hintergrund", "Farbe des leeren Teils", 0x50000000));
   private final NumberSetting cooldownWidth = this.add(new NumberSetting("Indikator-Breite", "Breite (Balken/Punkte) bzw. Durchmesser (Bogen/Ring)", 24.0, 8.0, 80.0, 1.0, "px"));
   private final NumberSetting cooldownThick = this.add(new NumberSetting("Indikator-Dicke", "Strichstärke", 2.0, 1.0, 6.0, 1.0, "px"));
   private final NumberSetting cooldownOffset = this.add(new NumberSetting("Indikator-Abstand", "Abstand unter der Mitte (Balken/Punkte)", 16.0, 4.0, 60.0, 1.0, "px"));
   private final BooleanSetting readyFlash = this.add(new BooleanSetting("Bereit-Blitz", "Kurzes Aufleuchten, sobald der Schlag voll geladen ist", true));
   private final BooleanSetting entityIndicator = this.add(new BooleanSetting("Entity-Indikator", "Ecken, wenn ein Wesen anvisiert ist", true));
   private final ColorSetting entityColor = this.add(new ColorSetting("Entity-Farbe", "Farbe des Entity-Indikators", -570920591));
   private final BooleanSetting blockIndicator = this.add(new BooleanSetting("Block-Indikator", "Ecken, wenn ein Block anvisiert ist", false));
   private final ColorSetting blockColor = this.add(new ColorSetting("Block-Farbe", "Farbe des Block-Indikators", -1436506630));
   private final NumberSetting frameSize = this.add(new NumberSetting("Rahmen-Größe", "Größe der Ecken um das Fadenkreuz", 14.0, 4.0, 40.0, 1.0, "px"));
   private final NumberSetting frameLength = this.add(new NumberSetting("Ecken-Länge", "Länge der Eckenstriche", 5.0, 2.0, 20.0, 1.0, "px"));
   private final NumberSetting frameThick = this.add(new NumberSetting("Ecken-Dicke", "Strichstärke der Ecken", 1.0, 1.0, 4.0, 1.0, "px"));
   private final NumberSetting shieldSize = this.add(new NumberSetting("Schild-Größe", "Größe des Schild-Symbols", 16.0, 8.0, 48.0, 1.0, "px"));
   private final NumberSetting infoScale = this.add(new NumberSetting("Ziel-Info-Größe", "Größe von Name und Leben", 0.75, 0.4, 1.5, 0.05, "x"));
   private final BooleanSetting targetInfo = this.add(new BooleanSetting("Ziel-Info", "Name und Leben des anvisierten Ziels unter dem Fadenkreuz", false));
   private final BooleanSetting shieldIndicator = this.add(
      new BooleanSetting("Block-Anzeige (Schild)", "Schild-Symbol, wenn dein Ziel gerade mit dem Schild blockt (Axt nehmen!)", true)
   );
   private final BooleanSetting range = this.add(new BooleanSetting("Reichweite", "Entfernung zum anvisierten Ziel", true));
   private final ColorSetting rangeColor = this.add(new ColorSetting("Reichweite-Farbe", "Farbe der Entfernung", -1578509));
   private final BooleanSetting rangeWarn = this.add(new BooleanSetting("Reichweiten-Ampel", "Grün in Schlagreichweite (≤ 3 Blöcke), sonst rot", true));
   private final HudBox rangeBox = this.hudBox("Reichweite", 51.6, 51.8);
   private final BooleanSetting cps = this.add(new BooleanSetting("CPS-Anzeige", "Klicks pro Sekunde (links | rechts)", false));
   private final ColorSetting cpsColor = this.add(new ColorSetting("CPS-Farbe", "Textfarbe der CPS-Anzeige", -1578509));
   private final HudBox cpsBox = this.hudBox("CPS", 1.0, 60.0);
   private final BooleanSetting combo = this.add(new BooleanSetting("Combo-Zähler", "Treffer in Folge, ohne selbst getroffen zu werden", false));
   private final ColorSetting comboColor = this.add(new ColorSetting("Combo-Farbe", "Textfarbe des Combo-Zählers", -11930));
   private final HudBox comboBox = this.hudBox("Combo", 1.0, 66.0);
   private long hitTime;
   private boolean hitConfirmed;
   private LivingEntity hitTarget;
   private int hitTicks;
   private LivingEntity lastVictim;
   private long lastVictimTime;
   private long killTime;
   private float lastCooldown = 1.0F;
   private long readyTime;
   private final Deque<Long> leftClicks = new ArrayDeque<>();
   private final Deque<Long> rightClicks = new ArrayDeque<>();
   private boolean leftDown;
   private boolean rightDown;
   private int comboCount;
   private long comboTime;
   private int lastHurtTime;

   public CrosshairAddons() {
      super("Crosshair Addons", "Hitmarker, Kill-Marker, Attack-Indikator, Ziel-Info, Schild-Anzeige, Reichweite, CPS und Combo.", Category.HUD);
      this.section(Section.PVP);
      this.icon(Items.SPYGLASS);
      this.rangeBox.background.set(false);
      this.group(
         "Hitmarker",
         new Setting[]{
            this.hitmarker, this.hitStyle, this.hitSize, this.hitGap, this.hitThick, this.hitAnim, this.hitDuration, this.hitColor, this.confirmColor, this.hitSound, this.killMarker, this.killColor, this.killSize
         }
      );
      this.group("Attack-Indikator", new Setting[]{this.cooldown, this.cooldownStyle, this.cooldownColor, this.cooldownBg, this.cooldownWidth, this.cooldownThick, this.cooldownOffset, this.readyFlash});
      this.group("Ziel", new Setting[]{this.entityIndicator, this.entityColor, this.blockIndicator, this.blockColor, this.frameSize, this.frameLength, this.frameThick, this.targetInfo, this.infoScale, this.shieldIndicator, this.shieldSize});
      this.group("Reichweite", new Setting[]{this.range, this.rangeColor, this.rangeWarn});
      this.group("CPS & Combo", new Setting[]{this.cps, this.cpsColor, this.combo, this.comboColor});
      this.group("HUD-Kästen", this.boxSettings());

      for (Setting<?> s : new Setting[]{this.hitStyle, this.hitSize, this.hitGap, this.hitThick, this.hitAnim, this.hitDuration, this.hitColor, this.confirmColor, this.hitSound}) {
         s.setVisibility(this.hitmarker::get);
      }

      this.killColor.setVisibility(this.killMarker::get);
      this.killSize.setVisibility(this.killMarker::get);
      for (Setting<?> s : new Setting[]{this.cooldownStyle, this.cooldownColor, this.cooldownBg, this.cooldownWidth, this.cooldownThick}) {
         s.setVisibility(this.cooldown::get);
      }

      this.cooldownOffset.setVisibility(() -> this.cooldown.get() && !this.cooldownStyle.is("Bogen") && !this.cooldownStyle.is("Ring"));
      for (Setting<?> s : new Setting[]{this.frameSize, this.frameLength, this.frameThick}) {
         s.setVisibility(() -> this.entityIndicator.get() || this.blockIndicator.get());
      }

      this.infoScale.setVisibility(this.targetInfo::get);
      this.shieldSize.setVisibility(this.shieldIndicator::get);
      this.readyFlash.setVisibility(this.cooldown::get);
      this.blockColor.setVisibility(this.blockIndicator::get);
      this.entityColor.setVisibility(this.entityIndicator::get);
      this.rangeColor.setVisibility(() -> this.range.get() && !this.rangeWarn.get());
      this.rangeWarn.setVisibility(this.range::get);
      this.cpsColor.setVisibility(this.cps::get);
      this.comboColor.setVisibility(this.combo::get);
      AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
         if (world.isClientSide() && this.isEnabled() && player == this.mc.player) {
            this.onAttack(entity);
         }

         return InteractionResult.PASS;
      });
   }

   private Setting<?>[] boxSettings() {
      List<Setting<?>> list = new ArrayList<>();

      for (HudBox b : new HudBox[]{this.rangeBox, this.cpsBox, this.comboBox}) {
         list.add(b.scale);
         list.add(b.opacity);
         list.add(b.background);
         list.add(b.backgroundColor);
         list.add(b.rounding);
      }

      return list.toArray(new Setting[0]);
   }

   private void onAttack(Entity entity) {
      this.hitTime = System.currentTimeMillis();
      this.hitConfirmed = false;
      this.hitTarget = entity instanceof LivingEntity living ? living : null;
      this.hitTicks = 0;
      if (this.hitTarget != null) {
         this.lastVictim = this.hitTarget;
         this.lastVictimTime = this.hitTime;
      }
   }

   @Override
   protected void onDisable() {
      this.hitTarget = null;
      this.lastVictim = null;
      this.comboCount = 0;
   }

   @Override
   public void onTick() {
      LocalPlayer player = this.mc.player;
      if (this.hitTarget != null) {
         if (this.hitTarget.hurtTime > 0) {
            this.hitConfirmed = true;
            this.hitTarget = null;
            this.comboCount++;
            this.comboTime = System.currentTimeMillis();
            if (this.hitSound.get() && this.hitmarker.get()) {
               this.mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.7F, 0.35F));
            }
         } else if (++this.hitTicks > 6) {
            this.hitTarget = null;
         }
      }

      long now = System.currentTimeMillis();
      if (this.lastVictim != null) {
         if (now - this.lastVictimTime > 1500L) {
            this.lastVictim = null;
         } else if (this.lastVictim.isDeadOrDying() || this.lastVictim.getHealth() <= 0.0F) {
            this.killTime = now;
            this.lastVictim = null;
         }
      }

      if (player != null) {
         if (player.hurtTime > this.lastHurtTime) {
            this.comboCount = 0;
         }

         this.lastHurtTime = player.hurtTime;
      }

      if (now - this.comboTime > 3000L) {
         this.comboCount = 0;
      }
   }

   private void pollClicks() {
      long now = System.currentTimeMillis();
      if (this.mc.gui.screen() == null && this.mc.getWindow() != null) {
         long handle = this.mc.getWindow().handle();
         boolean l = com.lennart.nebula.compat.Keys.mouseDown(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
         boolean r = com.lennart.nebula.compat.Keys.mouseDown(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT);
         if (l && !this.leftDown) {
            this.leftClicks.addLast(now);
         }

         if (r && !this.rightDown) {
            this.rightClicks.addLast(now);
         }

         this.leftDown = l;
         this.rightDown = r;
      }

      while (!this.leftClicks.isEmpty() && now - this.leftClicks.peekFirst() > 1000L) {
         this.leftClicks.pollFirst();
      }

      while (!this.rightClicks.isEmpty() && now - this.rightClicks.peekFirst() > 1000L) {
         this.rightClicks.pollFirst();
      }
   }

   @Override
   public void onRenderHud(GuiGraphicsExtractor ctx, float tickDelta) {
      LocalPlayer player = this.mc.player;
      boolean editing = HudLayout.isEditing();
      if (this.cps.get()) {
         this.pollClicks();
      }

      if (player != null && !this.mc.gui.hud.isHidden() || editing) {
         if (this.cps.get() || editing) {
            this.drawCps(ctx, editing);
         }

         if (this.combo.get() && (this.comboCount >= 2 || editing)) {
            this.drawCombo(ctx, editing);
         }
      }

      if (player != null && (editing || this.mc.options.getCameraType().isFirstPerson() && (this.mc.gui.screen() == null || com.lennart.nebula.gui.IngamePreview.active()))) {
         int cx = ctx.guiWidth() / 2;
         int cy = ctx.guiHeight() / 2;
         HitResult target = this.mc.hitResult;
         Entity targeted = this.mc.crosshairPickEntity;
         long now = System.currentTimeMillis();
         float progress = player.getAttackStrengthScale(0.0F);
         if (progress >= 1.0F && this.lastCooldown < 1.0F) {
            this.readyTime = now;
         }

         this.lastCooldown = progress;
         boolean block = target != null && target.getType() == Type.BLOCK;
         boolean shieldUp = targeted instanceof LivingEntity blocker && blocker.isBlocking();
         this.drawCenter(
            ctx, cx, cy, this.unit(), targeted != null, block, progress, now - this.readyTime, now - this.hitTime, this.hitConfirmed, now - this.killTime, shieldUp
         );
         if (this.targetInfo.get() && targeted instanceof LivingEntity living) {
            this.drawTargetInfo(ctx, cx, cy, living);
         }

         if (this.range.get()) {
            this.drawRange(ctx, player, target, tickDelta, editing);
         }
      }
   }

   /** GUI-Pixel pro echtem Bildschirmpixel: alles Mittige wird in echten Pixeln gezeichnet (fein, unabhängig von der GUI-Größe). */
   private float unit() {
      return 1.0F / (float)Math.max(1.0, (double)this.mc.getWindow().getGuiScale());
   }

   /** Alles um die Bildschirmmitte: Ziel-Ecken, Hit-/Kill-Marker, Attack-Indikator, Schild-Symbol. */
   private void drawCenter(
      GuiGraphicsExtractor ctx, int cx, int cy, float unit, boolean entity, boolean block, float progress, long readyAge, long hitAge, boolean confirmed, long killAge, boolean shieldUp
   ) {
      ctx.pose().pushMatrix();
      ctx.pose().translate(cx, cy);
      ctx.pose().scale(unit, unit);
      int fs = this.frameSize.getInt();
      int fl = this.frameLength.getInt();
      int ft = this.frameThick.getInt();
      if (this.entityIndicator.get() && entity) {
         corners(ctx, fs, fl, ft, this.entityColor.get());
      } else if (this.blockIndicator.get() && block) {
         corners(ctx, fs, fl, ft, this.blockColor.get());
      }

      if (this.killMarker.get() && killAge >= 0L && killAge < 700L) {
         float fade = 1.0F - killAge / 700.0F;
         int size = this.killSize.getInt();
         drawX(ctx, size / 4, size, Math.max(2, this.hitThick.getInt() + 1), fadeColor(this.killColor.get(), fade));
      }

      long dur = Math.round(this.hitDuration.get());
      if (this.hitmarker.get() && hitAge >= 0L && hitAge < dur) {
         float k = (float)hitAge / dur;
         float pop = this.hitAnim.is("Pop") ? 1.0F + 0.4F * Math.max(0.0F, 1.0F - hitAge / 110.0F) : 1.0F;
         float spread = this.hitAnim.is("Ausdehnen") ? 7.0F * k : 0.0F;
         int base = confirmed ? this.confirmColor.get() : this.hitColor.get();
         this.drawHitmarker(ctx, fadeColor(base, 1.0F - k), pop, spread);
      }

      if (this.cooldown.get()) {
         this.drawCooldown(ctx, progress, readyAge);
      }

      if (this.shieldIndicator.get() && shieldUp) {
         float ss = this.shieldSize.getInt();
         ctx.pose().pushMatrix();
         ctx.pose().translate(fs + 3, -fs - 3 - ss);
         ctx.pose().scale(ss / 16.0F, ss / 16.0F);
         ctx.item(shield(), 0, 0);
         ctx.pose().popMatrix();
      }

      ctx.pose().popMatrix();
   }

   private void drawHitmarker(GuiGraphicsExtractor ctx, int color, float pop, float spread) {
      int size = Math.round(this.hitSize.getInt() * pop);
      int gap = Math.round(this.hitGap.getInt() * pop + spread);
      int th = this.hitThick.getInt();
      switch (this.hitStyle.get()) {
         case "Lang":
            drawX(ctx, gap, gap + size, th, color);
            break;
         case "Ecken":
            corners(ctx, gap + size / 2, Math.max(2, size / 2), th, color);
            break;
         case "Plus":
            plus(ctx, gap, gap + size / 2 + 2, th, color);
            break;
         default:
            drawX(ctx, gap, gap + size / 2 + 1, th, color);
      }
   }

   private void drawCooldown(GuiGraphicsExtractor ctx, float progress, long readyAge) {
      int color = this.cooldownColor.get();
      int bg = this.cooldownBg.get();
      int w = this.cooldownWidth.getInt();
      int th = this.cooldownThick.getInt();
      int off = this.cooldownOffset.getInt();
      String style = this.cooldownStyle.get();
      boolean round = style.equals("Bogen") || style.equals("Ring");
      if (progress < 1.0F) {
         switch (style) {
            case "Bogen":
               arc(ctx, w / 2, th, progress, color, 0);
               break;
            case "Ring":
               arc(ctx, w / 2, th, progress, color, bg);
               break;
            case "Punkte": {
               int n = 5;
               int filled = Math.round(n * progress);
               int dot = th + 1;
               for (int i = 0; i < n; i++) {
                  int x = -w / 2 + Math.round(i * (w - dot) / (float)(n - 1));
                  ctx.fill(x, off, x + dot, off + dot, i < filled ? color : bg);
               }
               break;
            }
            default:
               ctx.fill(-w / 2, off, w / 2, off + th, bg);
               ctx.fill(-w / 2, off, -w / 2 + Math.round(w * progress), off + th, color);
         }
      } else if (this.readyFlash.get() && readyAge >= 0L && readyAge < 250L) {
         int c = fadeColor(color, 1.0F - readyAge / 250.0F);
         if (round) {
            arc(ctx, w / 2, th, 1.0F, c, 0);
         } else {
            ctx.fill(-w / 2 - 1, off, w / 2 + 1, off + th, c);
         }
      }
   }
   private void drawTargetInfo(GuiGraphicsExtractor ctx, int cx, int cy, LivingEntity living) {
      Font tr = this.mc.font;
      String name = living.getDisplayName().getString();
      float hp = living.getHealth() + living.getAbsorptionAmount();
      String health = String.format(Locale.ROOT, "%.1f ❤", hp / 2.0F);
      float ratio = Math.max(0.0F, Math.min(1.0F, living.getHealth() / Math.max(1.0F, living.getMaxHealth())));
      int hpColor = ratio > 0.6F ? -11870592 : (ratio > 0.3F ? -278748 : -495247);
      int y = cy + 16;
      ctx.pose().pushMatrix();
      ctx.pose().translate(cx, y);
      float sc = (float)this.infoScale.get().doubleValue();
      ctx.pose().scale(sc, sc);
      int nw = tr.width(name);
      ctx.text(tr, name, -nw / 2, 0, -1, true);
      int hw = tr.width(health);
      ctx.text(tr, health, -hw / 2, 10, hpColor, true);
      ctx.pose().popMatrix();
      int bw = 30;
      ctx.fill(cx - bw / 2, y + 17, cx + bw / 2, y + 18, Integer.MIN_VALUE);
      ctx.fill(cx - bw / 2, y + 17, cx - bw / 2 + Math.round(bw * ratio), y + 18, hpColor);
   }

   private void drawRange(GuiGraphicsExtractor ctx, LocalPlayer player, HitResult target, float tickDelta, boolean editing) {
      String text = null;
      double dist = 0.0;
      if (target != null && target.getType() != Type.MISS && (target instanceof BlockHitResult || this.mc.crosshairPickEntity != null)) {
         Vec3 eye = player.getEyePosition(tickDelta);
         Vec3 pos = this.mc.crosshairPickEntity != null
            ? this.mc.crosshairPickEntity.position().add(0.0, this.mc.crosshairPickEntity.getBbHeight() / 2.0F, 0.0)
            : target.getLocation();
         dist = eye.distanceTo(pos);
         text = String.format(Locale.ROOT, "%.1fm", dist);
      } else if (editing) {
         text = "3.2m";
         dist = 3.2;
      }

      if (text != null) {
         int color = this.rangeWarn.get() ? (dist <= 3.0 ? -11870592 : -495247) : this.rangeColor.get();
         int w = this.mc.font.width(text) + 4;
         this.rangeBox.begin(ctx, w, 11);
         ctx.text(this.mc.font, text, 2, 2, this.rangeBox.fade(color), true);
         this.rangeBox.end(ctx);
      }
   }

   private void drawCps(GuiGraphicsExtractor ctx, boolean editing) {
      int l = this.leftClicks.size();
      int r = this.rightClicks.size();
      String text = (editing && l == 0 ? 12 : l) + " | " + (editing && r == 0 ? 4 : r) + " CPS";
      int w = this.mc.font.width(text) + 8;
      this.cpsBox.begin(ctx, w, 13);
      ctx.text(this.mc.font, text, 4, 3, this.cpsBox.fade(this.cpsColor.get()), true);
      this.cpsBox.end(ctx);
   }

   private void drawCombo(GuiGraphicsExtractor ctx, boolean editing) {
      int n = editing && this.comboCount < 2 ? 5 : this.comboCount;
      String text = n + " Combo";
      int w = this.mc.font.width(text) + 8;
      this.comboBox.begin(ctx, w, 13);
      ctx.text(this.mc.font, text, 4, 3, this.comboBox.fade(this.comboColor.get()), true);
      this.comboBox.end(ctx);
   }

   /** Vorschau im Menü: gleicher Code wie ingame, Treffer/Kill/Cooldown laufen in einer Schleife. */
   void drawPreviewOverlay(GuiGraphicsExtractor ctx, int cx, int cy, Crosshair.State state, long now) {
      long hitAge = now % 1400L;
      boolean confirmed = now / 1400L % 2L == 1L;
      long killAge = now % 4200L;
      float progress = state.target() ? 1.0F : (now % 1200L) / 1200.0F;
      this.drawCenter(ctx, cx, cy, this.unit(), state.target(), false, progress, now % 1200L, hitAge, confirmed, killAge, state.target());
   }
   /** Beim Ziehen an den Reglern: Menü ausblenden, das echte Crosshair ingame zeigen. */
   @Override
   public boolean hasIngamePreview() {
      return true;
   }

   @Override
   public ModulePreview preview() {
      Crosshair crosshair = NebulaClient.getInstance().getModuleManager().get(Crosshair.class);
      return crosshair == null ? null : new Crosshair.CrosshairPreview(crosshair, this);
   }

   private static int fadeColor(int argb, float fade) {
      int a = Math.round((argb >>> 24 & 0xFF) * Math.max(0.0F, Math.min(1.0F, fade)));
      return a << 24 | argb & 16777215;
   }

   /** Diagonales X von from bis to (Pixel um die Mitte), Strichstärke t. */
   private static void drawX(GuiGraphicsExtractor ctx, int from, int to, int t, int color) {
      for (int i = from; i <= to; i++) {
         ctx.fill(i, i, i + t, i + t, color);
         ctx.fill(-i - t + 1, i, -i + 1, i + t, color);
         ctx.fill(i, -i - t + 1, i + t, -i + 1, color);
         ctx.fill(-i - t + 1, -i - t + 1, -i + 1, -i + 1, color);
      }
   }

   /** Gerades Plus mit Lücke in der Mitte. */
   private static void plus(GuiGraphicsExtractor ctx, int from, int to, int t, int color) {
      int a = -t / 2;
      int b = a + t;
      ctx.fill(from, a, to, b, color);
      ctx.fill(-to, a, -from, b, color);
      ctx.fill(a, from, b, to, color);
      ctx.fill(a, -to, b, -from, color);
   }

   /** Vier Ecken im Abstand r von der Mitte, Strichlänge l, Stärke t. */
   private static void corners(GuiGraphicsExtractor ctx, int r, int l, int t, int color) {
      for (int sx = -1; sx <= 1; sx += 2) {
         for (int sy = -1; sy <= 1; sy += 2) {
            int x = sx < 0 ? -r : r - t;
            int y = sy < 0 ? -r : r - t;
            int hx = sx < 0 ? x : r - l;
            int vy = sy < 0 ? y : r - l;
            ctx.fill(hx, y, hx + l, y + t, color);
            ctx.fill(x, vy, x + t, vy + l, color);
         }
      }
   }

   /** Kreis um die Mitte mit Radius r; der Anteil progress in color, der Rest in bg (0 = weglassen). */
   private static void arc(GuiGraphicsExtractor ctx, int r, int t, float progress, int color, int bg) {
      int steps = Math.max(36, r * 7);
      int filled = Math.round(steps * progress);
      for (int i = 0; i < steps; i++) {
         int c = i < filled ? color : bg;
         if (c == 0) {
            continue;
         }

         double ang = -Math.PI / 2 + i * (Math.PI * 2 / steps);
         int x = (int)Math.round(Math.cos(ang) * r) - t / 2;
         int y = (int)Math.round(Math.sin(ang) * r) - t / 2;
         ctx.fill(x, y, x + t, y + t, c);
      }
   }}
