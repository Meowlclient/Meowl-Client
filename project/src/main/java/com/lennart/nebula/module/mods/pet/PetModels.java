package com.lennart.nebula.module.mods.pet;

import com.lennart.nebula.util.WorldRender;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;

public final class PetModels {
   public static final String WET_OWL = "Nasse Eule";
   public static final String REX = "Mini-Rex";
   public static final String RAPTOR = "Mini-Raptor";
   public static final String TRICERA = "Mini-Triceratops";
   public static final String DRAGON = "Baby-Drache";
   public static final String GHOST = "Geisterchen";
   public static final String DUO = "Duolingo-Eule";
   public static final String BB8 = "BB-8";
   public static final String R2 = "R2-D2";
   public static final String[] ALL = new String[]{
      "Nasse Eule", "Mini-Rex", "Mini-Raptor", "Mini-Triceratops", "Baby-Drache", "Geisterchen", "Duolingo-Eule", "BB-8", "R2-D2"
   };

   private PetModels() {
   }

   public static boolean has(String skin) {
      for (String s : ALL) {
         if (s.equals(skin)) {
            return true;
         }
      }

      return false;
   }

   public static float hover(String skin, float time) {
      return "Geisterchen".equals(skin) ? 3.0F + Mth.sin(time * 2.2F) * 1.2F : 0.0F;
   }

   public static void draw(String skin, PoseStack m, VertexConsumer vc, float light, PetModels.Anim a) {
      switch (skin) {
         case "Nasse Eule":
            wetOwl(m, vc, light, a);
            break;
         case "Mini-Rex":
            rex(m, vc, light, a);
            break;
         case "Mini-Raptor":
            raptor(m, vc, light, a);
            break;
         case "Mini-Triceratops":
            triceratops(m, vc, light, a);
            break;
         case "Baby-Drache":
            dragon(m, vc, light, a);
            break;
         case "Geisterchen":
            ghost(m, vc, light, a);
            break;
         case "Duolingo-Eule":
            duo(m, vc, light, a);
            break;
         case "BB-8":
            bb8(m, vc, light, a);
            break;
         case "R2-D2":
            r2(m, vc, light, a);
      }
   }

   // ------------------------------------------------------------------ Duolingo-Eule

   private static void duo(PoseStack m, VertexConsumer vc, float light, PetModels.Anim a) {
      int green = 0x58CC02;
      int greenDark = 0x46A302;
      int belly = 0x89E219;
      int orange = 0xFF9600;
      int orangeDark = 0xE07B00;
      float breathe = Mth.sin(a.time() * 2.4F) * 0.2F;
      float squash = a.onGround() ? 0.0F : 0.3F;
      // Füße
      for (int side = -1; side <= 1; side += 2) {
         m.pushPose();
         m.translate(side * 2.0F, 1.6F, 0.6F);
         m.mulPose(Axis.XP.rotationDegrees(side * a.legSwing() * 0.7F));
         box(m, vc, -0.5F, -1.6F, -0.5F, 0.5F, 0.0F, 0.5F, orangeDark, light);

         for (int toe = -1; toe <= 1; toe++) {
            box(m, vc, toe * 0.75F - 0.35F, -1.6F, 0.2F, toe * 0.75F + 0.35F, -1.0F, 2.0F, orange, light);
         }

         m.popPose();
      }

      // runder Körper (Kopf und Bauch in einem, wie bei Duo)
      m.pushPose();
      m.translate(0.0F, 1.6F, 0.0F);
      m.scale(1.0F + squash * 0.1F, 1.0F - squash * 0.1F, 1.0F);
      box(m, vc, -5.0F, 1.0F, -4.2F, 5.0F, 12.0F + breathe, 4.2F, green, light);
      box(m, vc, -4.4F, 0.2F, -3.6F, 4.4F, 1.0F, 3.6F, green, light);
      box(m, vc, -4.4F, 12.0F + breathe, -3.6F, 4.4F, 12.8F + breathe, 3.6F, green, light);
      box(m, vc, -5.6F, 2.0F, -3.4F, 5.6F, 11.0F, 3.4F, green, light);
      // heller Bauch
      box(m, vc, -3.6F, 1.0F, 4.2F, 3.6F, 6.2F, 4.45F, belly, light);
      box(m, vc, -2.6F, 0.6F, 3.6F, 2.6F, 1.1F, 4.3F, belly, light);
      // Haarbüschel oben
      for (int k = -1; k <= 1; k++) {
         float hgt = k == 0 ? 2.2F : 1.4F;
         box(m, vc, k * 1.3F - 0.45F, 12.6F + breathe, -0.5F, k * 1.3F + 0.45F, 12.6F + breathe + hgt, 0.5F, greenDark, light);
      }

      // Flügel
      for (int side = -1; side <= 1; side += 2) {
         m.pushPose();
         m.translate(side * 5.6F, 8.5F, 0.0F);
         m.mulPose(Axis.ZP.rotationDegrees(side * (10.0F + a.flap() * 0.7F + Mth.sin(a.time() * 3.0F) * 4.0F)));
         float x1 = side < 0 ? -1.1F : 0.0F;
         float x2 = side < 0 ? 0.0F : 1.1F;
         box(m, vc, x1, -6.0F, -2.6F, x2, 0.0F, 2.6F, greenDark, light);
         box(m, vc, x1, -7.2F, -1.6F, x2, -6.0F, 1.8F, greenDark, light);
         m.popPose();
      }

      // Kopfbereich dreht leicht mit dem Blick
      m.pushPose();
      m.translate(0.0F, 0.0F, 0.0F);
      m.mulPose(Axis.YP.rotationDegrees(-a.headYaw() * 0.35F));
      float look = Mth.clamp(a.headYaw() / 70.0F, -1.0F, 1.0F) * 0.5F;

      for (int side = -1; side <= 1; side += 2) {
         float cx = side * 2.35F;
         // große weiße Augen mit grünem Rand
         box(m, vc, cx - 2.15F, 6.3F, 4.2F, cx + 2.15F, 10.9F, 4.35F, greenDark, light);
         if (a.blink()) {
            box(m, vc, cx - 1.9F, 8.2F, 4.35F, cx + 1.9F, 8.9F, 4.55F, 0x2B6E00, light);
         } else {
            box(m, vc, cx - 1.9F, 6.6F, 4.35F, cx + 1.9F, 10.6F, 4.5F, 0xFFFFFF, Math.max(light, 0.9F));
            box(m, vc, cx - 0.95F - look, 7.4F, 4.5F, cx + 0.75F - look, 9.6F, 4.65F, 0x1A1A1A, 1.0F);
            box(m, vc, cx - 0.7F - look, 9.0F, 4.65F, cx - 0.2F - look, 9.5F, 4.7F, 0xFFFFFF, 1.0F);
         }
      }

      // Schnabel
      box(m, vc, -1.0F, 5.0F, 4.2F, 1.0F, 6.6F, 5.8F, orange, light);
      box(m, vc, -0.6F, 4.3F, 4.4F, 0.6F, 5.0F, 5.4F, orangeDark, light);
      m.popPose();
      m.popPose();
   }

