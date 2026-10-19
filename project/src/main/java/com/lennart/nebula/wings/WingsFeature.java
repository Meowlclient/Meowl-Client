package com.lennart.nebula.wings;

import com.lennart.nebula.module.mods.Wings;
import com.lennart.nebula.util.WorldRender;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** Zeichnet die Wings am Rücken (eigene + die anderer Meowl-Spieler): fester Teil und Leucht-Teil getrennt. */
public class WingsFeature extends RenderLayer<AvatarRenderState, PlayerModel> {
   public WingsFeature(RenderLayerParent<AvatarRenderState, PlayerModel> context) {
      super(context);
   }

   @Override
   public void submit(PoseStack matrices, SubmitNodeCollector queue, int light, AvatarRenderState state, float limbAngle, float limbDistance) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && mc.level != null && !state.isInvisible && com.lennart.nebula.module.mods.OtherClients.showMeowlCosmetics()) {
         // Pose dieses Spielers sicher setzen: andere Ebenen (z.B. anderer Mods) können das geteilte Modell verstellen,
         // dann säße Kopfschmuck beim Hoch-/Runterschauen nicht mehr auf dem Kopf
         this.getParentModel().setupAnim(state);
         Entity entity = mc.level.getEntity(state.id);
         if (entity instanceof Player player) {
            com.lennart.nebula.cosmetic.PreviewOverrides.Entry preview = com.lennart.nebula.cosmetic.PreviewOverrides.get(state);
            WingsModel.Spec spec = preview == null
               ? Wings.specFor(player)
               : (preview.target() == com.lennart.nebula.cosmetic.PreviewOverrides.WINGS ? Wings.previewSpec(preview.style()) : null);
            if (spec != null) {
               float speedMul = player == mc.player ? Wings.ownFlapSpeed() : 1.0F;
               WingsAnim anim = WingsAnim.of(player.getId());
               anim.update(state.x, state.y, state.z, state.bodyRot, player.onGround(), player.isFallFlying(), player.getAbilities().flying, player.isCrouching(), speedMul, spec.replaceElytra() && Wings.wearsElytra(player));
               WingsModel.Anim a = anim.anim();
               // Helligkeit aus dem Licht an der Stelle (Leuchtteile bleiben hell)
               int block = LightTexture.block(light);
               int sky = LightTexture.sky(light);
               float bright = 0.32F + 0.68F * Math.max(block, sky) / 15.0F;
               matrices.pushPose();
               this.getParentModel().body.translateAndRotate(matrices);
               queue.submitCustomGeometry(matrices, WorldRender.solidColor(), (entry, vc) -> {
                  PoseStack local = local(entry);
                  WingsModel.draw(local, vc, false, spec, a, bright);
               });
               if (spec.fx() > 0.01F) {
                  queue.submitCustomGeometry(matrices, WorldRender.glow(), (entry, vc) -> {
                     PoseStack local = local(entry);
                     WingsModel.draw(local, vc, true, spec, a, 1.0F);
                  });
               }

               matrices.popPose();
            }
         }
      }
   }

   private static PoseStack local(PoseStack.Pose entry) {
      PoseStack local = new PoseStack();
      local.last().pose().set(entry.pose());
      local.last().normal().set(entry.normal());
      local.scale(0.0625F, 0.0625F, 0.0625F);
      return local;
   }
}
