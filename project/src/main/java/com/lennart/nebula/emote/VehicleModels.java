package com.lennart.nebula.emote;

import com.lennart.nebula.cosmetic.Shapes;
import com.lennart.nebula.util.WorldRender;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;

/**
 * Fahrzeuge der Fahrzeug-Emotes (eigene Block-Modelle): E-Scooter, Auto, Panzer, Hubschrauber. Pixel-Einheiten im
 * Körperraum des Spielers: Füße bei y = 24, y nach unten, vorne = -z. Die Höhe des Spielers kommt aus
 * {@link EmotePoses#yOffset}, der Boden liegt also bei y = 24 + Versatz.
 */
public final class VehicleModels {
   private static final int RUBBER = 0xFF1B1B1F;
   private static final int DARK = 0xFF2A2B30;
   private static final int GREY = 0xFF6E717A;
   private static final int METAL = 0xFFB9BECB;
   private static final int ORANGE = 0xFFF08A24;

   private VehicleModels() {
   }

   /**
    * @param blend 0..1 (Ein-/Ausblenden) @param wheel Raddrehung (Bogenmaß) @param headYaw Kopfdrehung relativ zum
    * Körper (Panzerturm) @param time Sekunden
    */
   public static void submit(
      PoseStack matrices, SubmitNodeCollector queue, EmoteType type, float blend, float wheel, float headYaw, float time, float bright
   ) {
      if (blend <= 0.01F || !type.vehicle()) {
         return;
      }

      matrices.pushPose();
      // aus den Füßen herauswachsen
      matrices.translate(0.0F, 1.5F, 0.0F);
      matrices.scale(blend, blend, blend);
      matrices.translate(0.0F, -1.5F, 0.0F);
      queue.submitCustomGeometry(matrices, WorldRender.solidColor(), com.lennart.nebula.util.Perf.timed(com.lennart.nebula.module.mods.Emotes.class, (entry, vc) -> {
         PoseStack m = local(entry);
         switch (type) {
            case ROLLER -> scooter(m, vc, wheel, bright);
            case AUTO -> car(m, vc, wheel, bright);
            case PANZER -> tank(m, vc, wheel, headYaw, bright);
            case HELI -> heli(m, vc, time, bright);
            default -> {
            }
         }
      }));
      queue.submitCustomGeometry(matrices, WorldRender.glow(), com.lennart.nebula.util.Perf.timed(com.lennart.nebula.module.mods.Emotes.class, (entry, vc) -> {
         PoseStack m = local(entry);
         switch (type) {
            case ROLLER -> scooterGlow(m, vc, time);
            case AUTO -> carGlow(m, vc);
            case HELI -> heliGlow(m, vc, time);
            default -> {
            }
         }
      }));
      matrices.popPose();
   }

   private static PoseStack local(PoseStack.Pose entry) {
      PoseStack m = new PoseStack();
      m.last().pose().set(entry.pose());
      m.last().normal().set(entry.normal());
      m.scale(0.0625F, 0.0625F, 0.0625F);
      return m;
   }

   private static void box(PoseStack m, VertexConsumer vc, float x1, float y1, float z1, float x2, float y2, float z2, int c, float l) {
      Shapes.box(m, vc, x1, y1, z1, x2, y2, z2, c, l);
   }

   /** Rad um die x-Achse (Achse quer), Mittelpunkt (x, y, z). */
   private static void wheel(PoseStack m, VertexConsumer vc, float x, float y, float z, float r, float halfW, float turn, int rim, float l) {
      m.pushPose();
      m.translate(x, y, z);
      m.rotate(Axis.XP.rotation(turn));
      float s = r * 0.6F;
      box(m, vc, -halfW, -r, -s, halfW, r, s, RUBBER, l);
      box(m, vc, -halfW, -s, -r, halfW, s, r, RUBBER, l);
      // Profilstollen, damit man das Drehen sieht
      m.pushPose();
      m.rotate(Axis.XP.rotationDegrees(45.0F));
      box(m, vc, -halfW * 0.9F, -r * 0.92F, -s * 0.5F, halfW * 0.9F, r * 0.92F, s * 0.5F, RUBBER, l * 0.8F);
      m.popPose();
      box(m, vc, -halfW - 0.1F, -r * 0.42F, -r * 0.42F, halfW + 0.1F, r * 0.42F, r * 0.42F, rim, l);
      box(m, vc, -halfW - 0.05F, -r * 0.7F, -0.25F, halfW + 0.05F, r * 0.7F, 0.25F, rim, l);
      m.popPose();
   }

