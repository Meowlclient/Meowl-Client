package com.lennart.nebula.module.render;

import com.lennart.nebula.gui.ModulePreview;
import com.lennart.nebula.module.Category;
import com.lennart.nebula.module.Module;
import com.lennart.nebula.setting.NumberSetting;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;

public class ColorSaturation extends Module {
   private static ColorSaturation instance;
   private final NumberSetting saturation = this.add(
      new NumberSetting("Sättigung", "0 % = schwarz-weiß, 100 % = normal, 200 % = doppelt so kräftig", 130.0, 0.0, 200.0, 5.0, "%")
   );

   public ColorSaturation() {
      super("Color Saturation", "Farbstärke des ganzen Minecraft-Bildes ändern – von schwarz-weiß bis knallig. Keybind zum schnellen An/Aus.", Category.RENDER);
      this.icon(Items.MAGENTA_DYE);
      instance = this;
   }

   private Identifier effectId() {
      int pct = (int)Math.round(this.saturation.get() / 5.0) * 5;
      pct = Math.max(0, Math.min(200, pct));
      return Identifier.fromNamespaceAndPath("nebula", String.format(Locale.ROOT, "saturation_%03d", pct));
   }

   public static void apply(Minecraft client, CrossFrameResourcePool pool) {
      ColorSaturation s = instance;
      if (s != null && s.isEnabled() && Math.abs(s.saturation.get() - 100.0) >= 2.5) {
         try {
            PostChain fx = client.getShaderManager().getPostChain(s.effectId(), LevelTargetBundle.MAIN_TARGETS);
            if (fx != null) {
               fx.process(client.getMainRenderTarget(), pool);
            }
         } catch (RuntimeException var4) {
         }
      }
   }

   private static int saturate(int rgb, float s) {
      float r = (rgb >> 16 & 0xFF) / 255.0F;
      float g = (rgb >> 8 & 0xFF) / 255.0F;
      float b = (rgb & 0xFF) / 255.0F;
      float l = 0.2126F * r + 0.7152F * g + 0.0722F * b;
      r = Math.max(0.0F, Math.min(1.0F, l + (r - l) * s));
      g = Math.max(0.0F, Math.min(1.0F, l + (g - l) * s));
      b = Math.max(0.0F, Math.min(1.0F, l + (b - l) * s));
      return 0xFF000000 | Math.round(r * 255.0F) << 16 | Math.round(g * 255.0F) << 8 | Math.round(b * 255.0F);
   }

   @Override
   public ModulePreview preview() {
      return (ctx, x, y, w, h, mouseX, mouseY, delta) -> {
         int pw = w - 16;
         int ph = Math.min(h - 40, Math.round(pw * 0.62F));
         int px = x + 8;
         int py = y + 20;
         float s = (float)(this.saturation.get() / 100.0);
         ctx.drawString(Minecraft.getInstance().font, "Vorher  |  Nachher", px, y + 6, -4670261, false);
         int[] sky = new int[]{7907327, 9419007, 11915519};
         int cell = Math.max(2, pw / 32);
         int cols = pw / cell;
         int rows = ph / cell;

         for (int cy = 0; cy < rows; cy++) {
            for (int cx = 0; cx < cols; cx++) {
               int hill = rows / 2 + (int)Math.round(Math.sin(cx * 0.35) * 2.0 + Math.sin(cx * 0.9) * 1.0);
               int color;
               if (cy < hill) {
                  color = sky[Math.min(2, cy * 3 / Math.max(1, hill))];
                  if (cx >= cols - 6 && cx < cols - 3 && cy >= 1 && cy < 4) {
                     color = 16769899;
                  }
               } else if (cy == hill) {
                  color = cx % 7 == 3 ? 15221306 : (cx % 9 == 5 ? 15913274 : 6139960);
               } else if (cy < hill + 3) {
                  color = 9067062;
               } else {
                  color = (cx * 7 + cy * 3) % 11 == 0 ? 4182232 : 8158332;
               }

               boolean after = cx >= cols / 2;
               int c = after ? saturate(color, s) : 0xFF000000 | color;
               ctx.fill(px + cx * cell, py + cy * cell, px + (cx + 1) * cell, py + (cy + 1) * cell, c);
            }
         }

         ctx.fill(px + cols / 2 * cell, py, px + cols / 2 * cell + 1, py + rows * cell, -1);
         ctx.drawString(
            Minecraft.getInstance().font,
            Math.round(this.saturation.get()) + " %" + (this.isEnabled() ? "" : "  (Modul aus)"),
            px,
            py + rows * cell + 6,
            -1644304,
            false
         );
      };
   }
}
