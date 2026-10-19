package com.lennart.nebula.nametag;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.gui.Font;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Platzhalter für das Symbol von Simple Voice Chat in der Nametag-Zeile: reserviert Platz und merkt sich, wo das Symbol
 * hin soll. Voice Chat zeichnet es danach selbst – verschoben an genau diese Stelle.
 */
public record VoicePiece(int entityId) implements NametagPiece {
   public static final float WIDTH = 10.0F;
   private static final Map<Integer, Vector3f> ANCHORS = new ConcurrentHashMap<>();
   private static Vector3f base = new Vector3f();

   @Override
   public float width(Font tr) {
      return WIDTH;
   }

   @Override
   public void draw(Font tr, NameCanvas canvas, float x, float y) {
      Matrix4f pose = canvas.pose();
      if (pose != null) {
         // linke obere Ecke des 10-px-Symbols (Voice Chat zeichnet es von y-1 bis y+9)
         ANCHORS.put(this.entityId, pose.transformPosition(x, y - 1.0F, 0.0F, new Vector3f()).sub(base));
      }
   }

   /** Zu Beginn eines Nametags: Ursprung des Spieler-Raums merken. */
   public static void begin(Matrix4f entityPose) {
      base = entityPose.transformPosition(0.0F, 0.0F, 0.0F, new Vector3f());
      ANCHORS.clear();
   }

   /** Gewünschte Position des Symbols relativ zum Spieler-Raum (oder null = Voice Chat entscheidet selbst). */
   public static Vector3f take(int entityId) {
      return ANCHORS.remove(entityId);
   }

   /** Zeigt Voice Chat bei diesem Spieler gerade ein Symbol? (Sprechen, Flüstern, getrennt, stumm, Gruppe) */
   public static boolean predicts(UUID uuid) {
      return com.lennart.nebula.compat.VoiceChatCompat.hasIcon(uuid);
   }
}