   // ================================================================== E-Scooter (Offroad-E-Scooter: schwarz, grau, orange)

   private static final int DECK_GREY = 0xFF3A3D44;
   private static final int FRAME = 0xFF1C1D22;

   /** Balken von a nach b (beliebige Richtung), Querschnitt 2·half. */
   private static void seg(PoseStack m, VertexConsumer vc, float ax, float ay, float az, float bx, float by, float bz, float half, int c, float l) {
      float dx = bx - ax;
      float dy = by - ay;
      float dz = bz - az;
      float len = (float)Math.sqrt(dx * dx + dy * dy + dz * dz);
      if (len < 0.01F) {
         return;
      }

      m.pushPose();
      m.translate(ax, ay, az);
      m.rotate(new org.joml.Quaternionf().rotationTo(0.0F, 1.0F, 0.0F, dx / len, dy / len, dz / len));
      box(m, vc, -half, 0.0F, -half, half, len, half, c, l);
      m.popPose();
   }

   /** Stollenreifen: Reifen, Stollen rundherum, graue Felge. */
   private static void knobbyWheel(PoseStack m, VertexConsumer vc, float y, float z, float r, float halfW, float turn, float l) {
      wheel(m, vc, 0.0F, y, z, r, halfW, turn, GREY, l);
      m.pushPose();
      m.translate(0.0F, y, z);
      m.rotate(Axis.XP.rotation(turn));

      for (int i = 0; i < 12; i++) {
         m.pushPose();
         m.rotate(Axis.XP.rotationDegrees(i * 30.0F));
         box(m, vc, -halfW - 0.1F, -r - 0.35F, -0.45F, halfW + 0.1F, -r + 0.3F, 0.45F, RUBBER, l * 0.85F);
         m.popPose();
      }

      // Bremsscheibe
      box(m, vc, halfW + 0.15F, -r * 0.55F, -r * 0.55F, halfW + 0.35F, r * 0.55F, r * 0.55F, METAL, l);
      m.popPose();
   }

   /** Karo-Muster (orange/schwarz) auf der Seite des Trittbretts. */
   private static void checker(PoseStack m, VertexConsumer vc, float x, float y, float z, float l) {
      for (int i = 0; i < 4; i++) {
         for (int j = 0; j < 2; j++) {
            int c = (i + j) % 2 == 0 ? ORANGE : FRAME;
            float zz = z + i * 0.5F;
            float yy = y + j * 0.5F;
            box(m, vc, x - 0.05F, yy, zz, x + 0.05F, yy + 0.5F, zz + 0.5F, c, l);
         }
      }
   }

