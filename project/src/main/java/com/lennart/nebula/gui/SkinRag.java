package com.lennart.nebula.gui;

import net.minecraft.client.model.geom.ModelPart;

/** Schwerelose Ragdoll-Haltung für den Skin im Hauptmenü: Körper rollt, Arme und Beine schlenkern hinterher. */
public final class SkinRag {
   /** Rollen des ganzen Körpers (Bogenmaß). */
   public float roll;
   /** 0 = ruhig, 1 = wild schlenkern. */
   public float flail;
   /** Nachziehen gegen die Bewegungsrichtung (-1..1). */
   public float lagX;
   public float lagY;

   static void apply(ModelPart root, float t, SkinRag r) {
      try {
         ModelPart head = root.getChild("head");
         ModelPart ra = root.getChild("right_arm");
         ModelPart la = root.getChild("left_arm");
         ModelPart rl = root.getChild("right_leg");
         ModelPart ll = root.getChild("left_leg");
         float f = r.flail;
         float flap = (float)Math.sin(t * (2.0F + 6.0F * f));
         float swim = (float)Math.sin(t * 1.4F);
         root.zRot = r.roll;
         float spread = 0.25F + 1.1F * f + 0.35F * flap * f + 0.12F * swim * (1.0F - f);
         ra.zRot = spread;
         la.zRot = -spread;
         ra.xRot = r.lagY * 1.2F + 0.4F * flap * f;
         la.xRot = r.lagY * 1.2F - 0.4F * flap * f;
         float legSpread = 0.12F + 0.55F * f;
         rl.zRot = legSpread;
         ll.zRot = -legSpread;
         rl.xRot = -r.lagY * 0.8F + 0.45F * (float)Math.sin(t * (1.6F + 5.0F * f)) * (0.3F + f);
         ll.xRot = -r.lagY * 0.8F - 0.45F * (float)Math.sin(t * (1.6F + 5.0F * f)) * (0.3F + f);
         head.xRot = r.lagY * 0.45F;
         head.yRot = -r.lagX * 0.5F;
      } catch (RuntimeException var11) {
      }
   }
}
