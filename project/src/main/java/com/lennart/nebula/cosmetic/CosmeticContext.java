package com.lennart.nebula.cosmetic;

import com.lennart.nebula.util.WorldRender;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.player.Player;

/**
 * Zeichen-Umgebung für Kosmetik. Gezeichnet wird in Pixel-Einheiten (1 = 1/16 Block), y nach unten:
 * <ul>
 *   <li>Grundraum: Ursprung 1,5 Blöcke über den Füßen (Füße bei y = 24), dreht sich mit dem Körper.</li>
 *   <li>{@link #onHead}: Kopfraum, Kopf von (-4,-8,-4) bis (4,0,4), Gesicht bei z = -4.</li>
 *   <li>{@link #onBody}: Körperraum, Rumpf von (-4,0,-2) bis (4,12,2), Rücken bei z = +2.</li>
 * </ul>
 */
public final class CosmeticContext {
   public final PoseStack matrices;
   public final SubmitNodeCollector queue;
   public final AvatarRenderState state;
   public final PlayerModel model;
   public final Player player;
   public final float bright;
   /** Zeit in Sekunden, je Spieler leicht versetzt. */
   public final float time;
   /** Geht der Spieler (0..1)? */
   public final float walk;
   /** Über der zweiten Skin-Ebene (Hut-Ebene, Jacke) statt direkt auf der Haut. */
   public boolean outer;

   public interface Painter {
      void paint(PoseStack m, VertexConsumer vc);
   }

   public CosmeticContext(
      PoseStack matrices, SubmitNodeCollector queue, AvatarRenderState state, PlayerModel model, Player player, float bright, float time
   ) {
      this.matrices = matrices;
      this.queue = queue;
      this.state = state;
      this.model = model;
      this.player = player;
      this.bright = bright;
      this.time = time;
      this.walk = Math.min(1.0F, state.walkAnimationSpeed * 1.6F);
   }

   private static PoseStack local(PoseStack.Pose entry) {
      PoseStack local = new PoseStack();
      local.last().pose().set(entry.pose());
      local.last().normal().set(entry.normal());
      local.scale(0.0625F, 0.0625F, 0.0625F);
      return local;
   }

   /** Feste Teile. */
   public void solid(CosmeticContext.Painter p) {
      this.queue.submitCustomGeometry(this.matrices, WorldRender.solidColor(), (entry, vc) -> p.paint(local(entry), vc));
   }

   /** Leuchtende Teile (additiv, Alpha = Stärke). */
   public void glow(CosmeticContext.Painter p) {
      this.queue.submitCustomGeometry(this.matrices, WorldRender.glow(), (entry, vc) -> p.paint(local(entry), vc));
   }

   public void onHead(Runnable r) {
      this.matrices.pushPose();
      // immer exakt die Pose des Kopfmodells nehmen: genau damit wird auch der echte Kopf gezeichnet
      // (inklusive Sitzen, Emotes und Animationen anderer Mods)
      this.model.root().translateAndRotate(this.matrices);
      this.model.head.translateAndRotate(this.matrices);

      if (this.outer) {
         // Hut-Ebene steht 0,5 px über dem 8-px-Kopf
         this.matrices.scale(1.125F, 1.125F, 1.125F);
      }

      r.run();
      this.matrices.popPose();
   }

   /**
    * Kopfraum ohne Nicken/Neigen: folgt der Kopfposition (Hocken, Emotes, Klein-Emote) und der Drehung nach links/rechts,
    * bleibt aber immer genau senkrecht über der Kopfmitte – für Heiligenschein, Stimmungs-Symbol usw.
    */
   public void aboveHead(Runnable r) {
      this.matrices.pushPose();
      this.model.root().translateAndRotate(this.matrices);
      net.minecraft.client.model.geom.ModelPart h = this.model.head;
      this.matrices.translate(h.x / 16.0F, h.y / 16.0F, h.z / 16.0F);
      // Kopfmitte (4 px über dem Hals) mit der echten Kopfdrehung, dann wieder 4 px senkrecht nach unten
      org.joml.Vector3f center = new org.joml.Vector3f(0.0F, -4.0F / 16.0F, 0.0F).rotate(new org.joml.Quaternionf().rotationZYX(h.zRot, h.yRot, h.xRot));
      this.matrices.translate(center.x, center.y + 4.0F / 16.0F, center.z);
      this.matrices.mulPose(com.mojang.math.Axis.YP.rotation(h.yRot));
      r.run();
      this.matrices.popPose();
   }

   /** Raum eines Modellteils (Arm: Schulter oben, Hand bei y = 10; Bein: Hüfte oben, Fuß bei y = 12). */
   public void onPart(net.minecraft.client.model.geom.ModelPart part, Runnable r) {
      this.matrices.pushPose();
      this.model.root().translateAndRotate(this.matrices);
      part.translateAndRotate(this.matrices);
      r.run();
      this.matrices.popPose();
   }

   public void onBody(Runnable r) {
      this.matrices.pushPose();
      this.model.root().translateAndRotate(this.matrices);
      this.model.body.translateAndRotate(this.matrices);
      if (this.outer) {
         // Jacke steht 0,25 px über dem Rumpf (8 x 12 x 4 px), etwas Luft dazu
         this.matrices.scale(1.09F, 1.0F, 1.18F);
      }

      r.run();
      this.matrices.popPose();
   }
}
