package com.lennart.nebula.gui;

import com.mojang.blaze3d.platform.InputConstants;

import com.lennart.nebula.gui.widget.MeowlTextField;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

/** Brau-Helfer: links alle Tränke (mit Suche), rechts Schritt für Schritt, wie man sie braut, plus Varianten. */
public class BrewingScreen extends com.lennart.nebula.compat.MeowlBaseScreen {
   private static final int ROW = 20;
   private static List<BrewingScreen.Recipe> recipes;
   private final Screen parent;
   private final MeowlTextField search = new MeowlTextField(24).placeholder("Trank suchen …");
   private final List<int[]> rowRects = new ArrayList<>();
   private final List<BrewingScreen.Recipe> rowRecipes = new ArrayList<>();
   private BrewingScreen.Recipe selected;
   private float scroll;

   /** result entsteht aus from + ingredient; longV/strongV = Redstone/Glowstone-Varianten (oder null). */
   private record Recipe(Holder<Potion> result, Holder<Potion> from, Item ingredient, Holder<Potion> longV, Holder<Potion> strongV) {
   }

   private record Step(Item ingredient, Holder<Potion> result) {
   }

   public BrewingScreen(Screen parent) {
      super(Component.literal("Brau-Helfer"));
      this.parent = parent;
      // letzte Auswahl wiederherstellen
      com.google.gson.JsonObject st = ToolState.get("brewing");
      int idx = ToolState.getInt(st, "selected", -1);
      if (idx >= 0 && idx < recipes().size()) {
         this.selected = recipes().get(idx);
      }

      this.search.setText(ToolState.getString(st, "search", ""));
      this.scroll = ToolState.getInt(st, "scroll", 0);
      this.search.onChange(s -> this.scroll = 0.0F);
   }

   @Override
   public void removed() {
      com.google.gson.JsonObject st = new com.google.gson.JsonObject();
      st.addProperty("selected", this.selected == null ? -1 : recipes().indexOf(this.selected));
      st.addProperty("search", this.search.getText());
      st.addProperty("scroll", Math.round(this.scroll));
      ToolState.put("brewing", st);
      super.removed();
   }

   private static List<BrewingScreen.Recipe> recipes() {
      if (recipes == null) {
         Holder<Potion> a = Potions.AWKWARD;
         List<BrewingScreen.Recipe> r = new ArrayList<>();
         r.add(new BrewingScreen.Recipe(Potions.SWIFTNESS, a, Items.SUGAR, Potions.LONG_SWIFTNESS, Potions.STRONG_SWIFTNESS));
         r.add(new BrewingScreen.Recipe(Potions.LEAPING, a, Items.RABBIT_FOOT, Potions.LONG_LEAPING, Potions.STRONG_LEAPING));
         r.add(new BrewingScreen.Recipe(Potions.STRENGTH, a, Items.BLAZE_POWDER, Potions.LONG_STRENGTH, Potions.STRONG_STRENGTH));
         r.add(new BrewingScreen.Recipe(Potions.HEALING, a, Items.GLISTERING_MELON_SLICE, null, Potions.STRONG_HEALING));
         r.add(new BrewingScreen.Recipe(Potions.REGENERATION, a, Items.GHAST_TEAR, Potions.LONG_REGENERATION, Potions.STRONG_REGENERATION));
         r.add(new BrewingScreen.Recipe(Potions.FIRE_RESISTANCE, a, Items.MAGMA_CREAM, Potions.LONG_FIRE_RESISTANCE, null));
         r.add(new BrewingScreen.Recipe(Potions.WATER_BREATHING, a, Items.PUFFERFISH, Potions.LONG_WATER_BREATHING, null));
         r.add(new BrewingScreen.Recipe(Potions.NIGHT_VISION, a, Items.GOLDEN_CARROT, Potions.LONG_NIGHT_VISION, null));
         r.add(new BrewingScreen.Recipe(Potions.INVISIBILITY, Potions.NIGHT_VISION, Items.FERMENTED_SPIDER_EYE, Potions.LONG_INVISIBILITY, null));
         r.add(new BrewingScreen.Recipe(Potions.SLOW_FALLING, a, Items.PHANTOM_MEMBRANE, Potions.LONG_SLOW_FALLING, null));
         r.add(new BrewingScreen.Recipe(Potions.TURTLE_MASTER, a, Items.TURTLE_HELMET, Potions.LONG_TURTLE_MASTER, Potions.STRONG_TURTLE_MASTER));
         r.add(new BrewingScreen.Recipe(Potions.POISON, a, Items.SPIDER_EYE, Potions.LONG_POISON, Potions.STRONG_POISON));
         r.add(new BrewingScreen.Recipe(Potions.HARMING, Potions.POISON, Items.FERMENTED_SPIDER_EYE, null, Potions.STRONG_HARMING));
         r.add(new BrewingScreen.Recipe(Potions.SLOWNESS, Potions.SWIFTNESS, Items.FERMENTED_SPIDER_EYE, Potions.LONG_SLOWNESS, Potions.STRONG_SLOWNESS));
         r.add(new BrewingScreen.Recipe(Potions.WEAKNESS, Potions.WATER, Items.FERMENTED_SPIDER_EYE, Potions.LONG_WEAKNESS, null));
         r.add(new BrewingScreen.Recipe(Potions.WIND_CHARGED, a, Items.BREEZE_ROD, null, null));
         r.add(new BrewingScreen.Recipe(Potions.WEAVING, a, Items.COBWEB, null, null));
         r.add(new BrewingScreen.Recipe(Potions.OOZING, a, Items.SLIME_BLOCK, null, null));
         r.add(new BrewingScreen.Recipe(Potions.INFESTED, a, Items.STONE, null, null));
         recipes = r;
      }

      return recipes;
   }

