package com.lennart.nebula.cosmetic.mods;

import com.lennart.nebula.cosmetic.Art;
import com.lennart.nebula.cosmetic.Cosmetic;
import com.lennart.nebula.cosmetic.CosmeticContext;
import com.lennart.nebula.cosmetic.CosmeticValue;
import com.lennart.nebula.cosmetic.Shapes;
import com.lennart.nebula.setting.ModeSetting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.Random;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

/**
 * Kleiner Begleiter auf der Schulter: abgerundete Körper, atmet, blinzelt, schaut sich um und hat je Tier eigene
 * Bewegungen (Schwanz, Flügel, Kiemen …). Ursprung = Schulter oben, oben = -y, vorne = -z.
 */
public class ShoulderBuddy extends Cosmetic {
   public static final String[] STYLES = {"Katze", "Eule", "Mini-Drache", "Schleim", "Geist", "Pinguin", "Fuchs", "Axolotl", "Biene", "Sakura-Hirsch"};
   private static final int BLACK = 0xFF141414;
   private static final int WHITE = 0xFFF4F2EE;
   private final ModeSetting side = this.add(new ModeSetting("Schulter", "Auf welcher Schulter", "Links", "Links", "Rechts"));

   public ShoulderBuddy() {
      super(
         "Schulterbegleiter",
         "Ein kleiner Begleiter auf deiner Schulter: Katze, Eule, Mini-Drache, Schleim, Geist, Pinguin, Fuchs, Axolotl, Biene oder Sakura-Hirsch.",
         "shoulder",
         Items.PARROT_SPAWN_EGG,
         STYLES
      );
      this.groups(this.side);
   }

   @Override
   protected String extra() {
      return this.side.get().equals("Rechts") ? "r" : "l";
   }

   @Override
   public void render(CosmeticContext c, CosmeticValue v) {
      float t = c.time;
      float b = c.bright;
      float sx = v.extra().equals("r") ? -6.0F : 6.0F;
      String st = v.style();
      c.onBody(() -> {
         c.solid((m, vc) -> {
            m.translate(sx, 0.0F, 0.0F);
            m.scale(v.size() * 1.2F, v.size() * 1.2F, v.size() * 1.2F);
            this.creature(m, vc, st, v, t, b, false);
         });
         c.glow((m, vc) -> {
            m.translate(sx, 0.0F, 0.0F);
            m.scale(v.size() * 1.2F, v.size() * 1.2F, v.size() * 1.2F);
            this.creature(m, vc, st, v, t, b, true);
         });
      });
   }

   private static boolean blink(float t) {
      return t % 3.7F < 0.14F;
   }

   /** Auge auf der Vorderseite (z = Vorderfläche): Weiß/Farbe, Pupille, Glanzpunkt. */
   private static void eye(PoseStack m, VertexConsumer vc, float x, float y, float z, float r, int iris, float t, float b) {
      if (blink(t)) {
         Shapes.box(m, vc, x - r, y - 0.1F, z - 0.06F, x + r, y + 0.1F, z, BLACK, b);
      } else {
         Shapes.box(m, vc, x - r, y - r, z - 0.05F, x + r, y + r, z, iris, Math.max(b, 0.75F));
         Shapes.box(m, vc, x - r * 0.45F, y - r * 0.7F, z - 0.1F, x + r * 0.45F, y + r * 0.8F, z - 0.05F, BLACK, b);
         Shapes.box(m, vc, x - r * 0.75F, y - r * 0.75F, z - 0.14F, x - r * 0.35F, y - r * 0.35F, z - 0.1F, 0xFFFFFFFF, 1.0F);
      }
   }

   private void creature(PoseStack m, VertexConsumer vc, String st, CosmeticValue v, float t, float b, boolean glow) {
      float look = Mth.sin(t * 0.45F) * 30.0F + Mth.sin(t * 1.3F) * 6.0F;
      float breathe = 1.0F + Mth.sin(t * 2.2F) * 0.03F;
      m.pushPose();
      m.scale(1.0F, breathe, 1.0F);
      switch (st) {
         case "Eule" -> this.owl(m, vc, v, t, b, glow, look);
         case "Mini-Drache" -> this.dragon(m, vc, v, t, b, glow, look);
         case "Schleim" -> this.slime(m, vc, v, t, b, glow);
         case "Geist" -> this.ghost(m, vc, v, t, b, glow);
         case "Pinguin" -> this.penguin(m, vc, v, t, b, glow, look);
         case "Fuchs" -> this.fox(m, vc, v, t, b, glow, look);
         case "Axolotl" -> this.axolotl(m, vc, v, t, b, glow, look);
         case "Biene" -> this.bee(m, vc, v, t, b, glow, look);
         case "Sakura-Hirsch" -> this.stag(m, vc, v, t, b, glow, look);
         default -> this.cat(m, vc, v, t, b, glow, look);
      }

      m.popPose();
   }

   // ------------------------------------------------------------------ Tiere