   // ------------------------------------------------------------------ BB-8

   private static void bb8(PoseStack m, VertexConsumer vc, float light, PetModels.Anim a) {
      int white = 0xF2F2EE;
      int orange = 0xE8761E;
      int grey = 0xA9ADB2;
      int dark = 0x2B2B30;
      float r = 6.0F;
      // Kugel rollt beim Laufen um die x-Achse
      float roll = a.phase() * 32.0F;
      m.pushPose();
      m.translate(0.0F, r, 0.0F);
      m.mulPose(Axis.XP.rotationDegrees(roll));

      for (int i = -6; i < 6; i++) {
         float y = i + 0.5F;
         float rr = (float)Math.sqrt(Math.max(0.0F, r * r - y * y));
         box(m, vc, -rr, i, -rr, rr, i + 1, rr, white, light);
      }

      // orange Ringe mit grauen Kernen auf vier Seiten (rollen mit)
      for (int face = 0; face < 4; face++) {
         m.pushPose();
         m.mulPose(Axis.XP.rotationDegrees(face * 90.0F));
         ring(m, vc, 0.0F, 0.0F, r + 0.02F, 3.4F, 0.8F, orange, light);
         box(m, vc, -1.6F, -1.6F, r - 0.3F, 1.6F, 1.6F, r + 0.05F, grey, light);
         box(m, vc, -0.6F, -0.6F, r - 0.2F, 0.6F, 0.6F, r + 0.1F, dark, light);
         m.popPose();
      }

      for (int side = -1; side <= 1; side += 2) {
         m.pushPose();
         m.mulPose(Axis.YP.rotationDegrees(side * 90.0F));
         ring(m, vc, 0.0F, 0.0F, r + 0.02F, 3.4F, 0.8F, orange, light);
         box(m, vc, -1.6F, -1.6F, r - 0.3F, 1.6F, 1.6F, r + 0.05F, grey, light);
         m.popPose();
      }

      m.popPose();
      // Kopf bleibt oben, lehnt sich beim Rollen etwas nach vorn
      m.pushPose();
      m.translate(0.0F, r * 2.0F - 0.2F, 0.0F);
      m.mulPose(Axis.XP.rotationDegrees(a.walk() * 14.0F));
      m.mulPose(Axis.YP.rotationDegrees(-a.headYaw() + Mth.sin(a.time() * 0.8F) * 10.0F * (1.0F - a.walk())));
      float hr = 3.6F;

      for (int i = 0; i < 4; i++) {
         float y = i + 0.5F;
         float rr = (float)Math.sqrt(Math.max(0.0F, hr * hr - y * y));
         box(m, vc, -rr, i, -rr, rr, i + 1, rr, white, light);
      }

      box(m, vc, -3.2F, 0.2F, -3.2F, 3.2F, 0.8F, 3.2F, orange, light);
      box(m, vc, -2.6F, 2.6F, -2.6F, 2.6F, 3.0F, 2.6F, grey, light);
      // großes Auge + kleines Auge
      box(m, vc, -1.1F, 1.1F, 3.2F, 1.1F, 3.0F, 3.7F, dark, light);
      box(m, vc, -0.6F, 1.6F, 3.65F, 0.6F, 2.5F, 3.8F, 0x3B5A8A, 1.0F);
      box(m, vc, 1.5F, 0.9F, 2.9F, 2.2F, 1.6F, 3.4F, dark, light);
      box(m, vc, 1.65F, 1.05F, 3.35F, 2.05F, 1.45F, 3.45F, a.blink() ? dark : 0xD83A2A, 1.0F);
      // Antennen
      box(m, vc, -0.8F, 3.8F, -0.6F, -0.6F, 6.6F, -0.4F, grey, light);
      box(m, vc, 0.4F, 3.8F, -0.8F, 0.55F, 5.4F, -0.65F, grey, light);
      m.popPose();
   }

