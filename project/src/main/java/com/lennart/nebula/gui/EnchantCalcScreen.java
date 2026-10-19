package com.lennart.nebula.gui;

import com.lennart.nebula.tools.AnvilCalc;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;

public class EnchantCalcScreen extends com.lennart.nebula.compat.MeowlBaseScreen {
   private static final Item[] ITEMS = new Item[]{
      Items.NETHERITE_SWORD,
      Items.NETHERITE_AXE,
      Items.NETHERITE_PICKAXE,
      Items.NETHERITE_SHOVEL,
      Items.NETHERITE_HOE,
      Items.NETHERITE_HELMET,
      Items.NETHERITE_CHESTPLATE,
      Items.NETHERITE_LEGGINGS,
      Items.NETHERITE_BOOTS,
      Items.BOW,
      Items.CROSSBOW,
      Items.TRIDENT,
      Items.MACE,
      Items.FISHING_ROD,
      Items.ELYTRA,
      Items.SHIELD,
      Items.SHEARS,
      Items.BOOK
   };
   private static final int ROW_H = 16;
   private final Screen parent;
   private final Map<Holder<Enchantment>, Integer> chosen = new LinkedHashMap<>();
   private final List<EnchantCalcScreen.Hit> hits = new ArrayList<>();
   private List<Holder<Enchantment>> available = List.of();
   private int itemIndex;
   private int work;
   private boolean creative;
   private int scroll;
   private String note = "";
   private AnvilCalc.Result result;

   public EnchantCalcScreen(Screen parent) {
      super(Component.literal("Amboss-Rechner"));
      this.parent = parent;
   }

   private boolean restored;

   @Override
   protected void init() {
      if (!this.restored) {
         // letzte Eingaben wiederherstellen (auch nach Neustart)
         this.restored = true;
         com.google.gson.JsonObject st = ToolState.get("anvil_calc");
         this.itemIndex = Math.max(0, Math.min(ITEMS.length - 1, ToolState.getInt(st, "item", 0)));
         this.work = Math.max(0, ToolState.getInt(st, "work", 0));
         this.creative = ToolState.getBool(st, "creative", false);
         this.refreshItem();
         if (st.has("chosen") && st.get("chosen").isJsonObject()) {
            com.google.gson.JsonObject ch = st.getAsJsonObject("chosen");

            for (Holder<Enchantment> e : this.available) {
               String id = e.unwrapKey().map(k -> k.identifier().toString()).orElse("");
               if (ch.has(id)) {
                  this.chosen.put(e, Math.max(1, ToolState.getInt(ch, id, 1)));
               }
            }

            this.recalc();
         }

         return;
      }

      this.refreshItem();
   }

   @Override
   public void removed() {
      com.google.gson.JsonObject st = new com.google.gson.JsonObject();
      st.addProperty("item", this.itemIndex);
      st.addProperty("work", this.work);
      st.addProperty("creative", this.creative);
      com.google.gson.JsonObject ch = new com.google.gson.JsonObject();
      this.chosen.forEach((e, lvl) -> e.unwrapKey().ifPresent(k -> ch.addProperty(k.identifier().toString(), lvl)));
      st.add("chosen", ch);
      ToolState.put("anvil_calc", st);
      super.removed();
   }

   private Item item() {
      return ITEMS[this.itemIndex];
   }

   private boolean isBookTarget() {
      return this.item() == Items.BOOK;
   }

   private void refreshItem() {
      this.available = new ArrayList<>();
      if (this.minecraft != null && this.minecraft.level != null) {
         Registry<Enchantment> reg = this.minecraft.level.registryAccess().lookup(Registries.ENCHANTMENT).orElse(null);
         if (reg != null) {
            ItemStack stack = com.lennart.nebula.util.SafeStack.of(this.item());
            List<Holder<Enchantment>> list = new ArrayList<>();
            reg.listElements().forEach(e -> {
               if (this.isBookTarget() || e.value().canEnchant(stack)) {
                  list.add(e);
               }
            });
            list.sort((a, b) -> a.value().description().getString().compareToIgnoreCase(b.value().description().getString()));
            this.available = list;
         }
      }

      this.chosen.keySet().removeIf(e -> !this.available.contains(e));
      this.scroll = 0;
      this.recalc();
   }

   private void recalc() {
      List<AnvilCalc.Book> books = new ArrayList<>();
      this.chosen
         .forEach((e, lvl) -> books.add(new AnvilCalc.Book(Enchantment.getFullname((Holder<Enchantment>)e, lvl).getString(), lvl, e.value().getAnvilCost())));
      if (books.size() > 12) {
         this.result = null;
         this.note = "Höchstens 12 Verzauberungen";
      } else {
         String target = this.isBookTarget() ? null : com.lennart.nebula.util.SafeStack.of(this.item()).getHoverName().getString();
         this.result = AnvilCalc.solve(books, this.work, target, this.creative);
      }
   }

