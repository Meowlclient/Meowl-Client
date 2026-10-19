package com.lennart.nebula.nametag;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.FormattedCharSequence;

/**
 * Gemeinsames Zeichnen einer Namenszeile mit eigenem Stil (Farbe, Hintergrund, Schriftrand, Schatten) –
 * genutzt für den eigenen Nametag und für andere Spieler.
 */
public final class NametagStyle {
   private NametagStyle() {
   }

   public record Look(int textColor, boolean background, int backgroundColor, boolean outline, int outlineColor, boolean shadow, boolean fullBackground) {
   }

   /** Erwartet bereits ausgerichtete Matrizen (Blick zur Kamera, Skalierung 0.025). */
   public static void draw(
      Font tr,
      PoseStack matrices,
      SubmitNodeCollector queue,
      int light,
      boolean sneaking,
      FormattedCharSequence name,
      NametagLayout.Row row,
      float y,
      boolean center,
      NametagStyle.Look look
   ) {
      LabelCanvas canvas = new LabelCanvas(queue, matrices, !sneaking, light, look.background() ? look.backgroundColor() : 0);
      int color = sneaking ? fade(look.textColor(), 0.5F) : look.textColor();
      if (name != null && (look.outline() || look.shadow())) {
         NametagLayout.drawRow(tr, canvas, name, 0, NametagLayout.NameMode.RESERVE_WITH_BACKGROUND, row, y, center, look.fullBackground());
         float x = nameX(tr, row, tr.width(name), center);
         if (!sneaking) {
            // wie Vanilla durch Blöcke sichtbar (halb durchsichtig)
            queue.submitText(matrices, x, y, name, false, DisplayMode.SEE_THROUGH, light, fade(color, 0.5F), 0, 0);
         }

         queue.submitText(
            matrices,
            x,
            y,
            name,
            !look.outline() && look.shadow(),
            DisplayMode.NORMAL,
            LightTexture.lightCoordsWithEmission(light, 2),
            color,
            0,
            look.outline() ? look.outlineColor() | 0xFF000000 : 0
         );
      } else {
         NametagLayout.drawRow(tr, canvas, name, color, NametagLayout.NameMode.NORMAL, row, y, center, look.fullBackground());
      }
   }

   static int fade(int argb, float f) {
      int a = Math.round((argb >>> 24 & 0xFF) * f);
      return a << 24 | argb & 16777215;
   }

   public static float nameX(Font tr, NametagLayout.Row row, float nameW, boolean center) {
      float leftW = NametagLayout.width(tr, row.left());
      float rightW = NametagLayout.width(tr, row.right());
      float leftBlock = leftW > 0.0F ? leftW + row.gap() : 0.0F;
      float rightBlock = rightW > 0.0F ? rightW + row.gap() : 0.0F;
      float total = leftBlock + nameW + rightBlock;
      float start = center ? -total / 2.0F : -nameW / 2.0F - leftBlock;
      return start + leftBlock;
   }
}
