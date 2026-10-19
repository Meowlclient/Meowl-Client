package com.lennart.nebula.module.mods;

import com.mojang.blaze3d.platform.InputConstants;

import com.lennart.nebula.NebulaClient;
import com.lennart.nebula.mixin.HandledScreenAccessor;
import com.lennart.nebula.module.Category;
import com.lennart.nebula.module.Module;
import com.lennart.nebula.module.Section;
import com.lennart.nebula.setting.Bind;
import com.lennart.nebula.setting.KeybindSetting;
import com.lennart.nebula.setting.ModeSetting;
import com.lennart.nebula.setting.NumberSetting;
import com.lennart.nebula.setting.StringListSetting;
import com.lennart.nebula.util.ChatUtil;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class ItemHighlighter extends Module {
   private static final String SCOPE_LIST = "Nur markierte";
   private static final String SCOPE_ALL = "Alle Items";
   private static final String STYLE_FILL = "Füllung";
   private static final String STYLE_BORDER = "Rahmen";
   private static final String STYLE_BOTH = "Beides";
   private final ModeSetting scope = this.add(
      new ModeSetting("Items", "Nur die markierten Items hervorheben oder einfach alle", "Nur markierte", "Nur markierte", "Alle Items")
   );
   private final StringListSetting items = this.add(
      new StringListSetting(
            "Markierte Items", "Im Inventar mit der Maus auf ein Item zeigen und die Markier-Taste drücken", List.of("minecraft:totem_of_undying")
         )
         .normalizer(StringListSetting::itemId, "Item-ID, z.B. diamond oder netherite_ingot")
   );
   private final KeybindSetting markKey = this.add(
      new KeybindSetting("Markier-Taste", "Im Inventar: Item unter der Maus markieren / entfernen", Bind.keyboard(InputConstants.KEY_H))
   );
   private final NumberSetting red = this.add(new NumberSetting("Rot", "Rotanteil der Farbe", 139.0, 0.0, 255.0, 1.0, ""));
   private final NumberSetting green = this.add(new NumberSetting("Grün", "Grünanteil der Farbe", 92.0, 0.0, 255.0, 1.0, ""));
   private final NumberSetting blue = this.add(new NumberSetting("Blau", "Blauanteil der Farbe", 246.0, 0.0, 255.0, 1.0, ""));
   private final NumberSetting opacity = this.add(
      new NumberSetting("Deckkraft", "Wie stark die Füllung deckt (liegt hinter dem Item)", 35.0, 5.0, 100.0, 1.0, "%")
   );
   private final ModeSetting style = this.add(new ModeSetting("Darstellung", "Füllung, Rahmen oder beides", "Beides", "Füllung", "Rahmen", "Beides"));
   private boolean markWasDown;
   private static boolean slotHookActive;

   public ItemHighlighter() {
      super("Item Highlighter", "Hebt beliebige Items in Inventaren farbig hervor.", Category.PLAYER);
      this.section(Section.CLIENT);
      this.icon(Items.GLOW_INK_SAC);
      this.items.setVisibility(() -> this.scope.is("Nur markierte"));
      this.markKey.setVisibility(() -> this.scope.is("Nur markierte"));
      this.opacity.setVisibility(() -> !this.style.is("Rahmen"));
   }

   @Override
   public void onTick() {
      boolean down = this.markKey.get().isPressed();
      boolean pressed = down && !this.markWasDown;
      this.markWasDown = down;
      if (pressed && this.scope.is("Nur markierte") && this.mc.gui.screen() instanceof AbstractContainerScreen<?> screen) {
         Slot hovered = ((HandledScreenAccessor)screen).meowl$getFocusedSlot();
         if (hovered != null && hovered.hasItem()) {
            ItemStack stack = hovered.getItem();
            String id = itemId(stack);
            boolean added = this.items.toggle(id);
            ChatUtil.actionBar("Item Highlighter: " + stack.getHoverName().getString() + (added ? " §amarkiert" : " §centfernt"));
            NebulaClient client = NebulaClient.getInstance();
            if (client != null) {
               client.getModuleManager().notifyChanged();
            }
         }
      }
   }

   public static void drawUnderSlot(GuiGraphicsExtractor ctx, Slot slot) {
      slotHookActive = true;
      ItemHighlighter module = instance();
      if (module != null && slot.hasItem() && module.shouldHighlight(slot.getItem())) {
         module.drawMark(ctx, slot.x, slot.y);
      }
   }

   public static void renderOverlay(AbstractContainerScreen<?> screen, GuiGraphicsExtractor ctx) {
      if (!slotHookActive) {
         ItemHighlighter module = instance();
         if (module != null) {
            HandledScreenAccessor accessor = (HandledScreenAccessor)screen;

            for (Slot slot : screen.getMenu().slots) {
               if (slot.hasItem() && module.shouldHighlight(slot.getItem())) {
                  module.drawMark(ctx, accessor.meowl$getX() + slot.x, accessor.meowl$getY() + slot.y);
               }
            }
         }
      }
   }

   private static ItemHighlighter instance() {
      NebulaClient client = NebulaClient.getInstance();
      if (client != null && client.getModuleManager() != null) {
         ItemHighlighter module = client.getModuleManager().get(ItemHighlighter.class);
         return module != null && module.isEnabled() ? module : null;
      } else {
         return null;
      }
   }

   private void drawMark(GuiGraphicsExtractor ctx, int x, int y) {
      int rgb = this.red.getInt() << 16 | this.green.getInt() << 8 | this.blue.getInt();
      int fill = Math.round(255 * this.opacity.getInt() / 100.0F) << 24 | rgb;
      int border = 0xFF000000 | rgb;
      if (!this.style.is("Rahmen")) {
         ctx.fill(x, y, x + 16, y + 16, fill);
      }

      if (!this.style.is("Füllung")) {
         ctx.fill(x, y, x + 16, y + 1, border);
         ctx.fill(x, y + 15, x + 16, y + 16, border);
         ctx.fill(x, y, x + 1, y + 16, border);
         ctx.fill(x + 15, y, x + 16, y + 16, border);
      }
   }

   private boolean shouldHighlight(ItemStack stack) {
      return this.scope.is("Alle Items") || this.items.contains(itemId(stack));
   }

   private static String itemId(ItemStack stack) {
      return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
   }
}