   /** Flacher Ring (Kreis aus kleinen Boxen) auf der Ebene z = zFace. */
   private static void ring(PoseStack m, VertexConsumer vc, float cx, float cy, float zFace, float radius, float width, int rgb, float light) {
      int steps = 20;

      for (int i = 0; i < steps; i++) {
         double ang = i * Math.PI * 2.0 / steps;
         float x = cx + (float)Math.cos(ang) * radius;
         float y = cy + (float)Math.sin(ang) * radius;
         float h = width / 2.0F;
         box(m, vc, x - h, y - h, zFace - 0.35F, x + h, y + h, zFace, rgb, light);
      }
   }

   // ------------------------------------------------------------------ R2-D2

   private static void r2(PoseStack m, VertexConsumer vc, float light, PetModels.Anim a) {
      int white = 0xEDEEF2;
      int blue = 0x2457C4;
      int silver = 0xB9BEC6;
      int dark = 0x26262B;
      float waddle = Mth.sin(a.time() * 9.0F) * 6.0F * a.walk();
      // Seitenbeine mit Füßen
      for (int side = -1; side <= 1; side += 2) {
         m.pushPose();
         m.translate(side * 4.6F, 10.5F, 0.0F);
         box(m, vc, -0.9F, -9.0F, -1.2F, 0.9F, 1.2F, 1.2F, white, light);
         box(m, vc, side < 0 ? -0.95F : 0.9F, -6.5F, -0.9F, side < 0 ? -0.9F : 0.95F, -1.0F, 0.9F, blue, light);
         box(m, vc, -1.4F, -10.5F, -2.0F, 1.4F, -9.0F, 2.2F, silver, light);
         m.popPose();
      }

      // Mittelbein
      box(m, vc, -0.8F, 0.0F, -1.2F, 0.8F, 3.0F, 1.0F, silver, light);
      box(m, vc, -1.2F, 0.0F, -1.6F, 1.2F, 0.8F, 1.6F, dark, light);
      // Körper: Achteck aus zwei gekreuzten Boxen, wackelt beim Laufen
      m.pushPose();
      m.translate(0.0F, 2.5F, 0.0F);
      m.mulPose(Axis.ZP.rotationDegrees(waddle));
      box(m, vc, -3.8F, 0.0F, -2.6F, 3.8F, 9.0F, 2.6F, white, light);
      box(m, vc, -2.6F, 0.0F, -3.8F, 2.6F, 9.0F, 3.8F, white, light);
      // blaue Paneele vorne
      box(m, vc, -2.0F, 5.0F, 3.8F, -0.6F, 8.2F, 3.95F, blue, light);
      box(m, vc, 0.4F, 6.0F, 3.8F, 2.0F, 8.2F, 3.95F, blue, light);
      box(m, vc, -2.2F, 1.0F, 3.8F, 2.2F, 2.0F, 3.95F, blue, light);
      box(m, vc, -0.4F, 2.6F, 3.8F, 0.4F, 4.6F, 3.95F, silver, light);
      box(m, vc, -3.85F, 3.0F, -1.2F, -3.8F, 7.0F, 1.2F, blue, light);
      box(m, vc, 3.8F, 3.0F, -1.2F, 3.85F, 7.0F, 1.2F, blue, light);
      box(m, vc, -3.0F, 9.0F, -3.0F, 3.0F, 9.3F, 3.0F, silver, light);
      // Kuppel: dreht sich suchend hin und her
      m.pushPose();
      m.translate(0.0F, 9.3F, 0.0F);
      float search = Mth.sin(a.time() * 0.9F) * 50.0F * (1.0F - a.walk());
      m.mulPose(Axis.YP.rotationDegrees(-a.headYaw() * 0.5F + search));
      float hr = 3.9F;

      for (int i = 0; i < 4; i++) {
         float y = i + 0.5F;
         float rr = (float)Math.sqrt(Math.max(0.0F, hr * hr - y * y * 0.9F));
         box(m, vc, -rr, i, -rr, rr, i + 1, rr, silver, light);
      }

      box(m, vc, -1.0F, 1.2F, 3.4F, 1.0F, 2.6F, 3.95F, dark, light);
      box(m, vc, -0.5F, 1.6F, 3.9F, 0.5F, 2.2F, 4.0F, 0x1F1F2A, 1.0F);
      // Statuslicht wechselt langsam rot/blau
      boolean redLight = (int)(a.time() * 1.5F) % 2 == 0;
      box(m, vc, 1.6F, 0.6F, 3.0F, 2.3F, 1.3F, 3.6F, redLight ? 0xE8402E : 0x3C8CFF, 1.0F);
      box(m, vc, -2.8F, 0.3F, 2.2F, -1.6F, 1.6F, 3.4F, blue, light);
      box(m, vc, -1.6F, 2.8F, 2.0F, 1.6F, 3.3F, 2.8F, blue, light);
      m.popPose();
      m.popPose();
   }

   private static void box(PoseStack m, VertexConsumer vc, float x1, float y1, float z1, float x2, float y2, float z2, int rgb, float light) {
      WorldRender.box(m, vc, x1, y1, z1, x2, y2, z2, 0xFF000000 | rgb, light);
   }

   private static float hash(int a, int b) {
      int h = a * 73856093 ^ b * 19349663;
      h ^= h >>> 13;
      h *= 1540483477;
      h ^= h >>> 15;
      return (h & 65535) / 65535.0F;
   }

