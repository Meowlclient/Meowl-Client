package com.lennart.nebula.duel;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public record DuelArena(Vec3 center, double floorY, double radius, List<AABB> obstacles, List<AABB> boundary) {
   public static DuelArena fromJson(JsonObject o) {
      JsonArray c = o.getAsJsonArray("center");
      Vec3 center = new Vec3(c.get(0).getAsDouble(), c.get(1).getAsDouble(), c.get(2).getAsDouble());
      double floorY = o.get("floorY").getAsDouble();
      double radius = Math.max(4.0, Math.min(32.0, o.get("radius").getAsDouble()));
      List<AABB> obstacles = new ArrayList<>();

      for (JsonElement el : o.getAsJsonArray("obstacles")) {
         JsonArray a = el.getAsJsonArray();
         if (a.size() == 6) {
            obstacles.add(
               new AABB(
                  a.get(0).getAsDouble(),
                  a.get(1).getAsDouble(),
                  a.get(2).getAsDouble(),
                  a.get(3).getAsDouble(),
                  a.get(4).getAsDouble(),
                  a.get(5).getAsDouble()
               )
            );
         }

         if (obstacles.size() >= 32) {
            break;
         }
      }

      return new DuelArena(center, floorY, radius, List.copyOf(obstacles), buildBoundary(center, floorY, radius));
   }

   private static List<AABB> buildBoundary(Vec3 center, double floorY, double radius) {
      List<AABB> list = new ArrayList<>();
      int steps = (int)Math.ceil((Math.PI * 2) * radius / 0.4);

      for (int i = 0; i < steps; i++) {
         double a = i * 2 * Math.PI / steps;
         double x = center.x + Math.cos(a) * (radius + 0.25);
         double z = center.z + Math.sin(a) * (radius + 0.25);
         list.add(new AABB(x - 0.25, floorY - 3.0, z - 0.25, x + 0.25, floorY + 8.0, z + 0.25));
      }

      return List.copyOf(list);
   }

   public DuelArena withObstacles(List<AABB> list) {
      return new DuelArena(this.center, this.floorY, this.radius, List.copyOf(list), this.boundary);
   }

   public static List<AABB> parseObstacles(JsonArray arr) {
      List<AABB> obstacles = new ArrayList<>();

      for (JsonElement el : arr) {
         JsonArray a = el.getAsJsonArray();
         if (a.size() == 6) {
            obstacles.add(
               new AABB(
                  a.get(0).getAsDouble(),
                  a.get(1).getAsDouble(),
                  a.get(2).getAsDouble(),
                  a.get(3).getAsDouble(),
                  a.get(4).getAsDouble(),
                  a.get(5).getAsDouble()
               )
            );
         }

         if (obstacles.size() >= 32) {
            break;
         }
      }

      return obstacles;
   }

   public List<double[]> measureGround(Level world) {
      List<double[]> out = new ArrayList<>();

      for (AABB b : this.obstacles) {
         double lo = Double.MAX_VALUE;
         double hi = -Double.MAX_VALUE;

         for (int x = (int)Math.floor(b.minX); x < Math.ceil(b.maxX); x++) {
            for (int z = (int)Math.floor(b.minZ); z < Math.ceil(b.maxZ); z++) {
               double g = this.groundAt(world, x, z);
               if (!Double.isNaN(g)) {
                  lo = Math.min(lo, g);
                  hi = Math.max(hi, g);
               }
            }
         }

         if (lo == Double.MAX_VALUE) {
            hi = b.minY;
            lo = b.minY;
         }

         out.add(new double[]{lo, hi});
      }

      return out;
   }

   private double groundAt(Level world, int x, int z) {
      MutableBlockPos pos = new MutableBlockPos();
      int top = (int)Math.floor(this.floorY) + 6;
      int bottom = (int)Math.floor(this.floorY) - 12;

      for (int y = top; y >= bottom; y--) {
         pos.set(x, y, z);
         BlockState state = world.getBlockState(pos);
         if (!state.isAir() && !state.is(BlockTags.LEAVES)) {
            VoxelShape shape = state.getCollisionShape(world, pos);
            if (!shape.isEmpty()) {
               return y + shape.max(Axis.Y);
            }
         }
      }

      return Double.NaN;
   }

   public DuelArena grounded(List<double[]> grounds) {
      List<AABB> list = new ArrayList<>();

      for (int i = 0; i < this.obstacles.size(); i++) {
         AABB b = this.obstacles.get(i);
         double lo = grounds.get(i)[0];
         double hi = grounds.get(i)[1];
         list.add(new AABB(b.minX, lo, b.minZ, b.maxX, hi + (b.maxY - b.minY), b.maxZ));
      }

      return this.withObstacles(list);
   }

   public boolean inside(Vec3 pos) {
      double dx = pos.x - this.center.x;
      double dz = pos.z - this.center.z;
      return dx * dx + dz * dz <= this.radius * this.radius;
   }
}