   private void cat(PoseStack m, VertexConsumer vc, CosmeticValue v, float t, float b, boolean glow, float look) {
      if (glow) {
         return;
      }

      int fur = v.tint(0xFFE8963C);
      int dark = v.tint(0xFFB8641E);
      int light = 0xFFFFF0DC;
      // Körper sitzend, Brust hell
      Shapes.blob(m, vc, 0.0F, -2.1F, 0.5F, 1.7F, 2.1F, 1.9F, fur, b);
      Shapes.blob(m, vc, 0.0F, -2.0F, -0.9F, 1.0F, 1.5F, 0.7F, light, b);
      // Pfoten
      Shapes.blob(m, vc, -0.7F, -0.35F, -1.3F, 0.5F, 0.35F, 0.6F, light, b);
      Shapes.blob(m, vc, 0.7F, -0.35F, -1.3F, 0.5F, 0.35F, 0.6F, light, b);
      // Schwanz: schlängelt sich nach oben
      m.pushPose();
      m.translate(0.0F, -0.6F, 2.2F);

      for (int i = 0; i < 6; i++) {
         float w = Mth.sin(t * 2.2F - i * 0.6F) * 14.0F;
         m.rotate(Axis.XP.rotationDegrees(-18.0F));
         m.rotate(Axis.YP.rotationDegrees(w));
         Shapes.box(m, vc, -0.4F, -0.4F, 0.0F, 0.4F, 0.4F, 1.0F, i % 2 == 0 ? fur : dark, b);
         m.translate(0.0F, 0.0F, 0.95F);
      }

      m.popPose();
      // Kopf
      m.pushPose();
      m.translate(0.0F, -4.3F, -0.2F);
      m.rotate(Axis.YP.rotationDegrees(look));
      m.rotate(Axis.ZP.rotationDegrees(Mth.sin(t * 0.7F) * 6.0F));
      Shapes.blob(m, vc, 0.0F, -1.3F, 0.0F, 2.0F, 1.5F, 1.6F, fur, b);
      // Streifen
      Shapes.box(m, vc, -0.25F, -2.85F, -0.8F, 0.25F, -2.2F, 0.8F, dark, b);
      Shapes.box(m, vc, -1.1F, -2.7F, -0.4F, -0.7F, -2.2F, 0.6F, dark, b);
      Shapes.box(m, vc, 0.7F, -2.7F, -0.4F, 1.1F, -2.2F, 0.6F, dark, b);
      // Ohren mit rosa Innenseite
      for (int s = -1; s <= 1; s += 2) {
         float x = s * 1.25F;
         Shapes.box(m, vc, x - 0.7F, -3.2F, -0.4F, x + 0.7F, -2.6F, 0.5F, fur, b);
         Shapes.box(m, vc, x - 0.45F, -3.7F, -0.3F, x + 0.45F, -3.2F, 0.4F, fur, b);
         Shapes.box(m, vc, x - 0.2F, -4.0F, -0.2F, x + 0.2F, -3.7F, 0.3F, fur, b);
         Shapes.box(m, vc, x - 0.3F, -3.4F, -0.46F, x + 0.3F, -2.7F, -0.4F, 0xFFF0A0B0, b);
      }

      // Schnauze
      Shapes.blob(m, vc, 0.0F, -0.75F, -1.45F, 0.85F, 0.5F, 0.35F, light, b);
      Shapes.box(m, vc, -0.25F, -1.15F, -1.85F, 0.25F, -0.85F, -1.75F, 0xFFF07090, b);
      eye(m, vc, -0.8F, -1.6F, -1.5F, 0.42F, 0xFF7ED957, t, b);
      eye(m, vc, 0.8F, -1.6F, -1.5F, 0.42F, 0xFF7ED957, t, b);
      // Schnurrhaare
      for (int s = -1; s <= 1; s += 2) {
         Shapes.box(m, vc, s * 0.9F, -0.95F, -1.75F, s * 2.4F, -0.88F, -1.7F, 0xFFE8E8E8, b);
         Shapes.box(m, vc, s * 0.9F, -0.7F, -1.75F, s * 2.3F, -0.63F, -1.7F, 0xFFE8E8E8, b);
      }

      m.popPose();
   }