   private static void wetOwl(PoseStack m, VertexConsumer vc, float light, PetModels.Anim a) {
      int cream = 14471356;
      int creamLight = 15656148;
      int speck = 5981751;
      int speckLight = 9204574;
      int wing = 4864816;
      int wingEdge = 12890261;
      int legs = 15787736;
      int talon = 6183511;
      float shake = 0.0F;
      float cycle = a.time() % 7.0F;
      if (cycle > 6.2F) {
         shake = Mth.sin(a.time() * 55.0F) * 6.0F * (1.0F - (cycle - 6.2F) / 0.8F);
      }

      float breathe = Mth.sin(a.time() * 2.0F) * 0.15F;

      for (int side = -1; side <= 1; side += 2) {
         m.pushPose();
         m.translate(side * 1.4F, 4.2F, 0.0F);
         m.mulPose(Axis.XP.rotationDegrees(side * a.legSwing() * 0.8F));
         box(m, vc, -0.45F, -4.2F, -0.45F, 0.45F, 0.0F, 0.45F, 15787736, light);
         box(m, vc, -0.7F, -4.2F, -0.5F, 0.7F, -3.6F, 0.6F, 15787736, light);

         for (int toe = -1; toe <= 1; toe++) {
            m.pushPose();
            m.translate(toe * 0.55F, -4.2F, 0.4F);
            m.mulPose(Axis.YP.rotationDegrees(toe * 22.0F));
            box(m, vc, -0.22F, 0.0F, 0.0F, 0.22F, 0.4F, 1.5F, 15787736, light);
            box(m, vc, -0.18F, -0.1F, 1.5F, 0.18F, 0.35F, 2.0F, 6183511, light);
            m.popPose();
         }

         box(m, vc, -0.22F, -4.2F, -1.4F, 0.22F, -3.8F, -0.4F, 15787736, light);
         m.popPose();
      }

      m.pushPose();
      m.mulPose(Axis.ZP.rotationDegrees(shake));
      box(m, vc, -3.0F, 7.0F, -2.6F, 3.0F, 12.2F + breathe, 2.6F, 14471356, light);
      box(m, vc, -2.4F, 4.0F, -2.2F, 2.4F, 7.0F, 2.2F, 14471356, light);
      box(m, vc, -2.4F, 4.4F, 2.6F, 2.4F, 11.5F, 2.9F, 15656148, light);

      for (int iy = 0; iy < 9; iy++) {
         for (int ix = -3; ix <= 3; ix++) {
            float h = hash(ix + 11, iy + 7);
            if (h > 0.45F) {
               float x = ix * 0.8F;
               float y = 4.6F + iy * 0.85F;
               int c = h > 0.8F ? 5981751 : 9204574;
               box(m, vc, x - 0.28F, y, 2.9F, x + 0.28F, y + 0.45F, 3.0F, c, light);
            }
         }
      }

      for (int side = -1; side <= 1; side += 2) {
         for (int k = 0; k < 4; k++) {
            float z = -2.0F + k * 1.2F;
            float y1 = 5.0F + hash(side, k) * 2.0F;
            box(m, vc, side * 3.0F - 0.05F, y1, z, side * 3.0F + 0.05F, 11.6F, z + 0.4F, 9204574, light);
         }
      }

      for (int side = -1; side <= 1; side += 2) {
         m.pushPose();
         m.translate(side * 3.0F, 11.6F, 0.0F);
         m.mulPose(Axis.ZP.rotationDegrees(side * (4.0F + a.flap() * 0.3F)));
         float x1 = side < 0 ? -1.0F : 0.0F;
         float x2 = side < 0 ? 0.0F : 1.0F;
         box(m, vc, x1, -8.0F, -2.6F, x2, 0.0F, 2.2F, 4864816, light);

         for (int k = 0; k < 5; k++) {
            float y = -7.2F + k * 1.5F;
            float xs = side < 0 ? x1 - 0.06F : x2;
            box(m, vc, xs, y, -1.6F + hash(k, side) * 2.0F, xs + 0.06F, y + 0.5F, -0.9F + hash(k, side) * 2.0F, 12890261, light);
         }

         box(m, vc, x1, -9.2F, -2.0F, x2, -8.0F, 1.2F, 4864816, light);
         m.popPose();
      }

      m.pushPose();
      m.translate(0.0F, 11.8F + breathe, 0.0F);
      m.mulPose(Axis.YP.rotationDegrees(-a.headYaw()));
      float tilt = Mth.sin(a.time() * 0.7F) * 9.0F * (1.0F - a.walk());
      m.mulPose(Axis.ZP.rotationDegrees(tilt));
      box(m, vc, -4.4F, 0.0F, -3.4F, 4.4F, 7.4F, 3.4F, 14471356, light);

      for (int i = 0; i < 7; i++) {
         float x = -3.0F + i;
         float hgt = 0.6F + hash(i, 3) * 1.1F;
         box(m, vc, x - 0.25F, 7.4F, -1.5F + hash(i, 9) * 2.0F, x + 0.25F, 7.4F + hgt, -1.0F + hash(i, 9) * 2.0F, 15656148, light);
      }

      for (int i = 0; i < 10; i++) {
         float y = 1.0F + hash(i, 1) * 5.5F;
         float z = -2.8F + hash(i, 2) * 5.0F;
         int side = i % 2 == 0 ? -1 : 1;
         box(m, vc, side * 4.4F - 0.05F, y, z, side * 4.4F + 0.05F, y + 0.5F, z + 0.5F, 5981751, light);
      }

      box(m, vc, -4.0F, 0.4F, 3.4F, 4.0F, 6.8F, 3.6F, 15656148, light);

      for (int side = -1; side <= 1; side += 2) {
         float cx = side * 2.15F;
         box(m, vc, cx - 1.55F, 2.2F, 3.6F, cx + 1.55F, 5.6F, 3.7F, 13214604, light);
         if (a.blink()) {
            box(m, vc, cx - 1.25F, 3.6F, 3.7F, cx + 1.25F, 4.2F, 3.9F, 2761248, light);
         } else {
            box(m, vc, cx - 1.25F, 2.5F, 3.7F, cx + 1.25F, 5.3F, 3.95F, 1380366, 1.0F);
            box(m, vc, cx - 0.95F, 2.2F, 3.7F, cx + 0.95F, 5.6F, 3.9F, 1380366, 1.0F);
            box(m, vc, cx - 0.9F + side * 0.2F, 4.4F, 3.95F, cx - 0.4F + side * 0.2F, 4.9F, 4.0F, 16777215, 1.0F);
         }

         m.pushPose();
         m.translate(cx, 5.3F, 3.8F);
         m.mulPose(Axis.ZP.rotationDegrees(side * -14.0F));
         box(m, vc, -1.6F, -0.55F, 0.0F, 1.6F, 0.45F, 0.35F, 12101006, light);
         m.popPose();
      }

      box(m, vc, -0.6F, 0.9F, 3.5F, 0.6F, 3.4F, 5.2F, 15721932, light);
      box(m, vc, -0.45F, 0.1F, 4.6F, 0.45F, 1.0F, 5.5F, 14866104, light);
      box(m, vc, -0.3F, 3.0F, 4.0F, 0.3F, 3.3F, 4.5F, 7034180, light);
      box(m, vc, -1.3F, -1.6F, 2.6F, 1.3F, 0.3F, 3.5F, 15116194, light);
      box(m, vc, -0.8F, -1.3F, 3.5F, 0.8F, -0.1F, 3.6F, 10377822, light);
      m.popPose();
      m.popPose();
   }