   private static void scooter(PoseStack m, VertexConsumer vc, float turn, float l) {
      // Trittbrett: dunkelgraues Deck mit schwarzer Trittfläche und orangem Karo-Muster vorne und hinten
      box(m, vc, -3.2F, 24.0F, -8.5F, 3.2F, 25.6F, 8.5F, DECK_GREY, l);
      box(m, vc, -2.9F, 23.8F, -8.0F, 2.9F, 24.0F, 8.0F, FRAME, l);

      for (float side = -1.0F; side <= 1.0F; side += 2.0F) {
         float x = side * 3.25F;
         checker(m, vc, x, 24.3F, -7.6F, l);
         checker(m, vc, x, 24.3F, 5.6F, l);
         box(m, vc, x - 0.05F, 24.6F, -3.0F, x + 0.05F, 24.9F, 3.0F, 0xFF8E939C, l);
      }

      // Hinterrad an grauer Schwinge mit orangem Streifen, Schutzblech mit Sitz
      for (float side = -1.0F; side <= 1.0F; side += 2.0F) {
         seg(m, vc, side * 2.3F, 25.0F, 7.5F, side * 2.3F, 26.6F, 12.5F, 0.5F, GREY, l);
         seg(m, vc, side * 2.85F, 25.2F, 8.0F, side * 2.85F, 26.0F, 11.0F, 0.12F, ORANGE, l);
      }

      knobbyWheel(m, vc, 26.6F, 12.5F, 3.4F, 1.6F, turn, l);
      box(m, vc, -2.0F, 22.0F, 9.0F, 2.0F, 22.8F, 16.4F, FRAME, l);
      box(m, vc, -1.8F, 22.8F, 15.6F, 1.8F, 25.0F, 16.4F, FRAME, l);
      // Sitz auf dem Schutzblech, vorne etwas höher
      box(m, vc, -2.6F, 20.8F, 9.6F, 2.6F, 22.0F, 15.2F, 0xFF26262B, l);
      box(m, vc, -2.6F, 20.2F, 9.6F, 2.6F, 20.8F, 11.0F, 0xFF26262B, l);
      // Ständer in der Mitte
      seg(m, vc, -3.0F, 25.4F, 1.0F, -3.6F, 30.0F, 2.2F, 0.25F, METAL, l);
      // vorne: Lenkkopf-Gehäuse und Federgabel zum Vorderrad
      box(m, vc, -2.2F, 19.0F, -11.4F, 2.2F, 25.6F, -8.5F, FRAME, l);
      checker(m, vc, -2.25F, 21.0F, -10.8F, l);
      checker(m, vc, 2.25F, 21.0F, -10.8F, l);

      for (float side = -1.0F; side <= 1.0F; side += 2.0F) {
         seg(m, vc, side * 2.3F, 24.6F, -10.0F, side * 2.3F, 26.6F, -13.5F, 0.55F, GREY, l);
         seg(m, vc, side * 2.85F, 24.9F, -10.4F, side * 2.85F, 26.1F, -12.6F, 0.12F, ORANGE, l);
         // Federbein
         seg(m, vc, side * 1.9F, 20.0F, -11.0F, side * 2.1F, 25.4F, -12.4F, 0.45F, METAL, l);
      }

      knobbyWheel(m, vc, 26.6F, -13.5F, 3.4F, 1.6F, turn, l);
      box(m, vc, -2.0F, 22.0F, -17.0F, 2.0F, 22.8F, -10.6F, FRAME, l);
      // Lenkstange: leicht nach hinten geneigt, orange Schrift-Streifen vorne
      seg(m, vc, 0.0F, 19.5F, -10.4F, 0.0F, 3.4F, -9.2F, 1.0F, FRAME, l);
      seg(m, vc, 0.0F, 17.0F, -11.45F, 0.0F, 8.0F, -10.75F, 0.35F, ORANGE, l);
      // Display auf dem Lenkerkopf
      box(m, vc, -1.6F, 1.8F, -10.6F, 1.6F, 3.4F, -8.4F, FRAME, l);
      // gebogener Lenker: Mitte, nach außen ansteigend, Griffe leicht nach hinten gezogen
      seg(m, vc, -2.4F, 3.0F, -9.4F, 2.4F, 3.0F, -9.4F, 0.45F, FRAME, l);

      for (float side = -1.0F; side <= 1.0F; side += 2.0F) {
         seg(m, vc, side * 2.3F, 3.0F, -9.4F, side * 4.8F, 1.8F, -9.2F, 0.45F, FRAME, l);
         seg(m, vc, side * 4.6F, 1.8F, -9.2F, side * 8.4F, 2.2F, -8.4F, 0.6F, RUBBER, l);
         // Bremshebel
         seg(m, vc, side * 4.2F, 1.9F, -9.6F, side * 7.6F, 2.6F, -10.6F, 0.18F, METAL, l);
      }

      // Schlaufe, die vom Lenker herunterhängt
      seg(m, vc, -0.6F, 3.4F, -9.9F, -0.4F, 10.5F, -10.3F, 0.3F, FRAME, l);
      box(m, vc, -0.75F, 6.0F, -10.65F, -0.25F, 9.5F, -10.55F, ORANGE, l);
   }

