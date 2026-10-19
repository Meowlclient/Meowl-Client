package com.lennart.nebula.util;

import com.mojang.blaze3d.vertex.PoseStack;
import com.lennart.nebula.util.WorldCtx;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class NametagRender {
   private NametagRender() {
   }

   public static PoseStack begin(WorldCtx context, Player player, float tickDelta) {
      return begin(context, player, tickDelta, 0.0, 1.0F);
   }

   public static PoseStack begin(WorldCtx context, Player player, float tickDelta, double extraY, float scale) {
      CameraRenderState camera = context.camera();
      Vec3 cam = camera.pos;
      Vec3 pos = player.getPosition(tickDelta);
      Vec3 label = player.getAttachments().getNullable(EntityAttachment.NAME_TAG, 0, player.getYRot(tickDelta));
      double ly = label != null ? label.y : player.getBbHeight();
      PoseStack m = context.matrices();
      m.pushPose();
      m.translate(pos.x - cam.x, pos.y + ly + 0.5 + extraY - cam.y, pos.z - cam.z);
      m.mulPose(camera.orientation);
      float s = 0.025F * scale;
      m.scale(s, -s, s);
      return m;
   }
}