   private static void rex(PoseStack m, VertexConsumer vc, float light, PetModels.Anim a) {
      int green = 5151290;
      int dark = 3107624;
      int belly = 10932602;
      float breathe = Mth.sin(a.time() * 2.2F) * 0.2F;

      for (int side = -1; side <= 1; side += 2) {
         m.pushPose();
         m.translate(side * 2.1F, 7.0F, 0.0F);
         m.mulPose(Axis.XP.rotationDegrees(side * a.legSwing()));
         box(m, vc, -1.3F, -3.6F, -1.4F, 1.3F, 0.6F, 1.6F, 5151290, light);
         box(m, vc, -0.9F, -7.0F, -0.9F, 0.9F, -3.4F, 0.9F, 5151290, light);
         box(m, vc, -1.1F, -7.0F, -0.8F, 1.1F, -6.2F, 2.3F, 3107624, light);
         m.popPose();
      }

      box(m, vc, -3.2F, 6.0F, -4.0F, 3.2F, 12.0F + breathe, 4.0F, 5151290, light);
      box(m, vc, -2.4F, 6.3F, 1.5F, 2.4F, 10.5F, 4.2F, 10932602, light);

      for (int k = 0; k < 4; k++) {
         box(m, vc, -0.5F, 12.0F + breathe, -3.0F + k * 1.8F, 0.5F, 12.9F + breathe, -2.0F + k * 1.8F, 3107624, light);
      }

      m.pushPose();
      m.translate(0.0F, 9.5F, -4.0F);
      m.mulPose(Axis.YP.rotationDegrees(Mth.sin(a.time() * 2.0F) * 12.0F + a.legSwing() * 0.3F));
      box(m, vc, -2.2F, -1.6F, -4.5F, 2.2F, 2.2F, 0.0F, 5151290, light);
      box(m, vc, -1.3F, -0.8F, -8.5F, 1.3F, 1.4F, -4.5F, 5151290, light);
      box(m, vc, -0.6F, -0.3F, -11.0F, 0.6F, 0.9F, -8.5F, 3107624, light);
      m.popPose();

      for (int side = -1; side <= 1; side += 2) {
         m.pushPose();
         m.translate(side * 3.0F, 9.6F, 3.0F);
         m.mulPose(Axis.XP.rotationDegrees(-30.0F + Mth.sin(a.time() * 3.0F) * 15.0F));
         box(m, vc, -0.4F, -2.2F, -0.4F, 0.4F, 0.0F, 0.4F, 5151290, light);
         box(m, vc, -0.35F, -2.6F, -0.1F, 0.35F, -2.2F, 0.6F, 15591126, light);
         m.popPose();
      }

      m.pushPose();
      m.translate(0.0F, 11.5F, 3.0F);
      m.mulPose(Axis.YP.rotationDegrees(-a.headYaw()));
      float roar = Math.max(0.0F, Mth.sin(a.time() * 0.9F) - 0.75F) * 4.0F * (1.0F - a.walk());
      m.mulPose(Axis.XP.rotationDegrees(-roar * 12.0F));
      box(m, vc, -2.8F, 0.0F, -0.5F, 2.8F, 4.6F, 6.5F, 5151290, light);
      box(m, vc, -2.9F, 3.8F, 0.0F, 2.9F, 4.8F, 3.5F, 3107624, light);
      m.pushPose();
      m.translate(0.0F, 0.6F, 0.0F);
      m.mulPose(Axis.XP.rotationDegrees(roar * 30.0F + 6.0F));
      box(m, vc, -2.5F, -1.8F, 0.0F, 2.5F, 0.0F, 6.0F, 5151290, light);
      box(m, vc, -2.2F, -0.4F, 0.6F, 2.2F, 0.0F, 5.6F, 11552840, light);
      m.popPose();

      for (int k = 0; k < 4; k++) {
         float z = 2.0F + k * 1.1F;
         box(m, vc, -2.3F, 0.0F, z, -1.9F, 0.6F, z + 0.4F, 16118246, light);
         box(m, vc, 1.9F, 0.0F, z, 2.3F, 0.6F, z + 0.4F, 16118246, light);
      }

      for (int side = -1; side <= 1; side += 2) {
         float x = side * 2.85F;
         if (a.blink()) {
            box(m, vc, x - 0.06F, 3.0F, 3.4F, x + 0.06F, 3.3F, 4.6F, 1710618, light);
         } else {
            box(m, vc, x - 0.06F, 2.6F, 3.4F, x + 0.06F, 3.8F, 4.6F, 15913274, 1.0F);
            box(m, vc, x - 0.1F, 2.8F, 3.8F, x + 0.1F, 3.6F, 4.2F, 1118481, 1.0F);
         }
      }

      box(m, vc, -0.9F, 3.0F, 6.3F, -0.4F, 3.5F, 6.6F, 2047259, light);
      box(m, vc, 0.4F, 3.0F, 6.3F, 0.9F, 3.5F, 6.6F, 2047259, light);
      m.popPose();
   }

