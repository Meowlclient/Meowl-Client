package com.lennart.nebula.duel;

import com.lennart.nebula.util.WorldRender;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import com.lennart.nebula.util.WorldCtx;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public final class DuelRenderer {
   private static final long TRACER_MS = 350L;
   private final List<DuelRenderer.Tracer> tracers = new ArrayList<>();

   void addTracer(Vec3 from, Vec3 to, int color) {
      this.tracers.add(new DuelRenderer.Tracer(from, to, color, System.currentTimeMillis()));
      if (this.tracers.size() > 32) {
         this.tracers.remove(0);
      }
   }

   void clear() {
      this.tracers.clear();
   }

   public void render(WorldCtx ctx, DuelSession session, DuelWeapon weapon, int bubbleColor, boolean grid, float tickDelta) {
      Minecraft mc = Minecraft.getInstance();
      Vec3 cam = ctx.cameraPos();
      PoseStack m = ctx.matrices();
      DuelArena arena = session.arena;
      m.pushPose();
      m.translate(-cam.x, -cam.y, -cam.z);
      VertexConsumer solid = ctx.buffer(WorldRender.solidColor());

      for (AABB box : arena.obstacles()) {
         drawStoneBox(m, solid, box);
      }

      m.popPose();

      for (Player p : mc.level.players()) {
         boolean self = p == mc.player;
         if ((!self || !mc.options.getCameraType().isFirstPerson()) && (self || p.getUUID().equals(session.opponent))) {
            drawHeldGun(ctx, p, tickDelta, cam, self ? weapon : null);
         }
      }

      VertexConsumer translucent = ctx.buffer(WorldRender.translucentColor());
      m.pushPose();
      m.translate(-cam.x, -cam.y, -cam.z);
      drawBubble(m, translucent, arena, bubbleColor, grid);
      long now = System.currentTimeMillis();
      Iterator<DuelRenderer.Tracer> it = this.tracers.iterator();

      while (it.hasNext()) {
         DuelRenderer.Tracer t = it.next();
         float age = (float)(now - t.time) / 350.0F;
         if (age >= 1.0F) {
            it.remove();
         } else {
            int a = Math.round((t.color >>> 24 & 0xFF) * (1.0F - age));
            drawBeam(m.last().pose(), translucent, t.from, t.to, cam, 0.025F, a << 24 | t.color & 16777215);
            drawImpact(m.last().pose(), translucent, t.to, 0.08F * (1.0F - age), a << 24 | 16771488);
         }
      }

      m.popPose();
   }

   private static void drawStoneBox(PoseStack m, VertexConsumer vc, AABB box) {
      int x0 = Mth.floor(box.minX);
      int y0 = Mth.floor(box.minY);
      int z0 = Mth.floor(box.minZ);
      int x1 = Mth.ceil(box.maxX);
      int y1 = Mth.ceil(box.maxY);
      int z1 = Mth.ceil(box.maxZ);
      double yOff = box.minY - y0;

      for (int x = x0; x < x1; x++) {
         for (int y = y0; y < y1; y++) {
            for (int z = z0; z < z1; z++) {
               int h = x * 73856093 ^ y * 19349663 ^ z * 83492791;
               int shade = 112 + Math.floorMod(h, 24);
               int color = 0xFF000000 | shade << 16 | shade << 8 | shade + 4;
               WorldRender.box(m, vc, x, (float)(y + yOff), z, x + 1, (float)(y + 1 + yOff), z + 1, color, 1.0F);
            }
         }
      }
   }

   private static void drawBubble(PoseStack m, VertexConsumer vc, DuelArena arena, int color, boolean grid) {
      Matrix4f mat = m.last().pose();
      double r = arena.radius();
      double cx = arena.center().x;
      double cy = arena.floorY();
      double cz = arena.center().z;
      int seg = 40;
      int rings = 12;
      float time = (float)(System.currentTimeMillis() % 100000L) / 1000.0F;
      int baseA = color >>> 24 & 0xFF;

      for (int k = 0; k < rings; k++) {
         double la0 = k * (Math.PI / 2) / rings;
         double la1 = (k + 1) * (Math.PI / 2) / rings;
         float shimmer = 0.6F + 0.4F * Mth.sin(time * 1.6F - k * 0.5F);
         int a = Math.round(baseA * 0.35F * shimmer);
         int c = a << 24 | color & 16777215;

         for (int i = 0; i < seg; i++) {
            double lo0 = i * 2 * Math.PI / seg;
            double lo1 = (i + 1) * 2 * Math.PI / seg;
            vertexS(vc, mat, cx, cy, cz, r, la0, lo0, c);
            vertexS(vc, mat, cx, cy, cz, r, la0, lo1, c);
            vertexS(vc, mat, cx, cy, cz, r, la1, lo1, c);
            vertexS(vc, mat, cx, cy, cz, r, la1, lo0, c);
         }
      }

      int line = Math.min(255, baseA + 60) << 24 | color & 16777215;
      if (grid) {
         double t = 0.06 / r;

         for (int i = 0; i < seg; i += 4) {
            double lo = i * 2 * Math.PI / seg;

            for (int k = 0; k < rings; k++) {
               double la0 = k * (Math.PI / 2) / rings;
               double la1 = (k + 1) * (Math.PI / 2) / rings;
               vertexS(vc, mat, cx, cy, cz, r, la0, lo - t, line);
               vertexS(vc, mat, cx, cy, cz, r, la0, lo + t, line);
               vertexS(vc, mat, cx, cy, cz, r, la1, lo + t, line);
               vertexS(vc, mat, cx, cy, cz, r, la1, lo - t, line);
            }
         }

         for (int k = 1; k < rings; k += 3) {
            double la = k * (Math.PI / 2) / rings;

            for (int i = 0; i < seg; i++) {
               double lo0 = i * 2 * Math.PI / seg;
               double lo1 = (i + 1) * 2 * Math.PI / seg;
               vertexS(vc, mat, cx, cy, cz, r, la - t, lo0, line);
               vertexS(vc, mat, cx, cy, cz, r, la - t, lo1, line);
               vertexS(vc, mat, cx, cy, cz, r, la + t, lo1, line);
               vertexS(vc, mat, cx, cy, cz, r, la + t, lo0, line);
            }
         }
      }

      for (int i = 0; i < seg * 2; i++) {
         double a0 = i * Math.PI / seg;
         double a1 = (i + 1) * Math.PI / seg;
         float y = (float)(cy + 0.03);
         vc.addVertex(mat, (float)(cx + Math.cos(a0) * (r - 0.15)), y, (float)(cz + Math.sin(a0) * (r - 0.15))).setColor(line);
         vc.addVertex(mat, (float)(cx + Math.cos(a1) * (r - 0.15)), y, (float)(cz + Math.sin(a1) * (r - 0.15))).setColor(line);
         vc.addVertex(mat, (float)(cx + Math.cos(a1) * r), y, (float)(cz + Math.sin(a1) * r)).setColor(line);
         vc.addVertex(mat, (float)(cx + Math.cos(a0) * r), y, (float)(cz + Math.sin(a0) * r)).setColor(line);
      }
   }

   private static void vertexS(VertexConsumer vc, Matrix4f mat, double cx, double cy, double cz, double r, double lat, double lon, int color) {
      double cl = Math.cos(lat);
      vc.addVertex(mat, (float)(cx + Math.cos(lon) * cl * r), (float)(cy + Math.sin(lat) * r), (float)(cz + Math.sin(lon) * cl * r)).setColor(color);
   }

   private static void drawBeam(Matrix4f mat, VertexConsumer vc, Vec3 a, Vec3 b, Vec3 cam, float width, int color) {
      Vec3 dir = b.subtract(a);
      Vec3 toCam = cam.subtract(a.add(b).scale(0.5));
      Vec3 side = dir.cross(toCam);
      if (!(side.lengthSqr() < 1.0E-8)) {
         side = side.normalize().scale(width);
         vc.addVertex(mat, (float)(a.x - side.x), (float)(a.y - side.y), (float)(a.z - side.z)).setColor(color);
         vc.addVertex(mat, (float)(a.x + side.x), (float)(a.y + side.y), (float)(a.z + side.z)).setColor(color);
         vc.addVertex(mat, (float)(b.x + side.x), (float)(b.y + side.y), (float)(b.z + side.z)).setColor(color);
         vc.addVertex(mat, (float)(b.x - side.x), (float)(b.y - side.y), (float)(b.z - side.z)).setColor(color);
      }
   }

   private static void drawImpact(Matrix4f mat, VertexConsumer vc, Vec3 p, float s, int color) {
      float x0 = (float)p.x - s;
      float x1 = (float)p.x + s;
      float y0 = (float)p.y - s;
      float y1 = (float)p.y + s;
      float z0 = (float)p.z - s;
      float z1 = (float)p.z + s;
      float[][] faces = new float[][]{
         {x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0},
         {x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1},
         {x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0},
         {x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0},
         {x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1},
         {x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1}
      };

      for (float[] f : faces) {
         for (int i = 0; i < 12; i += 3) {
            vc.addVertex(mat, f[i], f[i + 1], f[i + 2]).setColor(color);
         }
      }
   }

   private static void drawHeldGun(WorldCtx ctx, Player p, float tickDelta, Vec3 cam, DuelWeapon weapon) {
      Vec3 eye = p.getEyePosition(tickDelta);
      float yaw = Mth.rotLerp(tickDelta, p.yHeadRotO, p.yHeadRot);
      float pitch = p.getViewXRot(tickDelta);
      PoseStack m = ctx.matrices();
      m.pushPose();
      m.translate(eye.x - cam.x, eye.y - cam.y, eye.z - cam.z);
      m.rotate(Axis.YP.rotationDegrees(-yaw));
      m.rotate(Axis.XP.rotationDegrees(pitch));
      m.translate(-0.33F, -0.3F, 0.5F);
      m.rotate(Axis.YP.rotationDegrees(180.0F));
      if (weapon != null) {
         m.rotate(Axis.XP.rotationDegrees(weapon.recoil() * 25.0F));
      }

      float s = 0.053125F;
      m.scale(s, s, s);
      VertexConsumer vc = ctx.buffer(WorldRender.solidColor());
      SheriffModel.draw(m, vc, 0.9F, 0.0F, weapon != null && weapon.muzzleFlash());
      m.popPose();
   }

   private record Tracer(Vec3 from, Vec3 to, int color, long time) {
   }
}
