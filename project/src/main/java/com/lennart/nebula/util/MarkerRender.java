package com.lennart.nebula.util;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Locale;
import com.lennart.nebula.util.WorldCtx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public final class MarkerRender {
   private static final double NEAR_LIMIT = 96.0;

   private MarkerRender() {
   }

   private static double pull(double dist) {
      return dist > 96.0 ? 96.0 / dist : 1.0;
   }

   public static void beam(WorldCtx ctx, Vec3 pos, int rgb, float alpha, float halfWidth) {
      Vec3 cam = ctx.cameraPos();
      double dx = pos.x - cam.x;
      double dy = pos.y - cam.y;
      double dz = pos.z - cam.z;
      double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
      double k = pull(dist);
      float grow = (float)Math.max(1.0, dist / 40.0);
      PoseStack m = ctx.matrices();
      m.pushPose();
      m.translate(dx * k, dy * k, dz * k);
      m.scale((float)k, (float)k, (float)k);
      Matrix4f mat = m.last().pose();
      float rx = (float)(-dz);
      float rz = (float)dx;
      float len = (float)Math.sqrt(rx * rx + rz * rz);
      if (len < 1.0E-4F) {
         rx = 1.0F;
         rz = 0.0F;
         len = 1.0F;
      }

      rx /= len;
      rz /= len;
      float height = (float)Math.max(64.0, 330.0 - pos.y);
      VertexConsumer vc = ctx.buffer(WorldRender.glow());
      quad(vc, mat, rx, rz, halfWidth * 0.35F * grow, height, rgb, alpha, 0.55F);
      quad(vc, mat, rx, rz, halfWidth * grow, height, rgb, alpha * 0.55F, 0.0F);
      quad(vc, mat, rx, rz, halfWidth * 2.4F * grow, height * 0.5F, rgb, alpha * 0.18F, 0.0F);
      m.popPose();
   }

   private static void quad(VertexConsumer vc, Matrix4f mat, float rx, float rz, float halfW, float h, int rgb, float alpha, float whiten) {
      int bottom = color(rgb, alpha, whiten);
      int top = color(rgb, 0.0F, whiten);
      vc.addVertex(mat, -rx * halfW, 0.0F, -rz * halfW).setColor(bottom);
      vc.addVertex(mat, rx * halfW, 0.0F, rz * halfW).setColor(bottom);
      vc.addVertex(mat, rx * halfW, h, rz * halfW).setColor(top);
      vc.addVertex(mat, -rx * halfW, h, -rz * halfW).setColor(top);
   }

   public static int color(int rgb, float alpha, float whiten) {
      int r = rgb >> 16 & 0xFF;
      int g = rgb >> 8 & 0xFF;
      int b = rgb & 0xFF;
      r = Math.round(r + (255 - r) * whiten);
      g = Math.round(g + (255 - g) * whiten);
      b = Math.round(b + (255 - b) * whiten);
      int a = Math.round(Math.max(0.0F, Math.min(1.0F, alpha)) * 255.0F);
      return a << 24 | r << 16 | g << 8 | b;
   }

   public static void label(WorldCtx ctx, Vec3 pos, int argb, float size, String... lines) {
      Minecraft mc = Minecraft.getInstance();
      Font tr = mc.font;
      Vec3 cam = ctx.cameraPos();
      double dx = pos.x - cam.x;
      double dy = pos.y - cam.y;
      double dz = pos.z - cam.z;
      double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
      double k = pull(dist);
      float scale = (float)(0.0045 * Math.max(5.0, dist * k)) * size;
      PoseStack m = ctx.matrices();
      m.pushPose();
      m.translate(dx * k, dy * k, dz * k);
      m.mulPose(ctx.cameraOrientation());
      m.scale(scale, -scale, scale);
      Matrix4f mat = m.last().pose();
      float y = -lines.length * 10.0F;

      for (int i = 0; i < lines.length; i++) {
         String line = lines[i];
         float x = -tr.width(line) / 2.0F;
         int color = i == 0 ? argb : -3551784;
         ctx.text(tr, line, x, y, color, false, mat, false, 1610612736, 15728880);
         y += 10.0F;
      }

      m.popPose();
   }

   public static void diamond(WorldCtx ctx, Vec3 pos, int rgb, float alpha, float size) {
      Vec3 cam = ctx.cameraPos();
      double dx = pos.x - cam.x;
      double dy = pos.y - cam.y;
      double dz = pos.z - cam.z;
      double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
      double k = pull(dist);
      float s = size * (float)Math.max(1.0, dist * k / 16.0);
      PoseStack m = ctx.matrices();
      m.pushPose();
      m.translate(dx * k, dy * k, dz * k);
      m.mulPose(ctx.cameraOrientation());
      Matrix4f mat = m.last().pose();
      VertexConsumer vc = ctx.buffer(WorldRender.glow());
      int c = color(rgb, alpha, 0.2F);
      int edge = color(rgb, alpha * 0.4F, 0.0F);
      vc.addVertex(mat, 0.0F, s, 0.0F).setColor(c);
      vc.addVertex(mat, -s * 0.6F, 0.0F, 0.0F).setColor(edge);
      vc.addVertex(mat, 0.0F, -s, 0.0F).setColor(c);
      vc.addVertex(mat, s * 0.6F, 0.0F, 0.0F).setColor(edge);
      m.popPose();
   }

   public static String distance(double blocks) {
      return blocks >= 1000.0 ? String.format(Locale.ROOT, "%.1f km", blocks / 1000.0) : Math.round(blocks) + " m";
   }
}