   private void owl(PoseStack m, VertexConsumer vc, CosmeticValue v, float t, float b, boolean glow, float look) {
      if (glow) {
         return;
      }

      int feather = v.tint(0xFF8A6440);
      int dark = v.tint(0xFF5A3E26);
      int belly = 0xFFEADBBE;
      Shapes.blob(m, vc, 0.0F, -2.4F, 0.1F, 1.9F, 2.4F, 1.8F, feather, b);
      Shapes.blob(m, vc, 0.0F, -2.2F, -1.1F, 1.25F, 1.8F, 0.75F, belly, b);
      // Federmuster auf dem Bauch
      for (int i = 0; i < 3; i++) {
         for (int k = -1; k <= 1; k += 2) {
            Shapes.box(m, vc, k * 0.5F - 0.15F, -3.2F + i * 0.9F, -1.88F, k * 0.5F + 0.15F, -2.95F + i * 0.9F, -1.8F, dark, b);
         }
      }

      // angelegte Flügel – zucken ab und zu
      float flick = Math.max(0.0F, Mth.sin(t * 0.9F) - 0.92F) * 120.0F;

      for (int s = -1; s <= 1; s += 2) {
         m.pushPose();
         m.translate(s * 1.75F, -3.6F, 0.3F);
         m.rotate(Axis.ZP.rotationDegrees(-s * (6.0F + flick)));
         Shapes.blob(m, vc, s * 0.25F, 1.5F, 0.0F, 0.55F, 1.7F, 1.35F, dark, b);
         m.popPose();
      }

      // Füße
      Shapes.box(m, vc, -1.0F, -0.3F, -1.3F, -0.3F, 0.0F, -0.4F, 0xFFE0A030, b);
      Shapes.box(m, vc, 0.3F, -0.3F, -1.3F, 1.0F, 0.0F, -0.4F, 0xFFE0A030, b);
      // Kopf dreht sich weit (Eulen!)
      m.pushPose();
      m.translate(0.0F, -4.6F, 0.0F);
      m.rotate(Axis.YP.rotationDegrees(look * 2.2F));
      Shapes.blob(m, vc, 0.0F, -1.4F, 0.0F, 2.1F, 1.6F, 1.8F, feather, b);
      // Gesichtsscheibe
      Shapes.blob(m, vc, -0.85F, -1.45F, -1.55F, 0.85F, 0.85F, 0.3F, belly, b);
      Shapes.blob(m, vc, 0.85F, -1.45F, -1.55F, 0.85F, 0.85F, 0.3F, belly, b);
      eye(m, vc, -0.85F, -1.5F, -1.88F, 0.55F, 0xFFFFB020, t, b);
      eye(m, vc, 0.85F, -1.5F, -1.88F, 0.55F, 0xFFFFB020, t, b);
      // Schnabel
      Shapes.box(m, vc, -0.25F, -1.0F, -2.0F, 0.25F, -0.3F, -1.75F, 0xFF4A3A2A, b);
      // Federohren
      for (int s = -1; s <= 1; s += 2) {
         Shapes.box(m, vc, s * 1.2F - 0.35F, -3.4F, -0.3F, s * 1.2F + 0.35F, -2.8F, 0.4F, dark, b);
         Shapes.box(m, vc, s * 1.45F - 0.2F, -3.9F, -0.2F, s * 1.45F + 0.2F, -3.4F, 0.3F, dark, b);
      }

      m.popPose();
   }

   private void dragon(PoseStack m, VertexConsumer vc, CosmeticValue v, float t, float b, boolean glow, float look) {
      int scale = v.tint(0xFF7A3AC8);
      int belly = v.tint(0xFFE8B860);
      int horn = 0xFFF0E8D0;
      float flap = Mth.sin(t * 6.0F);
      if (glow) {
         // Augen und gelegentliche Glut aus der Nase
         m.pushPose();
         m.translate(0.0F, -4.4F, -0.6F);
         m.rotate(Axis.YP.rotationDegrees(look));
         Shapes.glow(m, vc, -1.0F, -1.9F, -1.95F, -0.45F, -1.4F, -1.85F, 0xFFFFD040, 0.8F);
         Shapes.glow(m, vc, 0.45F, -1.9F, -1.95F, 1.0F, -1.4F, -1.85F, 0xFFFFD040, 0.8F);
         float puff = Math.max(0.0F, Mth.sin(t * 0.8F) - 0.85F) * 6.0F;
         if (puff > 0.0F) {
            Shapes.glowBall(m, vc, 0.0F, -0.9F, -3.6F - puff * 1.5F, 0.6F + puff * 0.5F, 0xFFFF7A20, 0.5F);
         }

         m.popPose();
         return;
      }

      Shapes.blob(m, vc, 0.0F, -2.0F, 0.4F, 1.6F, 2.0F, 2.0F, scale, b);
      Shapes.blob(m, vc, 0.0F, -1.9F, -1.0F, 0.95F, 1.5F, 0.55F, belly, b);
      // Bauchschuppen
      for (int i = 0; i < 3; i++) {
         Shapes.box(m, vc, -0.8F, -2.8F + i * 0.8F, -1.58F, 0.8F, -2.7F + i * 0.8F, -1.5F, Shapes.mix(belly, 0xFF000000, 0.25F), b);
      }

      // Flügel (Fledermausart) schlagen
      for (int s = -1; s <= 1; s += 2) {
         m.pushPose();
         m.translate(s * 1.2F, -3.4F, 1.2F);
         m.rotate(Axis.YP.rotationDegrees(s * (30.0F + flap * 25.0F)));
         m.rotate(Axis.ZP.rotationDegrees(-s * 20.0F));
         Shapes.box(m, vc, s > 0 ? 0.0F : -3.4F, -0.3F, -0.15F, s > 0 ? 3.4F : 0.0F, 0.2F, 0.15F, Shapes.mix(scale, 0xFF000000, 0.3F), b);

         for (int k = 0; k < 3; k++) {
            float x0 = s * (0.6F + k * 1.0F);
            Shapes.box(m, vc, Math.min(x0, x0 + s * 0.9F), 0.2F, -0.1F, Math.max(x0, x0 + s * 0.9F), 1.6F + k * 0.4F, 0.1F, Shapes.mix(scale, 0xFFFFFFFF, 0.15F), b);
         }

         m.popPose();
      }

      // Schwanz mit Spitze
      m.pushPose();
      m.translate(0.0F, -0.5F, 2.2F);

      for (int i = 0; i < 5; i++) {
         m.rotate(Axis.YP.rotationDegrees(Mth.sin(t * 1.8F - i * 0.7F) * 16.0F));
         m.rotate(Axis.XP.rotationDegrees(8.0F));
         float r = 0.55F - i * 0.07F;
         Shapes.box(m, vc, -r, -r, 0.0F, r, r, 1.0F, scale, b);
         m.translate(0.0F, 0.0F, 0.9F);
      }

      Shapes.box(m, vc, -0.6F, -0.15F, 0.0F, 0.6F, 0.15F, 0.9F, horn, b);
      m.popPose();
      // Kopf mit Schnauze und Hörnern
      m.pushPose();
      m.translate(0.0F, -4.4F, -0.6F);
      m.rotate(Axis.YP.rotationDegrees(look));
      Shapes.blob(m, vc, 0.0F, -1.3F, 0.0F, 1.7F, 1.4F, 1.6F, scale, b);
      Shapes.blob(m, vc, 0.0F, -0.8F, -1.9F, 1.05F, 0.65F, 0.9F, scale, b);
      Shapes.box(m, vc, -0.55F, -1.2F, -2.85F, -0.25F, -0.95F, -2.75F, BLACK, b);
      Shapes.box(m, vc, 0.25F, -1.2F, -2.85F, 0.55F, -0.95F, -2.75F, BLACK, b);
      eye(m, vc, -0.72F, -1.65F, -1.5F, 0.32F, 0xFFFFD040, t, b);
      eye(m, vc, 0.72F, -1.65F, -1.5F, 0.32F, 0xFFFFD040, t, b);

      for (int s = -1; s <= 1; s += 2) {
         m.pushPose();
         m.translate(s * 0.9F, -2.4F, 0.4F);
         m.rotate(Axis.XP.rotationDegrees(-35.0F));
         Shapes.box(m, vc, -0.25F, -1.6F, -0.25F, 0.25F, 0.0F, 0.25F, horn, b);
         m.popPose();
      }

      // Rückenzacken
      for (int i = 0; i < 3; i++) {
         Shapes.box(m, vc, -0.15F, -2.9F + i * 0.2F, 0.2F + i * 0.6F, 0.15F, -2.5F + i * 0.2F, 0.6F + i * 0.6F, belly, b);
      }

      m.popPose();
   }

