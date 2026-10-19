package com.lennart.nebula.images;

import com.lennart.nebula.util.WorldRender;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.lennart.nebula.util.WorldCtx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public final class ImageRenderer {
   private final ImageCache cache;

   public ImageRenderer(ImageCache cache) {
      this.cache = cache;
   }

   public void render(WorldCtx ctx, Iterable<PlacedImage> images, String dim, double maxDistance, boolean keepAspect) {
      Minecraft mc = Minecraft.getInstance();
      Vec3 cam = ctx.cameraPos();
      PoseStack m = ctx.matrices();

      for (PlacedImage image : images) {
         if (image.dim().equals(dim)) {
            ImageGeometry g = ImageGeometry.of(image.pos(), image.facing(), image.w(), image.h());
            if (!(g.center().distanceToSqr(cam) > maxDistance * maxDistance)) {
               ImageCache.Entry entry = this.cache.get(image.url());
               int light = com.lennart.nebula.compat.Compat.lightAt(mc.level, BlockPos.containing(g.center().add(g.normal().scale(0.5))));
               m.pushPose();
               m.translate(-cam.x, -cam.y, -cam.z);
               if (entry.state() == ImageCache.State.READY && entry.texture() != null) {
                  double u0 = 0.0;
                  double v0 = 0.0;
                  double u1 = g.w();
                  double v1 = g.h();
                  if (keepAspect && entry.height() > 0) {
                     double ia = (double)entry.width() / entry.height();
                     double a = g.w() / g.h();
                     if (ia > a) {
                        double hh = g.w() / ia;
                        v0 = (g.h() - hh) / 2.0;
                        v1 = v0 + hh;
                     } else {
                        double ww = g.h() * ia;
                        u0 = (g.w() - ww) / 2.0;
                        u1 = u0 + ww;
                     }
                  }

                  VertexConsumer vc = ctx.buffer(RenderTypes.text(entry.texture()));
                  texturedQuad(m, vc, g, u0, v0, u1, v1, light);
               } else {
                  int color = entry.state() == ImageCache.State.FAILED ? -2131803068 : 1351310582;
                  VertexConsumer vc = ctx.buffer(WorldRender.translucentColor());
                  colorQuad(m, vc, g, color);
               }

               m.popPose();
            }
         }
      }
   }

   public void renderPreview(WorldCtx ctx, ImageGeometry g, boolean valid) {
      Vec3 cam = ctx.cameraPos();
      PoseStack m = ctx.matrices();
      m.pushPose();
      m.translate(-cam.x, -cam.y, -cam.z);
      VertexConsumer vc = ctx.buffer(WorldRender.translucentColor());
      colorQuad(m, vc, g, valid ? 1619746038 : 1626293316);
      m.popPose();
   }

   private static void texturedQuad(PoseStack m, VertexConsumer vc, ImageGeometry g, double u0, double v0, double u1, double v1, int light) {
      Matrix4f mat = m.last().pose();
      Vec3 bl = g.at(u0, v0);
      Vec3 br = g.at(u1, v0);
      Vec3 tr = g.at(u1, v1);
      Vec3 tl = g.at(u0, v1);
      vertex(vc, mat, bl, 0.0F, 1.0F, light);
      vertex(vc, mat, br, 1.0F, 1.0F, light);
      vertex(vc, mat, tr, 1.0F, 0.0F, light);
      vertex(vc, mat, tl, 0.0F, 0.0F, light);
      vertex(vc, mat, tl, 0.0F, 0.0F, light);
      vertex(vc, mat, tr, 1.0F, 0.0F, light);
      vertex(vc, mat, br, 1.0F, 1.0F, light);
      vertex(vc, mat, bl, 0.0F, 1.0F, light);
   }

   private static void vertex(VertexConsumer vc, Matrix4f mat, Vec3 p, float u, float v, int light) {
      vc.addVertex(mat, (float)p.x, (float)p.y, (float)p.z).setColor(-1).setUv(u, v).setLight(light);
   }

   private static void colorQuad(PoseStack m, VertexConsumer vc, ImageGeometry g, int color) {
      Matrix4f mat = m.last().pose();
      Vec3 bl = g.at(0.0, 0.0);
      Vec3 br = g.at(g.w(), 0.0);
      Vec3 tr = g.at(g.w(), g.h());
      Vec3 tl = g.at(0.0, g.h());

      for (Vec3 p : new Vec3[]{bl, br, tr, tl}) {
         vc.addVertex(mat, (float)p.x, (float)p.y, (float)p.z).setColor(color);
      }
   }
}