   private static void raptor(PoseStack m, VertexConsumer vc, float light, PetModels.Anim a) {
      int skin = 13208139;
      int stripe = 7226662;
      int belly = 15653300;
      float breathe = Mth.sin(a.time() * 2.6F) * 0.15F;

      for (int side = -1; side <= 1; side += 2) {
         m.pushPose();
         m.translate(side * 1.6F, 7.0F, -0.5F);
         m.mulPose(Axis.XP.rotationDegrees(side * a.legSwing() * 1.2F));
         box(m, vc, -0.9F, -3.4F, -1.0F, 0.9F, 0.4F, 1.4F, 13208139, light);
         box(m, vc, -0.6F, -7.0F, -1.2F, 0.6F, -3.2F, -0.2F, 13208139, light);
         box(m, vc, -0.8F, -7.0F, -0.6F, 0.8F, -6.4F, 1.8F, 7226662, light);
         box(m, vc, -0.2F, -6.6F, 1.6F, 0.2F, -5.4F, 2.4F, 2829099, light);
         m.popPose();
      }

      box(m, vc, -2.4F, 6.5F, -3.5F, 2.4F, 10.8F + breathe, 3.5F, 13208139, light);
      box(m, vc, -1.9F, 6.6F, 0.0F, 1.9F, 9.0F, 3.7F, 15653300, light);

      for (int k = 0; k < 4; k++) {
         box(m, vc, -2.45F, 9.2F, -3.0F + k * 1.7F, 2.45F, 10.85F + breathe, -2.4F + k * 1.7F, 7226662, light);
      }

      m.pushPose();
      m.translate(0.0F, 9.0F, -3.5F);
      m.mulPose(Axis.YP.rotationDegrees(Mth.sin(a.time() * 2.4F) * 15.0F));
      m.mulPose(Axis.XP.rotationDegrees(-8.0F));
      box(m, vc, -1.5F, -1.2F, -5.0F, 1.5F, 1.4F, 0.0F, 13208139, light);
      box(m, vc, -0.9F, -0.7F, -10.0F, 0.9F, 0.9F, -5.0F, 13208139, light);
      box(m, vc, -0.5F, -0.4F, -13.0F, 0.5F, 0.6F, -10.0F, 7226662, light);
      m.popPose();

      for (int side = -1; side <= 1; side += 2) {
         m.pushPose();
         m.translate(side * 2.2F, 9.0F, 2.8F);
         m.mulPose(Axis.XP.rotationDegrees(-50.0F + a.legSwing() * 0.4F));
         box(m, vc, -0.35F, -2.4F, -0.35F, 0.35F, 0.0F, 0.35F, 13208139, light);
         box(m, vc, -0.3F, -3.0F, 0.0F, 0.3F, -2.4F, 0.7F, 2829099, light);
         m.popPose();
      }

      m.pushPose();
      m.translate(0.0F, 10.0F, 3.0F);
      m.mulPose(Axis.YP.rotationDegrees(-a.headYaw()));
      m.mulPose(Axis.XP.rotationDegrees(-20.0F));
      box(m, vc, -1.1F, 0.0F, -0.8F, 1.1F, 3.6F, 1.6F, 13208139, light);
      m.translate(0.0F, 3.6F, 0.8F);
      m.mulPose(Axis.XP.rotationDegrees(25.0F + Mth.sin(a.time() * 1.3F) * 5.0F));
      box(m, vc, -1.5F, -0.4F, -1.2F, 1.5F, 2.0F, 5.0F, 13208139, light);
      box(m, vc, -1.2F, -1.2F, 0.0F, 1.2F, -0.4F, 4.6F, 15653300, light);
      box(m, vc, -1.55F, 1.4F, -0.6F, 1.55F, 2.05F, 2.6F, 7226662, light);

      for (int side = -1; side <= 1; side += 2) {
         float x = side * 1.52F;
         box(m, vc, x - 0.05F, 0.8F, 1.8F, x + 0.05F, a.blink() ? 1.0F : 1.5F, 2.6F, a.blink() ? 1710618 : 15251754, 1.0F);
      }

      m.popPose();
   }