   private void slime(PoseStack m, VertexConsumer vc, CosmeticValue v, float t, float b, boolean glow) {
      float hop = Math.abs(Mth.sin(t * 2.6F));
      float squash = 1.0F + (1.0F - hop) * 0.18F;
      m.translate(0.0F, -hop * 1.4F, 0.0F);
      m.scale(squash, 1.0F / squash, squash);
      if (glow) {
         // durchscheinende Hülle
         Shapes.glow(m, vc, -2.2F, -4.4F, -2.2F, 2.2F, 0.0F, 2.2F, v.tint(0xFF6CE07A), 0.28F);
         Shapes.glow(m, vc, -2.4F, -4.6F, -2.4F, 2.4F, 0.2F, 2.4F, v.tint(0xFF6CE07A), 0.1F);
         return;
      }

      int core = v.tint(0xFF3EA84E);
      Shapes.box(m, vc, -1.3F, -3.1F, -1.3F, 1.3F, -0.6F, 1.3F, core, b);
      // Gesicht auf der Hülle
      float z = -2.25F;
      Shapes.box(m, vc, -1.5F, -3.4F, z, -0.6F, -2.5F, z + 0.1F, 0xFF1E5A28, b);
      Shapes.box(m, vc, 0.6F, -3.4F, z, 1.5F, -2.5F, z + 0.1F, 0xFF1E5A28, b);
      if (!blink(t)) {
         Shapes.box(m, vc, -1.3F, -3.2F, z - 0.05F, -0.9F, -2.8F, z, 0xFFFFFFFF, 1.0F);
         Shapes.box(m, vc, 0.9F, -3.2F, z - 0.05F, 1.3F, -2.8F, z, 0xFFFFFFFF, 1.0F);
      }

      Shapes.box(m, vc, -0.4F, -1.8F, z, 0.4F, -1.4F, z + 0.1F, 0xFF1E5A28, b);
   }

   private void ghost(PoseStack m, VertexConsumer vc, CosmeticValue v, float t, float b, boolean glow) {
      float bob = Mth.sin(t * 1.8F) * 0.7F;
      m.translate(0.0F, -1.2F + bob, 0.0F);
      int col = v.tint(0xFFE8F0FF);
      if (glow) {
         Shapes.glowBlob(m, vc, 0.0F, -3.0F, 0.0F, 2.0F, 2.2F, 1.9F, col, 0.55F);
         // Zipfel unten wehen
         for (int i = -2; i <= 2; i++) {
            float w = Mth.sin(t * 4.0F + i * 1.3F) * 0.5F;
            Shapes.glow(m, vc, i * 0.8F - 0.4F, -1.2F, -1.2F + w, i * 0.8F + 0.4F, 0.4F + Math.abs(i) * -0.2F, 1.2F + w, col, 0.45F);
         }

         Shapes.glowBlob(m, vc, 0.0F, -2.8F, 0.0F, 2.6F, 2.8F, 2.4F, col, 0.12F);
         return;
      }

      // Augen und Mund (dunkel, damit sie im Leuchten sichtbar sind)
      float z = -1.95F;
      if (blink(t)) {
         Shapes.box(m, vc, -1.1F, -3.4F, z, -0.4F, -3.25F, z + 0.1F, 0xFF20203A, b);
         Shapes.box(m, vc, 0.4F, -3.4F, z, 1.1F, -3.25F, z + 0.1F, 0xFF20203A, b);
      } else {
         Shapes.box(m, vc, -1.1F, -3.9F, z, -0.4F, -2.9F, z + 0.1F, 0xFF20203A, b);
         Shapes.box(m, vc, 0.4F, -3.9F, z, 1.1F, -2.9F, z + 0.1F, 0xFF20203A, b);
      }

      float o = 0.25F + 0.2F * Mth.sin(t * 1.1F);
      Shapes.box(m, vc, -o, -2.4F, z, o, -1.8F, z + 0.1F, 0xFF20203A, b);
      Shapes.box(m, vc, -1.6F, -2.6F, z, -1.2F, -2.4F, z + 0.1F, 0xFFFFA0C0, b);
      Shapes.box(m, vc, 1.2F, -2.6F, z, 1.6F, -2.4F, z + 0.1F, 0xFFFFA0C0, b);
   }