   private void change(Holder<Enchantment> e, int delta) {
      int max = e.value().getMaxLevel();
      int now = this.chosen.getOrDefault(e, 0);
      int next = Math.max(0, Math.min(max, now + delta));
      this.note = "";
      if (next == 0) {
         this.chosen.remove(e);
      } else {
         if (now == 0) {
            List<Holder<Enchantment>> clash = new ArrayList<>();

            for (Holder<Enchantment> other : this.chosen.keySet()) {
               if (!Enchantment.areCompatible(e, other)) {
                  clash.add(other);
               }
            }

            for (Holder<Enchantment> c : clash) {
               this.chosen.remove(c);
               this.note = "„" + c.value().description().getString() + "“ passt nicht dazu – abgewählt";
            }
         }

         this.chosen.put(e, next);
      }

      this.recalc();
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }

   @Override
   public void onClose() {
      this.minecraft.gui.setScreen(this.parent);
   }

   private void button(GuiGraphicsExtractor ctx, int x, int y, int w, int h, String label, int mouseX, int mouseY, Runnable action) {
      boolean hov = Render2D.inside(mouseX, mouseY, x, y, w, h);
      Render2D.roundRect(ctx, x, y, w, h, 4, hov ? -14407358 : -14012603);
      ctx.text(this.font, label, x + (w - this.font.width(label)) / 2, y + (h - 8) / 2, -1578509, false);
      this.hits.add(new EnchantCalcScreen.Hit(x, y, w, h, action));
   }