   private static void triceratops(PoseStack m, VertexConsumer vc, float light, PetModels.Anim a) {
      int body = 8293466;
      int dark = 5661499;
      int frill = 12608586;
      int horn = 15722191;
      float breathe = Mth.sin(a.time() * 1.8F) * 0.2F;
      float[][] legPos = new float[][]{{-2.6F, 3.0F}, {2.6F, 3.0F}, {-2.6F, -3.2F}, {2.6F, -3.2F}};

      for (int i = 0; i < 4; i++) {
         float swing = (i != 0 && i != 3 ? -1 : 1) * a.legSwing();
         m.pushPose();
         m.translate(legPos[i][0], 4.5F, legPos[i][1]);
         m.mulPose(Axis.XP.rotationDegrees(swing));
         box(m, vc, -1.1F, -4.5F, -1.1F, 1.1F, 0.0F, 1.1F, 8293466, light);
         box(m, vc, -1.2F, -4.5F, -0.9F, 1.2F, -4.0F, 1.6F, 5661499, light);
         m.popPose();
      }

      box(m, vc, -3.6F, 4.0F, -5.0F, 3.6F, 10.0F + breathe, 5.0F, 8293466, light);
      box(m, vc, -3.0F, 10.0F + breathe, -4.0F, 3.0F, 10.8F + breathe, 3.5F, 5661499, light);
      m.pushPose();
      m.translate(0.0F, 7.0F, -5.0F);
      m.mulPose(Axis.YP.rotationDegrees(Mth.sin(a.time() * 1.6F) * 14.0F));
      box(m, vc, -1.4F, -1.0F, -4.0F, 1.4F, 1.2F, 0.0F, 8293466, light);
      box(m, vc, -0.7F, -0.6F, -6.5F, 0.7F, 0.6F, -4.0F, 5661499, light);
      m.popPose();
      m.pushPose();
      m.translate(0.0F, 7.5F, 5.0F);
      m.mulPose(Axis.YP.rotationDegrees(-a.headYaw()));
      m.mulPose(Axis.XP.rotationDegrees(Mth.sin(a.time() * 0.8F) * 6.0F));
      box(m, vc, -4.8F, 1.0F, -0.4F, 4.8F, 8.0F, 0.4F, 12608586, light);

      for (int k = 0; k < 6; k++) {
         float x = -4.2F + k * 1.7F;
         box(m, vc, x - 0.35F, 8.0F, -0.3F, x + 0.35F, 8.7F, 0.3F, 15722191, light);
      }

      box(m, vc, -2.4F, -1.5F, 0.0F, 2.4F, 3.0F, 5.0F, 8293466, light);
      box(m, vc, -1.2F, -1.8F, 4.6F, 1.2F, 0.4F, 6.6F, 5661499, light);
      box(m, vc, -0.35F, 0.5F, 5.0F, 0.35F, 2.0F, 6.2F, 15722191, light);

      for (int side = -1; side <= 1; side += 2) {
         m.pushPose();
         m.translate(side * 1.4F, 3.0F, 2.6F);
         m.mulPose(Axis.XP.rotationDegrees(-55.0F));
         box(m, vc, -0.35F, 0.0F, -0.35F, 0.35F, 3.6F, 0.35F, 15722191, light);
         m.popPose();
         float x = side * 2.42F;
         box(m, vc, x - 0.05F, 1.6F, 2.0F, x + 0.05F, a.blink() ? 1.8F : 2.4F, 2.8F, a.blink() ? 1710618 : 1577486, 1.0F);
      }

      m.popPose();
   }

