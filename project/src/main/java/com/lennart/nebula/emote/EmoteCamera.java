package com.lennart.nebula.emote;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Ich-Ansicht in Fahrzeug-Emotes: die Kamera sitzt dort, wo man im Fahrzeug sitzt (Hubschrauber oben, Auto unten).
 * Kein Vorteil: nie durch Blöcke (bleibt vor Decken/Böden stehen), Zielen und Treffen laufen weiter über die echte
 * Augenposition, und Schlagen beendet den Emote ohnehin.
 */
public final class EmoteCamera {
   private EmoteCamera() {
   }

   /** Höhenversatz der Kamera in Blöcken (0 = keiner). */
   public static double lift(Entity entity, Level level, Vec3 eye) {
      Minecraft mc = Minecraft.getInstance();
      if (entity == null || entity != mc.player || level == null || eye == null) {
         return 0.0;
      }

      EmoteState.Active a = EmoteState.local();
      if (a == null || !a.type.vehicle()) {
         return 0.0;
      }

      double dy = EmotePoses.yOffset(a, EmoteState.now());
      if (Math.abs(dy) < 0.01) {
         return 0.0;
      }

      double margin = 0.3;
      Vec3 to = eye.add(0.0, dy + Math.signum(dy) * margin, 0.0);
      BlockHitResult hit = level.clip(new ClipContext(eye, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, entity));
      if (hit.getType() != HitResult.Type.MISS) {
         double free = Math.abs(hit.getLocation().y - eye.y) - margin;
         dy = Math.signum(dy) * Math.max(0.0, Math.min(Math.abs(dy), free));
      }

      return dy;
   }
}