   private static void scooterGlow(PoseStack m, VertexConsumer vc, float t) {
      // Frontlicht unten am Lenkkopf, Display, Rücklicht
      Shapes.glow(m, vc, -1.4F, 19.6F, -11.7F, 1.4F, 21.0F, -11.45F, 0xFFFFF4D8, 0.9F);
      Shapes.glow(m, vc, -1.2F, 2.0F, -10.75F, 1.2F, 3.2F, -10.6F, 0xFF60D8FF, 0.6F);
      Shapes.glow(m, vc, -1.2F, 23.2F, 16.4F, 1.2F, 24.2F, 16.7F, 0xFFFF2020, 0.6F + 0.3F * (float)Math.sin(t * 4.0F));
   }
   // ================================================================== Auto (Cabrio)

   private static final int CAR = 0xFFD8262E;

   private static void car(PoseStack m, VertexConsumer vc, float turn, float l) {
      int shade = Shapes.mix(CAR, 0xFF000000, 0.25F);
      // Boden
      box(m, vc, -7.5F, 14.0F, -17.0F, 7.5F, 15.4F, 13.0F, DARK, l);
      // Seitenwände
      box(m, vc, -8.0F, 7.0F, -17.0F, -7.0F, 14.0F, 13.0F, CAR, l);
      box(m, vc, 7.0F, 7.0F, -17.0F, 8.0F, 14.0F, 13.0F, CAR, l);
      box(m, vc, -8.1F, 9.6F, -17.0F, -7.9F, 10.4F, 13.0F, 0xFFF4F4F4, l);
      box(m, vc, 7.9F, 9.6F, -17.0F, 8.1F, 10.4F, 13.0F, 0xFFF4F4F4, l);
      // Motorhaube und Front
      box(m, vc, -7.0F, 8.0F, -17.0F, 7.0F, 10.0F, -10.0F, CAR, l);
      box(m, vc, -7.0F, 10.0F, -17.5F, 7.0F, 14.0F, -16.0F, shade, l);
      box(m, vc, -8.2F, 12.0F, -18.2F, 8.2F, 14.2F, -17.0F, GREY, l);
      box(m, vc, -3.0F, 10.6F, -17.7F, 3.0F, 12.6F, -17.4F, DARK, l);
      // Heck mit Kofferraum
      box(m, vc, -7.0F, 7.0F, 6.0F, 7.0F, 8.0F, 13.0F, CAR, l);
      box(m, vc, -7.0F, 8.0F, 11.0F, 7.0F, 14.0F, 13.0F, shade, l);
      box(m, vc, -8.2F, 12.0F, 13.0F, 8.2F, 14.2F, 14.0F, GREY, l);
      // Sitz
      box(m, vc, -5.0F, 2.0F, 2.6F, 5.0F, 13.0F, 4.6F, DARK, l);
      box(m, vc, -5.0F, 11.6F, -4.0F, 5.0F, 13.6F, 3.0F, DARK, l);
      // Windschutzscheiben-Rahmen und Lenkrad
      box(m, vc, -7.3F, -2.0F, -10.8F, -6.5F, 8.0F, -10.2F, DARK, l);
      box(m, vc, 6.5F, -2.0F, -10.8F, 7.3F, 8.0F, -10.2F, DARK, l);
      box(m, vc, -7.3F, -2.6F, -10.8F, 7.3F, -2.0F, -10.2F, DARK, l);
      box(m, vc, -0.4F, 5.5F, -10.0F, 0.4F, 8.6F, -8.6F, GREY, l);
      box(m, vc, -2.6F, 3.0F, -8.9F, 2.6F, 3.7F, -8.3F, RUBBER, l);
      box(m, vc, -2.6F, 7.3F, -8.9F, 2.6F, 8.0F, -8.3F, RUBBER, l);
      box(m, vc, -2.6F, 3.0F, -8.9F, -1.9F, 8.0F, -8.3F, RUBBER, l);
      box(m, vc, 1.9F, 3.0F, -8.9F, 2.6F, 8.0F, -8.3F, RUBBER, l);
      // Räder
      for (float side = -1.0F; side <= 1.0F; side += 2.0F) {
         wheel(m, vc, side * 8.8F, 13.4F, -12.5F, 3.4F, 1.2F, turn, METAL, l);
         wheel(m, vc, side * 8.8F, 13.4F, 8.5F, 3.4F, 1.2F, turn, METAL, l);
      }
   }

