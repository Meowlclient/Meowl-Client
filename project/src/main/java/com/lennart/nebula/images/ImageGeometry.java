package com.lennart.nebula.images;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

public record ImageGeometry(Vec3 bottomLeft, Vec3 right, Vec3 up, Vec3 normal, double w, double h) {
   public static ImageGeometry of(BlockPos pos, Direction facing, int w, int h) {
      Vec3 normal = Vec3.atLowerCornerOf(facing.getUnitVec3i());
      Vec3 right = Vec3.atLowerCornerOf(facing.getCounterClockWise().getUnitVec3i());
      Vec3 up = new Vec3(0.0, 1.0, 0.0);
      Vec3 faceCenter = Vec3.atCenterOf(pos).add(normal.scale(0.51));
      Vec3 bottomLeft = faceCenter.subtract(right.scale(0.5)).subtract(up.scale(0.5));
      return new ImageGeometry(bottomLeft, right, up, normal, w, h);
   }

   public Vec3 at(double u, double v) {
      return this.bottomLeft.add(this.right.scale(u)).add(this.up.scale(v));
   }

   public Vec3 center() {
      return this.at(this.w / 2.0, this.h / 2.0);
   }

   public double ray(Vec3 origin, Vec3 dir, double maxDist) {
      double denom = dir.dot(this.normal);
      if (denom >= -1.0E-6) {
         return -1.0;
      } else {
         double t = this.bottomLeft.subtract(origin).dot(this.normal) / denom;
         if (!(t < 0.0) && !(t > maxDist)) {
            Vec3 p = origin.add(dir.scale(t)).subtract(this.bottomLeft);
            double u = p.dot(this.right);
            double v = p.dot(this.up);
            return u >= 0.0 && u <= this.w && v >= 0.0 && v <= this.h ? t : -1.0;
         } else {
            return -1.0;
         }
      }
   }
}