   private void penguin(PoseStack m, VertexConsumer vc, CosmeticValue v, float t, float b, boolean glow, float look) {
      if (glow) {
         return;
      }

      int black = v.tint(0xFF20242E);
      float waddle = Mth.sin(t * 3.0F) * 4.0F;
      m.rotate(Axis.ZP.rotationDegrees(waddle));
      Shapes.blob(m, vc, 0.0F, -2.6F, 0.2F, 1.9F, 2.6F, 1.8F, black, b);
      Shapes.blob(m, vc, 0.0F, -2.3F, -0.9F, 1.35F, 2.0F, 1.0F, WHITE, b);
      // Flossen
      float flap = Math.max(0.0F, Mth.sin(t * 1.4F)) * 25.0F;

      for (int s = -1; s <= 1; s += 2) {
         m.pushPose();
         m.translate(s * 1.85F, -3.7F, 0.1F);
         m.rotate(Axis.ZP.rotationDegrees(-s * (8.0F + flap)));
         Shapes.blob(m, vc, s * 0.2F, 1.4F, 0.0F, 0.35F, 1.5F, 0.9F, black, b);
         m.popPose();
      }

      // Füße
      Shapes.box(m, vc, -1.2F, -0.3F, -1.4F, -0.2F, 0.0F, -0.2F, 0xFFF0901E, b);
      Shapes.box(m, vc, 0.2F, -0.3F, -1.4F, 1.2F, 0.0F, -0.2F, 0xFFF0901E, b);
      // Kopf
      m.pushPose();
      m.translate(0.0F, -5.0F, 0.0F);
      m.rotate(Axis.YP.rotationDegrees(look));
      Shapes.blob(m, vc, 0.0F, -1.2F, 0.0F, 1.7F, 1.4F, 1.6F, black, b);
      Shapes.blob(m, vc, -0.65F, -1.25F, -1.2F, 0.6F, 0.65F, 0.45F, WHITE, b);
      Shapes.blob(m, vc, 0.65F, -1.25F, -1.2F, 0.6F, 0.65F, 0.45F, WHITE, b);
      eye(m, vc, -0.65F, -1.35F, -1.66F, 0.28F, 0xFF2A2A2A, t, b);
      eye(m, vc, 0.65F, -1.35F, -1.66F, 0.28F, 0xFF2A2A2A, t, b);
      Shapes.box(m, vc, -0.45F, -0.85F, -2.3F, 0.45F, -0.45F, -1.5F, 0xFFF0901E, b);
      Shapes.box(m, vc, -1.5F, -0.75F, -1.3F, -1.1F, -0.5F, -1.2F, 0xFFFFA0B0, b);
      Shapes.box(m, vc, 1.1F, -0.75F, -1.3F, 1.5F, -0.5F, -1.2F, 0xFFFFA0B0, b);
      m.popPose();
   }

