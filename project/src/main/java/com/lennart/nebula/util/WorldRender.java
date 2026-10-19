package com.lennart.nebula.util;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;

public final class WorldRender {
   private static RenderType solidColor;
   private static RenderType glow;

   private WorldRender() {
   }

   public static RenderType solidColor() {
      if (solidColor == null) {
         RenderPipeline pipeline = com.lennart.nebula.compat.Pipelines.solid(Identifier.fromNamespaceAndPath("nebula", "pipeline/solid_color"));
         solidColor = RenderType.create("nebula_solid_color", RenderSetup.builder(pipeline).createRenderSetup());
      }

      return solidColor;
   }

   public static RenderType glow() {
      if (glow == null) {
         glow = RenderType.create(
            "nebula_glow",
            RenderSetup.builder(
                  com.lennart.nebula.compat.Pipelines.glow(Identifier.fromNamespaceAndPath("nebula", "pipeline/glow"))
               )
               .sortOnUpload()
               .createRenderSetup()
         );
      }

      return glow;
   }

   public static RenderType translucentColor() {
      return RenderTypes.debugQuads();
   }

   public static void box(PoseStack matrices, VertexConsumer vc, float x1, float y1, float z1, float x2, float y2, float z2, int argb, float brightness) {
      Matrix4f m = matrices.last().pose();
      int up = shade(argb, brightness);
      int down = shade(argb, brightness * 0.5F);
      int ns = shade(argb, brightness * 0.8F);
      int ew = shade(argb, brightness * 0.62F);
      quad(vc, m, up, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1);
      quad(vc, m, down, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2);
      quad(vc, m, ns, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1);
      quad(vc, m, ns, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2);
      quad(vc, m, ew, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1);
      quad(vc, m, ew, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2);
   }

   private static void quad(
      VertexConsumer vc,
      Matrix4f m,
      int color,
      float ax,
      float ay,
      float az,
      float bx,
      float by,
      float bz,
      float cx,
      float cy,
      float cz,
      float dx,
      float dy,
      float dz
   ) {
      vc.addVertex(m, ax, ay, az).setColor(color);
      vc.addVertex(m, bx, by, bz).setColor(color);
      vc.addVertex(m, cx, cy, cz).setColor(color);
      vc.addVertex(m, dx, dy, dz).setColor(color);
   }

   public static void flatQuad(PoseStack matrices, VertexConsumer vc, float x1, float y, float z1, float x2, float z2, int argb) {
      Matrix4f m = matrices.last().pose();
      quad(vc, m, argb, x1, y, z1, x1, y, z2, x2, y, z2, x2, y, z1);
   }

   public static void rect(PoseStack matrices, VertexConsumer vc, float x1, float y1, float x2, float y2, int argb) {
      Matrix4f m = matrices.last().pose();
      quad(vc, m, argb, x1, y1, 0.0F, x1, y2, 0.0F, x2, y2, 0.0F, x2, y1, 0.0F);
   }

   public static int shade(int argb, float factor) {
      factor = Math.max(0.0F, Math.min(1.0F, factor));
      int a = argb >>> 24 & 0xFF;
      int r = Math.round((argb >> 16 & 0xFF) * factor);
      int g = Math.round((argb >> 8 & 0xFF) * factor);
      int b = Math.round((argb & 0xFF) * factor);
      return a << 24 | r << 16 | g << 8 | b;
   }

   public static TextureAtlasSprite guiSprite(Identifier id) {
      return com.lennart.nebula.compat.Pipelines.guiSprite(id);
   }

   public static RenderType guiSpriteLayer() {
      return RenderTypes.text(Sheets.GUI_SHEET);
   }

   public static void sprite(PoseStack matrices, VertexConsumer vc, TextureAtlasSprite sprite, float x, float y, float w, float h, int argb) {
      Matrix4f m = matrices.last().pose();
      int light = 15728880;
      float u1 = sprite.getU0();
      float u2 = sprite.getU1();
      float v1 = sprite.getV0();
      float v2 = sprite.getV1();
      vc.addVertex(m, x, y, 0.0F).setColor(argb).setUv(u1, v1).setLight(light);
      vc.addVertex(m, x, y + h, 0.0F).setColor(argb).setUv(u1, v2).setLight(light);
      vc.addVertex(m, x + w, y + h, 0.0F).setColor(argb).setUv(u2, v2).setLight(light);
      vc.addVertex(m, x + w, y, 0.0F).setColor(argb).setUv(u2, v1).setLight(light);
   }
}
