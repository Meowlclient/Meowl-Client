package com.lennart.nebula.duel;

import com.lennart.nebula.util.WorldRender;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

public final class SheriffModel {
   private static final int GUNMETAL = -13947080;
   private static final int STEEL = -10590606;
   private static final int STEEL_LIGHT = -7629663;
   private static final int GRIP = -14015449;
   private static final int ACCENT = -7643914;
   private static final int FLASH = -11654;

   private SheriffModel() {
   }

   public static void draw(PoseStack m, VertexConsumer vc, float light, float spin, boolean flash) {
      m.pushPose();
      m.mulPose(Axis.XP.rotationDegrees(-16.0F));
      WorldRender.box(m, vc, -0.9F, -5.6F, -0.2F, 0.9F, 0.0F, 2.1F, -14015449, light);
      WorldRender.box(m, vc, 0.9F, -4.4F, 0.3F, 0.98F, -1.2F, 1.6F, -7643914, light);
      WorldRender.box(m, vc, -0.98F, -4.4F, 0.3F, -0.9F, -1.2F, 1.6F, -7643914, light);
      WorldRender.box(m, vc, -0.95F, -6.0F, -0.3F, 0.95F, -5.6F, 2.2F, -10590606, light);
      m.popPose();
      WorldRender.box(m, vc, -0.8F, 0.0F, -3.6F, 0.8F, 2.4F, 1.3F, -13947080, light);
      WorldRender.box(m, vc, -0.3F, -1.7F, -2.8F, 0.3F, -1.3F, -0.4F, -13947080, light);
      WorldRender.box(m, vc, -0.3F, -1.7F, -3.0F, 0.3F, 0.0F, -2.6F, -13947080, light);
      WorldRender.box(m, vc, -0.18F, -1.2F, -1.7F, 0.18F, 0.0F, -1.3F, -7629663, light);
      WorldRender.box(m, vc, -0.35F, 2.1F, 0.5F, 0.35F, 3.3F, 1.4F, -10590606, light);
      m.pushPose();
      m.translate(0.0F, 1.5F, 0.0F);
      m.mulPose(Axis.ZP.rotationDegrees(spin));
      WorldRender.box(m, vc, -1.3F, -1.3F, -3.4F, 1.3F, 1.3F, -0.6F, -10590606, light);

      for (int i = 0; i < 6; i++) {
         m.pushPose();
         m.mulPose(Axis.ZP.rotationDegrees(i * 60.0F));
         WorldRender.box(m, vc, -0.25F, 1.25F, -3.2F, 0.25F, 1.38F, -0.8F, -13947080, light);
         m.popPose();
      }

      m.popPose();
      WorldRender.box(m, vc, -0.55F, 1.0F, -11.0F, 0.55F, 2.1F, -3.4F, -13947080, light);
      WorldRender.box(m, vc, -0.3F, 2.1F, -11.0F, 0.3F, 2.5F, -3.4F, -10590606, light);
      WorldRender.box(m, vc, -0.5F, 0.4F, -8.0F, 0.5F, 1.0F, -3.4F, -13947080, light);
      WorldRender.box(m, vc, -0.15F, 2.5F, -10.8F, 0.15F, 3.1F, -10.2F, -7629663, light);
      WorldRender.box(m, vc, -0.56F, 1.35F, -11.05F, 0.56F, 1.75F, -10.95F, -16053233, light);
      if (flash) {
         WorldRender.box(m, vc, -0.9F, 0.7F, -13.0F, 0.9F, 2.4F, -11.1F, -11654, 1.0F);
         WorldRender.box(m, vc, -0.4F, 1.2F, -14.2F, 0.4F, 1.9F, -13.0F, -2871, 1.0F);
      }
   }
}
