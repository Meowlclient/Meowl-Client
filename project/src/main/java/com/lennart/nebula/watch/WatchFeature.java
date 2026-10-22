package com.lennart.nebula.watch;

import com.lennart.nebula.module.mods.Watches;
import com.lennart.nebula.util.MeowlNetwork;
import com.lennart.nebula.util.WorldRender;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.function.BiConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelType;

public class WatchFeature extends RenderLayer<AvatarRenderState, PlayerModel> {
   public WatchFeature(RenderLayerParent<AvatarRenderState, PlayerModel> context) {
      super(context);
   }

   @Override
   public void submit(PoseStack matrices, SubmitNodeCollector queue, int light, AvatarRenderState state, float limbAngle, float limbDistance) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && mc.level != null && !state.isInvisible && com.lennart.nebula.module.mods.OtherClients.showMeowlCosmetics()) {
         // Pose dieses Spielers sicher setzen: andere Ebenen (z.B. anderer Mods) können das geteilte Modell verstellen,
         // dann säße Kopfschmuck beim Hoch-/Runterschauen nicht mehr auf dem Kopf
         this.getParentModel().setupAnim(state);
         boolean slim = state.skin != null && state.skin.model() == PlayerModelType.SLIM;
         // Shop-Vorschau: genau dieses Uhrmodell am rechten Arm
         com.lennart.nebula.cosmetic.PreviewOverrides.Entry pe = com.lennart.nebula.cosmetic.PreviewOverrides.get(state);
         if (pe != null && pe.target() instanceof Watches && pe.style() != null) {
            MeowlNetwork.Watch watch = Watches.previewWatch(pe.style());
            this.submit(matrices, queue, true, (local, vc) -> Watches.drawOther(local, vc, watch, true, slim));
            return;
         }

         if (state.id == mc.player.getId()) {
            Watches w = Watches.active();
            if (w != null && w.thirdPerson()) {
               boolean right = w.watchArm(mc.player) == HumanoidArm.RIGHT;
               boolean empty = right ? state.rightHandItemState.isEmpty() : state.leftHandItemState.isEmpty();
               if (empty || !w.onlyEmptyHand()) {
                  this.submit(matrices, queue, right, (local, vc) -> w.drawWatch(local, vc, right, slim, 0.9F));
               }
            }
         } else if (mc.level.getEntity(state.id) instanceof Player other) {
            MeowlNetwork.Watch watch = Watches.othersWatch(other);
            if (watch != null) {
               HumanoidArm arm = watch.offArm() ? other.getMainArm().getOpposite() : other.getMainArm();
               boolean right = arm == HumanoidArm.RIGHT;
               this.submit(matrices, queue, right, (local, vc) -> Watches.drawOther(local, vc, watch, right, slim));
            }
         }
      }
   }

   private void submit(PoseStack matrices, SubmitNodeCollector queue, boolean right, BiConsumer<PoseStack, VertexConsumer> draw) {
      ModelPart part = right ? this.getParentModel().rightArm : this.getParentModel().leftArm;
      matrices.pushPose();
      part.translateAndRotate(matrices);
      queue.submitCustomGeometry(matrices, WorldRender.solidColor(), com.lennart.nebula.util.Perf.timed(com.lennart.nebula.module.mods.Watches.class, (entry, vc) -> {
         PoseStack local = new PoseStack();
         local.last().pose().set(entry.pose());
         local.last().normal().set(entry.normal());
         draw.accept(local, vc);
      }));
      matrices.popPose();
   }
}