   private void fox(PoseStack m, VertexConsumer vc, CosmeticValue v, float t, float b, boolean glow, float look) {
      if (glow) {
         return;
      }

      int fur = v.tint(0xFFE86A20);
      int dark = 0xFF2A1E18;
      Shapes.blob(m, vc, 0.0F, -2.1F, 0.6F, 1.6F, 2.1F, 2.0F, fur, b);
      Shapes.blob(m, vc, 0.0F, -2.3F, -0.8F, 1.0F, 1.5F, 0.7F, WHITE, b);
      // Vorderbeine mit dunklen Pfoten
      for (int s = -1; s <= 1; s += 2) {
         Shapes.box(m, vc, s * 0.7F - 0.35F, -1.6F, -1.2F, s * 0.7F + 0.35F, -0.4F, -0.5F, fur, b);
         Shapes.box(m, vc, s * 0.7F - 0.38F, -0.45F, -1.3F, s * 0.7F + 0.38F, 0.0F, -0.45F, dark, b);
      }

      // buschiger Schwanz mit weißer Spitze, um die Füße gelegt
      m.pushPose();
      m.translate(0.0F, -0.8F, 2.2F);
      float sw = Mth.sin(t * 1.5F) * 12.0F;

      for (int i = 0; i < 5; i++) {
         m.rotate(Axis.YP.rotationDegrees(-30.0F + sw * 0.4F));
         float r = 0.75F + (i < 3 ? i * 0.15F : (4 - i) * 0.2F);
         Shapes.blob(m, vc, 0.0F, 0.0F, 0.5F, r, r * 0.85F, 0.7F, i == 4 ? WHITE : fur, b);
         m.translate(0.0F, 0.0F, 1.0F);
      }

      m.popPose();
      // Kopf mit spitzer Schnauze und großen Ohren
      m.pushPose();
      m.translate(0.0F, -4.2F, -0.2F);
      m.rotate(Axis.YP.rotationDegrees(look));
      m.rotate(Axis.ZP.rotationDegrees(Mth.sin(t * 0.6F) * 8.0F));
      Shapes.blob(m, vc, 0.0F, -1.3F, 0.0F, 1.9F, 1.4F, 1.6F, fur, b);
      Shapes.blob(m, vc, -0.9F, -0.75F, -0.9F, 0.75F, 0.55F, 0.7F, WHITE, b);
      Shapes.blob(m, vc, 0.9F, -0.75F, -0.9F, 0.75F, 0.55F, 0.7F, WHITE, b);
      Shapes.blob(m, vc, 0.0F, -0.85F, -1.9F, 0.6F, 0.45F, 0.75F, WHITE, b);
      Shapes.box(m, vc, -0.25F, -1.15F, -2.7F, 0.25F, -0.85F, -2.55F, dark, b);
      eye(m, vc, -0.75F, -1.65F, -1.5F, 0.3F, 0xFFFFC040, t, b);
      eye(m, vc, 0.75F, -1.65F, -1.5F, 0.3F, 0xFFFFC040, t, b);

      for (int s = -1; s <= 1; s += 2) {
         float x = s * 1.15F;
         float twitch = Math.max(0.0F, Mth.sin(t * 1.7F + s) - 0.9F) * 60.0F;
         m.pushPose();
         m.translate(x, -2.5F, 0.0F);
         m.rotate(Axis.ZP.rotationDegrees(s * twitch));
         Shapes.box(m, vc, -0.75F, -0.6F, -0.35F, 0.75F, 0.0F, 0.45F, fur, b);
         Shapes.box(m, vc, -0.5F, -1.2F, -0.3F, 0.5F, -0.6F, 0.4F, fur, b);
         Shapes.box(m, vc, -0.25F, -1.7F, -0.25F, 0.25F, -1.2F, 0.35F, dark, b);
         Shapes.box(m, vc, -0.35F, -1.0F, -0.4F, 0.35F, -0.2F, -0.35F, WHITE, b);
         m.popPose();
      }

      m.popPose();
   }

   private void axolotl(PoseStack m, VertexConsumer vc, CosmeticValue v, float t, float b, boolean glow, float look) {
      int pink = v.tint(0xFFF4A6C8);
      int gill = v.tint(0xFFE0507E);
      if (glow) {
         return;
      }

      // liegt längs auf der Schulter, Kopf nach vorne
      Shapes.blob(m, vc, 0.0F, -0.9F, 0.6F, 1.3F, 0.9F, 2.4F, pink, b);
      Shapes.blob(m, vc, 0.0F, -0.6F, 0.4F, 0.9F, 0.4F, 1.8F, Shapes.mix(pink, 0xFFFFFFFF, 0.35F), b);
      // Beinchen
      for (int s = -1; s <= 1; s += 2) {
         for (int k = 0; k < 2; k++) {
            Shapes.box(m, vc, s * 1.1F - 0.25F, -0.4F, -0.6F + k * 2.2F, s * 1.6F, 0.0F, -0.1F + k * 2.2F, pink, b);
         }
      }

      // Schwanz mit Flosse schlängelt
      m.pushPose();
      m.translate(0.0F, -0.9F, 2.9F);

      for (int i = 0; i < 4; i++) {
         m.rotate(Axis.YP.rotationDegrees(Mth.sin(t * 2.4F - i * 0.8F) * 18.0F));
         Shapes.box(m, vc, -0.35F, -0.35F, 0.0F, 0.35F, 0.35F, 1.0F, pink, b);
         Shapes.box(m, vc, -0.08F, -1.0F, 0.0F, 0.08F, -0.35F, 1.0F, Shapes.mix(pink, 0xFFFFFFFF, 0.4F), b);
         m.translate(0.0F, 0.0F, 0.95F);
      }

      m.popPose();
      // Kopf breit mit Kiemen
      m.pushPose();
      m.translate(0.0F, -1.0F, -1.6F);
      m.rotate(Axis.YP.rotationDegrees(look * 0.6F));
      Shapes.blob(m, vc, 0.0F, -0.4F, -0.6F, 1.9F, 1.1F, 1.3F, pink, b);
      eye(m, vc, -1.15F, -0.75F, -1.85F, 0.28F, BLACK, t, b);
      eye(m, vc, 1.15F, -0.75F, -1.85F, 0.28F, BLACK, t, b);
      // Lächeln
      Shapes.box(m, vc, -0.7F, -0.05F, -1.92F, 0.7F, 0.1F, -1.85F, 0xFF9A3060, b);
      Shapes.box(m, vc, -0.9F, -0.2F, -1.92F, -0.7F, 0.05F, -1.85F, 0xFF9A3060, b);
      Shapes.box(m, vc, 0.7F, -0.2F, -1.92F, 0.9F, 0.05F, -1.85F, 0xFF9A3060, b);

      for (int s = -1; s <= 1; s += 2) {
         for (int g = 0; g < 3; g++) {
            m.pushPose();
            m.translate(s * 1.75F, -1.2F + g * 0.55F, -0.4F);
            m.rotate(Axis.ZP.rotationDegrees(s * (-40.0F + g * 40.0F + Mth.sin(t * 3.0F + g) * 10.0F)));
            Shapes.box(m, vc, s > 0 ? 0.0F : -1.4F, -0.18F, -0.18F, s > 0 ? 1.4F : 0.0F, 0.18F, 0.18F, gill, b);

            for (int f = 0; f < 3; f++) {
               float fx = s * (0.35F + f * 0.4F);
               Shapes.box(m, vc, fx - 0.08F, -0.5F, -0.1F, fx + 0.08F, -0.18F, 0.1F, gill, b);
            }

            m.popPose();
         }
      }

      m.popPose();
   }

