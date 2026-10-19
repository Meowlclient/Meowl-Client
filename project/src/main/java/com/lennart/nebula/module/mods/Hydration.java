package com.lennart.nebula.module.mods;

import com.lennart.nebula.gui.Render2D;
import com.lennart.nebula.module.Category;
import com.lennart.nebula.module.Module;
import com.lennart.nebula.module.Section;
import com.lennart.nebula.module.hud.HudBox;
import com.lennart.nebula.module.hud.HudLayout;
import com.lennart.nebula.setting.ActionSetting;
import com.lennart.nebula.setting.BooleanSetting;
import com.lennart.nebula.setting.ColorSetting;
import com.lennart.nebula.setting.ModeSetting;
import com.lennart.nebula.setting.NumberSetting;
import com.lennart.nebula.setting.Setting;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Items;

/**
 * Stay Hydrated: erinnert in einstellbaren Abständen ans Wassertrinken – mit animierter Wasser-Karte (fallender
 * Tropfen, Wellen, Glas, das sich füllt) und einem leisen Plätschern.
 */
public class Hydration extends Module {
   private final NumberSetting interval = this.add(new NumberSetting("Abstand", "Wie oft erinnert wird", 30.0, 5.0, 120.0, 5.0, "min"));
   private final NumberSetting showFor = this.add(new NumberSetting("Anzeigedauer", "Wie lange die Erinnerung sichtbar bleibt", 8.0, 3.0, 30.0, 1.0, "s"));
   private final ModeSetting text = this.add(
      new ModeSetting("Text", "Was auf der Karte steht", "STAY HYDRATED", "STAY HYDRATED", "Trink was!", "Wasserpause")
   );
   private final BooleanSetting sound = this.add(new BooleanSetting("Sound", "Leises Plätschern bei der Erinnerung", true));
   private final BooleanSetting countdown = this.add(new BooleanSetting("Countdown", "Kleine Anzeige, wie lange es bis zur nächsten Erinnerung dauert", false));
   private final ColorSetting color = this.add(new ColorSetting("Farbe", "Farbe des Wassers", 0xFF3FA9FF, false));
   private final BooleanSetting pauseInMenus = this.add(new BooleanSetting("In Menüs warten", "Nicht erinnern, solange ein Menü offen ist", false));
   private final HudBox box = this.hudBox("Stay Hydrated", 50.0, 14.0).reflow();
   private long nextAt;
   private long shownAt;
   private int drinks;

   public Hydration() {
      super("Stay Hydrated", "Erinnert dich regelmäßig daran, Wasser zu trinken – mit animierter Wasser-Karte.", Category.MISC);
      this.section(Section.MEOWL);
      this.icon(Items.POTION);
      this.add(new ActionSetting("Jetzt testen", "Zeigt die Erinnerung sofort", "Zeigen", this::remind));
      this.box.background.set(true);
      this.group("Erinnerung", new Setting[]{this.interval, this.showFor, this.text, this.sound, this.countdown, this.pauseInMenus});
      this.group("Karte", new Setting[]{this.color, this.box.scale, this.box.opacity, this.box.background, this.box.backgroundColor, this.box.rounding});
   }

   @Override
   protected void onEnable() {
      this.schedule();
   }

   private void schedule() {
      this.nextAt = System.currentTimeMillis() + (long)(this.interval.get() * 60000.0);
   }

