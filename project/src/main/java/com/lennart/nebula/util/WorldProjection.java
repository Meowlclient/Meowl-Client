package com.lennart.nebula.util;

import net.minecraft.world.phys.Vec3;

public final class WorldProjection {
   private final Vec3 cam;
   private final double fx;
   private final double fy;
   private final double fz;
   private final double rx;
   private final double rz;
   private final double ux;
   private final double uy;
   private final double uz;
   private final double tanHalfFov;
   private final double aspect;
   private final int width;
   private final int height;

   public WorldProjection(Vec3 cam, float yawDeg, float pitchDeg, int fovDeg, int width, int height) {
      this.cam = cam;
      double yaw = Math.toRadians(yawDeg);
      double pitch = Math.toRadians(pitchDeg);
      this.fx = -Math.sin(yaw) * Math.cos(pitch);
      this.fy = -Math.sin(pitch);
      this.fz = Math.cos(yaw) * Math.cos(pitch);
      this.rx = -Math.cos(yaw);
      this.rz = -Math.sin(yaw);
      this.ux = 0.0 * this.fz - this.rz * this.fy;
      this.uy = this.rz * this.fx - this.rx * this.fz;
      this.uz = this.rx * this.fy - 0.0 * this.fx;
      this.tanHalfFov = Math.tan(Math.toRadians(fovDeg) / 2.0);
      this.width = width;
      this.height = height;
      this.aspect = (double)width / Math.max(1, height);
   }

   public float[] project(double x, double y, double z) {
      double dx = x - this.cam.x;
      double dy = y - this.cam.y;
      double dz = z - this.cam.z;
      double depth = dx * this.fx + dy * this.fy + dz * this.fz;
      if (depth < 0.05) {
         return null;
      } else {
         double right = dx * this.rx + dz * this.rz;
         double up = dx * this.ux + dy * this.uy + dz * this.uz;
         double ndcX = right / (depth * this.tanHalfFov * this.aspect);
         double ndcY = up / (depth * this.tanHalfFov);
         float sx = (float)((ndcX + 1.0) * 0.5 * this.width);
         float sy = (float)((1.0 - ndcY) * 0.5 * this.height);
         return new float[]{sx, sy};
      }
   }
}