   private void bee(PoseStack m, VertexConsumer vc, CosmeticValue v, float t, float b, boolean glow, float look) {
      float hover = Mth.sin(t * 2.4F) * 0.6F;
      m.translate(0.0F, -2.6F + hover, 0.0F);
      m.rotate(Axis.YP.rotationDegrees(look * 0.7F));
      if (glow) {
         // schnell flatternde, durchsichtige Flügel
         float f = Mth.sin(t * 60.0F) * 30.0F;

         for (int s = -1; s <= 1; s += 2) {
            m.pushPose();
            m.translate(s * 0.6F, -1.6F, 0.3F);
            m.rotate(Axis.ZP.rotationDegrees(s * (20.0F + f)));
            Shapes.glow(m, vc, s > 0 ? 0.0F : -2.6F, -0.1F, -0.8F, s > 0 ? 2.6F : 0.0F, 0.05F, 1.2F, 0xFFE8F4FF, 0.45F);
            m.popPose();
         }

         return;
      }

      int yellow = v.tint(0xFFF8C830);
      Shapes.blob(m, vc, 0.0F, 0.0F, 0.4F, 1.5F, 1.4F, 2.0F, yellow, b);

      for (int i = 0; i < 2; i++) {
         float z = 0.2F + i * 1.2F;
         Shapes.box(m, vc, -1.45F, -1.3F, z, 1.45F, 1.3F, z + 0.5F, 0xFF2A2018, b);
      }

      Shapes.box(m, vc, -0.15F, -0.15F, 2.4F, 0.15F, 0.15F, 3.0F, 0xFF2A2018, b);
      // Gesicht
      Shapes.box(m, vc, -1.3F, -1.1F, -1.62F, 1.3F, 1.1F, -1.5F, yellow, b);
      eye(m, vc, -0.7F, -0.3F, -1.64F, 0.42F, 0xFF2A2018, t, b);
      eye(m, vc, 0.7F, -0.3F, -1.64F, 0.42F, 0xFF2A2018, t, b);
      Shapes.box(m, vc, -1.3F, 0.3F, -1.66F, -0.8F, 0.6F, -1.6F, 0xFFFF9AB0, b);
      Shapes.box(m, vc, 0.8F, 0.3F, -1.66F, 1.3F, 0.6F, -1.6F, 0xFFFF9AB0, b);

      // Fühler
      for (int s = -1; s <= 1; s += 2) {
         Shapes.box(m, vc, s * 0.5F - 0.08F, -2.2F, -1.2F, s * 0.5F + 0.08F, -1.3F, -1.05F, 0xFF2A2018, b);
         Shapes.box(m, vc, s * 0.55F - 0.18F, -2.45F, -1.3F, s * 0.55F + 0.18F, -2.1F, -0.95F, 0xFF2A2018, b);
      }
   }

   @Override
   public void particles(ClientLevel world, Player p, CosmeticValue v, Random r) {
      if (v.style().equals("Mini-Drache") && r.nextFloat() < 0.03F) {
         double yaw = Math.toRadians(p.yBodyRot);
         double side = v.extra().equals("r") ? -0.38 : 0.38;
         double x = p.getX() + Math.cos(yaw) * side - Math.sin(yaw) * 0.25;
         double z = p.getZ() + Math.sin(yaw) * side + Math.cos(yaw) * 0.25;
         world.addParticle(ParticleTypes.SMOKE, x, p.getY() + 1.75, z, 0.0, 0.02, 0.0);
      }
   }

   @Override
   protected boolean hasLayerOption() {
      return true;
   }

