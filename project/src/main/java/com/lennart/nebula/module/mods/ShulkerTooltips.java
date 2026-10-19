package com.lennart.nebula.module.mods;

import com.lennart.nebula.gui.Render2D;
import com.lennart.nebula.module.Category;
import com.lennart.nebula.module.Module;
import com.lennart.nebula.setting.BooleanSetting;
import com.lennart.nebula.setting.Setting;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.client.rendering.v1.ClientTooltipComponentCallback;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class ShulkerTooltips extends Module {
   private static ShulkerTooltips instance;
   private final BooleanSetting hideText = this.add(
      new BooleanSetting("Textliste ausblenden", "Die Vanilla-Zeilen „… x12 / und 5 weitere“ durch das Raster ersetzen", true)
   );
   private final BooleanSetting colored = this.add(new BooleanSetting("Kistenfarbe", "Rahmen in der Farbe der Shulker-Kiste", true));
   private final BooleanSetting world = this.add(
      new BooleanSetting("Am Boden anschauen", "Inhalt zeigen, wenn du eine am Boden liegende Shulker-Kiste anschaust", true)
   );
   private final BooleanSetting showEmpty = this.add(new BooleanSetting("Leere zeigen", "Raster auch bei leeren Kisten", false));
   private final BooleanSetting needShift = this.add(
      new BooleanSetting("Nur mit Shift", "Inhalt erst zeigen, wenn du Shift hältst (sonst nur ein Hinweis)", true)
   );
   private final BooleanSetting middleClick = this.add(
      new BooleanSetting("Mittelklick: durchsehen", "Mittlere Maustaste auf einer Shulker-Kiste öffnet eine Liste aller Items mit Namen (nur ansehen)", true)
   );

   public ShulkerTooltips() {
      super("Shulker Tooltips", "Inhalt von Shulker-Kisten als übersichtliches Inventar-Raster im Tooltip – auch für Items am Boden.", Category.PLAYER);
      this.icon(Items.SHULKER_BOX);
      instance = this;
      this.group("Tooltip", new Setting[]{this.needShift, this.middleClick, this.hideText, this.colored, this.showEmpty, this.world});
      this.setEnabled(true);
      ClientTooltipComponentCallback.EVENT.register(data -> data instanceof ShulkerTooltips.Data d ? new ShulkerTooltips.Grid(d) : null);
   }

   private static boolean active() {
      return instance != null && instance.isEnabled();
   }

   public static TooltipComponent dataFor(ItemStack stack) {
      return dataFor(stack, false);
   }

   /** Inhalt verdeckt: im Inventar ohne Shift (Einstellung „Nur mit Shift“). */
   public static boolean hiddenWithoutShift() {
      return active() && instance.needShift.get() && !com.lennart.nebula.compat.Keys.shiftDown();
   }

   public static boolean middleClickEnabled() {
      return active() && instance.middleClick.get();
   }

   /** Inhalt einer Kiste (null = keine Container-Daten). */
   public static List<ItemStack> contents(ItemStack stack) {
      ItemContainerContents c = stack.get(DataComponents.CONTAINER);
      if (c == null) {
         return null;
      } else {
         List<ItemStack> items = new ArrayList<>();
         c.itemCopies().forEach(items::add);
         return items;
      }
   }

   public static TooltipComponent dataFor(ItemStack stack, boolean ignoreShift) {
      if (!active() || !ignoreShift && hiddenWithoutShift()) {
         return null;
      } else {
         ItemContainerContents c = stack.get(DataComponents.CONTAINER);
         if (c == null) {
            return null;
         } else {
            List<ItemStack> items = new ArrayList<>();
            c.itemCopies().forEach(items::add);
            boolean any = items.stream().anyMatch(s -> !s.isEmpty());
            if (!any && !instance.showEmpty.get()) {
               return null;
            } else {
               int slots = Math.max(9, (items.size() + 8) / 9 * 9);

               while (items.size() < slots) {
                  items.add(ItemStack.EMPTY);
               }

               return new ShulkerTooltips.Data(items, instance.frameColor(stack));
            }
         }
      }
   }

   public static boolean hideTextList() {
      return active() && instance.hideText.get();
   }

   private int frameColor(ItemStack stack) {
      if (this.colored.get() && Block.byItem(stack.getItem()) instanceof ShulkerBoxBlock box) {
         DyeColor dye = box.getColor();
         return dye == null ? -6656080 : 0xFF000000 | dye.getTextureDiffuseColor();
      } else {
         return -9801856;
      }
   }

   @Override
   public void onRenderHud(GuiGraphicsExtractor ctx, float tickDelta) {
      if (this.world.get() && this.mc.gui.screen() == null && this.mc.player != null && !this.mc.gui.hud.isHidden()) {
         ItemEntity target = this.lookedAtItem(tickDelta);
         if (target != null) {
            if (dataFor(target.getItem(), true) instanceof ShulkerTooltips.Data data) {
               int w = 166;
               int rows = (data.items().size() + 8) / 9;
               int x = ctx.guiWidth() / 2 + 12;
               if (x + w > ctx.guiWidth() - 2) {
                  x = Math.max(2, ctx.guiWidth() / 2 - 12 - w);
               }

               int y = ctx.guiHeight() / 2 - (rows * 18 + 16) / 2;
               ctx.text(this.mc.font, target.getItem().getHoverName(), x + 2, y, -1, true);
               ShulkerTooltips.Grid.draw(ctx, this.mc.font, data, x, y + 11);
            }
         }
      }
   }

   private ItemEntity lookedAtItem(float tickDelta) {
      Vec3 eye = this.mc.player.getEyePosition(tickDelta);
      Vec3 dir = this.mc.player.getViewVector(tickDelta);
      Vec3 end = eye.add(dir.scale(6.0));
      ItemEntity best = null;
      double bestD = Double.MAX_VALUE;

      for (Entity e : this.mc.level.getEntities(this.mc.player, new AABB(eye, end).inflate(1.0), ex -> ex instanceof ItemEntity)) {
         ItemEntity item = (ItemEntity)e;
         if (item.getItem().get(DataComponents.CONTAINER) != null) {
            Optional<Vec3> hit = item.getBoundingBox().inflate(0.25).clip(eye, end);
            if (hit.isPresent()) {
               double dd = hit.get().distanceToSqr(eye);
               if (dd < bestD) {
                  bestD = dd;
                  best = item;
               }
            }
         }
      }

      return best;
   }

   public record Data(List<ItemStack> items, int color) implements TooltipComponent {
   }

   public static final class Grid implements ClientTooltipComponent {
      private final ShulkerTooltips.Data data;

      Grid(ShulkerTooltips.Data data) {
         this.data = data;
      }

      private int rows() {
         return (this.data.items().size() + 8) / 9;
      }

      @Override
      public int getHeight(Font textRenderer) {
         return this.rows() * 18 + 6;
      }

      @Override
      public int getWidth(Font textRenderer) {
         return 166;
      }

      @Override
      public void extractImage(Font textRenderer, int x, int y, int width, int height, GuiGraphicsExtractor context) {
         draw(context, textRenderer, this.data, x, y + 1);
      }

      public static void draw(GuiGraphicsExtractor ctx, Font tr, ShulkerTooltips.Data data, int x, int y) {
         int rows = (data.items().size() + 8) / 9;
         int c = data.color();
         ctx.fill(x, y, x + 162 + 4, y + rows * 18 + 4, 0xFF000000 | Render2D.alpha(c, 0.35F) & 16777215);
         ctx.fill(x + 1, y + 1, x + 162 + 3, y + rows * 18 + 3, -267382760);

         for (int i = 0; i < data.items().size(); i++) {
            int sx = x + 2 + i % 9 * 18;
            int sy = y + 2 + i / 9 * 18;
            ctx.fill(sx, sy, sx + 18, sy + 18, -7631989);
            ctx.fill(sx + 1, sy + 1, sx + 18, sy + 18, -13158601);
            ctx.fill(sx + 1, sy + 1, sx + 17, sy + 17, 1619757963);
            ItemStack s = data.items().get(i);
            if (!s.isEmpty()) {
               ctx.item(s, sx + 1, sy + 1);
               ctx.itemDecorations(tr, s, sx + 1, sy + 1);
            }
         }
      }
   }
}
