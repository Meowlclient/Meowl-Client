package com.lennart.nebula.util;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

public final class InteractionUtil {
   public static final int HOTBAR_SIZE = 9;

   private InteractionUtil() {
   }

   public static int findHotbarSlot(LocalPlayer player, Item item) {
      Inventory inventory = player.getInventory();

      for (int i = 0; i < 9; i++) {
         if (inventory.getItem(i).is(item)) {
            return i;
         }
      }

      return -1;
   }

   public static int findHotbarSlotWithout(LocalPlayer player, Item excluded, int preferred) {
      Inventory inventory = player.getInventory();
      if (preferred >= 0 && preferred < 9 && !inventory.getItem(preferred).is(excluded)) {
         return preferred;
      } else {
         for (int i = 0; i < 9; i++) {
            if (inventory.getItem(i).isEmpty()) {
               return i;
            }
         }

         for (int ix = 0; ix < 9; ix++) {
            if (!inventory.getItem(ix).is(excluded)) {
               return ix;
            }
         }

         return -1;
      }
   }

   public static BlockHitResult getTargetBlock(Minecraft mc, LocalPlayer player, double maxRange) {
      if (mc.hitResult instanceof BlockHitResult hit && hit.getType() == Type.BLOCK) {
         double reach = Math.min(maxRange, player.blockInteractionRange());
         if (player.getEyePosition().distanceTo(hit.getLocation()) > reach) {
            return null;
         } else {
            return !player.isWithinBlockInteractionRange(hit.getBlockPos(), 1.0) ? null : hit;
         }
      } else {
         return null;
      }
   }

   public static List<BlockHitResult> sweepRaycast(
      ClientLevel world, LocalPlayer player, float fromYaw, float fromPitch, float toYaw, float toPitch, double reach
   ) {
      float dYaw = wrapDegrees(toYaw - fromYaw);
      float dPitch = toPitch - fromPitch;
      int steps = (int)Math.ceil(Math.max(Math.abs(dYaw), Math.abs(dPitch)));
      steps = Math.max(1, Math.min(90, steps));
      Vec3 eye = player.getEyePosition();
      List<BlockHitResult> hits = new ArrayList<>(steps);

      for (int i = 1; i <= steps; i++) {
         float t = (float)i / steps;
         Vec3 dir = rotationVector(fromPitch + dPitch * t, fromYaw + dYaw * t);
         Vec3 end = eye.add(dir.x * reach, dir.y * reach, dir.z * reach);
         BlockHitResult hit = world.clip(new ClipContext(eye, end, Block.OUTLINE, Fluid.NONE, player));
         if (hit != null && hit.getType() == Type.BLOCK && eye.distanceTo(hit.getLocation()) <= reach && player.isWithinBlockInteractionRange(hit.getBlockPos(), 1.0)) {
            hits.add(hit);
         }
      }

      return hits;
   }

   public static BlockHitResult findPlacementHit(
      ClientLevel world, LocalPlayer player, BlockPos pos, BlockPos avoid, BlockState stateToPlace, double reach
   ) {
      if (!world.getBlockState(pos).canBeReplaced()) {
         return null;
      } else {
         Vec3 eye = player.getEyePosition();
         Direction[] order = new Direction[]{Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP};

         for (Direction toSupport : order) {
            BlockPos support = pos.relative(toSupport);
            if (!support.equals(avoid) && !world.getBlockState(support).canBeReplaced()) {
               Direction face = toSupport.getOpposite();
               Vec3 hitPos = Vec3.atCenterOf(support).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
               if (!(eye.distanceTo(hitPos) > reach) && player.isWithinBlockInteractionRange(support, 1.0)) {
                  BlockHitResult hit = new BlockHitResult(hitPos, face, support, false);
                  if (canPlace(world, player, hit, pos, stateToPlace)) {
                     return hit;
                  }
               }
            }
         }

         return null;
      }
   }

   private static Vec3 rotationVector(float pitch, float yaw) {
      double f = Math.toRadians(pitch);
      double g = Math.toRadians(-yaw);
      double cosG = Math.cos(g);
      double sinG = Math.sin(g);
      double cosF = Math.cos(f);
      double sinF = Math.sin(f);
      return new Vec3(sinG * cosF, -sinF, cosG * cosF);
   }

   private static float wrapDegrees(float degrees) {
      float d = degrees % 360.0F;
      if (d >= 180.0F) {
         d -= 360.0F;
      }

      if (d < -180.0F) {
         d += 360.0F;
      }

      return d;
   }

   public static BlockPos getPlacementPos(ClientLevel world, BlockHitResult hit) {
      BlockPos clicked = hit.getBlockPos();
      return world.getBlockState(clicked).canBeReplaced() ? clicked : clicked.relative(hit.getDirection());
   }

   public static boolean canPlace(ClientLevel world, LocalPlayer player, BlockHitResult hit, BlockPos pos, BlockState stateToPlace) {
      if (!player.isSpectator() && player.getAbilities().mayBuild) {
         if (world.isOutsideBuildHeight(pos) || !world.getWorldBorder().isWithinBounds(pos)) {
            return false;
         } else if (!world.getBlockState(pos).canBeReplaced()) {
            return false;
         } else if (!stateToPlace.canSurvive(world, pos)) {
            return false;
         } else if (!world.isUnobstructed(stateToPlace, pos, CollisionContext.of(player))) {
            return false;
         } else {
            BlockState clicked = world.getBlockState(hit.getBlockPos());
            return player.isShiftKeyDown() || clicked.canBeReplaced() || !isInteractable(world, hit.getBlockPos(), clicked);
         }
      } else {
         return false;
      }
   }

   public static boolean isInteractable(ClientLevel world, BlockPos pos, BlockState state) {
      return !state.hasBlockEntity() && state.getMenuProvider(world, pos) == null
         ? state.getBlock() instanceof DoorBlock
            || state.getBlock() instanceof TrapDoorBlock
            || state.getBlock() instanceof FenceGateBlock
            || state.getBlock() instanceof ButtonBlock
            || state.getBlock() instanceof LeverBlock
            || state.getBlock() instanceof NoteBlock
            || state.getBlock() instanceof RepeaterBlock
            || state.getBlock() instanceof ComparatorBlock
            || state.getBlock() instanceof CakeBlock
            || state.getBlock() instanceof SignBlock
            || state.getBlock() instanceof RespawnAnchorBlock
         : true;
   }

   public static InteractionResult useOnBlock(Minecraft mc, LocalPlayer player, int slot, BlockHitResult hit) {
      if (mc.gameMode == null) {
         return InteractionResult.FAIL;
      } else {
         Inventory inventory = player.getInventory();
         if (inventory.getSelectedSlot() != slot) {
            inventory.setSelectedSlot(slot);
         }

         InteractionResult result = mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
         if (result.consumesAction()) {
            com.lennart.nebula.compat.Compat.swingMainHand(player);
         }

         return result;
      }
   }

   public static void selectSlot(LocalPlayer player, int slot) {
      if (slot >= 0 && slot < 9) {
         player.getInventory().setSelectedSlot(slot);
      }
   }
}