   private void remind() {
      this.shownAt = System.currentTimeMillis();
      this.drinks++;
      this.schedule();
      if (this.sound.get()) {
         this.mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BUCKET_FILL, 1.25F, 0.55F));
         this.mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, 1.0F, 0.35F));
      }
   }

   @Override
   public void onTick() {
      if (this.nextAt == 0L) {
         this.schedule();
      }

      long now = System.currentTimeMillis();
      if (now >= this.nextAt && !(this.pauseInMenus.get() && this.mc.gui.screen() != null)) {
         this.remind();
      }
   }

   private float visibility() {
      float t = (System.currentTimeMillis() - this.shownAt) / 1000.0F;
      float dur = (float)this.showFor.get().doubleValue();
      if (this.shownAt == 0L || t > dur) {
         return 0.0F;
      } else {
         return Math.min(Math.min(1.0F, t / 0.35F), Math.min(1.0F, (dur - t) / 0.6F));
      }
   }

   @Override
   public void onRenderHud(GuiGraphicsExtractor ctx, float tickDelta) {
      boolean editing = HudLayout.isEditing();
      float vis = editing ? 1.0F : this.visibility();
      if (vis <= 0.0F) {
         if (this.countdown.get() && !this.mc.gui.hud.isHidden()) {
            this.drawCountdown(ctx);
         }

         return;
      }

      Font font = this.mc.font;
      float t = editing ? (System.currentTimeMillis() % 100000L) / 1000.0F : (System.currentTimeMillis() - this.shownAt) / 1000.0F;
      int water = this.color.get() & 16777215;
      String title = this.text.get();
      Component big = Component.literal(title).withStyle(ChatFormatting.BOLD);
      int titleW = Math.round(font.width(big) * 1.6F);
      String sub = this.drinks > 1 ? "Zeit für ein Glas Wasser · " + this.drinks + ". Erinnerung" : "Zeit für ein Glas Wasser!";
      // Breite nach dem längsten Text (auch übersetzt), damit nichts aus der Karte ragt
      int subW = font.width(com.lennart.nebula.gui.Lang.tr(sub));
      int w = this.box.layoutWidth(Math.max(150, 42 + Math.max(titleW, subW) + 10));
      int h = 46;
      // Einblenden: von oben hineingleiten
      float slide = (1.0F - vis) * -12.0F;
      ctx.pose().pushMatrix();
      ctx.pose().translate(0.0F, slide);
      this.box.begin(ctx, w, h);
      int a = Math.round(vis * 255.0F) << 24;
      // Wasser-Wellen am unteren Rand
      for (int x = 0; x < w; x += 2) {
         float wave = Mth.sin(x * 0.09F + t * 3.2F) * 2.0F + Mth.sin(x * 0.05F - t * 2.1F) * 1.5F;
         int top = Math.round(h - 9 + wave);
         ctx.fill(x, top, Math.min(w, x + 2), h - 1, this.box.fade(Math.round(vis * 110.0F) << 24 | water));
      }

      // Glas, das sich füllt
      int gx = 10;
      int gy = 8;
      int gw = 22;
      int gh = 30;
      float fill = Mth.clamp(t / 1.6F, 0.0F, 1.0F);
      fill = 1.0F - (1.0F - fill) * (1.0F - fill);
      int level = Math.round((gh - 4) * fill);
      int surf = gy + gh - 2 - level;
      for (int x = gx + 2; x < gx + gw - 2; x++) {
         int wy = surf + Math.round(Mth.sin(x * 0.6F + t * 6.0F) * (level > 2 ? 1.0F : 0.0F));
         ctx.fill(x, Math.max(gy + 2, wy), x + 1, gy + gh - 2, this.box.fade(a | water));
      }

      int glass = this.box.fade(Math.round(vis * 200.0F) << 24 | 0xE8F4FF);
      ctx.fill(gx, gy, gx + 2, gy + gh, glass);
      ctx.fill(gx + gw - 2, gy, gx + gw, gy + gh, glass);
      ctx.fill(gx, gy + gh - 2, gx + gw, gy + gh, glass);
      ctx.fill(gx + 4, gy + 4, gx + 5, gy + gh - 6, this.box.fade(Math.round(vis * 120.0F) << 24 | 0xFFFFFF));
      // fallender Tropfen über dem Glas
      float dropT = (t * 0.9F) % 1.0F;
      int dy = Math.round(-6.0F + dropT * (surf - gy + 4));
      if (dropT < 0.92F) {
         int dx = gx + gw / 2;
         Render2D.circle(ctx, dx, gy + dy + 2, 2, this.box.fade(a | water));
         ctx.fill(dx - 1, gy + dy - 1, dx + 1, gy + dy + 1, this.box.fade(a | water));
      }

      // Bläschen im Glas
      for (int i = 0; i < 3; i++) {
         float bt = (t * 0.7F + i * 0.33F) % 1.0F;
         int by = Math.round(gy + gh - 4 - bt * level);
         if (by > surf) {
            ctx.fill(gx + 6 + i * 5, by, gx + 8 + i * 5, by + 2, this.box.fade(Math.round(vis * 160.0F) << 24 | 0xFFFFFF));
         }
      }

      // Titel mit leichtem Wellen-Schimmer
      float shimmer = 0.75F + 0.25F * Mth.sin(t * 4.0F);
      int titleColor = mix(0xFFFFFF, water, 0.35F * shimmer);
      ctx.pose().pushMatrix();
      ctx.pose().translate(42.0F, 9.0F);
      ctx.pose().scale(1.6F, 1.6F);
      ctx.text(font, big, 0, 0, this.box.fade(a | titleColor), true);
      ctx.pose().popMatrix();
      ctx.text(font, sub, 42, 26, this.box.fade(Math.round(vis * 200.0F) << 24 | 0xD8E8F8), false);
      this.box.end(ctx);
      ctx.pose().popMatrix();
   }

   private void drawCountdown(GuiGraphicsExtractor ctx) {
      long left = Math.max(0L, this.nextAt - System.currentTimeMillis()) / 1000L;
      String s = String.format("Wasser in %d:%02d", left / 60L, left % 60L);
      Font font = this.mc.font;
      int w = font.width(s) + 8;
      this.box.begin(ctx, w, 12);
      ctx.text(font, s, 4, 2, this.box.fade(0xFF000000 | this.color.get()), true);
      this.box.end(ctx);
   }

   private static int mix(int a, int b, float t) {
      int r = Math.round((a >> 16 & 255) * (1.0F - t) + (b >> 16 & 255) * t);
      int g = Math.round((a >> 8 & 255) * (1.0F - t) + (b >> 8 & 255) * t);
      int bl = Math.round((a & 255) * (1.0F - t) + (b & 255) * t);
      return r << 16 | g << 8 | bl;
   }
}