   private static void carGlow(PoseStack m, VertexConsumer vc) {
      // Scheinwerfer, Rücklichter, Windschutzscheibe
      Shapes.glow(m, vc, -6.6F, 10.4F, -17.8F, -4.0F, 12.0F, -17.5F, 0xFFFFF4C8, 0.9F);
      Shapes.glow(m, vc, 4.0F, 10.4F, -17.8F, 6.6F, 12.0F, -17.5F, 0xFFFFF4C8, 0.9F);
      Shapes.glow(m, vc, -6.6F, 9.0F, 13.0F, -4.0F, 10.6F, 13.3F, 0xFFFF2020, 0.7F);
      Shapes.glow(m, vc, 4.0F, 9.0F, 13.0F, 6.6F, 10.6F, 13.3F, 0xFFFF2020, 0.7F);
      Shapes.glow(m, vc, -6.5F, -2.0F, -10.6F, 6.5F, 8.0F, -10.4F, 0xFF9AD8FF, 0.18F);
   }

   // ================================================================== Panzer

   private static final int ARMY = 0xFF5A6B3A;

   private static void tank(PoseStack m, VertexConsumer vc, float turn, float headYaw, float l) {
      int dark = Shapes.mix(ARMY, 0xFF000000, 0.3F);
      int light = Shapes.mix(ARMY, 0xFFFFFFFF, 0.12F);
      // Ketten mit Laufrädern; die Glieder oben wandern beim Fahren mit
      float shift = (float)(((turn * 1.5F) % 3.0F + 3.0F) % 3.0F);

      for (float side = -1.0F; side <= 1.0F; side += 2.0F) {
         float x1 = side < 0.0F ? -10.0F : 6.5F;
         float x2 = x1 + 3.5F;
         box(m, vc, x1, 24.0F, -15.0F, x2, 30.0F, 15.0F, 0xFF2B2B2B, l);

         for (int k = -2; k <= 2; k++) {
            float wx = side < 0.0F ? x1 - 0.4F : x2;
            box(m, vc, wx, 25.8F, k * 5.6F - 1.8F, wx + 0.4F, 29.4F, k * 5.6F + 1.8F, GREY, l);
         }

         for (float z = -15.0F + shift; z < 15.0F; z += 3.0F) {
            box(m, vc, x1 + 0.2F, 23.8F, z, x2 - 0.2F, 24.0F, Math.min(15.0F, z + 1.0F), 0xFF3C3C3C, l);
         }
      }

      // Wanne
      box(m, vc, -7.0F, 17.0F, -14.0F, 7.0F, 25.0F, 14.0F, ARMY, l);
      box(m, vc, -7.0F, 19.0F, -16.0F, 7.0F, 25.0F, -14.0F, dark, l);
      box(m, vc, -10.2F, 16.6F, -15.0F, 10.2F, 17.4F, 15.0F, light, l);
      box(m, vc, -3.0F, 16.0F, 9.0F, 3.0F, 17.0F, 13.0F, dark, l);
      // Turm dreht sich mit dem Blick, Kanone zeigt dorthin, wo man hinschaut
      m.pushPose();
      m.rotate(Axis.YP.rotation(headYaw));
      box(m, vc, -6.0F, 9.0F, -7.0F, 6.0F, 17.0F, -5.0F, ARMY, l);
      box(m, vc, -6.0F, 9.0F, 5.0F, 6.0F, 17.0F, 8.0F, ARMY, l);
      box(m, vc, -7.0F, 9.0F, -7.0F, -5.0F, 17.0F, 8.0F, ARMY, l);
      box(m, vc, 5.0F, 9.0F, -7.0F, 7.0F, 17.0F, 8.0F, ARMY, l);
      box(m, vc, -7.2F, 8.6F, -7.2F, 7.2F, 9.2F, 8.2F, light, l);
      box(m, vc, -1.0F, 11.0F, -26.0F, 1.0F, 13.0F, -7.0F, dark, l);
      box(m, vc, -1.5F, 10.5F, -27.5F, 1.5F, 13.5F, -25.0F, 0xFF3A3A3A, l);
      box(m, vc, -2.2F, 10.2F, -8.0F, 2.2F, 13.8F, -7.0F, dark, l);
      // offene Luke und Antenne
      box(m, vc, -3.0F, 5.4F, 8.0F, 3.0F, 8.8F, 8.8F, dark, l);
      box(m, vc, 4.6F, -7.0F, 6.2F, 5.0F, 8.6F, 6.6F, 0xFF3A3A3A, l);
      m.popPose();
   }

