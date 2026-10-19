package com.lennart.nebula.gui;

import com.mojang.blaze3d.platform.InputConstants;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.item.equipment.trim.TrimMaterial;
import net.minecraft.world.item.equipment.trim.TrimPattern;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class ArmorTrimScreen extends Screen {
   private static final String[] SET_NAMES = new String[]{"Leder", "Kette", "Eisen", "Gold", "Diamant", "Netherit", "Kupfer"};
   private static final Item[][] SETS = new Item[][]{
      {Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS},
      {Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS},
      {Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS},
      {Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS},
      {Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS},
      {Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS},
      {Items.COPPER_HELMET, Items.COPPER_CHESTPLATE, Items.COPPER_LEGGINGS, Items.COPPER_BOOTS}
   };
   private static final EquipmentSlot[] SLOTS = new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
   private static final String[] SLOT_NAMES = new String[]{"Helm", "Brustplatte", "Hose", "Stiefel"};
   private static final int[] DYES = new int[]{
      10511680, 16383998, 10329495, 4673362, 1908001, 11546150, 16351261, 16701501, 8439583, 6192150, 1481884, 3847130, 3949738, 8991416, 13061821, 15961002
   };
   private final Screen parent;
   private final List<Holder<TrimPattern>> patterns = new ArrayList<>();
   private final List<Holder<TrimMaterial>> materials = new ArrayList<>();
   private final ArmorTrimScreen.Piece[] pieces = new ArmorTrimScreen.Piece[4];
   private int target = -1;
   private LivingEntity model;
   private float yaw = 200.0F;
   private float pitch = -8.0F;
   private boolean spin = true;
   private boolean dragging;
   private double lastX;
   private double lastY;
   private long lastFrame = System.nanoTime();
   private final List<ArmorTrimScreen.Hit> hits = new ArrayList<>();
   private String tooltip;

   public ArmorTrimScreen(Screen parent) {
      super(Component.literal("Armor Trims"));
      this.parent = parent;

      for (int i = 0; i < 4; i++) {
         this.pieces[i] = new ArmorTrimScreen.Piece(5, null, null, DYES[0], true);
      }
   }

   @Override
   protected void init() {
      if (this.minecraft.level != null && this.patterns.isEmpty()) {
         Registry<TrimPattern> p = this.minecraft.level.registryAccess().lookupOrThrow(Registries.TRIM_PATTERN);
         p.listElements().sorted(Comparator.comparing(e -> idOf(e).getPath())).forEach(this.patterns::add);
         Registry<TrimMaterial> m = this.minecraft.level.registryAccess().lookupOrThrow(Registries.TRIM_MATERIAL);
         m.listElements().sorted(Comparator.comparing(e -> idOf(e).getPath())).forEach(this.materials::add);
         Holder<TrimPattern> first = this.patterns
            .stream()
            .filter(e -> idOf((Holder<?>)e).getPath().equals("sentry"))
            .findFirst()
            .orElse(this.patterns.isEmpty() ? null : this.patterns.get(0));
         Holder<TrimMaterial> gold = this.materials
            .stream()
            .filter(e -> idOf((Holder<?>)e).getPath().equals("gold"))
            .findFirst()
            .orElse(this.materials.isEmpty() ? null : this.materials.get(0));

         for (ArmorTrimScreen.Piece piece : this.pieces) {
            piece.pattern = first;
            piece.material = gold;
         }

         this.restore();
      }

      if (this.model == null && this.minecraft.level != null && this.minecraft.player == null) {
         this.model = new ArmorStand(this.minecraft.level, 0.0, 0.0, 0.0);
      }
   }

   /** Letzte Auswahl wiederherstellen (auch nach einem Neustart). */
   private void restore() {
      com.google.gson.JsonObject st = ToolState.get("armor_trims");
      this.spin = ToolState.getBool(st, "spin", true);
      this.target = Math.max(-1, Math.min(3, ToolState.getInt(st, "target", -1)));

      for (int i = 0; i < 4; i++) {
         if (!st.has("p" + i) || !st.get("p" + i).isJsonObject()) {
            continue;
         }

         com.google.gson.JsonObject p = st.getAsJsonObject("p" + i);
         ArmorTrimScreen.Piece piece = this.pieces[i];
         piece.set = Math.max(0, Math.min(SETS.length - 1, ToolState.getInt(p, "set", piece.set)));
         piece.dye = ToolState.getInt(p, "dye", piece.dye);
         piece.visible = ToolState.getBool(p, "visible", true);
         String pat = ToolState.getString(p, "pattern", null);
         if ("-".equals(pat)) {
            piece.pattern = null;
         } else if (pat != null) {
            for (Holder<TrimPattern> h : this.patterns) {
               if (idOf(h).toString().equals(pat)) {
                  piece.pattern = h;
               }
            }
         }

         String mat = ToolState.getString(p, "material", null);
         if (mat != null) {
            for (Holder<TrimMaterial> h : this.materials) {
               if (idOf(h).toString().equals(mat)) {
                  piece.material = h;
               }
            }
         }
      }
   }

   @Override
   public void removed() {
      com.google.gson.JsonObject st = new com.google.gson.JsonObject();
      st.addProperty("spin", this.spin);
      st.addProperty("target", this.target);

      for (int i = 0; i < 4; i++) {
         ArmorTrimScreen.Piece piece = this.pieces[i];
         com.google.gson.JsonObject p = new com.google.gson.JsonObject();
         p.addProperty("set", piece.set);
         p.addProperty("dye", piece.dye);
         p.addProperty("visible", piece.visible);
         p.addProperty("pattern", piece.pattern == null ? "-" : idOf(piece.pattern).toString());
         if (piece.material != null) {
            p.addProperty("material", idOf(piece.material).toString());
         }

         st.add("p" + i, p);
      }

      ToolState.put("armor_trims", st);
      super.removed();
   }

   private static Identifier idOf(Holder<?> e) {
      return e.unwrapKey().map(k -> k.identifier()).orElse(Identifier.withDefaultNamespace("unknown"));
   }

   private static Item templateFor(Holder<TrimPattern> pattern) {
      Identifier id = idOf(pattern);
      Item item = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(id.getNamespace(), id.getPath() + "_armor_trim_smithing_template"));
      return item == Items.AIR ? Items.PAPER : item;
   }

   private static Item ingredientFor(Holder<TrimMaterial> material) {
      String p = idOf(material).getPath();

      return switch (p) {
         case "quartz" -> Items.QUARTZ;
         case "iron" -> Items.IRON_INGOT;
         case "netherite" -> Items.NETHERITE_INGOT;
         case "redstone" -> Items.REDSTONE;
         case "copper" -> Items.COPPER_INGOT;
         case "gold" -> Items.GOLD_INGOT;
         case "emerald" -> Items.EMERALD;
         case "diamond" -> Items.DIAMOND;
         case "lapis" -> Items.LAPIS_LAZULI;
         case "amethyst" -> Items.AMETHYST_SHARD;
         case "resin" -> Items.RESIN_BRICK;
         default -> {
            Item item = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(idOf(material).getNamespace(), p));
            yield item == Items.AIR ? Items.PAPER : item;
         }
      };
   }

   private ItemStack stackFor(int slot) {
      ArmorTrimScreen.Piece piece = this.pieces[slot];
      if (!piece.visible) {
         return ItemStack.EMPTY;
      } else {
         ItemStack stack = com.lennart.nebula.util.SafeStack.of(SETS[piece.set][slot]);
         if (piece.pattern != null && piece.material != null) {
            stack.set(DataComponents.TRIM, new ArmorTrim(piece.material, piece.pattern));
         }

         if (piece.set == 0) {
            stack.set(DataComponents.DYED_COLOR, new DyedItemColor(piece.dye));
         }

         return stack;
      }
   }

   private void apply(Consumer<ArmorTrimScreen.Piece> change) {
      if (this.target < 0) {
         for (ArmorTrimScreen.Piece p : this.pieces) {
            change.accept(p);
         }
      } else {
         change.accept(this.pieces[this.target]);
      }
   }

   private ArmorTrimScreen.Piece current() {
      return this.pieces[Math.max(0, this.target)];
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }

   @Override
   public void onClose() {
      this.minecraft.setScreen(this.parent);
   }

   @Override
   public void render(GuiGraphics context, int mouseX, int mouseY, float deltaTicks) {
      long now = System.nanoTime();
      float dt = Math.min(0.1F, (float)(now - this.lastFrame) / 1.0E9F);
      this.lastFrame = now;
      if (this.spin && !this.dragging) {
         this.yaw += dt * 28.0F;
      }

      this.hits.clear();
      this.tooltip = null;
      context.fill(0, 0, this.width, this.height, -1073214958);
      int pw = Math.min(520, this.width - 16);
      int ph = Math.min(320, this.height - 16);
      int px = (this.width - pw) / 2;
      int py = (this.height - ph) / 2;
      Render2D.glow(context, px, py, pw, ph, 8, -7643914, 0.35F);
      Render2D.roundRect(context, px, py, pw, ph, 8, -15855591);
      Render2D.roundRect(context, px, py, pw, 26, 8, -15065296);
      context.drawString(this.font, Component.literal("✦ Armor Trim Vorschau").withStyle(ChatFormatting.BOLD), px + 10, py + 9, -1578509, false);
      String hint = "Ziehen = drehen";
      context.drawString(this.font, hint, px + pw - this.font.width(hint) - 10, py + 9, -10656900, false);
      int mw = Math.round(pw * 0.4F);
      int mx = px + 8;
      int my = py + 32;
      int mh = ph - 70;
      Render2D.roundRect(context, mx, my, mw, mh, 6, -15723487);
      context.fillGradient(mx + 1, my + mh / 2, mx + mw - 1, my + mh - 1, 0, 816868351);
      this.drawModel(context, mx, my, mw, mh);
      int sy = my + mh + 6;
      int chipW = (mw - 16) / 5;
      this.chip(context, mx, sy, chipW, 24, this.target < 0, null, "Alle Teile", mouseX, mouseY, () -> this.target = -1, "Alle");

      for (int i = 0; i < 4; i++) {
         int idx = i;
         ArmorTrimScreen.Piece piece = this.pieces[i];
         int cx = mx + (i + 1) * (chipW + 4);
         this.chip(
            context,
            cx,
            sy,
            chipW,
            24,
            this.target == i,
            com.lennart.nebula.util.SafeStack.of(SETS[piece.set][i]),
            SLOT_NAMES[i] + (piece.visible ? "" : " (aus)") + " – Rechtsklick: an/aus",
            mouseX,
            mouseY,
            () -> this.target = idx,
            null
         );
         this.hits.add(new ArmorTrimScreen.Hit(cx, sy, chipW, 24, null, () -> piece.visible = !piece.visible));
         if (!piece.visible) {
            context.fill(cx + 2, sy + 11, cx + chipW - 2, sy + 13, -495247);
         }
      }

      int toggleY = py + ph - 14;
      String spinLabel = (this.spin ? "■ " : "▶ ") + "Drehen";
      context.drawString(this.font, spinLabel, mx + 2, toggleY, Render2D.inside(mouseX, mouseY, mx, toggleY - 2, 60, 12) ? -1578509 : -7564628, false);
      this.hits.add(new ArmorTrimScreen.Hit(mx, toggleY - 2, 60, 12, () -> this.spin = !this.spin, null));
      int rx = mx + mw + 10;
      int rw = px + pw - 8 - rx;
      int y = py + 34;
      String editing = this.target < 0 ? "Alle Teile" : SLOT_NAMES[this.target];
      context.drawString(this.font, Component.literal("Bearbeiten: ").append(Component.literal(editing).withStyle(ChatFormatting.LIGHT_PURPLE)), rx, y, -7564628, false);
      y += 13;
      y = this.section(context, rx, y, "Rüstung");

      for (int ix = 0; ix < SETS.length; ix++) {
         int cx = rx + ix * 26;
         int i = ix;
         this.chip(
            context, cx, y, 24, 24, this.current().set == ix, com.lennart.nebula.util.SafeStack.of(SETS[ix][1]), SET_NAMES[ix], mouseX, mouseY, () -> this.apply(p -> p.set = i), null
         );
      }

      y += 28;
      if (this.current().set == 0) {
         for (int ix = 0; ix < DYES.length; ix++) {
            int dye = DYES[ix];
            int cx = rx + ix % 16 * 13;
            boolean sel = this.current().dye == dye;
            Render2D.roundRect(context, cx, y, 11, 11, 3, sel ? -1 : -14275777);
            Render2D.roundRect(context, cx + 1, y + 1, 9, 9, 2, 0xFF000000 | dye);
            this.hits.add(new ArmorTrimScreen.Hit(cx, y, 11, 11, () -> this.apply(p -> p.dye = dye), null));
         }

         y += 15;
      }

      y = this.section(context, rx, y, "Trim-Muster");
      int perRow = Math.max(1, rw / 22);
      this.chip(
         context,
         rx,
         y,
         20,
         20,
         this.current().pattern == null,
         com.lennart.nebula.util.SafeStack.of(Items.BARRIER),
         "Kein Trim",
         mouseX,
         mouseY,
         () -> this.apply(p -> p.pattern = null),
         null
      );

      for (int ix = 0; ix < this.patterns.size(); ix++) {
         Holder<TrimPattern> e = this.patterns.get(ix);
         int slot = ix + 1;
         int cx = rx + slot % perRow * 22;
         int cy = y + slot / perRow * 22;
         this.chip(
            context,
            cx,
            cy,
            20,
            20,
            e.equals(this.current().pattern),
            com.lennart.nebula.util.SafeStack.of(templateFor(e)),
            e.value().description().getString(),
            mouseX,
            mouseY,
            () -> this.apply(p -> p.pattern = e),
            null
         );
      }

      y += (this.patterns.size() + 1 + perRow - 1) / perRow * 22 + 4;
      y = this.section(context, rx, y, "Trim-Material");

      for (int ix = 0; ix < this.materials.size(); ix++) {
         Holder<TrimMaterial> e = this.materials.get(ix);
         int cx = rx + ix % perRow * 22;
         int cy = y + ix / perRow * 22;
         this.chip(
            context,
            cx,
            cy,
            20,
            20,
            e.equals(this.current().material),
            com.lennart.nebula.util.SafeStack.of(ingredientFor(e)),
            e.value().description().getString(),
            mouseX,
            mouseY,
            () -> this.apply(p -> p.material = e),
            null
         );
      }

      if (this.tooltip != null) {
         context.setTooltipForNextFrame(this.font, Component.literal(this.tooltip), mouseX, mouseY);
      }
   }

   private int section(GuiGraphics ctx, int x, int y, String title) {
      ctx.drawString(this.font, Component.literal(title).withStyle(ChatFormatting.BOLD), x, y, -1578509, false);
      return y + 12;
   }

   private void chip(
      GuiGraphics ctx, int x, int y, int w, int h, boolean selected, ItemStack icon, String tip, int mouseX, int mouseY, Runnable click, String label
   ) {
      boolean hov = Render2D.inside(mouseX, mouseY, x, y, w, h);
      Render2D.roundRect(ctx, x, y, w, h, 4, selected ? -7643914 : (hov ? -14407358 : -15394521));
      if (selected) {
         Render2D.roundRect(ctx, x + 1, y + 1, w - 2, h - 2, 3, -14016699);
      }

      if (icon != null) {
         ctx.renderItem(icon, x + (w - 16) / 2, y + (h - 16) / 2);
      } else if (label != null) {
         int tw = this.font.width(label);
         ctx.drawString(this.font, label, x + (w - tw) / 2, y + (h - 8) / 2, selected ? -1 : -7564628, false);
      }

      if (hov) {
         this.tooltip = tip;
      }

      this.hits.add(new ArmorTrimScreen.Hit(x, y, w, h, click, null));
   }

   /**
    * Dein Charakter mit der gewählten Rüstung. Gezeichnet wird der echte Spieler (wie in der Kosmetik-Vorschau); die
    * Rüstung wird nur im Render-Zustand ersetzt – dein Inventar bleibt unberührt.
    */
   private void drawModel(GuiGraphics ctx, int x, int y, int w, int h) {
      LivingEntity entity = this.minecraft.player != null ? this.minecraft.player : this.model;
      if (entity == null) {
         return;
      }

      try {
         EntityRenderDispatcher manager = this.minecraft.getEntityRenderDispatcher();
         EntityRenderer<? super LivingEntity, ?> renderer = manager.getRenderer(entity);
         EntityRenderState state = renderer.createRenderState(entity, 1.0F);
         state.lightCoords = 15728880;
         state.shadowPieces.clear();
         state.outlineColor = 0;
         state.nameTag = null;
         if (state instanceof LivingEntityRenderState living) {
            living.bodyRot = this.yaw;
            living.yRot = 0.0F;
            living.xRot = 0.0F;
            living.walkAnimationSpeed = 0.0F;
            living.walkAnimationPos = 0.0F;
            living.hasRedOverlay = false;
            living.deathTime = 0.0F;
            living.boundingBoxWidth = living.boundingBoxWidth / living.scale;
            living.boundingBoxHeight = living.boundingBoxHeight / living.scale;
            living.scale = 1.0F;
            living.headItem.clear();
            living.wornHeadType = null;
         }

         if (state instanceof net.minecraft.client.renderer.entity.state.HumanoidRenderState humanoid) {
            humanoid.headEquipment = this.stackFor(0);
            humanoid.chestEquipment = this.stackFor(1);
            humanoid.legsEquipment = this.stackFor(2);
            humanoid.feetEquipment = this.stackFor(3);
            humanoid.rightHandItemState.clear();
            humanoid.leftHandItemState.clear();
            humanoid.rightHandItemStack = ItemStack.EMPTY;
            humanoid.leftHandItemStack = ItemStack.EMPTY;
         }

         int x1 = x + 2;
         int x2 = x + w - 2;
         int y1 = y + 2;
         int y2 = y + h - 2;
         int size = Math.round(Math.min(w / 1.5F, (h - 12) / 2.25F));
         int feet = y2 - 8;
         float cy = (y1 + y2) / 2.0F;
         Quaternionf camera = new Quaternionf().rotateX((float)Math.toRadians(this.pitch));
         Quaternionf rotation = new Quaternionf().rotateZ((float)Math.PI).mul(camera);
         ctx.enableScissor(x1, y1, x2, y2);
         ctx.submitEntityRenderState(state, size, new Vector3f(0.0F, (feet - cy) / size, 0.0F), rotation, camera, x1, y1, x2, y2);
         ctx.disableScissor();
      } catch (RuntimeException var14) {
         com.lennart.nebula.NebulaClient.LOGGER.debug("Armor-Trim-Vorschau: {}", var14.toString());
      }
   }
   @Override
   public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
      double mx = click.x();
      double my = click.y();

      for (int i = this.hits.size() - 1; i >= 0; i--) {
         ArmorTrimScreen.Hit hit = this.hits.get(i);
         if (Render2D.inside(mx, my, hit.x(), hit.y(), hit.w(), hit.h())) {
            Runnable action = click.button() == InputConstants.MOUSE_BUTTON_RIGHT ? hit.right() : hit.left();
            if (action != null) {
               action.run();
               return true;
            }
         }
      }

      if (click.button() == InputConstants.MOUSE_BUTTON_LEFT) {
         this.dragging = true;
         this.lastX = mx;
         this.lastY = my;
         return true;
      } else {
         return super.mouseClicked(click, doubled);
      }
   }

   @Override
   public boolean mouseDragged(MouseButtonEvent click, double offsetX, double offsetY) {
      if (this.dragging) {
         this.yaw = this.yaw - (float)(click.x() - this.lastX) * 1.6F;
         this.pitch = Math.max(-40.0F, Math.min(40.0F, this.pitch + (float)(click.y() - this.lastY) * 0.8F));
         this.lastX = click.x();
         this.lastY = click.y();
         return true;
      } else {
         return super.mouseDragged(click, offsetX, offsetY);
      }
   }

   @Override
   public boolean mouseReleased(MouseButtonEvent click) {
      this.dragging = false;
      return super.mouseReleased(click);
   }

   private record Hit(int x, int y, int w, int h, Runnable left, Runnable right) {
   }

   private static final class Piece {
      int set;
      Holder<TrimPattern> pattern;
      Holder<TrimMaterial> material;
      int dye;
      boolean visible;

      Piece(int set, Holder<TrimPattern> pattern, Holder<TrimMaterial> material, int dye, boolean visible) {
         this.set = set;
         this.pattern = pattern;
         this.material = material;
         this.dye = dye;
         this.visible = visible;
      }
   }
}
