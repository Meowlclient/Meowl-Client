package com.lennart.nebula.cosmetic.mods;

import com.lennart.nebula.cosmetic.Cosmetic;
import com.lennart.nebula.cosmetic.CosmeticContext;
import com.lennart.nebula.cosmetic.CosmeticValue;
import com.lennart.nebula.cosmetic.Shapes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Items;

/** Schweife: wedeln beim Stehen sanft, beim Laufen lebhafter. */
public class Tails extends Cosmetic {
   public static final String[] STYLES = {"Katze", "Fuchs", "Neunschwanz", "Wolf", "Drache", "Teufel", "Hase", "Roboter"};

   public Tails() {
      super("Schweife", "Ein Schweif, der mitwedelt: Katze, Fuchs, Neunschwanz, Wolf, Drache, Teufel, Hase oder Roboter.", "tail", Items.RABBIT_FOOT, STYLES);
      this.groups();
   }

   /** Weicher Bewegungszustand je Spieler: Phase wird aufsummiert, Laufen geglättet – kein Ruckeln bei Tempowechsel. */
   private static final class Motion {
      long last;
      float phase;
      float walk;
   }

   private static final java.util.Map<Integer, Tails.Motion> MOTION = new java.util.concurrent.ConcurrentHashMap<>();

   private static Tails.Motion motion(int id, float walkTarget) {
      Tails.Motion s = MOTION.computeIfAbsent(id, k -> new Tails.Motion());
      long now = System.nanoTime();
      float dt = s.last == 0L ? 0.0F : Math.min(0.1F, (now - s.last) / 1.0E9F);
      if (dt > 0.001F || s.last == 0L) {
         s.last = now;
         s.walk += (walkTarget - s.walk) * (1.0F - (float)Math.exp(-dt * 4.0F));
         s.phase = (s.phase + dt * (2.2F + s.walk * 5.0F)) % 6283.0F;
      }

      if (MOTION.size() > 64) {
         MOTION.entrySet().removeIf(e -> now - e.getValue().last > 10_000_000_000L);
      }

      return s;
   }

   @Override
   public void render(CosmeticContext c, CosmeticValue v) {
      Tails.Motion mo = motion(c.player.getId(), c.walk);
      // t ist hier die geglättete Wellenphase
      float t = mo.phase;
      float b = c.bright;
      float walk = mo.walk;
      String st = v.style();
      c.onBody(() -> {
         c.solid((m, vc) -> {
            m.translate(0.0F, 10.5F, 1.8F);
            m.scale(v.size(), v.size(), v.size());
            this.draw(m, vc, st, v, t, walk, b, false);
         });
         if (st.equals("Teufel") || st.equals("Roboter") || st.equals("Neunschwanz")) {
            c.glow((m, vc) -> {
               m.translate(0.0F, 10.5F, 1.8F);
               m.scale(v.size(), v.size(), v.size());
               this.draw(m, vc, st, v, t, walk, b, true);
            });
         }
      });
   }

   private void draw(PoseStack m, VertexConsumer vc, String st, CosmeticValue v, float t, float walk, float b, boolean glow) {
      switch (st) {
         case "Hase":
            if (!glow) {
               Shapes.blob(m, vc, 0.0F, 0.0F, 1.2F, 1.7F, 1.6F + Mth.sin(t * 2.5F) * 0.08F, 1.5F, v.tint(0xFFF4F0EC), b);
            }
            break;
         case "Neunschwanz":
            for (int i = 0; i < 9; i++) {
               m.pushPose();
               m.rotate(Axis.YP.rotationDegrees(-64.0F + i * 16.0F));
               m.rotate(Axis.XP.rotationDegrees(-10.0F + Math.abs(i - 4) * 4.0F));
               this.chain(m, vc, "Fuchs", v, t + i * 0.7F, walk, b, glow, 0.8F);
               m.popPose();
            }
            break;
         default:
            this.chain(m, vc, st, v, t, walk, b, glow, 1.0F);
      }
   }