   // ================================================================== Hubschrauber

   private static final int HELI = 0xFF2F6FD8;

   private static void heli(PoseStack m, VertexConsumer vc, float t, float l) {
      int stripe = 0xFFF4F4F4;
      // Kabine: Boden, Seiten, Nase, Dach, Rückwand
      box(m, vc, -6.0F, 14.0F, -15.0F, 6.0F, 15.6F, 10.0F, HELI, l);
      box(m, vc, -7.0F, 8.0F, -15.0F, -6.0F, 14.0F, 10.0F, HELI, l);
      box(m, vc, 6.0F, 8.0F, -15.0F, 7.0F, 14.0F, 10.0F, HELI, l);
      box(m, vc, -7.05F, 10.0F, -15.0F, -6.95F, 11.0F, 10.0F, stripe, l);
      box(m, vc, 6.95F, 10.0F, -15.0F, 7.05F, 11.0F, 10.0F, stripe, l);
      box(m, vc, -6.0F, 8.0F, -19.0F, 6.0F, 15.6F, -15.0F, HELI, l);
      box(m, vc, -4.5F, 13.0F, -20.0F, 4.5F, 15.0F, -19.0F, Shapes.mix(HELI, 0xFF000000, 0.3F), l);
      box(m, vc, -6.6F, -10.6F, -18.0F, 6.6F, -10.0F, 10.5F, HELI, l);
      box(m, vc, -6.6F, -10.0F, 9.0F, 6.6F, 14.0F, 10.5F, HELI, l);
      // Fensterstreben
      box(m, vc, -6.6F, -10.0F, -18.6F, -6.0F, 8.0F, -18.0F, DARK, l);
      box(m, vc, 6.0F, -10.0F, -18.6F, 6.6F, 8.0F, -18.0F, DARK, l);
      // Sitz
      box(m, vc, -4.5F, 2.0F, 2.6F, 4.5F, 14.0F, 4.6F, DARK, l);
      box(m, vc, -4.5F, 12.6F, -4.0F, 4.5F, 14.0F, 3.0F, DARK, l);
      // Steuerknüppel
      box(m, vc, -2.4F, 6.5F, -6.0F, -1.6F, 14.0F, -5.2F, RUBBER, l);
      // Heckausleger, Leitwerk, Heckrotor
      box(m, vc, -1.5F, 1.0F, 10.5F, 1.5F, 5.0F, 36.0F, HELI, l);
      box(m, vc, -1.55F, 2.6F, 10.5F, 1.55F, 3.4F, 36.0F, stripe, l);
      box(m, vc, -0.4F, -6.0F, 33.0F, 0.4F, 5.0F, 37.0F, HELI, l);
      box(m, vc, -4.0F, 3.0F, 32.0F, 4.0F, 3.8F, 35.0F, HELI, l);
      m.pushPose();
      m.translate(1.4F, -1.0F, 35.0F);
      m.rotate(Axis.XP.rotation(t * 30.0F));
      box(m, vc, 0.0F, -4.5F, -0.5F, 0.4F, 4.5F, 0.5F, 0xFFD8D8D8, l);
      box(m, vc, 0.0F, -0.5F, -4.5F, 0.4F, 0.5F, 4.5F, 0xFFD8D8D8, l);
      m.popPose();
      // Rotor
      box(m, vc, -0.8F, -13.0F, -4.0F, 0.8F, -10.6F, -2.4F, GREY, l);
      m.pushPose();
      m.translate(0.0F, -13.6F, -3.2F);
      m.rotate(Axis.YP.rotation(t * 22.0F));
      box(m, vc, -1.4F, -0.6F, -1.4F, 1.4F, 0.6F, 1.4F, DARK, l);
      box(m, vc, -30.0F, -0.25F, -1.1F, 30.0F, 0.25F, 1.1F, 0xFF3A3D44, l);
      box(m, vc, -1.1F, -0.25F, -30.0F, 1.1F, 0.25F, 30.0F, 0xFF3A3D44, l);
      m.popPose();
      // Kufen
      for (float side = -1.0F; side <= 1.0F; side += 2.0F) {
         box(m, vc, side * 5.0F - 0.3F, 15.6F, -10.3F, side * 5.0F + 0.3F, 20.0F, -9.7F, GREY, l);
         box(m, vc, side * 5.0F - 0.3F, 15.6F, 5.7F, side * 5.0F + 0.3F, 20.0F, 6.3F, GREY, l);
         box(m, vc, side * 5.0F - 0.6F, 20.0F, -14.0F, side * 5.0F + 0.6F, 21.0F, 10.0F, GREY, l);
      }
   }

