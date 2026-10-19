package com.lennart.nebula.wings;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.util.Mth;

/**
 * Weicher Bewegungszustand der Flügel je Spieler. Die Schlagphase wird über die echte Bildzeit aufsummiert und
 * Schlagstärke/Öffnung gleiten exponentiell zum Ziel – so gibt es keine Sprünge, wenn sich Tempo oder Zustand ändern.
 */
public final class WingsAnim {
   private static final Map<Integer, WingsAnim> STATES = new HashMap<>();
   public float time;
   public float phase;
   public float flap;
   public float open = 0.15F;
   public float lag;
   public float elytra;
   public float glide;
   private float lastYaw;
   private double lastX;
   private double lastY;
   private double lastZ;
   private float speedH;
   private float speedV;
   private long last;
   private boolean init;

   public static synchronized WingsAnim of(int id) {
      return STATES.computeIfAbsent(id, k -> new WingsAnim());
   }

   public static synchronized WingsAnim peek(int id) {
      return STATES.get(id);
   }

   /** Alte Zustände entfernen (Spieler weg). */
   public static synchronized void cleanup() {
      long now = System.nanoTime();
      Iterator<WingsAnim> it = STATES.values().iterator();

      while (it.hasNext()) {
         if (now - it.next().last > 10_000_000_000L) {
            it.remove();
         }
      }
   }

   public float horizontalSpeed() {
      return this.speedH;
   }

   /**
    * Ein Bild weiter. Liefert false, wenn im selben Bild schon aktualisiert wurde (z.B. Inventar-Vorschau).
    */
   public boolean update(double x, double y, double z, float bodyYaw, boolean onGround, boolean gliding, boolean flying, boolean sneaking, float speedMul) {
      return this.update(x, y, z, bodyYaw, onGround, gliding, flying, sneaking, speedMul, false);
   }

   /** Wie oben; {@code elytraMode} = die Flügel ersetzen gerade eine angezogene Elytra. */
   public boolean update(
      double x, double y, double z, float bodyYaw, boolean onGround, boolean gliding, boolean flying, boolean sneaking, float speedMul, boolean elytraMode
   ) {
      long now = System.nanoTime();
      float dt = this.init ? (float)((now - this.last) / 1.0E9) : 0.0F;
      if (this.init && dt < 0.002F) {
         return false;
      } else {
         dt = Math.min(dt, 0.1F);
         this.last = now;
         if (!this.init) {
            this.init = true;
            this.lastX = x;
            this.lastY = y;
            this.lastZ = z;
            this.lastYaw = bodyYaw;
            return true;
         } else {
            // Tempo aus der (interpolierten) Position – funktioniert auch für andere Spieler
            float vh = (float)(Math.sqrt((x - this.lastX) * (x - this.lastX) + (z - this.lastZ) * (z - this.lastZ)) / dt);
            float vv = (float)((y - this.lastY) / dt);
            this.lastX = x;
            this.lastY = y;
            this.lastZ = z;
            if (vh > 60.0F) {
               vh = 0.0F;
               vv = 0.0F;
            }

            float ks = 1.0F - (float)Math.exp(-dt * 5.0F);
            this.speedH += (vh - this.speedH) * ks;
            this.speedV += (vv - this.speedV) * ks;
            float flapT = Math.min(1.0F, this.speedH / 8.0F) * 0.7F;
            float openT = sneaking ? 0.0F : 0.15F + Math.min(1.0F, this.speedH / 10.0F) * 0.15F;
            if (!onGround && this.speedV < -6.0F) {
               flapT = Math.max(flapT, 0.95F);
               openT = 0.65F;
            }

            if (flying) {
               flapT = Math.max(flapT, 0.75F);
               openT = 0.7F;
            }

            if (gliding) {
               flapT = 0.18F;
               openT = 1.0F;
            }

            float k = 1.0F - (float)Math.exp(-dt * 4.0F);
            this.flap += (flapT - this.flap) * k;
            this.open += (openT - this.open) * k;
            // Elytra: Ein- und Ausklappen etwas schneller als der normale Flügelschlag
            float ke = 1.0F - (float)Math.exp(-dt * 6.0F);
            this.elytra += ((elytraMode ? 1.0F : 0.0F) - this.elytra) * ke;
            this.glide += ((gliding ? 1.0F : 0.0F) - this.glide) * ke;
            this.time += dt;
            this.phase = (float)((this.phase + dt * (1.6F + this.flap * 5.5F) * Math.max(0.2F, speedMul)) % (Math.PI * 200.0));
            float dyaw = Mth.wrapDegrees(bodyYaw - this.lastYaw);
            this.lastYaw = bodyYaw;
            float lagT = Mth.clamp(-dyaw / dt * 0.035F, -14.0F, 14.0F);
            this.lag += (lagT - this.lag) * (1.0F - (float)Math.exp(-dt * 6.0F));
            return true;
         }
      }
   }

   public WingsModel.Anim anim() {
      return new WingsModel.Anim(this.time, this.phase, this.flap, this.open, this.lag, this.elytra, this.glide);
   }
}