   /** Gliederkette: jedes Glied dreht sich etwas weiter, die Welle läuft zur Spitze. */
   private void chain(PoseStack m, VertexConsumer vc, String st, CosmeticValue v, float t, float walk, float b, boolean glow, float scale) {
      int segs;
      float len;
      float start;
      float curl;
      int col1;
      int col2;
      switch (st) {
         case "Fuchs":
            segs = 7;
            len = 1.9F;
            start = -35.0F;
            curl = 9.0F;
            col1 = v.tint(0xFFE07020);
            col2 = 0xFFF8F4EC;
            break;
         case "Wolf":
            segs = 7;
            len = 1.8F;
            start = -60.0F;
            curl = 4.0F;
            col1 = v.tint(0xFF707478);
            col2 = v.tint(0xFF9AA0A4);
            break;
         case "Drache":
            segs = 11;
            len = 1.7F;
            start = -55.0F;
            curl = 2.0F;
            col1 = v.tint(0xFF1E6A3A);
            col2 = v.tint(0xFF3A9A50);
            break;
         case "Teufel":
            segs = 10;
            len = 1.4F;
            start = -50.0F;
            curl = 11.0F;
            col1 = v.tint(0xFFA01818);
            col2 = v.tint(0xFFD02828);
            break;
         case "Roboter":
            segs = 8;
            len = 1.6F;
            start = -40.0F;
            curl = 8.0F;
            col1 = v.tint(0xFF707884);
            col2 = v.tint(0xFFA0A8B4);
            break;
         default:
            segs = 9;
            len = 1.5F;
            start = -40.0F;
            curl = 12.0F;
            col1 = v.tint(0xFF2A2A30);
            col2 = v.tint(0xFF3A3A44);
      }

      float amp = 10.0F + walk * 22.0F;
      m.pushPose();
      m.scale(scale, scale, scale);
      m.rotate(Axis.XP.rotationDegrees(start));

      for (int k = 0; k < segs; k++) {
         float q = (float)k / segs;
         float w;
         switch (st) {
            case "Fuchs":
               w = 1.2F + Mth.sin(q * (float)Math.PI) * 1.6F;
               break;
            case "Wolf":
               w = 1.1F + Mth.sin(q * (float)Math.PI) * 1.2F;
               break;
            case "Drache":
               w = 1.8F * (1.0F - q * 0.75F);
               break;
            case "Teufel":
               w = 0.55F;
               break;
            default:
               w = 0.9F * (1.0F - q * 0.3F);
         }

         m.rotate(Axis.YP.rotationDegrees(Mth.sin(t - k * 0.55F) * amp / segs * 2.0F));
         m.rotate(Axis.XP.rotationDegrees(k == 0 ? 0.0F : curl));
         int c = st.equals("Fuchs") && k >= segs - 2 ? col2 : Shapes.mix(col1, col2, q);
         if (!glow) {
            // Gelenk schließen: kleine Rundung am Anfang jedes Glieds verdeckt die Knickstelle
            if (k > 0) {
               Shapes.box(m, vc, -w * 0.52F, -w * 0.52F, -w * 0.3F, w * 0.52F, w * 0.52F, w * 0.3F, c, b);
            }

            Shapes.box(m, vc, -w / 2.0F, -w / 2.0F, 0.0F, w / 2.0F, w / 2.0F, len + 0.1F, c, b);
            if (st.equals("Drache")) {
               Shapes.box(m, vc, -0.2F, -w / 2.0F - 0.7F * (1.0F - q), len * 0.3F, 0.2F, -w / 2.0F, len * 0.7F, 0xFF0F2A18, b);
            } else if (st.equals("Roboter") && k % 2 == 0) {
               Shapes.box(m, vc, -w / 2.0F - 0.15F, -w / 2.0F - 0.15F, 0.0F, w / 2.0F + 0.15F, w / 2.0F + 0.15F, 0.4F, 0xFF404048, b);
            }
         } else if (st.equals("Roboter")) {
            Shapes.glowCube(m, vc, 0.0F, 0.0F, 0.2F, w * 0.4F, v.tint(0xFF40E0FF), 0.6F + 0.3F * Mth.sin(t * 5.0F - k));
         }

         m.translate(0.0F, 0.0F, len);
      }

      if (st.equals("Teufel")) {
         if (!glow) {
            for (int i = 0; i < 3; i++) {
               float r = 1.3F * (1.0F - i * 0.33F);
               Shapes.box(m, vc, -r, -0.3F, i * 0.8F, r, 0.3F, i * 0.8F + 0.8F, v.tint(0xFFC02020), b);
            }
         } else {
            Shapes.glow(m, vc, -1.3F, -0.4F, 0.0F, 1.3F, 0.4F, 2.4F, v.tint(0xFFFF3010), 0.35F);
         }
      }

      m.popPose();
   }

   @Override
   protected boolean hasLayerOption() {
      return true;
   }

   @Override
   protected boolean previewFromBehind() {
      return true;
   }
}