   @Override
   public void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
      this.hits.clear();
      context.fill(0, 0, this.width, this.height, -1610612736);
      int pw = Math.min(470, this.width - 16);
      int ph = Math.min(310, this.height - 16);
      int px = (this.width - pw) / 2;
      int py = (this.height - ph) / 2;
      Render2D.glow(context, px, py, pw, ph, 8, -7643914, 0.35F);
      Render2D.roundRect(context, px, py, pw, ph, 8, -15855591);
      Render2D.roundRect(context, px, py, pw, 26, 8, -15065296);
      context.text(this.font, Component.literal("⚒ Amboss-Rechner").withStyle(ChatFormatting.BOLD), px + 10, py + 9, -1578509, false);
      String sub = "Vanilla-Mechanik · günstigste Reihenfolge";
      context.text(this.font, sub, px + pw - 10 - this.font.width(sub), py + 9, -10656900, false);
      if (this.minecraft.level == null) {
         context.text(this.font, "Betritt eine Welt – die Verzauberungsdaten kommen vom Server.", px + 12, py + 40, -278748, false);
      } else {
         int y = py + 32;
         this.button(context, px + 10, y, 16, 18, "<", mouseX, mouseY, () -> {
            this.itemIndex = (this.itemIndex + ITEMS.length - 1) % ITEMS.length;
            this.refreshItem();
         });
         context.item(com.lennart.nebula.util.SafeStack.of(this.item()), px + 30, y + 1);
         String itemName = this.isBookTarget() ? "Nur Bücher kombinieren" : com.lennart.nebula.util.SafeStack.of(this.item()).getHoverName().getString();
         context.text(this.font, this.font.plainSubstrByWidth(itemName, 120), px + 50, y + 5, -1578509, false);
         this.button(context, px + 174, y, 16, 18, ">", mouseX, mouseY, () -> {
            this.itemIndex = (this.itemIndex + 1) % ITEMS.length;
            this.refreshItem();
         });
         int wx = px + 200;
         if (!this.isBookTarget()) {
            context.text(this.font, "Amboss-Nutzungen:", wx, y + 5, -7564628, false);
            int bx = wx + this.font.width("Amboss-Nutzungen:") + 4;
            this.button(context, bx, y, 14, 18, "-", mouseX, mouseY, () -> {
               this.work = Math.max(0, this.work - 1);
               this.recalc();
            });
            context.text(this.font, String.valueOf(this.work), bx + 19, y + 5, -1578509, false);
            this.button(context, bx + 30, y, 14, 18, "+", mouseX, mouseY, () -> {
               this.work = Math.min(6, this.work + 1);
               this.recalc();
            });
         }

         int cx = px + pw - 80;
         Render2D.toggleSwitch(context, cx, y + 3, this.creative ? 1.0F : 0.0F);
         context.text(this.font, "Kreativ", cx + 30, y + 5, -7564628, false);
         this.hits.add(new EnchantCalcScreen.Hit(cx, y, 72, 18, () -> {
            this.creative = !this.creative;
            this.recalc();
         }));
         int top = py + 58;
         int listW = (int)(pw * 0.48F);
         int lh = ph - (top - py) - 22;
         context.text(this.font, "Verzauberungen (Level 0 = aus)", px + 10, top - 4, -3885571, false);
         int listTop = top + 8;
         int listH = lh - 8;
         int maxScroll = Math.max(0, this.available.size() * 16 - listH);
         this.scroll = Math.max(0, Math.min(this.scroll, maxScroll));
         context.enableScissor(px + 6, listTop, px + 6 + listW, listTop + listH);

         for (int i = 0; i < this.available.size(); i++) {
            Holder<Enchantment> e = this.available.get(i);
            int ry = listTop + i * 16 - this.scroll;
            if (ry + 16 >= listTop && ry <= listTop + listH) {
               int lvl = this.chosen.getOrDefault(e, 0);
               boolean blocked = false;

               for (Holder<Enchantment> other : this.chosen.keySet()) {
                  if (other != e && !Enchantment.areCompatible(e, other)) {
                     blocked = true;
                  }
               }

               Render2D.roundRect(context, px + 8, ry, listW - 4, 14, 4, lvl > 0 ? -14536630 : -15394521);
               String name = this.font.plainSubstrByWidth(e.value().description().getString(), listW - 70);
               int color = lvl > 0 ? -1578509 : (blocked ? -10656900 : -7564628);
               context.text(this.font, name, px + 13, ry + 3, color, false);
               int bx = px + 8 + listW - 4 - 50;
               if (ry >= listTop - 2 && ry + 16 <= listTop + listH + 2) {
                  this.button(context, bx, ry + 1, 12, 12, "-", mouseX, mouseY, () -> this.change(e, -1));
                  this.button(context, bx + 34, ry + 1, 12, 12, "+", mouseX, mouseY, () -> this.change(e, 1));
               }

               String lv = lvl + "/" + e.value().getMaxLevel();
               context.text(this.font, lv, bx + 23 - this.font.width(lv) / 2, ry + 3, lvl > 0 ? -11870592 : -10656900, false);
            }
         }

         context.disableScissor();
         int rx = px + listW + 16;
         int rw = pw - listW - 26;
         Render2D.roundRect(context, rx, top - 6, rw, lh + 6, 6, -15394521);
         int ty = top;
         if (this.chosen.isEmpty()) {
            context.text(this.font, "Wähle links Verzauberungen.", rx + 8, top + 4, -7564628, false);
         } else if (this.result != null && this.result.possible()) {
            int ix = 1;

            for (AnvilCalc.Step s : this.result.steps()) {
               if (ty > top + lh - 44) {
                  context.text(this.font, "…", rx + 8, ty, -10656900, false);
                  break;
               }

               int c = s.cost() >= 30 ? -278748 : -1578509;
               context.text(this.font, ix + ". " + this.font.plainSubstrByWidth(s.left(), rw - 60), rx + 8, ty, -1578509, false);
               String cost = s.cost() + " Lvl";
               context.text(this.font, cost, rx + rw - 8 - this.font.width(cost), ty, c, false);
               context.text(this.font, "  + " + this.font.plainSubstrByWidth(s.right(), rw - 30), rx + 8, ty + 9, -7564628, false);
               ty += 21;
               ix++;
            }

            int by = top + lh - 30;
            Render2D.rect(context, rx + 6, by - 4, rw - 12, 1, -14802381);
            context.text(
               this.font, Component.literal("Gesamt: " + this.result.totalLevels() + " Level").withStyle(ChatFormatting.BOLD), rx + 8, by, -11870592, false
            );
            context.text(
               this.font,
               "≈ " + this.result.totalXp() + " XP-Punkte · danach " + this.result.finalWork() + " Nutzungen",
               rx + 8,
               by + 11,
               -7564628,
               false
            );
         } else {
            context.text(this.font, Component.literal("Zu teuer!").withStyle(ChatFormatting.BOLD), rx + 8, top + 4, -495247, false);
            context.text(this.font, "Jede Reihenfolge braucht", rx + 8, top + 18, -7564628, false);
            context.text(this.font, "einen Schritt ab 40 Level.", rx + 8, top + 28, -7564628, false);
         }

         if (!this.note.isEmpty()) {
            context.text(this.font, this.note, px + 10, py + ph - 13, -278748, false);
         } else {
            context.text(this.font, "Ein Buch je Verzauberung · Bücher werden zur Hälfte berechnet", px + 10, py + ph - 13, -10656900, false);
         }
      }
   }

   @Override
   public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
      for (EnchantCalcScreen.Hit h : List.copyOf(this.hits)) {
         if (Render2D.inside(click.x(), click.y(), h.x, h.y, h.w, h.h)) {
            h.action.run();
            return true;
         }
      }

      return super.mouseClicked(click, doubled);
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      this.scroll -= (int)(verticalAmount * 16.0 * 2.0);
      return true;
   }

   private record Hit(int x, int y, int w, int h, Runnable action) {
   }
}
