package com.lennart.nebula.duel;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class DuelObstacleManager {
   private static volatile DuelArena active;

   private DuelObstacleManager() {
   }

   static void setArena(DuelArena arena) {
      active = arena;
   }

   public static List<VoxelShape> extraCollisions(Entity entity, AABB area, List<VoxelShape> vanilla) {
      DuelArena arena = active;
      if (arena != null && entity == Minecraft.getInstance().player) {
         List<VoxelShape> out = null;

         for (List<AABB> list : List.of(arena.obstacles(), arena.boundary())) {
            boolean obstacles = list == arena.obstacles();

            for (AABB b : list) {
               AABB box = obstacles ? new AABB(b.minX, b.minY, b.minZ, b.maxX, b.maxY + 4.0, b.maxZ) : b;
               if (box.intersects(area)) {
                  if (out == null) {
                     out = new ArrayList<>(vanilla);
                  }

                  out.add(Shapes.create(box));
               }
            }
         }

         return out == null ? vanilla : out;
      } else {
         return vanilla;
      }
   }
}