   private static void heliGlow(PoseStack m, VertexConsumer vc, float t) {
      // Kanzel aus Glas
      Shapes.glow(m, vc, -6.0F, -10.0F, -18.9F, 6.0F, 8.0F, -18.4F, 0xFF9AD8FF, 0.2F);
      Shapes.glow(m, vc, -6.9F, -10.0F, -18.0F, -6.5F, 8.0F, 6.0F, 0xFF9AD8FF, 0.16F);
      Shapes.glow(m, vc, 6.5F, -10.0F, -18.0F, 6.9F, 8.0F, 6.0F, 0xFF9AD8FF, 0.16F);
      // verwischte Rotorscheibe
      Shapes.glow(m, vc, -28.0F, -13.75F, -31.2F, 28.0F, -13.45F, 24.8F, 0xFFFFFFFF, 0.05F);
      // Positionslichter
      boolean blink = (t * 1.5F) % 1.0F < 0.25F;
      Shapes.glowCube(m, vc, -7.2F, 12.0F, -14.0F, 0.5F, 0xFFFF2020, 0.8F);
      Shapes.glowCube(m, vc, 7.2F, 12.0F, -14.0F, 0.5F, 0xFF20FF40, 0.8F);
      Shapes.glowCube(m, vc, 0.0F, -6.4F, 37.0F, 0.5F, 0xFFFF2020, blink ? 1.0F : 0.1F);
   }
}
