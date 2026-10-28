package com.lennart.nebula.cosmetic.mods;

import com.lennart.nebula.cosmetic.Cosmetic;
import com.lennart.nebula.cosmetic.CosmeticContext;
import com.lennart.nebula.cosmetic.CosmeticValue;
import com.lennart.nebula.cosmetic.Shapes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.Map;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Items;

/** Status-Symbol über dem Kopf (Pixel-Art aus Blöcken), dreht sich langsam und schwebt. */
public class MoodIcon extends Cosmetic {
   public static final String[] STYLES = {
      "Herz", "Schlafen", "Frage", "Ausrufezeichen", "Musik", "Stern", "Feuer", "Krone", "Totenkopf", "Sonne", "Blitz", "Smiley", "Wütend", "Diamant", "Pfote", "Sanduhr"
   };
   private static final Map<Character, Integer> PALETTE = Map.ofEntries(
      Map.entry('r', 0xFFE8304A),
      Map.entry('R', 0xFFFF7A8A),
      Map.entry('w', 0xFFF8F8F8),
      Map.entry('k', 0xFF1A1A1A),
      Map.entry('y', 0xFFF8D030),
      Map.entry('Y', 0xFFFFF0A0),
      Map.entry('o', 0xFFFF8A20),
      Map.entry('b', 0xFF4A8AF0),
      Map.entry('B', 0xFFA0D8FF),
      Map.entry('g', 0xFF808890),
      Map.entry('p', 0xFFB070F0),
      Map.entry('c', 0xFF40E8E0),
      Map.entry('n', 0xFF6A4020)
   );

   public MoodIcon() {
      super("Status-Symbol", "Ein Symbol schwebt über deinem Kopf: Herz, Zzz, Fragezeichen, Musik, Stern, Feuer, Krone, Totenkopf, Sanduhr und mehr.", "mood", Items.NAME_TAG, STYLES);
      this.groups();
   }

   private static String[] art(String st) {
      return switch (st) {
         case "Schlafen" -> new String[]{"   wwww", "     w ", "    w  ", "www www", "  w    ", " w     ", "www    "};
         case "Frage" -> new String[]{" yyyyy ", "yy   yy", "    yy ", "   yy  ", "   yy  ", "       ", "   yy  "};
         case "Ausrufezeichen" -> new String[]{"  rrr  ", "  rrr  ", "  rrr  ", "  rrr  ", "   r   ", "       ", "  rrr  "};
         case "Musik" -> new String[]{"   pppp", "   p  p", "   p  p", "   p  p", " ppp pp", "pppp pp", " pp    "};
         case "Stern" -> new String[]{"   y   ", "  yyy  ", "yyyYyyy", " yyYyy ", "  yyy  ", " yy yy ", "yy   yy"};
         case "Feuer" -> new String[]{"   o   ", "  oo   ", "  ooo o", " ooyoo ", "ooyYyoo", "ooyYyoo", " ooooo "};
         case "Krone" -> new String[]{"y  y  y", "yy yyy y".substring(0, 7), "yyyyyyy", "yrybyry", "yyyyyyy", "       ", "       "};
         case "Totenkopf" -> new String[]{" wwwww ", "wwwwwww", "wkkwkkw", "wkkwkkw", "www www", " wwwww ", " w w w "};
         case "Sonne" -> new String[]{"y  y  y", " yyyyy ", " yYYYy ", "yyYYYyy", " yYYYy ", " yyyyy ", "y  y  y"};
         case "Blitz" -> new String[]{"   yyy ", "  yyy  ", " yyy   ", "yyyyyy ", "   yyy ", "  yyy  ", " yy    "};
         case "Smiley" -> new String[]{" yyyyy ", "yyyyyyy", "yykykyy", "yyyyyyy", "ykyyyky", "yykkkyy", " yyyyy "};
         case "Wütend" -> new String[]{" rrrrr ", "rkrrrkr", "rrkrkrr", "rrrrrrr", "rrkkkrr", "rkrrrkr", " rrrrr "};
         case "Diamant" -> new String[]{" cBBBc ", "cBBccBc", "ccccccc", " ccccc ", "  ccc  ", "   c   ", "       "};
         case "Pfote" -> new String[]{" p   p ", "pp   pp", "   p   ", "  ppp  ", " ppppp ", " ppppp ", "  ppp  "};
         case "Sanduhr" -> new String[]{"nnnnnnn", " yyyyy ", "  yyy  ", "   y   ", "  w y  ", " wyyyw ", "nnnnnnn"};
         default -> new String[]{" rr rr ", "rRrrrrr", "rRrrrrr", "rrrrrrr", " rrrrr ", "  rrr  ", "   r   "};
      };
   }

   private static int glowColor(String st) {
      return switch (st) {
         case "Schlafen", "Totenkopf", "Sanduhr" -> 0xFFE8F0FF;
         case "Frage", "Stern", "Krone", "Sonne", "Blitz", "Smiley" -> 0xFFFFE060;
         case "Musik", "Pfote" -> 0xFFC090FF;
         case "Feuer" -> 0xFFFF8020;
         case "Diamant" -> 0xFF60F0F0;
         default -> 0xFFFF4060;
      };
   }

   @Override
   public void render(CosmeticContext c, CosmeticValue v) {
      float t = c.time;
      float b = c.bright;
      String st = v.style();
      String[] art = art(st);
      boolean sneak = c.player.isCrouching();
      c.aboveHead(() -> {
      c.solid((m, vc) -> {
         this.place(m, t, v, false);
         this.pixels(m, vc, art, v, b);
      });
      c.glow((m, vc) -> {
         this.place(m, t, v, false);
         float pulse = 0.6F + 0.4F * Mth.sin(t * 2.5F);
         Shapes.glow(m, vc, -4.5F, -4.5F, -1.2F, 4.5F, 4.5F, 1.2F, v.tint(glowColor(st)), 0.12F * pulse);
      });
      });
   }

   private void place(PoseStack m, float t, CosmeticValue v, boolean sneak) {
      m.translate(0.0F, -20.5F + (sneak ? 3.0F : 0.0F) + Mth.sin(t * 1.7F) * 0.8F, 0.0F);
      m.mulPose(Axis.YP.rotationDegrees(Mth.sin(t * 0.8F) * 35.0F));
      float s = v.size() * 0.75F;
      m.scale(s, s, s);
   }

   private void pixels(PoseStack m, VertexConsumer vc, String[] art, CosmeticValue v, float b) {
      float bb = Math.max(b, 0.85F);

      for (int row = 0; row < art.length; row++) {
         String line = art[row];

         for (int col = 0; col < line.length() && col < 7; col++) {
            Integer c = PALETTE.get(line.charAt(col));
            if (c != null) {
               float x = col - 3.5F;
               float y = row - 3.5F;
               Shapes.box(m, vc, x, y, -0.5F, x + 1.0F, y + 1.0F, 0.5F, v.tint(c), bb);
            }
         }
      }
   }
}
