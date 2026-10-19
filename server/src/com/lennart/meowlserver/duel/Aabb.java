package com.lennart.meowlserver.duel;

import java.util.List;

public record Aabb(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
   public double ray(Vec3 origin, Vec3 dir, double maxT) {
      double tMin = 0.0;
      double tMax = maxT;
      double[] o = origin.toArray();
      double[] d = dir.toArray();
      double[] lo = new double[]{this.minX, this.minY, this.minZ};
      double[] hi = new double[]{this.maxX, this.maxY, this.maxZ};

      for (int a = 0; a < 3; a++) {
         if (Math.abs(d[a]) < 1.0E-12) {
            if (o[a] < lo[a] || o[a] > hi[a]) {
               return -1.0;
            }
         } else {
            double t1 = (lo[a] - o[a]) / d[a];
            double t2 = (hi[a] - o[a]) / d[a];
            if (t1 > t2) {
               double tmp = t1;
               t1 = t2;
               t2 = tmp;
            }

            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);
            if (tMin > tMax) {
               return -1.0;
            }
         }
      }

      return tMin;
   }

   public boolean intersects(Aabb o) {
      return this.minX < o.maxX && this.maxX > o.minX && this.minY < o.maxY && this.maxY > o.minY && this.minZ < o.maxZ && this.maxZ > o.minZ;
   }

   public Aabb inflate(double d) {
      return new Aabb(this.minX - d, this.minY - d, this.minZ - d, this.maxX + d, this.maxY + d, this.maxZ + d);
   }

   public List<Double> toList() {
      return List.of(this.minX, this.minY, this.minZ, this.maxX, this.maxY, this.maxZ);
   }
}
