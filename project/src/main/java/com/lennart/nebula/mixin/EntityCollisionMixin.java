package com.lennart.nebula.mixin;

import com.lennart.nebula.duel.DuelObstacleManager;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin({Entity.class})
public abstract class EntityCollisionMixin {
   @Redirect(
      method = {"collide(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/level/Level;getEntityCollisions(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"
      )
   )
   private List<VoxelShape> nebula$duelCollisions(Level world, Entity entity, AABB box) {
      return DuelObstacleManager.extraCollisions(entity, box, world.getEntityCollisions(entity, box));
   }
}