   private static void dragon(PoseStack m, VertexConsumer vc, float light, PetModels.Anim a) {
      int scale = 8081353;
      int dark = 5123727;
      int belly = 15320170;
      int horn = 16051161;
      float breathe = Mth.sin(a.time() * 2.0F) * 0.2F;
      float wingFlap = a.onGround() ? Mth.sin(a.time() * 3.0F) * 10.0F + a.flap() * 0.4F : Mth.sin(a.time() * 14.0F) * 40.0F;

      for (int side = -1; side <= 1; side += 2) {
         m.pushPose();
         m.translate(side * 1.9F, 4.5F, 0.0F);
         m.mulPose(Axis.XP.rotationDegrees(side * a.legSwing()));
         box(m, vc, -0.9F, -4.5F, -0.9F, 0.9F, 0.0F, 1.0F, 8081353, light);
         box(m, vc, -1.0F, -4.5F, -0.6F, 1.0F, -3.9F, 1.8F, 5123727, light);
         m.popPose();
      }

      box(m, vc, -3.0F, 4.0F, -3.4F, 3.0F, 10.5F + breathe, 3.4F, 8081353, light);
      box(m, vc, -2.3F, 4.4F, 3.4F, 2.3F, 9.8F, 3.7F, 15320170, light);

      for (int k = 0; k < 4; k++) {
         box(m, vc, -0.4F, 10.5F + breathe, -2.6F + k * 1.6F, 0.4F, 11.4F + breathe, -1.8F + k * 1.6F, 5123727, light);
      }

      for (int side = -1; side <= 1; side += 2) {
         m.pushPose();
         m.translate(side * 2.6F, 10.0F, -0.5F);
         m.mulPose(Axis.ZP.rotationDegrees(side * (20.0F + wingFlap)));
         float x1 = side < 0 ? -7.0F : 0.0F;
         float x2 = side < 0 ? 0.0F : 7.0F;
         box(m, vc, x1, -0.3F, -3.0F, x2, 0.3F, 2.0F, 5123727, light);
         box(m, vc, side < 0 ? -7.0F : 6.0F, -0.4F, -3.2F, side < 0 ? -6.0F : 7.0F, 0.4F, 2.2F, 8081353, light);
         box(m, vc, side < 0 ? -5.5F : 0.5F, -0.32F, -2.6F, side < 0 ? -0.5F : 5.5F, 0.32F, 1.4F, 11766762, light);
         m.popPose();
      }

      m.pushPose();
      m.translate(0.0F, 6.0F, -3.4F);
      m.mulPose(Axis.YP.rotationDegrees(Mth.sin(a.time() * 2.5F) * 18.0F));
      box(m, vc, -1.4F, -1.0F, -4.0F, 1.4F, 1.2F, 0.0F, 8081353, light);
      box(m, vc, -0.8F, -0.6F, -7.0F, 0.8F, 0.7F, -4.0F, 8081353, light);
      box(m, vc, -1.2F, -0.8F, -8.6F, 1.2F, 0.9F, -7.0F, 5123727, light);
      m.popPose();
      m.pushPose();
      m.translate(0.0F, 10.5F + breathe, 1.0F);
      m.mulPose(Axis.YP.rotationDegrees(-a.headYaw()));
      box(m, vc, -3.0F, 0.0F, -2.5F, 3.0F, 5.0F, 3.2F, 8081353, light);
      box(m, vc, -1.8F, 0.4F, 3.2F, 1.8F, 2.6F, 5.4F, 8081353, light);
      box(m, vc, -1.6F, 0.4F, 4.0F, 1.6F, 1.0F, 5.45F, 15320170, light);
      box(m, vc, -1.0F, 2.0F, 5.4F, -0.5F, 2.4F, 5.6F, 2759242, light);
      box(m, vc, 0.5F, 2.0F, 5.4F, 1.0F, 2.4F, 5.6F, 2759242, light);

      for (int side = -1; side <= 1; side += 2) {
         float cx = side * 1.6F;
         if (a.blink()) {
            box(m, vc, cx - 0.9F, 3.0F, 3.2F, cx + 0.9F, 3.4F, 3.4F, 1708080, light);
         } else {
            box(m, vc, cx - 0.9F, 2.4F, 3.2F, cx + 0.9F, 4.1F, 3.4F, 16769658, 1.0F);
            box(m, vc, cx - 0.25F, 2.5F, 3.4F, cx + 0.25F, 4.0F, 3.5F, 1708080, 1.0F);
         }

         m.pushPose();
         m.translate(side * 2.0F, 5.0F, -1.0F);
         m.mulPose(Axis.XP.rotationDegrees(-35.0F));
         box(m, vc, -0.45F, 0.0F, -0.45F, 0.45F, 2.6F, 0.45F, 16051161, light);
         m.popPose();
      }

      m.popPose();
   }

   private static void ghost(PoseStack m, VertexConsumer vc, float light, PetModels.Anim a) {
      int white = 16054015;
      int shade = 14015216;
      float sway = Mth.sin(a.time() * 1.7F) * 6.0F;
      m.mulPose(Axis.ZP.rotationDegrees(sway * 0.5F));
      box(m, vc, -4.0F, 4.0F, -3.5F, 4.0F, 13.0F, 3.5F, 16054015, Math.max(light, 0.85F));
      box(m, vc, -3.2F, 13.0F, -2.8F, 3.2F, 14.4F, 2.8F, 16054015, Math.max(light, 0.85F));

      for (int k = 0; k < 4; k++) {
         float x = -4.0F + k * 2.0F;
         float drop = 1.2F + Mth.sin(a.time() * 4.0F + k * 1.5F) * 0.8F;
         box(m, vc, x, 4.0F - drop, -3.5F, x + 2.0F, 4.0F, 3.5F, k % 2 == 0 ? 16054015 : 14015216, Math.max(light, 0.85F));
      }

      for (int side = -1; side <= 1; side += 2) {
         m.pushPose();
         m.translate(side * 4.0F, 10.0F, 0.0F);
         m.mulPose(Axis.ZP.rotationDegrees(side * (25.0F + Mth.sin(a.time() * 3.0F + side) * 15.0F)));
         box(m, vc, side < 0 ? -1.2F : 0.0F, -3.5F, -1.0F, side < 0 ? 0.0F : 1.2F, 0.0F, 1.0F, 14015216, Math.max(light, 0.85F));
         m.popPose();
      }

      m.pushPose();
      m.translate(0.0F, 0.0F, 0.0F);
      m.mulPose(Axis.YP.rotationDegrees(-a.headYaw() * 0.6F));

      for (int side = -1; side <= 1; side += 2) {
         float cx = side * 1.6F;
         if (a.blink()) {
            box(m, vc, cx - 0.9F, 9.6F, 3.5F, cx + 0.9F, 10.0F, 3.7F, 1776426, 1.0F);
         } else {
            box(m, vc, cx - 0.8F, 8.8F, 3.5F, cx + 0.8F, 11.0F, 3.7F, 1776426, 1.0F);
            box(m, vc, cx - 0.5F + side * 0.1F, 10.2F, 3.7F, cx - 0.1F + side * 0.1F, 10.7F, 3.75F, 16777215, 1.0F);
         }
      }

      box(m, vc, -0.9F, 7.0F, 3.5F, 0.9F, 8.0F, 3.7F, 3811908, 1.0F);
      box(m, vc, -2.9F, 8.0F, 3.5F, -2.0F, 8.5F, 3.6F, 16230600, 1.0F);
      box(m, vc, 2.0F, 8.0F, 3.5F, 2.9F, 8.5F, 3.6F, 16230600, 1.0F);
      m.popPose();
   }

   /** phase = fortlaufende Laufphase (nur beim Laufen größer werdend). */
   public record Anim(float time, float headYaw, float legSwing, float walk, float flap, boolean blink, boolean onGround, float phase) {
   }
}
