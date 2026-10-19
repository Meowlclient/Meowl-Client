package com.lennart.nebula.module.hud;

import com.lennart.nebula.gui.Render2D;
import com.lennart.nebula.module.Category;
import com.lennart.nebula.module.Module;
import com.lennart.nebula.setting.BooleanSetting;
import com.lennart.nebula.setting.ColorSetting;
import com.lennart.nebula.setting.ModeSetting;
import com.lennart.nebula.setting.NumberSetting;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class Saturation extends Module {
   private static final Identifier FOOD_FULL = Identifier.withDefaultNamespace("hud/food_full");
   private static final Identifier FOOD_HALF = Identifier.withDefaultNamespace("hud/food_half");
   private final BooleanSetting saturationOverlay = this.add(new BooleanSetting("Sättigung", "Sättigung als goldener Umriss um die Keulen", true));
   private final ModeSetting satStyle = this.add(
      new ModeSetting("Stil", "Umriss = Kontur der Keule golden, Rahmen = eckiger Kasten", "Umriss", "Umriss", "Rahmen")
   );
   private final ColorSetting saturationColor = this.add(new ColorSetting("Sättigungs-Farbe", "Farbe der Sättigung", -14003));
   private final NumberSetting glow = this.add(new NumberSetting("Schein", "Leichter Schein außen um die Keule", 30.0, 0.0, 100.0, 5.0, "%"));
   private final BooleanSetting foodPreview = this.add(
      new BooleanSetting("Essens-Vorschau", "Zeigt blinkend, wie viel das Essen in der Hand wiederherstellt", true)
   );
   private final BooleanSetting numbers = this.add(new BooleanSetting("Werte als Text", "Hunger und Sättigung als Zahlen über der Leiste", true));
   private static final String[] RING = new String[]{
      "..##.....", ".#..#....", "#....#...", "#.....#..", ".#....#..", "..#...#..", "...###.##", "......#.#", "......##."
   };
   private static final String[] SHAPE = new String[]{
      "..##.....", ".####....", "######...", "#######..", ".######..", "..#####..", "...######", "......###", "......##."
   };

   public Saturation() {
      super("Saturation", "AppleSkin-artig: Sättigung, Essens-Vorschau und Hunger-Werte an der Hungerleiste.", Category.HUD);
      this.icon(Items.COOKED_BEEF);
      this.saturationColor.setVisibility(this.saturationOverlay::get);
      this.satStyle.setVisibility(this.saturationOverlay::get);
      this.glow.setVisibility(() -> this.saturationOverlay.get() && this.satStyle.is("Umriss"));
   }

   public void renderFoodOverlay(GuiGraphics ctx) {
      LocalPlayer player = this.mc.player;
      if (this.isEnabled()
         && player != null
         && this.mc.gameMode != null
         && this.mc.gameMode.getPlayerMode().isSurvival()
         && !(player.getVehicle() instanceof LivingEntity)) {
         FoodData hunger = player.getFoodData();
         int food = hunger.getFoodLevel();
         float sat = hunger.getSaturationLevel();
         int right = ctx.guiWidth() / 2 + 91;
         int top = ctx.guiHeight() - 39;
         FoodProperties held = heldFood(player);
         int gainFood = 0;
         float gainSat = 0.0F;
         if (held != null && this.foodPreview.get()) {
            int newFood = Math.min(20, food + held.nutrition());
            gainFood = newFood - food;
            gainSat = Math.min((float)newFood, sat + held.saturation()) - sat;
         }

         float blink = (float)(0.55 + 0.45 * Math.sin(System.currentTimeMillis() / 160.0));
         if (gainFood > 0) {
            int alpha = Math.round(blink * 255.0F);
            int color = alpha << 24 | 16777215;

            for (int j = 0; j < 10; j++) {
               int iconPoints = j * 2;
               int x = right - j * 8 - 9;
               if (iconPoints + 2 > food && iconPoints < food + gainFood) {
                  boolean full = iconPoints + 2 <= food + gainFood;
                  ctx.blitSprite(RenderPipelines.GUI_TEXTURED, full ? FOOD_FULL : FOOD_HALF, x, top, 9, 9, color);
               }
            }
         }

         if (this.saturationOverlay.get()) {
            this.drawSaturation(ctx, right, top, 0.0F, sat, this.saturationColor.get());
            if (gainSat > 0.0F) {
               int a = Math.round(blink * 200.0F);
               this.drawSaturation(ctx, right, top, sat, gainSat, a << 24 | this.saturationColor.get() & 16777215);
            }
         }

         if (this.numbers.get()) {
            String text = String.format(Locale.ROOT, "%d · %.1f", food, sat);
            if (gainFood > 0 || gainSat > 0.0F) {
               text = text + String.format(Locale.ROOT, "  (+%d · +%.1f)", gainFood, gainSat);
            }

            int bubbleRow = player.getAirSupply() >= player.getMaxAirSupply() && !player.isUnderWater() ? 0 : 10;
            int w = this.mc.font.width(text) + 6;
            int y = top - 11 - bubbleRow;
            Render2D.roundRect(ctx, right - w, y, w, 10, 3, -1877863392);
            Render2D.roundRect(ctx, right - w, y + 9, w, 1, 0, -1879048192 | this.saturationColor.get() & 16777215);
            ctx.drawString(this.mc.font, text, right - w + 3, y + 1, -1578509, false);
         }
      }
   }

   private static boolean in(String[] mask, int x, int y) {
      return x >= 0 && y >= 0 && x < 9 && y < 9 && mask[y].charAt(x) == '#';
   }

   private void drawSaturation(GuiGraphics ctx, int right, int top, float from, float amount, int color) {
      boolean outline = !this.satStyle.is("Rahmen");
      int glowColor = (color >>> 24) * this.glow.getInt() / 100 << 24 | color & 16777215;

      for (int j = 0; j < 10; j++) {
         float lo = Math.max(from, j * 2.0F);
         float hi = Math.min(from + amount, j * 2.0F + 2.0F);
         if (!(hi <= lo)) {
            int x = right - j * 8 - 9;
            float part = (hi - lo) / 2.0F;
            if (!outline) {
               int w = Math.max(1, Math.round(9.0F * part));
               int x2 = x + 9;
               int x1 = x2 - w;
               ctx.fill(x1, top, x2, top + 1, color);
               ctx.fill(x1, top + 8, x2, top + 9, color);
               ctx.fill(x2 - 1, top, x2, top + 9, color);
               if (part >= 0.99F) {
                  ctx.fill(x, top, x + 1, top + 9, color);
               }
            } else {
               int cut = Math.round(9.0F * (1.0F - part));

               for (int py = 0; py < 9; py++) {
                  for (int px = Math.max(0, cut - 1); px < 10; px++) {
                     if (px >= cut && in(RING, px, py)) {
                        ctx.fill(x + px, top + py, x + px + 1, top + py + 1, color);
                     }
                  }
               }

               if (glowColor >>> 24 > 0) {
                  for (int py = -1; py < 10; py++) {
                     for (int pxx = Math.max(-1, cut); pxx < 10; pxx++) {
                        if (!in(SHAPE, pxx, py) && (in(RING, pxx - 1, py) || in(RING, pxx + 1, py) || in(RING, pxx, py - 1) || in(RING, pxx, py + 1))) {
                           ctx.fill(x + pxx, top + py, x + pxx + 1, top + py + 1, glowColor);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static FoodProperties heldFood(LocalPlayer player) {
      for (ItemStack stack : new ItemStack[]{player.getMainHandItem(), player.getOffhandItem()}) {
         FoodProperties food = stack.get(DataComponents.FOOD);
         if (food != null && (player.getFoodData().needsFood() || food.canAlwaysEat())) {
            return food;
         }
      }

      return null;
   }
}
