package com.lennart.meowlserver.duel;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class DuelArena {
   private final Vec3 center;
   private final double floorY;
   private final double radius;
   private final List<Aabb> obstacles;

   public DuelArena(Vec3 center, double floorY, double radius, List<Aabb> obstacles) {
      this.center = center;
      this.floorY = floorY;
      this.radius = radius;
      this.obstacles = List.copyOf(obstacles);
   }

   public Vec3 center() {
      return this.center;
   }

   public double floorY() {
      return this.floorY;
   }

   public double radius() {
      return this.radius;
   }

   public List<Aabb> obstacles() {
      return this.obstacles;
   }

   public static DuelArena generate(Vec3 a, Vec3 b, double radius, long seed) {
      Random random = new Random(seed);
      Vec3 center = new Vec3((a.x() + b.x()) / 2.0, Math.min(a.y(), b.y()), (a.z() + b.z()) / 2.0);
      double floorY = Math.floor(Math.min(a.y(), b.y()) + 0.01);
      int cx = (int)Math.floor(center.x());
      int cz = (int)Math.floor(center.z());
      int wanted = (int)Math.max(8.0, Math.min(24.0, radius * 0.9));
      List<Aabb> result = new ArrayList<>();

      for (int attempt = 0; attempt < 600 && result.size() < wanted; attempt++) {
         double angle = random.nextDouble() * Math.PI * 2.0;
         double dist = Math.sqrt(random.nextDouble()) * radius * 0.75;
         int ox = cx + (int)Math.round(Math.cos(angle) * dist);
         int oz = cz + (int)Math.round(Math.sin(angle) * dist);
         boolean alongX = random.nextBoolean();
         int len;
         int height;
         int depth;
         switch (random.nextInt(4)) {
            case 0:
               len = 3 + random.nextInt(3);
               height = 2 + random.nextInt(2);
               depth = 1;
               break;
            case 1:
               len = 3 + random.nextInt(2);
               height = 2;
               depth = 1;
               break;
            case 2:
               len = 1;
               height = 3;
               depth = 1;
               break;
            default:
               len = 2 + random.nextInt(2);
               height = 2 + random.nextInt(2);
               depth = 2;
         }

         int sx = alongX ? len : depth;
         int sz = alongX ? depth : len;
         Aabb box = new Aabb(ox, floorY, oz, ox + sx, floorY + height, oz + sz);
         if (fitsInside(box, center, radius - 1.0) && !nearSpawn(box, a) && !nearSpawn(box, b)) {
            boolean overlaps = false;

            for (Aabb other : result) {
               if (other.inflate(1.0).intersects(box)) {
                  overlaps = true;
                  break;
               }
            }

            if (!overlaps) {
               result.add(box);
            }
         }
      }

      return new DuelArena(center, floorY, radius, result);
   }

   private static boolean nearSpawn(Aabb box, Vec3 spawn) {
      return box.inflate(2.5).intersects(new Aabb(spawn.x(), box.minY(), spawn.z(), spawn.x(), box.maxY(), spawn.z()));
   }

   private static boolean fitsInside(Aabb box, Vec3 c, double r) {
      double[][] corners = new double[][]{{box.minX(), box.minZ()}, {box.maxX(), box.minZ()}, {box.minX(), box.maxZ()}, {box.maxX(), box.maxZ()}};

      for (double[] p : corners) {
         double dx = p[0] - c.x();
         double dz = p[1] - c.z();
         if (dx * dx + dz * dz > r * r) {
            return false;
         }
      }

      return true;
   }

   public DuelArena grounded(List<double[]> grounds) {
      if (grounds.size() != this.obstacles.size()) {
         throw new IllegalArgumentException("Falsche Anzahl Boden-Höhen");
      } else {
         List<Aabb> out = new ArrayList<>();

         for (int i = 0; i < this.obstacles.size(); i++) {
            Aabb b = this.obstacles.get(i);
            double lo = grounds.get(i)[0];
            double hi = grounds.get(i)[1];
            if (Double.isNaN(lo) || Double.isNaN(hi) || lo > hi || hi - lo > 16.0 || Math.abs(lo - this.floorY) > 16.0 || Math.abs(hi - this.floorY) > 16.0) {
               throw new IllegalArgumentException("Ungültige Boden-Höhe");
            }

            out.add(new Aabb(b.minX(), lo, b.minZ(), b.maxX(), hi + (b.maxY() - b.minY()), b.maxZ()));
         }

         return new DuelArena(this.center, this.floorY, this.radius, out);
      }
   }

   public double rayObstacles(Vec3 origin, Vec3 dir, double maxT) {
      double best = -1.0;

      for (Aabb box : this.obstacles) {
         double t = box.ray(origin, dir, maxT);
         if (t >= 0.0 && (best < 0.0 || t < best)) {
            best = t;
         }
      }

      return best;
   }
}
