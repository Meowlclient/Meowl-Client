package com.lennart.nebula.module.hud;

import com.lennart.nebula.module.Section;
import com.lennart.nebula.module.Category;
import com.lennart.nebula.module.Module;
import com.lennart.nebula.setting.BooleanSetting;
import com.lennart.nebula.setting.ModeSetting;
import com.lennart.nebula.setting.NumberSetting;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class ArmorHud extends Module {
   private static final EquipmentSlot[] SLOTS = new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
   private static final String MEOWL = "Meowl";
   private static final String VANILLA = "Vanilla (Hotbar)";
   private static final String FREE = "Frei";
   private static final String LEFT = "Links neben Hotbar";
   private static final String RIGHT = "Rechts neben Hotbar";
   private final ModeSetting style = this.add(
      new ModeSetting("Stil", "Eigener Kasten oder wie die normale Hotbar", "Vanilla (Hotbar)", "Meowl", "Vanilla (Hotbar)")
   );
   private final ModeSetting anchor = this.add(
      new ModeSetting("Position", "Frei oder fest neben der Hotbar", "Frei", "Frei", "Links neben Hotbar", "Rechts neben Hotbar")
   );
   private final ModeSetting layout = this.add(new ModeSetting("Ausrichtung", "Untereinander oder nebeneinander", "Vertikal", "Vertikal", "Horizontal"));
   private final NumberSetting spacing = this.add(new NumberSetting("Abstand", "Abstand zwischen den Teilen", 2.0, 0.0, 12.0, 1.0, "px"));
   private final BooleanSetting bar = this.add(new BooleanSetting("Haltbarkeitsbalken", "Der normale grüne/rote Balken am Item", true));
   private final BooleanSetting durability = this.add(new BooleanSetting("Haltbarkeit", "Haltbarkeit als Text anzeigen", true));
   private final ModeSetting durabilityMode = this.add(
      new ModeSetting("Haltbarkeit als", "Prozent oder Haltbarkeitspunkte", "Prozent", "Prozent", "Durability", "Beides")
   );
   private final BooleanSetting itemName = this.add(new BooleanSetting("Itemname", "Namen des Teils anzeigen (nur vertikal)", false));
   private final BooleanSetting showEmpty = this.add(new BooleanSetting("Leere Slots", "Auch leere Rüstungsslots als Platzhalter", false));
   private final HudBox box = this.hudBox("Armor HUD", 1.0, 60.0);

   public ArmorHud() {
      super("Armor HUD", "Zeigt deine Rüstung mit Haltbarkeit im HUD.", Category.HUD);
      this.section(Section.PVP);
      this.icon(Items.DIAMOND_CHESTPLATE);
      this.durabilityMode.setVisibility(this.durability::get);
      this.layout.setVisibility(() -> this.anchor.is("Frei"));
      this.spacing.setVisibility(() -> this.style.is("Meowl"));
      this.itemName.setVisibility(() -> this.anchor.is("Frei") && this.layout.is("Vertikal"));
      this.box.background.setVisibility(() -> this.style.is("Meowl"));
      this.box.setDetachAction(() -> this.anchor.set("Frei"));
   }

   private String durabilityText(ItemStack stack) {
      if (!stack.isDamageableItem()) {
         return "";
      } else {
         int max = stack.getMaxDamage();
         int left = max - stack.getDamageValue();
         int pct = Math.round(left * 100.0F / max);
         String var5 = this.durabilityMode.get();

         return switch (var5) {
            case "Durability" -> String.valueOf(left);
            case "Beides" -> left + " (" + pct + "%)";
            default -> pct + "%";
         };
      }
   }

   private static int durabilityColor(ItemStack stack) {
      if (!stack.isDamageableItem()) {
         return -1578509;
      } else {
         float f = 1.0F - (float)stack.getDamageValue() / stack.getMaxDamage();
         if (f > 0.6F) {
            return -7934036;
         } else {
            return f > 0.3F ? -278748 : -495247;
         }
      }
   }

   @Override
   public void onRenderHud(GuiGraphicsExtractor ctx, float tickDelta) {
      LocalPlayer player = this.mc.player;
      if (player != null && !this.mc.gui.hud.isHidden()) {
         List<ItemStack> stacks = new ArrayList<>(4);

         for (EquipmentSlot slot : SLOTS) {
            ItemStack stack = player.getItemBySlot(slot);
            if (!stack.isEmpty() || this.showEmpty.get()) {
               stacks.add(stack);
            }
         }

         if (stacks.isEmpty() && HudLayout.isEditing()) {
            for (Item item : new Item[]{Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS}) {
               ItemStack sample = new ItemStack(item);
               sample.setDamageValue(sample.getMaxDamage() / 3);
               stacks.add(sample);
            }
         }

         if (!stacks.isEmpty()) {
            if (this.style.is("Vanilla (Hotbar)")) {
               this.renderVanilla(ctx, player, stacks);
            } else {
               this.renderMeowl(ctx, player, stacks);
            }
         }
      }
   }

   private void renderVanilla(GuiGraphicsExtractor ctx, LocalPlayer player, List<ItemStack> stacks) {
      Font tr = this.mc.font;
      int n = stacks.size();
      boolean anchored = !this.anchor.is("Frei");
      boolean vertical = !anchored && this.layout.is("Vertikal");
      boolean text = this.durability.get();
      boolean names = vertical && this.itemName.get();
      int textW = 0;

      for (ItemStack s : stacks) {
         int w = text ? tr.width(this.durabilityText(s)) : 0;
         if (names && !s.isEmpty()) {
            w = Math.max(w, tr.width(s.getHoverName()));
         }

         textW = Math.max(textW, w);
      }

      int slotColor = VanillaSlots.white(this.box.alpha());
      if (vertical) {
         int w = VanillaSlots.stripWidth(1) + (textW > 0 ? textW + 4 : 0);
         int h = n * 22;
         this.box.begin(ctx, w, h, false);

         for (int i = 0; i < n; i++) {
            ItemStack s = stacks.get(i);
            int y = i * 22;
            VanillaSlots.cell(ctx, 0, y, slotColor);
            this.drawStack(ctx, s, 3, y + 3);
            if (!s.isEmpty() && text) {
               ctx.text(tr, this.durabilityText(s), 26, names ? y + 12 : y + 7, this.box.fade(durabilityColor(s)), true);
            }

            if (names && !s.isEmpty()) {
               ctx.text(tr, s.getHoverName(), 26, y + 2, this.box.fade(-1578509), true);
            }
         }

         this.box.end(ctx);
      } else {
         int w = VanillaSlots.stripWidth(n);
         int textH = text ? 9 : 0;
         int h = 22 + textH;
         int stripY = anchored ? textH : 0;
         int textY = anchored ? 0 : 23;
         if (anchored) {
            int[] p = this.anchorPos(ctx, player, w, h);
            this.box.beginAt(ctx, p[0], p[1], w, h, false);
         } else {
            this.box.begin(ctx, w, h, false);
         }

         VanillaSlots.strip(ctx, 0, stripY, n, slotColor);

         for (int i = 0; i < n; i++) {
            ItemStack sx = stacks.get(i);
            int x = 1 + i * 20;
            this.drawStack(ctx, sx, x + 2, stripY + 3);
            if (!sx.isEmpty() && text) {
               this.drawSmallCentered(ctx, this.durabilityText(sx), x + 10, textY, this.box.fade(durabilityColor(sx)));
            }
         }

         this.box.end(ctx);
      }
   }

   private int[] anchorPos(GuiGraphicsExtractor ctx, LocalPlayer player, int w, int h) {
      int sw = ctx.guiWidth();
      int sh = ctx.guiHeight();
      boolean offhandLeft = player.getMainArm() == HumanoidArm.RIGHT;
      boolean hasOffhand = !player.getOffhandItem().isEmpty();
      boolean left = this.anchor.is("Links neben Hotbar");
      int extra = hasOffhand && offhandLeft == left ? 29 : 0;
      int x = left ? sw / 2 - 91 - extra - 4 - w : sw / 2 + 91 + extra + 4;
      return new int[]{x, sh - h};
   }

   private void drawSmallCentered(GuiGraphicsExtractor ctx, String s, int cx, int y, int color) {
      float k = 0.75F;
      int tw = this.mc.font.width(s);
      ctx.pose().pushMatrix();
      ctx.pose().translate(cx - tw * k / 2.0F, y + 1);
      ctx.pose().scale(k, k);
      ctx.text(this.mc.font, s, 0, 0, color, true);
      ctx.pose().popMatrix();
   }

   private void drawStack(GuiGraphicsExtractor ctx, ItemStack s, int x, int y) {
      if (!s.isEmpty()) {
         ctx.item(s, x, y);
         if (this.bar.get()) {
            ctx.itemDecorations(this.mc.font, s, x, y);
         }
      }
   }

   private void renderMeowl(GuiGraphicsExtractor ctx, LocalPlayer player, List<ItemStack> stacks) {
      Font tr = this.mc.font;
      boolean anchored = !this.anchor.is("Frei");
      boolean vertical = !anchored && this.layout.is("Vertikal");
      int gap = this.spacing.getInt();
      int count = stacks.size();
      int textW = 0;

      for (ItemStack stack : stacks) {
         int w = this.durability.get() ? tr.width(this.durabilityText(stack)) : 0;
         if (vertical && this.itemName.get() && !stack.isEmpty()) {
            w = Math.max(w, tr.width(stack.getHoverName()));
         }

         textW = Math.max(textW, w);
      }

      boolean twoLines = vertical && this.itemName.get();
      int cellW;
      int cellH;
      int totalW;
      int totalH;
      if (vertical) {
         cellW = 18 + (textW > 0 ? textW + 4 : 0);
         cellH = 16;
         totalW = cellW + 6;
         totalH = count * cellH + (count - 1) * gap + 6;
      } else {
         cellW = Math.max(16, textW);
         cellH = 16 + (this.durability.get() ? 10 : 0);
         totalW = count * cellW + (count - 1) * gap + 6;
         totalH = cellH + 6;
      }

      if (anchored) {
         int[] p = this.anchorPos(ctx, player, totalW, totalH);
         this.box.beginAt(ctx, p[0], p[1], totalW, totalH, this.box.background.get());
      } else {
         this.box.begin(ctx, totalW, totalH);
      }

      int i = 0;

      for (ItemStack stack : stacks) {
         int x = 3 + (vertical ? 0 : i * (cellW + gap));
         int y = 3 + (vertical ? i * (cellH + gap) : 0);
         int ix = vertical ? x : x + (cellW - 16) / 2;
         if (stack.isEmpty()) {
            ctx.fill(ix + 2, y + 2, ix + 14, y + 14, this.box.fade(1090519039));
         } else {
            this.drawStack(ctx, stack, ix, y);
         }

         if (!stack.isEmpty() && this.durability.get()) {
            String d = this.durabilityText(stack);
            int color = this.box.fade(durabilityColor(stack));
            if (vertical) {
               ctx.text(tr, d, x + 20, twoLines ? y + 8 : y + 4, color, true);
            } else {
               ctx.text(tr, d, x + (cellW - tr.width(d)) / 2, y + 17, color, true);
            }
         }

         if (twoLines && !stack.isEmpty()) {
            ctx.text(tr, stack.getHoverName(), x + 20, y - 1, this.box.fade(-1578509), true);
         }

         i++;
      }

      this.box.end(ctx);
   }
}