   private static BrewingScreen.Recipe recipeFor(Holder<Potion> p) {
      for (BrewingScreen.Recipe r : recipes()) {
         if (r.result().equals(p)) {
            return r;
         }
      }

      return null;
   }

   /** Alle Schritte ab der Wasserflasche. */
   private static List<BrewingScreen.Step> steps(BrewingScreen.Recipe r) {
      List<BrewingScreen.Step> out = new ArrayList<>();
      if (r.from().equals(Potions.AWKWARD)) {
         out.add(new BrewingScreen.Step(Items.NETHER_WART, Potions.AWKWARD));
      } else if (!r.from().equals(Potions.WATER)) {
         BrewingScreen.Recipe before = recipeFor(r.from());
         if (before != null) {
            out.addAll(steps(before));
         }
      }

      out.add(new BrewingScreen.Step(r.ingredient(), r.result()));
      return out;
   }

   private static ItemStack potion(Holder<Potion> p) {
      return PotionContents.createItemStack(Items.POTION, p);
   }

   private static String name(Holder<Potion> p) {
      return potion(p).getHoverName().getString();
   }

   private static String name(Item i) {
      return com.lennart.nebula.util.SafeStack.of(i).getHoverName().getString();
   }

   private List<BrewingScreen.Recipe> filtered() {
      String q = this.search.getText().trim().toLowerCase(Locale.ROOT);
      List<BrewingScreen.Recipe> out = new ArrayList<>();

      for (BrewingScreen.Recipe r : recipes()) {
         if (q.isEmpty() || name(r.result()).toLowerCase(Locale.ROOT).contains(q) || name(r.ingredient()).toLowerCase(Locale.ROOT).contains(q)) {
            out.add(r);
         }
      }

      return out;
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }

   @Override
   public void onClose() {
      this.minecraft.gui.setScreen(this.parent);
   }