   /** Kleiner Waldgeist-Hirsch aus elfenbeinfarbenen Blöcken, rosa Rauten und einem blühenden Geweih. */
   private void stag(PoseStack m, VertexConsumer vc, CosmeticValue v, float t, float b, boolean glow, float look) {
      int pink = v.tint(Art.SAKURA);
      float nod = Mth.sin(t * 1.1F) * 5.0F;
      if (glow) {
         float tw = 0.5F + 0.5F * Mth.sin(t * 2.0F);
         Art.glowSpark(m, vc, -1.6F, -8.6F, -2.3F, 0.6F, pink, 0.1F + 0.12F * tw);
         Art.glowSpark(m, vc, 1.6F, -8.6F, -2.3F, 0.6F, pink, 0.1F + 0.12F * (1.0F - tw));
         Art.glowSpark(m, vc, -1.05F, -2.7F, -0.75F, 0.25F, pink, 0.4F * tw);
         Art.glowSpark(m, vc, 1.05F, -2.7F, -0.75F, 0.25F, pink, 0.4F * tw);
         return;
      }

      m.pushPose();
      m.scale(0.62F, 0.62F, 0.62F);
      int bone = v.tint(Art.IVORY);
      int white = Art.WHITE;
      int hoof = Art.STONE;
      // Beine mit Hufen und Gelenkplatten
      for (int sx = -1; sx <= 1; sx += 2) {
         for (int sz = -1; sz <= 1; sz += 2) {
            float x = sx * 1.05F, z = sz * 2.0F;
            Art.bevel(m, vc, x - 0.38F, -3.4F, z - 0.38F, x + 0.38F, -0.45F, z + 0.38F, 0.08F, bone, b);
            Art.bevel(m, vc, x - 0.5F, -2.3F, z - 0.5F, x + 0.5F, -1.7F, z + 0.5F, 0.1F, white, b);
            Art.bevel(m, vc, x - 0.45F, -0.5F, z - 0.45F, x + 0.45F, 0.0F, z + 0.45F, 0.08F, hoof, b);
         }
      }

      // Rumpf in Lagen, Rauten an den Seiten
      Art.bevel(m, vc, -1.6F, -5.6F, -2.8F, 1.6F, -3.2F, 2.8F, 0.3F, bone, b);
      Art.bevel(m, vc, -1.75F, -5.9F, -2.2F, 1.75F, -4.9F, 1.6F, 0.25F, white, b);
      Art.bevel(m, vc, -1.45F, -3.5F, -2.4F, 1.45F, -3.05F, 2.4F, 0.12F, Art.bright(bone, 0.9F), b);
      for (int sx = -1; sx <= 1; sx += 2) {
         Art.diamond(m, vc, sx * 1.66F, -4.3F, -1.2F, 0.32F, 0, pink, b);
         Art.diamond(m, vc, sx * 1.66F, -4.0F, 0.6F, 0.24F, 0, pink, b);
         Art.diamond(m, vc, sx * 1.66F, -4.5F, 1.9F, 0.2F, 0, pink, b);
      }

      // Stummelschwanz
      Art.bevel(m, vc, -0.45F, -5.8F, 2.7F, 0.45F, -4.9F, 3.4F, 0.12F, white, b);
      // Hals aus gestapelten Platten
      for (int i = 0; i < 3; i++) {
         float y = -5.2F - i * 0.7F, z = -2.3F - i * 0.5F, w = 0.95F - i * 0.06F;
         Art.bevel(m, vc, -w, y - 0.9F, z - 0.7F, w, y, z + 0.7F, 0.15F, i % 2 == 0 ? bone : white, b);
      }

      Art.diamond(m, vc, 0.0F, -6.0F, -3.55F, 0.26F, 2, pink, b);
      // Kopf: nickt und schaut sich um
      m.pushPose();
      m.translate(0.0F, -7.6F, -3.9F);
      m.rotate(Axis.YP.rotationDegrees(look * 0.6F));
      m.rotate(Axis.XP.rotationDegrees(nod));
      Art.bevel(m, vc, -0.95F, -1.3F, -0.9F, 0.95F, 0.2F, 0.9F, 0.25F, bone, b);
      Art.bevel(m, vc, -0.6F, -0.85F, -2.4F, 0.6F, 0.1F, -0.7F, 0.18F, white, b);
      Art.bevel(m, vc, -0.35F, -0.7F, -2.55F, 0.35F, -0.3F, -2.3F, 0.08F, Art.CHARCOAL, b);
      Art.diamond(m, vc, 0.0F, -0.85F, -0.95F, 0.24F, 2, pink, b);
      for (int sx = -1; sx <= 1; sx += 2) {
         // Augen (dunkel mit Glanzpunkt, blinzeln) und Ohren
         if (blink(t)) {
            Art.box(m, vc, sx * 0.96F - 0.04F, -0.65F, -0.55F, sx * 0.96F + 0.04F, -0.55F, -0.05F, Art.INK, b);
         } else {
            Art.box(m, vc, sx * 0.96F - 0.04F, -0.85F, -0.55F, sx * 0.96F + 0.04F, -0.4F, -0.1F, Art.INK, b);
            Art.box(m, vc, sx * 1.0F - 0.03F, -0.82F, -0.5F, sx * 1.0F + 0.03F, -0.68F, -0.38F, 0xFFFFFFFF, 1.0F);
         }

         float flick = blink(t * 1.3F + sx) ? 18.0F : 0.0F;
         m.pushPose();
         m.translate(sx * 0.95F, -1.0F, 0.3F);
         m.rotate(Axis.ZP.rotationDegrees(sx * (35.0F + flick)));
         Art.bevel(m, vc, -0.25F, -1.1F, -0.2F, 0.25F, 0.0F, 0.2F, 0.08F, white, b);
         Art.box(m, vc, -0.14F, -0.95F, -0.24F, 0.14F, -0.15F, -0.18F, pink, b);
         m.popPose();
         m.pushPose();
         m.translate(sx * 0.7F, -1.25F, 0.2F);
         Art.antler(m, vc, sx, 0.85F, bone, b);
         m.popPose();
      }

      m.popPose();
      Art.drift(m, vc, 0.0F, -14.0F, -1.0F, 4.0F, 14.0F, 6, t, 0.8F, b);
      m.popPose();
   }
}
