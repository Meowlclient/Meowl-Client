package com.lennart.nebula.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class InventoryUtil {
   public static final int OFFHAND_SWAP_BUTTON = 40;

   private InventoryUtil() {
   }

   public static int toHandlerSlot(int inventoryIndex) {
      return inventoryIndex < 9 ? 36 + inventoryIndex : inventoryIndex;
   }

   public static int count(LocalPlayer player, Item item) {
      Inventory inventory = player.getInventory();
      int total = 0;

      for (int i = 0; i < 36; i++) {
         ItemStack stack = inventory.getItem(i);
         if (stack.is(item)) {
            total += stack.getCount();
         }
      }

      if (player.getOffhandItem().is(item)) {
         total += player.getOffhandItem().getCount();
      }

      return total;
   }

   public static int find(LocalPlayer player, Item item, boolean hotbarFirst, int skipIndex) {
      Inventory inventory = player.getInventory();
      int[][] ranges = hotbarFirst ? new int[][]{{0, 9}, {9, 36}} : new int[][]{{9, 36}, {0, 9}};

      for (int[] range : ranges) {
         for (int i = range[0]; i < range[1]; i++) {
            if (i != skipIndex && inventory.getItem(i).is(item)) {
               return i;
            }
         }
      }

      return -1;
   }

   public static boolean canClickPlayerInventory(Minecraft mc) {
      if (mc.player != null && mc.gameMode != null) {
         return mc.player.containerMenu != mc.player.inventoryMenu ? false : mc.player.inventoryMenu.getCarried().isEmpty();
      } else {
         return false;
      }
   }

   public static void swap(Minecraft mc, int inventoryIndex, int button) {
      InventoryMenu handler = mc.player.inventoryMenu;
      mc.gameMode.handleInventoryMouseClick(handler.containerId, toHandlerSlot(inventoryIndex), button, ClickType.SWAP, mc.player);
   }

   public static void swapHandlerSlot(Minecraft mc, int handlerSlotId, int button) {
      InventoryMenu handler = mc.player.inventoryMenu;
      mc.gameMode.handleInventoryMouseClick(handler.containerId, handlerSlotId, button, ClickType.SWAP, mc.player);
   }

   public static void swapHotbarWithOffhand(Minecraft mc, int hotbarSlot) {
      LocalPlayer player = mc.player;
      int original = player.getInventory().getSelectedSlot();
      if (hotbarSlot != original) {
         player.connection.send(new ServerboundSetCarriedItemPacket(hotbarSlot));
      }

      player.connection.send(new ServerboundPlayerActionPacket(Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN));
      if (hotbarSlot != original) {
         player.connection.send(new ServerboundSetCarriedItemPacket(original));
      }
   }
}