   @Override
   public void renderBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
   }

   @Override
   public void render(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
      this.rowRects.clear();
      this.rowRecipes.clear();
      ctx.fill(0, 0, this.width, this.height, 0xB005060C);
      int pw = Math.min(520, this.width - 20);
      int ph = Math.min(300, this.height - 20);
      int px = (this.width - pw) / 2;
      int py = (this.height - ph) / 2;
      Render2D.roundRect(ctx, px, py, pw, ph, 8, 0xF0101420);
      ctx.text(this.font, Component.literal("Brau-Helfer").withStyle(ChatFormatting.BOLD), px + 10, py + 8, -1, true);
      int listW = Math.min(170, pw / 3 + 10);
      int lx = px + 10;
      this.search.setBounds(lx, py + 22, listW, 16);
      this.search.render(ctx, this.font, false);
      int listTop = py + 42;
      int listBottom = py + ph - 8;
      List<BrewingScreen.Recipe> list = this.filtered();
      if (this.selected == null && !list.isEmpty()) {
         this.selected = list.get(0);
      }

      float maxScroll = Math.max(0, list.size() * ROW - (listBottom - listTop));
      this.scroll = Math.max(0.0F, Math.min(maxScroll, this.scroll));
      ctx.enableScissor(lx, listTop, lx + listW, listBottom);
      if (list.isEmpty()) {
         ctx.text(this.font, "Nichts gefunden", lx + 4, listTop + 4, -7564628, false);
      }

      for (int i = 0; i < list.size(); i++) {
         BrewingScreen.Recipe r = list.get(i);
         int y = listTop + i * ROW - Math.round(this.scroll);
         if (y + ROW < listTop || y > listBottom) {
            continue;
         }

         boolean sel = r == this.selected;
         boolean hot = mouseY >= listTop && mouseY < listBottom && Render2D.inside(mouseX, mouseY, lx, y, listW, ROW - 2);
         Render2D.roundRect(ctx, lx, y, listW, ROW - 2, 4, sel ? 0xE04C3A8A : (hot ? 0xE0303A58 : 0xC01C2234));
         ctx.item(potion(r.result()), lx + 2, y + 1);
         ctx.text(this.font, this.font.plainSubstrByWidth(name(r.result()), listW - 24), lx + 21, y + 5, -1, false);
         if (mouseY >= listTop && mouseY < listBottom) {
            this.rowRects.add(new int[]{lx, y, listW, ROW - 2});
            this.rowRecipes.add(r);
         }
      }

      ctx.disableScissor();
      if (this.selected != null) {
         this.renderDetail(ctx, lx + listW + 12, py + 22, px + pw - 10 - (lx + listW + 12), this.selected);
      }
   }

   private void renderDetail(GuiGraphicsExtractor ctx, int x, int y, int w, BrewingScreen.Recipe r) {
      ctx.item(potion(r.result()), x, y);
      ctx.text(this.font, Component.literal(name(r.result())).withStyle(ChatFormatting.BOLD), x + 20, y + 4, -1, true);
      y += 22;
      ctx.text(this.font, "Rezept", x, y, -3885571, false);
      y += 12;
      // Start: Wasserflasche
      Render2D.roundRect(ctx, x, y, w, 20, 4, 0xC01C2234);
      ctx.item(potion(Potions.WATER), x + 2, y + 2);
      ctx.text(this.font, name(Potions.WATER), x + 22, y + 6, -1, false);
      y += 22;

      int n = 1;
      for (BrewingScreen.Step s : steps(r)) {
         Render2D.roundRect(ctx, x, y, w, 20, 4, 0xC01C2234);
         ctx.text(this.font, n++ + ".", x + 5, y + 6, -7564628, false);
         ctx.item(com.lennart.nebula.util.SafeStack.of(s.ingredient()), x + 18, y + 2);
         String ing = name(s.ingredient());
         ctx.text(this.font, ing, x + 38, y + 6, 0xFFFFD27A, false);
         int ax = x + 44 + this.font.width(ing);
         ctx.text(this.font, "→", ax, y + 6, -7564628, false);
         ctx.item(potion(s.result()), ax + 12, y + 2);
         ctx.text(this.font, this.font.plainSubstrByWidth(name(s.result()), Math.max(10, x + w - ax - 34)), ax + 31, y + 6, -1, false);
         y += 22;
      }

      y += 6;
      ctx.text(this.font, "Varianten (danach hinzufügen)", x, y, -3885571, false);
      y += 12;
      y = this.variant(ctx, x, y, w, Items.REDSTONE, r.longV() != null ? potion(r.longV()) : null, "Länger");
      y = this.variant(ctx, x, y, w, Items.GLOWSTONE_DUST, r.strongV() != null ? potion(r.strongV()) : null, "Stärker (Stufe II)");
      y = this.variant(ctx, x, y, w, Items.GUNPOWDER, PotionContents.createItemStack(Items.SPLASH_POTION, r.result()), "Wurftrank");
      y = this.variant(ctx, x, y, w, Items.DRAGON_BREATH, PotionContents.createItemStack(Items.LINGERING_POTION, r.result()), "Verweiltrank (nach Wurftrank)");
      y += 6;
      ctx.text(this.font, "Brennstoff: " + name(Items.BLAZE_POWDER) + " · bis zu 3 Flaschen gleichzeitig", x, y, -10656900, false);
   }

   private int variant(GuiGraphicsExtractor ctx, int x, int y, int w, Item ingredient, ItemStack out, String label) {
      Render2D.roundRect(ctx, x, y, w, 20, 4, out == null ? 0x801C2234 : 0xC01C2234);
      ctx.item(com.lennart.nebula.util.SafeStack.of(ingredient), x + 2, y + 2);
      if (out == null) {
         ctx.text(this.font, label + " – gibt es nicht", x + 22, y + 6, -10656900, false);
      } else {
         ctx.text(this.font, label, x + 22, y + 6, 0xFFFFD27A, false);
         ctx.item(out, x + w - 20, y + 2);
      }

      return y + 22;
   }

   @Override
   public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
      if (this.search.mouseClicked(click.x(), click.y(), this.font)) {
         return true;
      } else {
         if (click.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            for (int i = 0; i < this.rowRects.size(); i++) {
               int[] r = this.rowRects.get(i);
               if (Render2D.inside(click.x(), click.y(), r[0], r[1], r[2], r[3])) {
                  this.selected = this.rowRecipes.get(i);
                  return true;
               }
            }
         }

         return super.mouseClicked(click, doubled);
      }
   }

   @Override
   public boolean mouseScrolled(double mx, double my, double h, double v) {
      this.scroll = Math.max(0.0F, this.scroll - (float)v * ROW);
      return true;
   }

   @Override
   public boolean charTyped(CharacterEvent input) {
      if (!this.search.isFocused() && input.codepoint() > 32) {
         this.search.setFocused(true);
      }

      return this.search.charTyped(input.codepoint()) || super.charTyped(input);
   }

   @Override
   public boolean keyPressed(KeyEvent input) {
      if (input.key() == InputConstants.KEY_RETURN && !this.filtered().isEmpty()) {
         this.selected = this.filtered().get(0);
         return true;
      } else if (this.search.isFocused() && input.key() != InputConstants.KEY_ESCAPE && this.search.keyPressed(input.key(), input.modifiers())) {
         return true;
      } else {
         return super.keyPressed(input);
      }
   }
}
